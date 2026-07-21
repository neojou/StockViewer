package com.neojou.stockviewer.presentation.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Selected-day OHLCV header as a 2×3 grid (matches prior field order):
 * ```
 * | 日期     | 開  xxx | 低  xxx |
 * | 量  xxx  | 高  xxx | 收  xxx |
 * ```
 * Column 2 labels (開/高) and column 3 labels (低/收) share a fixed label
 * width so they line up vertically.
 */
@Composable
private fun ChartHeader(entry: DailyOhlcv) {
    val accent = if (entry.isUp()) ChartColors.Up else ChartColors.Down
    val grid = ChartColors.Grid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .border(width = 1.dp, color = grid),
    ) {
        // Row 1: 日期 | 開 | 低
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderGridCell {
                HeaderDateContent(text = formatChartDate(entry.date))
            }
            HeaderVLine(color = grid)
            HeaderGridCell {
                HeaderFieldContent(label = "開", value = formatPrice(entry.open), valueColor = accent)
            }
            HeaderVLine(color = grid)
            HeaderGridCell {
                HeaderFieldContent(label = "低", value = formatPrice(entry.low), valueColor = accent)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = grid)
        // Row 2: 量 | 高 | 收
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderGridCell {
                HeaderFieldContent(
                    label = "量",
                    value = formatVolume(entry.volume),
                    valueColor = ChartColors.HeaderText,
                )
            }
            HeaderVLine(color = grid)
            HeaderGridCell {
                HeaderFieldContent(label = "高", value = formatPrice(entry.high), valueColor = accent)
            }
            HeaderVLine(color = grid)
            HeaderGridCell {
                HeaderFieldContent(label = "收", value = formatPrice(entry.close), valueColor = accent)
            }
        }
    }
}

@Composable
private fun RowScope.HeaderGridCell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun HeaderVLine(color: Color) {
    VerticalDivider(
        modifier = Modifier.fillMaxHeight(),
        thickness = 1.dp,
        color = color,
    )
}

@Composable
private fun HeaderDateContent(text: String) {
    Text(
        text = text,
        color = ChartColors.HeaderText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}

/**
 * Label + value. [LABEL_WIDTH] is shared so 開/高 and 低/收 align across rows
 * within the same column.
 */
@Composable
private fun HeaderFieldContent(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(LABEL_WIDTH),
            color = ChartColors.AxisText,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** Fixed label column width so 開↔高 and 低↔收 line up. */
private val LABEL_WIDTH = 18.dp

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

        // Price horizontal grid + labels (mapped into pricePlot* so labels stay in pane)
        val priceTicks = layout.priceTicks()
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
            val labelY = (y - measured.size.height / 2f)
                .coerceIn(layout.priceTop, layout.priceBottom - measured.size.height)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (layout.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                    y = labelY,
                ),
            )
        }

        // Clear separator between price K-line pane and volume pane
        drawRect(
            color = Color(0xFF141414),
            topLeft = Offset(0f, layout.priceBottom),
            size = Size(size.width, layout.separatorHeight),
        )
        drawLine(
            color = ChartColors.HeaderText.copy(alpha = 0.35f),
            start = Offset(layout.chartLeft, layout.separatorY),
            end = Offset(layout.chartRight, layout.separatorY),
            strokeWidth = 1.5f,
        )
        // Thin edges of the separator band for a clearer split
        drawLine(
            color = ChartColors.Grid,
            start = Offset(layout.chartLeft, layout.priceBottom),
            end = Offset(layout.chartRight, layout.priceBottom),
            strokeWidth = 1f,
        )
        drawLine(
            color = ChartColors.Grid,
            start = Offset(layout.chartLeft, layout.volumeTop),
            end = Offset(layout.chartRight, layout.volumeTop),
            strokeWidth = 1f,
        )

        // Volume horizontal grid + labels (inside volume plot area only)
        val volSteps = 3
        for (i in 0..volSteps) {
            val ratio = i.toFloat() / volSteps
            val y = layout.volumePlotBottom - ratio * layout.volumePlotHeight
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
                val labelY = (y - measured.size.height / 2f)
                    .coerceIn(layout.volumePlotTop, layout.volumePlotBottom - measured.size.height)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (layout.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                        y = labelY,
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

            val volTop = layout.volumeBarTop(bar.volume)
            val volH = layout.volumePlotBottom - volTop
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, volTop),
                size = Size(bodyWidth, volH.coerceAtLeast(1f)),
            )
        }

        // Selected day crosshair (vertical through price + volume; horizontal at close) + outline
        if (selectedIndex in data.indices) {
            val bar = data[selectedIndex]
            val cx = layout.slotCenterX(selectedIndex)
            val openY = layout.priceToY(bar.open)
            val closeY = layout.priceToY(bar.close)

            // Vertical: day slot (price pane + volume pane)
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.pricePlotTop),
                end = Offset(cx, layout.pricePlotBottom),
                strokeWidth = 1f,
            )
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.volumePlotTop),
                end = Offset(cx, layout.volumePlotBottom),
                strokeWidth = 1f,
            )
            // Horizontal: selected day's close — full width to read against left price scale
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(0f, closeY),
                end = Offset(size.width, closeY),
                strokeWidth = 1f,
            )

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

        // Selected volume caption sits in the dedicated caption strip (above volume bars)
        val selected = data.getOrNull(selectedIndex)
        if (selected != null) {
            val title = "成交量 ${selected.volume}"
            val measured = textMeasurer.measure(
                title,
                labelStyle.copy(color = ChartColors.HeaderText, fontSize = 11.sp),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = layout.chartLeft + 4f,
                    y = layout.volumeTop +
                        ((layout.volumeCaptionHeight - measured.size.height) / 2f)
                            .coerceAtLeast(0f),
                ),
            )
        }
    }
}
