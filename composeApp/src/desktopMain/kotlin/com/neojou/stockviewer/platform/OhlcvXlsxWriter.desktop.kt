package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.model.DailyOhlcv
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook

actual suspend fun writeOhlcvXlsxFile(
    path: String,
    rows: List<DailyOhlcv>,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("OHLCV")
            val header = sheet.createRow(0)
            listOf("date", "open", "high", "low", "close", "volume").forEachIndexed { i, title ->
                header.createCell(i).setCellValue(title)
            }
            rows.forEachIndexed { index, row ->
                val excelRow = sheet.createRow(index + 1)
                excelRow.createCell(0).setCellValue(row.date.toString())
                excelRow.createCell(1).setCellValue(row.open)
                excelRow.createCell(2).setCellValue(row.high)
                excelRow.createCell(3).setCellValue(row.low)
                excelRow.createCell(4).setCellValue(row.close)
                excelRow.createCell(5).setCellValue(row.volume.toDouble())
            }
            // Reasonable column widths
            for (col in 0..5) {
                sheet.autoSizeColumn(col)
            }
            FileOutputStream(path).use { out ->
                workbook.write(out)
            }
        }
    }
}
