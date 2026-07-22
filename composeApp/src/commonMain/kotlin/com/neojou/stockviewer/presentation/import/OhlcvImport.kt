package com.neojou.stockviewer.presentation.import

import com.neojou.stockviewer.domain.import.OhlcvCsvImporter
import com.neojou.stockviewer.domain.import.OhlcvParseResult
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.model.isSameContentAs
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.platform.readOhlcvFromXlsx
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.io.pickOpenFilePath
import com.neojou.tools.io.readTextFileUtf8

private const val TAG = "OhlcvImport"

/**
 * Stats after applying import merge rules.
 */
data class OhlcvImportStats(
    val inserted: Int,
    val updated: Int,
    val unchangedSkipped: Int,
    val skippedInvalid: Int,
    val failedWrite: Int,
) {
    val summary: String
        get() = "新增 $inserted，更新 $updated，相同略過 $unchangedSkipped，無效 $skippedInvalid" +
            if (failedWrite > 0) "，寫入失敗 $failedWrite" else ""
}

sealed class OhlcvImportPickResult {
    data class Ready(val preview: OhlcvImportPreview) : OhlcvImportPickResult()
    data object Cancelled : OhlcvImportPickResult()
    data class Failure(val message: String) : OhlcvImportPickResult()
}

/**
 * Parsed file ready for user confirmation before DB writes.
 */
data class OhlcvImportPreview(
    val path: String,
    val rows: List<DailyOhlcv>,
    val skippedInvalid: Int,
    val sampleErrors: List<String>,
)

/**
 * Step 1: open file dialog, parse CSV/XLSX, return preview (no DB writes yet).
 */
suspend fun pickAndParseOhlcvImport(): OhlcvImportPickResult {
    val path = pickOpenFilePath(
        title = "匯入 OHLCV 資料",
        extensionFilterLabel = "CSV / Excel",
        allowedExtensions = listOf("csv", "xlsx"),
    )
    if (path == null) {
        MyLog.add(TAG, "Import cancelled or unsupported", LogLevel.DEBUG)
        return OhlcvImportPickResult.Cancelled
    }

    val lower = path.lowercase()
    val parseResult: OhlcvParseResult = when {
        lower.endsWith(".csv") -> {
            val text = readTextFileUtf8(path).getOrElse { e ->
                return OhlcvImportPickResult.Failure(e.message ?: "讀取 CSV 失敗")
            }
            OhlcvCsvImporter.parse(text)
        }
        lower.endsWith(".xlsx") -> {
            readOhlcvFromXlsx(path).getOrElse { e ->
                return OhlcvImportPickResult.Failure(e.message ?: "讀取 XLSX 失敗")
            }
        }
        else -> {
            return OhlcvImportPickResult.Failure("不支援的副檔名（請使用 .csv 或 .xlsx）")
        }
    }

    if (parseResult.rows.isEmpty()) {
        val hint = parseResult.errors.firstOrNull() ?: "沒有有效資料列"
        return OhlcvImportPickResult.Failure(
            "無法匯入：有效列為 0（略過 ${parseResult.skippedInvalid}）。$hint",
        )
    }

    MyLog.add(
        TAG,
        "Parsed ${parseResult.rows.size} rows, skipped=${parseResult.skippedInvalid} from $path",
        LogLevel.INFO,
    )
    return OhlcvImportPickResult.Ready(
        OhlcvImportPreview(
            path = path,
            rows = parseResult.rows,
            skippedInvalid = parseResult.skippedInvalid,
            sampleErrors = parseResult.errors,
        ),
    )
}

/**
 * Step 2: merge into DB.
 *
 * - file only → insert
 * - both, content differs → update
 * - both, content same → skip write
 * - DB only → leave unchanged
 */
suspend fun applyOhlcvImport(
    repository: OhlcvRepository,
    rows: List<DailyOhlcv>,
    skippedInvalid: Int,
): OhlcvImportStats {
    var inserted = 0
    var updated = 0
    var unchanged = 0
    var failed = 0

    for (row in rows) {
        val existingResult = repository.get(row.date)
        if (existingResult.isFailure) {
            failed++
            continue
        }
        val existing = existingResult.getOrNull()
        when {
            existing == null -> {
                val w = repository.upsert(row)
                if (w.isSuccess) inserted++ else failed++
            }
            existing.isSameContentAs(row) -> {
                unchanged++
            }
            else -> {
                val w = repository.upsert(row)
                if (w.isSuccess) updated++ else failed++
            }
        }
    }

    val stats = OhlcvImportStats(
        inserted = inserted,
        updated = updated,
        unchangedSkipped = unchanged,
        skippedInvalid = skippedInvalid,
        failedWrite = failed,
    )
    MyLog.add(TAG, "Import apply: ${stats.summary}", LogLevel.INFO)
    return stats
}
