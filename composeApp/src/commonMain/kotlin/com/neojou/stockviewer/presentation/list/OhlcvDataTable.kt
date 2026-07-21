package com.neojou.stockviewer.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlin.math.round

private const val TAG = "OhlcvDataTable"
private const val DEFAULT_LIMIT = 100

// Relative column weights so all 6 fields stay visible inside the dialog.
private const val W_DATE = 1.35f
private const val W_PRICE = 1f
private const val W_VOLUME = 1.15f

/**
 * Popup scroll window listing OHLCV rows from the database.
 *
 * - Header row with column labels (日期 / 開 / 高 / 低 / 收 / 量)
 * - Up to [limit] most recent rows, ordered by date descending (newest first)
 * - Columns use [weight] so 收盤 and 成交量 are not clipped off-screen
 */
@Composable
fun OhlcvDataTableDialog(
    repository: OhlcvRepository,
    onDismiss: () -> Unit,
    limit: Int = DEFAULT_LIMIT,
) {
    var rows by remember { mutableStateOf<List<DailyOhlcv>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, limit) {
        isLoading = true
        errorMessage = null
        repository.getRecent(limit)
            .onSuccess {
                rows = it
                MyLog.add(TAG, "Loaded ${it.size} rows", LogLevel.DEBUG)
            }
            .onFailure { e ->
                errorMessage = e.message ?: "讀取資料失敗"
                MyLog.add(TAG, "Load failed: $errorMessage", LogLevel.ERROR)
            }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("OHLCV 資料（最近 $limit 筆）")
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 520.dp, max = 760.dp)
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 480.dp),
            ) {
                Text(
                    text = "依日期由近到遠排序；最多顯示 $limit 筆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    rows.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "尚無資料，請先由 Database → Input 新增",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        // Full-width table: weight columns keep 收/量 visible (no clipped fixed widths).
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OhlcvTableHeaderRow()
                            HorizontalDivider()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                            ) {
                                itemsIndexed(
                                    items = rows,
                                    key = { _, item -> item.date.toString() },
                                ) { index, entry ->
                                    OhlcvTableDataRow(
                                        entry = entry,
                                        striped = index % 2 == 1,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "共 ${rows.size} 筆",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        },
    )
}

@Composable
private fun OhlcvTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(text = "日期", weight = W_DATE)
        HeaderCell(text = "開盤", weight = W_PRICE)
        HeaderCell(text = "最高", weight = W_PRICE)
        HeaderCell(text = "最低", weight = W_PRICE)
        HeaderCell(text = "收盤", weight = W_PRICE)
        HeaderCell(text = "成交量", weight = W_VOLUME)
    }
}

@Composable
private fun OhlcvTableDataRow(entry: DailyOhlcv, striped: Boolean) {
    val bg = if (striped) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DataCell(text = entry.date.toString(), weight = W_DATE, alignStart = true)
        DataCell(text = formatPrice(entry.open), weight = W_PRICE)
        DataCell(text = formatPrice(entry.high), weight = W_PRICE)
        DataCell(text = formatPrice(entry.low), weight = W_PRICE)
        DataCell(text = formatPrice(entry.close), weight = W_PRICE)
        DataCell(text = entry.volume.toString(), weight = W_VOLUME)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.DataCell(text: String, weight: Float, alignStart: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        textAlign = if (alignStart) TextAlign.Start else TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Compact decimal display for price columns (avoids long doubles).
 */
private fun formatPrice(value: Double): String {
    val scaled = round(value * 10000.0) / 10000.0
    val asLong = scaled.toLong()
    return if (scaled == asLong.toDouble()) {
        asLong.toString()
    } else {
        val raw = scaled.toString()
        if (raw.contains('.')) raw.trimEnd('0').trimEnd('.') else raw
    }
}
