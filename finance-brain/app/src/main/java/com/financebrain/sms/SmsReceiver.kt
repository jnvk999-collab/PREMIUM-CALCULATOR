package com.financebrain.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.financebrain.FinanceBrainApp
import com.financebrain.data.Source
import com.financebrain.parser.BankSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Catches bank alerts as they arrive so the ledger updates within a second of a payment. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val at = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val parsed = BankSmsParser.parse(sender, body, at) ?: return
        val pending = goAsync()
        val repo = FinanceBrainApp.get(context).repository
        CoroutineScope(Dispatchers.IO).launch {
            try { repo.ingest(parsed, body, Source.SMS) } finally { pending.finish() }
        }
    }
}
