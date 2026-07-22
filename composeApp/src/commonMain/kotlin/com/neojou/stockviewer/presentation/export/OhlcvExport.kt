package com.neojou.stockviewer.presentation.export

import com.neojou.stockviewer.domain.export.OhlcvCsvExporter
import com.neojou.stockviewer.domain.export.OhlcvExportFormat
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.platform.writeOhlcvXlsxFile
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.io.pickSaveFilePath
import com.neojou.tools.io.writeTextFileUtf8

private const val TAG = "OhlcvExport"

/**
 * UI flow result for exporting all OHLCV rows to a user-chosen file.
 */
sealed class OhlcvExportResult {
    data class Success(
        val path: String,
        val rowCount: Int,
        val format: OhlcvExportFormat,
    ) : OhlcvExportResult()

    data object Cancelled : OhlcvExportResult()
    data class Failure(val message: String) : OhlcvExportResult()
}

/**
 * Export flow (after user already chose [format]):
 * 1. Native save dialog (extension matches format)
 * 2. Load all rows from [repository]
 * 3. Write CSV (UTF-8) or XLSX (Desktop POI)
 */
suspend fun exportAllOhlcv(
    repository: OhlcvRepository,
    format: OhlcvExportFormat,
): OhlcvExportResult {
    val path = pickSaveFilePath(
        title = "匯出 OHLCV 資料 (${format.extension.uppercase()})",
        suggestedFileName = format.suggestedFileName(),
        extensionFilterLabel = format.fileFilterLabel,
        extension = format.extension,
    )
    if (path == null) {
        MyLog.add(TAG, "Export cancelled or unsupported platform", LogLevel.DEBUG)
        return OhlcvExportResult.Cancelled
    }

    val rowsResult = repository.listAll()
    val rows = rowsResult.getOrElse { e ->
        val msg = e.message ?: "讀取資料庫失敗"
        MyLog.add(TAG, "listAll failed: $msg", LogLevel.ERROR)
        return OhlcvExportResult.Failure(msg)
    }

    val writeResult = when (format) {
        OhlcvExportFormat.Csv -> {
            val csv = "\uFEFF" + OhlcvCsvExporter.toCsv(rows)
            writeTextFileUtf8(path, csv)
        }
        OhlcvExportFormat.Xlsx -> writeOhlcvXlsxFile(path, rows)
    }

    return writeResult.fold(
        onSuccess = {
            MyLog.add(TAG, "Exported ${rows.size} rows as ${format.extension} → $path", LogLevel.INFO)
            OhlcvExportResult.Success(path = path, rowCount = rows.size, format = format)
        },
        onFailure = { e ->
            val msg = e.message ?: "寫入檔案失敗"
            MyLog.add(TAG, "Write failed: $msg", LogLevel.ERROR)
            OhlcvExportResult.Failure(msg)
        },
    )
}

/** @deprecated Use [exportAllOhlcv] with [OhlcvExportFormat.Csv]. */
suspend fun exportAllOhlcvToCsv(repository: OhlcvRepository): OhlcvExportResult =
    exportAllOhlcv(repository, OhlcvExportFormat.Csv)
