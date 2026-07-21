package com.neojou.stockviewer.presentation.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.flow.map

private const val TAG = "CandleChart"
private const val CHART_DAYS = 30

/**
 * Full K-line panel inspired by [docs/k_chart.jpg]:
 * - Header: selected day's OHLCV
 * - Middle: candlesticks (red up / green down)
 * - Bottom: volume bars
 *
 * Shows the most recent [CHART_DAYS] sessions (oldest → left, newest → right).
 * Initial selection is the rightmost bar; click a bar to update the header.
 */
@Composable
fun CandlestickChart(
    repository: OhlcvRepository,
    chartModifier: Modifier = Modifier,
    dayCount: Int = CHART_DAYS,
) {
    var data by remember { mutableStateOf<List<DailyOhlcv>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(-1) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, dayCount) {
        isLoading = true
        errorMessage = null
        repository.observeAll()
            .map { all -> all.sortedBy { it.date }.takeLast(dayCount) }
            .collect { rows ->
                val previousDate = data.getOrNull(selectedIndex)?.date
                data = rows
                // Prefer previous date; otherwise rightmost bar (closest to today).
                selectedIndex = when {
                    rows.isEmpty() -> -1
                    previousDate != null -> {
                        val idx = rows.indexOfFirst { it.date == previousDate }
                        if (idx >= 0) idx else rows.lastIndex
                    }
                    else -> rows.lastIndex
                }
                isLoading = false
                MyLog.add(TAG, "Chart data size=${rows.size}, selected=$selectedIndex", LogLevel.DEBUG)
            }
    }

    Column(
        modifier = chartModifier
            .fillMaxSize()
            .background(ChartColors.Background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ChartColors.Up)
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = errorMessage.orEmpty(), color = ChartColors.Up)
                }
            }
            data.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "尚無 K 線資料，請先由 Database → Input 新增",
                        color = ChartColors.AxisText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                val selected = data.getOrNull(selectedIndex) ?: data.last()
                ChartHeader(entry = selected)
                Spacer(modifier = Modifier.height(6.dp))
                CandlestickCanvas(
                    data = data,
                    selectedIndex = selectedIndex.coerceIn(0, data.lastIndex),
                    onSelectIndex = { selectedIndex = it },
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChartHeader(entry: DailyOhlcv) {
    val accent = if (entry.isUp()) ChartColors.Up else ChartColors.Down

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderLabel(text = formatChartDate(entry.date), color = ChartColors.HeaderText, bold = true)
            HeaderPair(label = "開", value = formatPrice(entry.open), valueColor = accent)
            HeaderPair(label = "低", value = formatPrice(entry.low), valueColor = accent)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPair(label = "量", value = formatVolume(entry.volume), valueColor = ChartColors.HeaderText)
            HeaderPair(label = "高", value = formatPrice(entry.high), valueColor = accent)
            HeaderPair(label = "收", value = formatPrice(entry.close), valueColor = accent)
        }
    }
}

@Composable
private fun HeaderPair(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = ChartColors.AxisText,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeaderLabel(text: String, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CandlestickCanvas(
    data: List<DailyOhlcv>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    canvasModifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = ChartColors.AxisText,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )

    Canvas(
        modifier = canvasModifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val layout = ChartLayout.from(
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        data = data,
                    )
                    layout.indexAtX(offset.x)?.let(onSelectIndex)
                }
            },
    ) {
        val layout = ChartLayout.from(
            width = size.width,
            height = size.height,
            data = data,
        )

        // Price horizontal grid + labels
        val priceTicks = niceTicks(layout.priceMin, layout.priceMax, targetCount = 7)
        for (tick in priceTicks) {
            val y = layout.priceToY(tick)
            drawLine(
                color = ChartColors.Grid,
                start = Offset(layout.chartLeft, y),
                end = Offset(layout.chartRight, y),
                strokeWidth = 1f,
            )
            val label = formatPrice(tick)
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (layout.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                    y = y - measured.size.height / 2f,
                ),
            )
        }

        // Volume horizontal grid + labels
        val volSteps = 4
        for (i in 0..volSteps) {
            val ratio = i.toFloat() / volSteps
            val y = layout.volumeBottom - ratio * layout.volumeHeight
            drawLine(
                color = ChartColors.Grid,
                start = Offset(layout.chartLeft, y),
                end = Offset(layout.chartRight, y),
                strokeWidth = 1f,
            )
            if (i > 0) {
                val vol = (layout.volumeMax * ratio).toLong()
                val label = formatVolume(vol)
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (layout.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                        y = y - measured.size.height / 2f,
                    ),
                )
            }
        }

        // Candles + volume bars
        val bodyWidth = (layout.slotWidth * 0.55f).coerceIn(3f, 18f)
        data.forEachIndexed { index, bar ->
            val color = bar.candleColor()
            val cx = layout.slotCenterX(index)

            val highY = layout.priceToY(bar.high)
            val lowY = layout.priceToY(bar.low)
            drawLine(
                color = color,
                start = Offset(cx, highY),
                end = Offset(cx, lowY),
                strokeWidth = 2f,
            )

            val openY = layout.priceToY(bar.open)
            val closeY = layout.priceToY(bar.close)
            val top = minOf(openY, closeY)
            val bottom = maxOf(openY, closeY)
            val bodyH = (bottom - top).coerceAtLeast(1.5f)
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, top),
                size = Size(bodyWidth, bodyH),
            )

            val volH = layout.volumeToHeight(bar.volume)
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, layout.volumeBottom - volH),
                size = Size(bodyWidth, volH),
            )
        }

        // Selected day crosshair + outline
        if (selectedIndex in data.indices) {
            val cx = layout.slotCenterX(selectedIndex)
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.priceTop),
                end = Offset(cx, layout.volumeBottom),
                strokeWidth = 1f,
            )
            val bar = data[selectedIndex]
            val openY = layout.priceToY(bar.open)
            val closeY = layout.priceToY(bar.close)
            val top = minOf(openY, closeY)
            val bottom = maxOf(openY, closeY)
            val bodyH = (bottom - top).coerceAtLeast(1.5f)
            drawRect(
                color = ChartColors.HeaderText,
                topLeft = Offset(cx - bodyWidth / 2f - 1f, top - 1f),
                size = Size(bodyWidth + 2f, bodyH + 2f),
                style = Stroke(width = 1.5f),
            )
        }

        // Sparse date labels
        val labelEvery = maxOf(1, data.size / 6)
        data.forEachIndexed { index, bar ->
            if (index % labelEvery == 0 || index == data.lastIndex) {
                val label = formatAxisDate(bar.date)
                val measured = textMeasurer.measure(label, labelStyle)
                val cx = layout.slotCenterX(index)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (cx - measured.size.width / 2f)
                            .coerceIn(layout.chartLeft, layout.chartRight - measured.size.width),
                        y = layout.volumeBottom + 4f,
                    ),
                )
            }
        }

        // Selected volume caption (like reference image)
        val selected = data.getOrNull(selectedIndex)
        if (selected != null) {
            val title = "成交量 ${selected.volume}"
            val measured = textMeasurer.measure(
                title,
                labelStyle.copy(color = ChartColors.HeaderText, fontSize = 11.sp),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(layout.chartLeft + 4f, layout.volumeTop + 2f),
            )
        }
    }
}
