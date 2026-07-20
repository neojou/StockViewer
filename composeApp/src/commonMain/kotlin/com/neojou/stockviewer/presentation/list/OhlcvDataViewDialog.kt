package com.neojou.stockviewer.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.abs

private const val TAG = "OhlcvDataView"
private const val MAX_ROWS = 100

private data class OhlcvTableColumn(
    val header: String,
    val width: Int,
)

private val TABLE_COLUMNS = listOf(
    OhlcvTableColumn("日期", 108),
    OhlcvTableColumn("開盤價", 88),
    OhlcvTableColumn("最高價格", 88),
    OhlcvTableColumn("最低價格", 88),
    OhlcvTableColumn("收盤價", 88),
    OhlcvTableColumn("成交量", 96),
)


@Composable
private fun OhlcvDataTable(rows: List<DailyOhlcv>) {
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = Modifier.horizontalScroll(horizontalScroll),
    ) {
        OhlcvTableHeaderRow()
        HorizontalDivider()
        LazyColumn {
            items(rows, key = { it.date }) { entry ->
                OhlcvTableDataRow(entry)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun OhlcvTableHeaderRow() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        TABLE_COLUMNS.forEach { column ->
            Text(
                text = column.header,
                modifier = Modifier.width(column.width.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OhlcvTableDataRow(entry: DailyOhlcv) {
    val cells = listOf(
        entry.date.toString(),
        entry.open.toDisplayPrice(),
        entry.high.toDisplayPrice(),
        entry.low.toDisplayPrice(),
        entry.close.toDisplayPrice(),
        entry.volume.toString(),
    )

    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        cells.forEachIndexed { index, value ->
            Text(
                text = value,
                modifier = Modifier.width(TABLE_COLUMNS[index].width.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun List<DailyOhlcv>.sortedByProximityTo(referenceDate: kotlinx.datetime.LocalDate): List<DailyOhlcv> =
    sortedWith(
        compareBy<DailyOhlcv> { abs(it.date.toEpochDays() - referenceDate.toEpochDays()) }
            .thenByDescending { it.date },
    )

private fun Double.toDisplayPrice(): String {
    val scaled = (this * 100).toLong()
    val intPart = scaled / 100
    val fracPart = abs(scaled % 100)
    return if (fracPart == 0L) {
        "$intPart"
    } else {
        "$intPart.${fracPart.toString().padStart(2, '0')}"
    }
}
