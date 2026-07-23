package com.neojou.stockviewer.presentation.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Configure the three SMA periods drawn on the K-line price pane.
 *
 * Slot colors are fixed: 黃 / 紫 / 藍 for lines 1 / 2 / 3.
 */
@Composable
fun ChartMaSettingsDialog(
    current: MovingAverageSettings,
    onDismiss: () -> Unit,
    onConfirm: (MovingAverageSettings) -> Unit,
) {
    var p1 by remember(current) { mutableStateOf(current.period1.toString()) }
    var p2 by remember(current) { mutableStateOf(current.period2.toString()) }
    var p3 by remember(current) { mutableStateOf(current.period3.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("K 線均線設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "以收盤價計算簡單移動平均（SMA）。" +
                        "均線從「累積天數足以計算」的那一日起開始繪製。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaPeriodField(
                    label = "均線 1（黃）",
                    value = p1,
                    accent = MaColors.Short,
                    onValueChange = {
                        p1 = it
                        error = null
                    },
                )
                MaPeriodField(
                    label = "均線 2（紫）",
                    value = p2,
                    accent = MaColors.Mid,
                    onValueChange = {
                        p2 = it
                        error = null
                    },
                )
                MaPeriodField(
                    label = "均線 3（藍）",
                    value = p3,
                    accent = MaColors.Long,
                    onValueChange = {
                        p3 = it
                        error = null
                    },
                )
                Text(
                    text = "天數範圍：${MovingAverageSettings.MIN_PERIOD}–${MovingAverageSettings.MAX_PERIOD}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    MovingAverageSettings.parse(p1, p2, p3)
                        .onSuccess { onConfirm(it) }
                        .onFailure { error = it.message ?: "設定無效" }
                },
            ) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MaPeriodField(
    label: String,
    value: String,
    accent: Color,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "●",
            color = accent,
            modifier = Modifier.padding(end = 2.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp),
            label = { Text("日") },
        )
    }
}
