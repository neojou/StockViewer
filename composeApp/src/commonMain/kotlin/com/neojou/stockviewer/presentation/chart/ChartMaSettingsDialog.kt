package com.neojou.stockviewer.presentation.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
 * K Chart indicator settings: three SMA periods + KD (period, K, D).
 *
 * MA slot colors: 黃 / 紫 / 藍. KD: K 紅 / D 綠.
 */
@Composable
fun ChartMaSettingsDialog(
    currentMa: MovingAverageSettings,
    currentKd: KdSettings,
    onDismiss: () -> Unit,
    onConfirm: (MovingAverageSettings, KdSettings) -> Unit,
) {
    var p1 by remember(currentMa) { mutableStateOf(currentMa.period1.toString()) }
    var p2 by remember(currentMa) { mutableStateOf(currentMa.period2.toString()) }
    var p3 by remember(currentMa) { mutableStateOf(currentMa.period3.toString()) }
    var kdPeriod by remember(currentKd) { mutableStateOf(currentKd.period.toString()) }
    var kdK by remember(currentKd) { mutableStateOf(currentKd.k.toString()) }
    var kdD by remember(currentKd) { mutableStateOf(currentKd.d.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("K 線設定") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "移動平均（SMA，收盤價）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "從「累積天數足以計算」的那一日起開始繪製。",
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
                    text = "均線天數：${MovingAverageSettings.MIN_PERIOD}–${MovingAverageSettings.MAX_PERIOD}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "KD 指標",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "RSV 期間 + K／D 平滑（預設 6,3,3）。K 紅、D 綠。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaPeriodField(
                    label = "期間 n（RSV）",
                    value = kdPeriod,
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    fieldLabel = "n",
                    onValueChange = {
                        kdPeriod = it
                        error = null
                    },
                )
                MaPeriodField(
                    label = "K 平滑",
                    value = kdK,
                    accent = KdColors.K,
                    fieldLabel = "K",
                    onValueChange = {
                        kdK = it
                        error = null
                    },
                )
                MaPeriodField(
                    label = "D 平滑",
                    value = kdD,
                    accent = KdColors.D,
                    fieldLabel = "D",
                    onValueChange = {
                        kdD = it
                        error = null
                    },
                )
                Text(
                    text = "KD 參數：${KdSettings.MIN_PARAM}–${KdSettings.MAX_PARAM}",
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
                    val ma = MovingAverageSettings.parse(p1, p2, p3)
                        .getOrElse {
                            error = it.message ?: "均線設定無效"
                            return@TextButton
                        }
                    val kd = KdSettings.parse(kdPeriod, kdK, kdD)
                        .getOrElse {
                            error = it.message ?: "KD 設定無效"
                            return@TextButton
                        }
                    onConfirm(ma, kd)
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
    fieldLabel: String = "日",
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
            label = { Text(fieldLabel) },
        )
    }
}
