package com.neojou.stockviewer.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlin.math.round

private const val TAG = "OhlcvDataTable"
private const val DEFAULT_LIMIT = 100

private val ColDate = 110.dp
private val ColPrice = 88.dp
private val ColVolume = 100.dp

/**
 * Popup scroll window listing OHLCV rows from the database.
 *
 * - Header row with column labels
 * - Up to [limit] most recent rows, ordered by date descending (newest first)
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

    val hScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("OHLCV 資料（最近 $limit 筆）")
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp, max = 720.dp)
                    .heightIn(min = 200.dp, max = 480.dp),
            ) {
                Text(
                    text = "依日期由近到遠排序；最多顯示 $limit 筆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

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
                            text = errorMessage!!,
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(hScroll),
                        ) {
                            // Header row
                            OhlcvTableHeaderRow()
                            HorizontalDivider()
                            LazyColumn(
                                modifier = Modifier
                                    .heightIn(max = 400.dp)
                                    .fillMaxWidth(),
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
                        Spacer(Modifier.height(4.dp))
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        HeaderCell("日期", ColDate)
        HeaderCell("開盤", ColPrice)
        HeaderCell("最高", ColPrice)
        HeaderCell("最低", ColPrice)
        HeaderCell("收盤", ColPrice)
        HeaderCell("成交量", ColVolume)
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
            .background(bg)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        DataCell(entry.date.toString(), ColDate, alignStart = true)
        DataCell(formatPrice(entry.open), ColPrice)
        DataCell(formatPrice(entry.high), ColPrice)
        DataCell(formatPrice(entry.low), ColPrice)
        DataCell(formatPrice(entry.close), ColPrice)
        DataCell(entry.volume.toString(), ColVolume)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DataCell(text: String, width: androidx.compose.ui.unit.Dp, alignStart: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        textAlign = if (alignStart) TextAlign.Start else TextAlign.End,
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
        // Trim trailing zeros without java.text (commonMain-safe)
        val raw = scaled.toString()
        if (raw.contains('.')) raw.trimEnd('0').trimEnd('.') else raw
    }
}
