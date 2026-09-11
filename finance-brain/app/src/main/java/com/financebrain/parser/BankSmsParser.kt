package com.financebrain.parser

import com.financebrain.data.Direction
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Turns a bank alert SMS into a [ParsedTransaction], or null when the message is not a
 * completed money movement (OTP, promo, reminder, failed transaction, balance enquiry...).
 *
 * Strategy: identify the bank from sender id + signature, then run a set of ordered
 * patterns that cover the real formats of SBI, Federal Bank, HDFC, ICICI and PhonePe.
 * A generic pattern catches anything the bank specific ones miss.
 */
object BankSmsParser {

    private val amountRe = Regex(
        """(?:(?:INR|Rs\.?|₹)\s*)([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:INR|Rs\.?)""",
        RegexOption.IGNORE_CASE
    )

    private val ignoreRe = Regex(
        """\b(otp|one time password|will be debited|is due|due on|has been declined|declined|failed|unsuccessful|request(?:ed)? money|has requested|collect request|autopay mandate|e-?mandate|reminder|offer|cashback of up to|apply now|pre-?approved|win |congratulations|insufficient|not been processed|could not be processed|reversed|balance enquiry|avl bal in a/c \S+ is)\b""",
        RegexOption.IGNORE_CASE
    )

    private val debitRe = Regex(
        """\b(debited|debit(?:ed)? by|sent|paid|spent|withdrawn|purchase|txn of|transferred|payment of)\b""",
        RegexOption.IGNORE_CASE
    )
    private val creditRe = Regex(
        """\b(credited|deposited|received|refund(?:ed)?|credit(?:ed)? by|cr(?:edit)?\b.*?(?:salary|neft|imps|upi))\b""",
        RegexOption.IGNORE_CASE
    )

    private val tailRe = Regex(
        """(?:a/?c(?:ct|count)?|acct|card|account)\s*(?:no\.?\s*)?(?:ending\s*)?[:\s]*(?:[xX*]+|XX|\*)?[xX*]*(\d{3,6})\b""",
        RegexOption.IGNORE_CASE
    )
    private val balanceRe = Regex(
        """(?:avl(?:bl)?\.?\s*bal(?:ance)?|available\s*balance|bal(?:ance)?|clear\s*bal(?:ance)?)\s*(?:is|:|of)?\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val refRe = Regex(
        """(?:upi(?:\s*ref(?:\s*no)?)?|ref(?:erence)?(?:\s*no)?|txn\s*id|transaction\s*id|rrn|imps|neft)[:\s.#-]*([A-Za-z0-9]{6,})""",
        RegexOption.IGNORE_CASE
    )

    private val counterpartyPatterns = listOf(
        // "to VPA merchant@ybl", "to merchant@okaxis"
        Regex("""\b(?:to|towards)\s+(?:VPA\s+)?([A-Za-z0-9._-]+@[A-Za-z]+)""", RegexOption.IGNORE_CASE),
        // ICICI: "; JOHN DOE credited"
        Regex(""";\s*([A-Za-z0-9 &._'-]{2,60}?)\s+credited""", RegexOption.IGNORE_CASE),
        // "trf to X", "transfer to X", "Sent ... To X On", "paid to X"
        Regex("""\b(?:trf|transfer|transferred|sent|paid|payment)\s+(?:of\s+\S+\s+)?to\s+([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|ref|via|upi|for|\.|,|$))""", RegexOption.IGNORE_CASE),
        // Federal: "towards UPI/123/MERCHANT" or "towards MERCHANT"
        Regex("""\btowards\s+(?:UPI/\d+/)?([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|ref|avl|\.|,|$)|\.|$)""", RegexOption.IGNORE_CASE),
        // Cards: "at MERCHANT on", "on MERCHANT. Avl"
        Regex("""\b(?:at|@)\s+([A-Za-z0-9 &._'*-]{2,60}?)\s+on\b""", RegexOption.IGNORE_CASE),
        Regex("""\bon\s+\d[\d/\-A-Za-z:]*\s+(?:on|at)\s+([A-Za-z0-9 &._'*-]{2,60}?)(?:\.|,|\s+avl|$)""", RegexOption.IGNORE_CASE),
        // HDFC UPI: "Sent Rs.X From ... To MERCHANT On date"
        Regex("""\bTo\s+([A-Za-z0-9 &._'@-]{2,60}?)\s+On\s+\d""", RegexOption.IGNORE_CASE),
        // Credits: "from X", "by X", "for NEFT Cr-X"
        Regex("""\bfrom\s+(?:VPA\s+)?([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|ref|via|upi|in|to|\.|,|$)|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:cr|credit)\s*[-:]\s*([A-Za-z0-9 &._'-]{2,60}?)(?:\.|,|\s+avl|\s+ref|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bby\s+(?:transfer\s+from\s+|a/c\s+linked\s+to\s+mobile\s+\S+\s+)?([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|ref|avl|\.|,|$)|\.|$)""", RegexOption.IGNORE_CASE),
    )

    private val dateFormats = listOf(
        "ddMMMyy", "ddMMMyyyy", "dd-MMM-yy", "dd-MMM-yyyy", "dd/MM/yy", "dd/MM/yyyy",
        "dd-MM-yy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yy", "dd MMM yyyy", "ddMMyy"
    ).map { SimpleDateFormat(it, Locale.ENGLISH).apply { isLenient = false } }
    private val dateRe = Regex("""\b(\d{1,2}[-/ ]?(?:\d{1,2}|[A-Za-z]{3})[-/ ]?\d{2,4}|\d{4}-\d{2}-\d{2})\b""")

    fun identifyBank(sender: String?, body: String): String? {
        val s = (sender ?: "").uppercase()
        val b = body.uppercase()
        return when {
            s.contains("SBI") || s.contains("SBIINB") || s.contains("SBIUPI") || s.contains("SBIPSG") ||
                b.contains("-SBI") || b.contains("SBI BANK") || b.contains("STATE BANK") -> "SBI"
            s.contains("HDFC") || b.contains("HDFC BANK") || b.contains("HDFCBK") -> "HDFC"
            s.contains("ICICI") || b.contains("ICICI BANK") || b.contains("ICICIB") -> "ICICI"
            s.contains("FEDBNK") || s.contains("FEDERAL") || b.contains("FEDERAL BANK") ||
                b.contains("-FEDERAL") || b.contains("FEDBNK") -> "Federal Bank"
            s.contains("PHONPE") || s.contains("PHONEPE") || b.contains("PHONEPE") -> "PhonePe"
            s.contains("AXIS") || b.contains("AXIS BANK") -> "Axis Bank"
            s.contains("KOTAK") || b.contains("KOTAK") -> "Kotak"
            s.contains("PAYTM") || b.contains("PAYTM") -> "Paytm"
            else -> null
        }
    }

    fun parse(sender: String?, body: String, receivedAt: Long): ParsedTransaction? {
        val text = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val bank = identifyBank(sender, text) ?: return null
        if (ignoreRe.containsMatchIn(text)) return null

        val direction = detectDirection(text) ?: return null
        val amount = extractAmount(text) ?: return null
        if (amount <= 0) return null

        val tail = tailRe.find(text)?.groupValues?.get(1)?.takeLast(4)
        val balance = balanceRe.find(text)?.groupValues?.get(1)?.let(::toPaise)
        val reference = refRe.find(text)?.groupValues?.get(1)?.takeIf { it.length in 6..24 }
        val channel = detectChannel(text)
        val isCard = Regex("""\bcard\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("""\b(a/?c|account)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val counterparty = extractCounterparty(text, channel) ?: defaultCounterparty(channel, bank)
        val timestamp = extractDate(text) ?: receivedAt

        return ParsedTransaction(
            amountPaise = amount,
            direction = direction,
            bank = bank,
            accountTail = tail,
            counterparty = counterparty,
            channel = channel,
            reference = reference,
            balancePaise = balance,
            timestamp = timestamp,
            accountKind = if (isCard) "CARD" else "BANK",
        )
    }

    private fun detectDirection(text: String): Direction? {
        val d = debitRe.find(text)?.range?.first
        val c = creditRe.find(text)?.range?.first
        return when {
            d == null && c == null -> null
            d == null -> Direction.CREDIT
            c == null -> Direction.DEBIT
            // ICICI phrases both: "Acct debited ...; X credited" – the first verb wins.
            d <= c -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    private val verbAmountRe = Regex(
        """\b(?:debited|credited|sent|paid|spent|withdrawn|deposited|received)\s+(?:by|of|with|for)?\s*(?:INR|Rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun extractAmount(text: String): Long? {
        // "debited by 250.0" (SBI omits the currency) – the number right after the verb wins.
        verbAmountRe.find(text)?.groupValues?.get(1)?.let { return toPaise(it) }
        // Otherwise prefer the currency-tagged amount nearest to the direction verb.
        val verb = (debitRe.find(text) ?: creditRe.find(text))?.range?.first ?: 0
        val matches = amountRe.findAll(text).toList()
        if (matches.isEmpty()) return null
        val best = matches.minByOrNull { kotlin.math.abs(it.range.first - verb) } ?: matches.first()
        val raw = best.groupValues[1].ifEmpty { best.groupValues[2] }
        return toPaise(raw)
    }

    private fun detectChannel(text: String): String {
        val t = text.uppercase()
        return when {
            t.contains("ATM") || t.contains("WITHDRAWN") || t.contains("CASH WDL") -> "ATM"
            t.contains("UPI") || Regex("""@[A-Z]{2,}""").containsMatchIn(t) || t.contains("VPA") || t.contains("PHONEPE") -> "UPI"
            t.contains("NEFT") -> "NEFT"
            t.contains("IMPS") -> "IMPS"
            t.contains("RTGS") -> "RTGS"
            t.contains("CARD") || t.contains("POS ") || t.contains("SPENT") -> "CARD"
            t.contains("EMI") -> "EMI"
            t.contains("CHEQUE") || t.contains("CHQ") -> "CHEQUE"
            else -> "OTHER"
        }
    }

    private fun extractCounterparty(text: String, channel: String): String? {
        for (re in counterpartyPatterns) {
            val m = re.find(text) ?: continue
            val v = clean(m.groupValues[1])
            if (v.length >= 2 && !v.equals("you", true) && !v.contains("bank a/c", true)) return v
        }
        return null
    }

    private fun defaultCounterparty(channel: String, bank: String) = when (channel) {
        "ATM" -> "ATM Withdrawal"
        "NEFT", "IMPS", "RTGS" -> "Bank Transfer"
        else -> "$bank transaction"
    }

    private fun clean(v: String): String {
        var s = v.trim().trimEnd('.', ',', ';', ':', '-')
        s = s.replace(Regex("""^(?:VPA|UPI)\s+""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""\s+(?:on|ref|via)$""", RegexOption.IGNORE_CASE), "")
        return s.trim()
    }

    private fun extractDate(text: String): Long? {
        for (m in dateRe.findAll(text)) {
            val token = m.groupValues[1]
            for (f in dateFormats) {
                try {
                    val d = f.parse(token) ?: continue
                    val t = d.time
                    // Reject nonsense like year 0026 from 2-digit parsing drift or far-future dates.
                    if (t in 1_262_304_000_000L..(System.currentTimeMillis() + 2 * 86_400_000L)) return t
                } catch (_: Exception) { }
            }
        }
        return null
    }

    fun toPaise(raw: String): Long? {
        val n = raw.replace(",", "").toDoubleOrNull() ?: return null
        return (n * 100).roundToLong()
    }
}
