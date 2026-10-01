package dev.yashas.expensetracker.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * P6 CSV export: writes the full ledger to Downloads via MediaStore (no storage
 * permission needed on API 29+, and the app keeps its zero-INTERNET guarantee —
 * MediaStore writes are local IPC, not network).
 *
 * Columns: date, time, type, amount_rupees, merchant, vpa, category, provenance, note, utr
 */
object CsvExporter {

    data class Result(val uri: android.net.Uri?, val rowCount: Int, val fileName: String)

    suspend fun export(context: Context, rows: List<TransactionEntity>): Result {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now())
        val fileName = "expense-tracker-$stamp.csv"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter(Charsets.UTF_8).use { w ->
                    w.write("date,time,type,amount_rupees,merchant,vpa,category,provenance,note,utr")
                    w.write("\r\n")
                    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    rows.forEach { t ->
                        val zoned = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
                        w.write(escape(zoned.toLocalDate().format(dateFmt)))
                        w.write(",")
                        w.write(escape(DateTimeFormatter.ofPattern("HH:mm:ss").format(zoned.toLocalTime())))
                        w.write(",")
                        w.write(escape(t.type.name))
                        w.write(",")
                        w.write(escape(formatRupees(t.amountPaise)))
                        w.write(",")
                        w.write(escape(t.merchantName))
                        w.write(",")
                        w.write(escape(t.vpa))
                        w.write(",")
                        w.write(escape(t.categoryKey))
                        w.write(",")
                        w.write(escape(t.provenance.name))
                        w.write(",")
                        w.write(escape(t.note))
                        w.write(",")
                        w.write(escape(t.utrRef))
                        w.write("\r\n")
                    }
                }
            }
            val pending = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, pending, null, null)
        }
        return Result(uri, rows.size, fileName)
    }

    /** RFC-4180 quoting: wrap when needed, double any embedded quotes. */
    private fun escape(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val needsQuoting = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }

    private fun formatRupees(paise: Long): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        val rupees = abs / 100
        val rest = abs % 100
        return (if (negative) "-" else "") + rupees + "." + rest.toString().padStart(2, '0')
    }
}
