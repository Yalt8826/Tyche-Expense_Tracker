package dev.yashas.expensetracker.data.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.yashas.expensetracker.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Funnel entry point (04-TECH §4). SMS_RECEIVED broadcasts are delivered here;
 * each message goes through SmsIngestor (allowlist → cheap screen → template →
 * persist as AUTO_REVIEW). READ_SMS is the only sensitive permission; the app
 * declares no INTERNET permission, so capture cannot leave the device.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val corpus = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.mapNotNull { msg ->
                val sender = msg.originatingAddress ?: return@mapNotNull null
                val body = msg.displayMessageBody ?: return@mapNotNull null
                Triple(sender, body, msg.timestampMillis)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ingestor = AppGraph.smsIngestor(context)
                corpus.forEach { (sender, body, ts) ->
                    runCatching { ingestor.ingest(sender, body, ts) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
