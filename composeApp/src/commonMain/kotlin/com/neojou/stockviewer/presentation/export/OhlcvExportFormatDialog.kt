package com.neojou.stockviewer.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.neojou.stockviewer.domain.export.OhlcvExportFormat

/**
 * Choose export file format before the system save dialog.
 *
 * - CSV: portable text (Excel can open)
 * - XLSX: native Excel workbook (Desktop only at write time)
 */
@Composable
fun OhlcvExportFormatDialog(
    onDismiss: () -> Unit,
    onConfirm: (OhlcvExportFormat) -> Unit,
) {
    var selected by remember { mutableStateOf(OhlcvExportFormat.Csv) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("匯出 OHLCV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "請選擇匯出格式，接著會開啟系統「另存新檔」視窗。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OhlcvExportFormat.entries.forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == format,
                                onClick = { selected = format },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == format,
                            onClick = { selected = format },
                        )
                        Text(
                            text = format.displayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = "說明：.xlsx 為正式 Excel 格式（Desktop）；.csv 為通用文字檔，Excel 亦可開啟。不建議使用舊版 .xls。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
