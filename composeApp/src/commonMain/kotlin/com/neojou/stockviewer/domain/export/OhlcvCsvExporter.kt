package com.neojou.stockviewer.domain.export

import com.neojou.stockviewer.domain.model.DailyOhlcv

/**
 * Builds CSV text for OHLCV rows (Excel-compatible UTF-8 CSV).
 *
 * Columns: date, open, high, low, close, volume — one header row, then data by date ASC.
 */
object OhlcvCsvExporter {

    const val HEADER = "date,open,high,low,close,volume"

    fun toCsv(rows: List<DailyOhlcv>): String {
        val sb = StringBuilder()
        sb.append(HEADER).append('\n')
        for (row in rows) {
            sb.append(escape(row.date.toString())).append(',')
            sb.append(formatNumber(row.open)).append(',')
            sb.append(formatNumber(row.high)).append(',')
            sb.append(formatNumber(row.low)).append(',')
            sb.append(formatNumber(row.close)).append(',')
            sb.append(row.volume).append('\n')
        }
        return sb.toString()
    }

    private fun formatNumber(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }

    /** RFC-style CSV escape when needed. */
    private fun escape(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
