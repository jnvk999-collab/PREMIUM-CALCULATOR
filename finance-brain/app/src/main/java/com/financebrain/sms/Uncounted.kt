package com.financebrain.sms

import android.content.Context
import android.provider.Telephony
import com.financebrain.parser.BankSmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UncountedSms(val id: Long, val sender: String, val body: String, val at: Long, val amountPaise: Long?)

/** Bank-looking messages from the last days that the parser did not turn into a transaction. */
object Uncounted {
    private val amountRe = Regex("""(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    suspend fun recent(context: Context, days: Int = 7): List<UncountedSms> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val out = ArrayList<UncountedSms>()
        val c = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} DESC"
        ) ?: return@withContext out
        c.use {
            while (it.moveToNext()) {
                val sender = it.getString(1) ?: ""; val body = it.getString(2) ?: ""; val at = it.getLong(3); val id = it.getLong(0)
                val bank = BankSmsParser.identifyBank(sender, body) ?: continue
                if (BankSmsParser.isPromotionalSender(sender)) continue
                if (BankSmsParser.parse(sender, body, at) != null) continue
                val looksLikeMoney = Regex("""debit|credit|paid|sent|received|spent|withdraw|deposit""", RegexOption.IGNORE_CASE).containsMatchIn(body)
                if (!looksLikeMoney) continue
                out += UncountedSms(id, "$bank · $sender", body, at, amountRe.find(body)?.groupValues?.get(1)?.let(BankSmsParser::toPaise))
                if (out.size >= 30) break
            }
        }
        out
    }
}
