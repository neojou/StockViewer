package com.neojou.stockviewer.presentation.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Confirm before writing parsed import rows into the database.
 */
@Composable
fun OhlcvImportConfirmDialog(
    preview: OhlcvImportPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確認匯入 OHLCV") },
        text = {
            Column {
                Text(
                    text = "檔案：${preview.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "有效資料 ${preview.rows.size} 筆" +
                        if (preview.skippedInvalid > 0) {
                            "（解析略過 ${preview.skippedInvalid} 列）"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "合併規則：\n" +
                        "• 檔案有、資料庫無 → 新增\n" +
                        "• 兩邊都有且內容不同 → 更新資料庫\n" +
                        "• 兩邊都有且內容相同 → 不寫入\n" +
                        "• 檔案沒有、資料庫有 → 保留資料庫",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.sampleErrors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "部分列錯誤示例：\n" +
                            preview.sampleErrors.take(5).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("開始匯入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
