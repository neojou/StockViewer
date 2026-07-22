package com.neojou.stockviewer.domain.import

import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.validation.OhlcvValidationResult
import com.neojou.stockviewer.domain.validation.OhlcvValidator

/**
 * Result of parsing a CSV document into OHLCV rows (no I/O).
 */
data class OhlcvParseResult(
    val rows: List<DailyOhlcv>,
    val skippedInvalid: Int,
    val errors: List<String> = emptyList(),
)

/**
 * Parses Export-compatible CSV (`date,open,high,low,close,volume`).
 * Tolerates UTF-8 BOM and case-insensitive header.
 */
object OhlcvCsvImporter {

    fun parse(text: String): OhlcvParseResult {
        val normalized = text.removePrefix("\uFEFF")
        val lines = normalized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            return OhlcvParseResult(emptyList(), 0, listOf("檔案為空"))
        }

        var startIndex = 0
        val first = splitCsvLine(lines[0]).map { it.trim().lowercase() }
        if (first.size >= 6 && first[0] == "date") {
            startIndex = 1
        }

        val rows = mutableListOf<DailyOhlcv>()
        var skipped = 0
        val errors = mutableListOf<String>()

        for (i in startIndex until lines.size) {
            val lineNo = i + 1
            val cols = splitCsvLine(lines[i])
            if (cols.size < 6) {
                skipped++
                errors.add("第 $lineNo 列：欄位不足")
                continue
            }
            val (entry, validation) = OhlcvValidator.parseAndValidate(
                dateText = cols[0],
                openText = cols[1],
                highText = cols[2],
                lowText = cols[3],
                closeText = cols[4],
                volumeText = cols[5],
            )
            if (entry == null || validation is OhlcvValidationResult.Error) {
                skipped++
                val reason = (validation as? OhlcvValidationResult.Error)?.message ?: "無效"
                errors.add("第 $lineNo 列：$reason")
                continue
            }
            rows.add(entry)
        }

        // Last row wins if duplicate dates in file
        val byDate = LinkedHashMap<String, DailyOhlcv>()
        for (r in rows) {
            byDate[r.date.toString()] = r
        }
        return OhlcvParseResult(
            rows = byDate.values.toList(),
            skippedInvalid = skipped,
            errors = errors.take(20),
        )
    }

    /** Minimal CSV split supporting quoted fields. */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
