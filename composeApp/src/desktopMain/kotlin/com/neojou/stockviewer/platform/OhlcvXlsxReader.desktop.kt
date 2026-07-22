package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.import.OhlcvParseResult
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.validation.OhlcvValidationResult
import com.neojou.stockviewer.domain.validation.OhlcvValidator
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook

actual suspend fun readOhlcvFromXlsx(path: String): Result<OhlcvParseResult> =
    withContext(Dispatchers.IO) {
        runCatching {
            FileInputStream(path).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheet("OHLCV")
                        ?: workbook.getSheetAt(0)
                        ?: return@runCatching OhlcvParseResult(emptyList(), 0, listOf("活頁簿無工作表"))

                    val formatter = DataFormatter()
                    val rows = mutableListOf<DailyOhlcv>()
                    var skipped = 0
                    val errors = mutableListOf<String>()
                    var startRow = 0

                    val header = sheet.getRow(0)
                    if (header != null) {
                        val h0 = formatter.formatCellValue(header.getCell(0)).trim().lowercase()
                        if (h0 == "date") startRow = 1
                    }

                    for (r in startRow..sheet.lastRowNum) {
                        val excelRow = sheet.getRow(r) ?: continue
                        val lineNo = r + 1
                        val dateText = cellToDateString(excelRow.getCell(0), formatter)
                        val openText = cellToNumberString(excelRow.getCell(1), formatter)
                        val highText = cellToNumberString(excelRow.getCell(2), formatter)
                        val lowText = cellToNumberString(excelRow.getCell(3), formatter)
                        val closeText = cellToNumberString(excelRow.getCell(4), formatter)
                        val volumeText = cellToNumberString(excelRow.getCell(5), formatter)

                        if (dateText.isBlank() && openText.isBlank()) continue

                        val (entry, validation) = OhlcvValidator.parseAndValidate(
                            dateText = dateText,
                            openText = openText,
                            highText = highText,
                            lowText = lowText,
                            closeText = closeText,
                            volumeText = volumeText,
                        )
                        if (entry == null || validation is OhlcvValidationResult.Error) {
                            skipped++
                            val reason = (validation as? OhlcvValidationResult.Error)?.message ?: "無效"
                            errors.add("第 $lineNo 列：$reason")
                            continue
                        }
                        rows.add(entry)
                    }

                    val byDate = LinkedHashMap<String, DailyOhlcv>()
                    for (row in rows) {
                        byDate[row.date.toString()] = row
                    }
                    OhlcvParseResult(
                        rows = byDate.values.toList(),
                        skippedInvalid = skipped,
                        errors = errors.take(20),
                    )
                }
            }
        }
    }

private fun cellToDateString(cell: Cell?, formatter: DataFormatter): String {
    if (cell == null) return ""
    return when (cell.cellType) {
        CellType.NUMERIC -> {
            if (DateUtil.isCellDateFormatted(cell)) {
                val d = cell.dateCellValue
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.format(d)
            } else {
                // numeric serial date sometimes without format
                val n = cell.numericCellValue
                if (n > 20000 && n < 60000) {
                    val d = DateUtil.getJavaDate(n)
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                } else {
                    formatNumeric(n)
                }
            }
        }
        CellType.STRING -> cell.stringCellValue.trim()
        CellType.FORMULA -> formatter.formatCellValue(cell).trim()
        else -> formatter.formatCellValue(cell).trim()
    }
}

private fun cellToNumberString(cell: Cell?, formatter: DataFormatter): String {
    if (cell == null) return ""
    return when (cell.cellType) {
        CellType.NUMERIC -> formatNumeric(cell.numericCellValue)
        CellType.STRING -> cell.stringCellValue.trim()
        CellType.FORMULA -> {
            return try {
                formatNumeric(cell.numericCellValue)
            } catch (_: Exception) {
                formatter.formatCellValue(cell).trim()
            }
        }
        else -> formatter.formatCellValue(cell).trim()
    }
}

private fun formatNumeric(n: Double): String {
    val asLong = n.toLong()
    return if (n == asLong.toDouble()) asLong.toString() else n.toString()
}
