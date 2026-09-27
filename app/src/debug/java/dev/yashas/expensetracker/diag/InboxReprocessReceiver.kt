package dev.yashas.expensetracker.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.yashas.expensetracker.AppGraph
import dev.yashas.expensetracker.data.capture.SmsIngestor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY: one-shot reprocessor for the phone's real SMS inbox. Android does not
 * replay history to SMS_RECEIVED, so back-filling requires reading content://sms/inbox
 * (READ_SMS is already granted) and pushing each bank message through the funnel.
 *
 * Trigger over adb while testing:
 *   adb shell am broadcast -a dev.yashas.expensetracker.REPROCESS_INBOX \
 *     -n dev.yashas.expensetracker/.diag.InboxReprocessReceiver --ei days 21
 *
 * Stripped from release via the debug source set.
 */
class InboxReprocessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val days = intent.getIntExtra("days", 14).coerceIn(1, 90)
        val cutoff = System.currentTimeMillis() - days * 86_400_000L

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // ensure bank rows (incl. new seeds) exist before the allowlist check
                AppGraph.bootstrap(context)
                val ingestor = AppGraph.smsIngestor(context)
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.DATE),
                    "${Telephony.Sms.Inbox.DATE} >= ?",
                    arrayOf(cutoff.toString()),
                    "${Telephony.Sms.Inbox.DATE} DESC",
                )
                var considered = 0
                var captured = 0
                var dup = 0
                var ignored = 0
                cursor?.use { c ->
                    val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
                    val iBody = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                    val iDate = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)
                    while (c.moveToNext()) {
                        val sender = c.getString(iAddr) ?: continue
                        val body = c.getString(iBody) ?: continue
                        val ts = c.getLong(iDate)
                        considered++
                        when (runCatching { ingestor.ingest(sender, body, ts) }.getOrNull()) {
                            SmsIngestor.Outcome.CAPTURED -> captured++
                            SmsIngestor.Outcome.DUPLICATE_SMS, SmsIngestor.Outcome.DUPLICATE_TXN -> dup++
                            else -> ignored++
                        }
                    }
                }
                android.util.Log.i(TAG, "reprocess: considered=$considered captured=$captured dup=$dup ignored=$ignored")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "dev.yashas.expensetracker.REPROCESS_INBOX"
        private const val TAG = "InboxReprocess"
    }
}
