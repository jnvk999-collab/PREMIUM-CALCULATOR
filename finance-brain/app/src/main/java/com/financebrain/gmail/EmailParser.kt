package com.financebrain.gmail

import com.financebrain.data.Direction
import com.financebrain.parser.BankSmsParser
import com.financebrain.parser.ParsedTransaction

/**
 * Turns a bank / card / UPI / merchant email into a transaction. Bank alert emails read
 * almost like the SMS versions, so the SMS parser does the heavy lifting on the email body;
 * a few email-only shapes (PhonePe receipts, card statements, order confirmations) are
 * handled first.
 */
object EmailParser {

    /** Gmail search that pulls only money-related mail. Kept broad; the parser filters. */
    const val QUERY = "(from:(hdfcbank OR icicibank OR sbi OR onlinesbi OR sbicard OR federalbank OR phonepe OR paytm OR axisbank OR kotak OR amazon.in OR flipkart OR swiggy OR zomato OR irctc OR makemytrip OR cred.club) " +
        "OR subject:(\"transaction alert\" OR debited OR credited OR \"payment successful\" OR \"paid\" OR receipt OR invoice OR statement OR \"order confirmation\" OR \"your order\" OR \"tax invoice\")) " +
        "-subject:(otp OR newsletter OR offer OR unsubscribe)"

    private val phonePeRe = Regex("""(?:paid|sent)\s*(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*to\s+([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|via|using|from|\.|,|\n)|$)""", RegexOption.IGNORE_CASE)
    private val phonePeRecvRe = Regex("""received\s*(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*from\s+([A-Za-z0-9 &._'@-]{2,60}?)(?:\s+(?:on|via|in|\.|,|\n)|$)""", RegexOption.IGNORE_CASE)
    private val cardRe = Regex("""(?:credit|debit)\s*card\s*(?:ending|no\.?|xx|\*+)?\s*[xX*]*(\d{4}).{0,40}?(?:for|of)\s*(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:at|on|to)\s+([A-Za-z0-9 &._'*-]{2,60}?)\s+on\s+(\d[\d/\-A-Za-z:, ]{5,20})""", RegexOption.IGNORE_CASE)
    private val orderRe = Regex("""(?:order\s*total|grand\s*total|total\s*(?:amount|paid)|amount\s*paid|you\s*paid)\s*:?\s*(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    fun parse(m: GmailMessage): ParsedTransaction? {
        val from = m.from.lowercase()
        val subject = m.subject
        val body = m.text.replace(' ', ' ')
        val head = body.take(4000)

        // Skip statements, promos and anything that is not a completed payment.
        val sub = subject.lowercase()
        if (sub.contains("statement") && !sub.contains("transaction")) return null
        if (Regex("""\b(otp|offer|cashback offer|apply|pre-approved|reminder|due on|will be debited|refund initiated)\b""").containsMatchIn(sub)) return null

        // PhonePe / Paytm receipts.
        if (from.contains("phonepe") || from.contains("paytm") || sub.contains("phonepe")) {
            phonePeRe.find(head)?.let { mm ->
                return tx(mm.groupValues[1], Direction.DEBIT, if (from.contains("paytm")) "Paytm" else "PhonePe", null, mm.groupValues[2], "UPI", m)
            }
            phonePeRecvRe.find(head)?.let { mm ->
                return tx(mm.groupValues[1], Direction.CREDIT, if (from.contains("paytm")) "Paytm" else "PhonePe", null, mm.groupValues[2], "UPI", m)
            }
        }

        // Bank alerts by email are the SMS text with more words; let the SMS parser try.
        val bank = BankSmsParser.identifyBank(m.from, subject + " " + head)
        if (bank != null) {
            BankSmsParser.parse(m.from, subject + ". " + head, m.date)?.let { return it }
            cardRe.find(head)?.let { mm ->
                return tx(mm.groupValues[2], Direction.DEBIT, bank, mm.groupValues[1], mm.groupValues[3], "CARD", m, "CARD")
            }
        }

        // Merchant order confirmations (Amazon, Flipkart, Swiggy, Zomato ...): total paid.
        val merchant = when {
            from.contains("amazon") -> "Amazon"
            from.contains("flipkart") -> "Flipkart"
            from.contains("swiggy") -> "Swiggy"
            from.contains("zomato") -> "Zomato"
            from.contains("irctc") -> "IRCTC"
            from.contains("makemytrip") -> "MakeMyTrip"
            from.contains("myntra") -> "Myntra"
            from.contains("bigbasket") -> "BigBasket"
            from.contains("blinkit") -> "Blinkit"
            from.contains("zepto") -> "Zepto"
            else -> null
        }
        if (merchant != null && Regex("""order|confirm|receipt|invoice|payment|booking|ticket""").containsMatchIn(sub)) {
            if (Regex("""cancel|refund|return|shipped|delivered|out for delivery|arriving""").containsMatchIn(sub)) return null
            orderRe.find(head)?.let { mm -> return tx(mm.groupValues[1], Direction.DEBIT, merchant, null, merchant, "ONLINE", m) }
        }
        return null
    }

    private fun tx(amount: String, dir: Direction, bank: String, tail: String?, counterparty: String, channel: String, m: GmailMessage, kind: String = "BANK"): ParsedTransaction? {
        val paise = BankSmsParser.toPaise(amount) ?: return null
        if (paise <= 0) return null
        return ParsedTransaction(paise, dir, bank, tail, counterparty.trim().trimEnd('.', ','), channel, "gm-" + m.id, null, m.date, kind)
    }
}
