package com.neojou.stockviewer.presentation.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs
import kotlinx.coroutines.flow.map

private const val TAG = "CandleChart"

/**
 * Full K-line panel inspired by [docs/k_chart.jpg]:
 * - Header: selected day's OHLCV
 * - MA strip: three SMA values for the selected day (黃 / 紫 / 藍)
 * - MA strip (left) + nav `<+->` (right, top of price pane)
 * - Price pane: candlesticks + three SMA overlays
 * - Volume pane: volume bars
 * - KD strip + KD pane (K red / D green, scale 0–100)
 * - MACD strip + DIFF histogram (0-centered, red+/green−)
 *
 * Loads full series via [OhlcvRepository.observeAll], then shows a viewport
 * (default last [DEFAULT_VISIBLE_COUNT] sessions). Left = older, right = newer.
 * SMA / KD / MACD are computed on the **full** series, then sliced to the viewport.
 */
@Composable
fun CandlestickChart(
    repository: OhlcvRepository,
    chartModifier: Modifier = Modifier,
    maSettings: MovingAverageSettings = MovingAverageSettings.Default,
    kdSettings: KdSettings = KdSettings.Default,
    macdSettings: MacdSettings = MacdSettings.Default,
) {
    var allData by remember { mutableStateOf<List<DailyOhlcv>>(emptyList()) }
    var viewport by remember { mutableStateOf(ChartViewport.initial(0)) }
    /** Index within the *visible* window (0 .. visible.lastIndex). */
    var selectedVisibleIndex by remember { mutableStateOf(-1) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        isLoading = true
        errorMessage = null
        repository.observeAll()
            .map { all -> all.sortedBy { it.date } }
            .collect { rows ->
                val previousAll = allData
                val previousVisible = viewport.slice(previousAll)
                val previousSelectedDate =
                    previousVisible.getOrNull(selectedVisibleIndex)?.date

                viewport = ChartViewport.reconcile(viewport, previousAll, rows)
                allData = rows

                val visible = viewport.slice(rows)
                selectedVisibleIndex = when {
                    visible.isEmpty() -> -1
                    previousSelectedDate != null -> {
                        val idx = visible.indexOfFirst { it.date == previousSelectedDate }
                        if (idx >= 0) idx else visible.lastIndex
                    }
                    else -> visible.lastIndex
                }
                isLoading = false
                MyLog.add(
                    TAG,
                    "Chart n=${rows.size}, visible=${visible.size}, " +
                        "end=${viewport.windowEnd}, sel=$selectedVisibleIndex",
                    LogLevel.DEBUG,
                )
            }
    }

    fun applyViewport(next: ChartViewport) {
        val prevVisible = viewport.slice(allData)
        val prevDate = prevVisible.getOrNull(selectedVisibleIndex)?.date
        viewport = next
        val visible = next.slice(allData)
        selectedVisibleIndex = when {
            visible.isEmpty() -> -1
            prevDate != null -> {
                val idx = visible.indexOfFirst { it.date == prevDate }
                if (idx >= 0) idx else visible.lastIndex
            }
            else -> visible.lastIndex
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
            allData.isEmpty() -> {
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
                val visible = viewport.slice(allData)
                val selected = visible.getOrNull(selectedVisibleIndex)
                    ?: visible.lastOrNull()
                    ?: return@Column
                val selIndex = selectedVisibleIndex.coerceIn(0, visible.lastIndex)

                val fullMa = remember(allData, maSettings) {
                    computeMovingAverages(allData, maSettings)
                }
                val fullKd = remember(allData, kdSettings) {
                    computeKd(allData, kdSettings)
                }
                val fullMacd = remember(allData, macdSettings) {
                    computeMacd(allData, macdSettings)
                }
                val (winStart, winEnd) = viewport.resolvedRange(allData.size)
                val visibleMa = remember(fullMa, winStart, winEnd) {
                    fullMa.map { series -> sliceMaSeries(series, winStart, winEnd) }
                }
                val visibleKd = remember(fullKd, winStart, winEnd) {
                    sliceKdSeries(fullKd, winStart, winEnd)
                }
                val visibleMacd = remember(fullMacd, winStart, winEnd) {
                    sliceMacdSeries(fullMacd, winStart, winEnd)
                }
                val absSelected = (winStart + selIndex).coerceIn(0, allData.lastIndex)
                val selectedMaValues = maSettings.periods.mapIndexed { slot, _ ->
                    fullMa.getOrNull(slot)?.getOrNull(absSelected)
                }
                val selectedK = fullKd.k.getOrNull(absSelected)
                val selectedD = fullKd.d.getOrNull(absSelected)
                val selectedDiff = fullMacd.diff.getOrNull(absSelected)

                ChartHeader(entry = selected)

                val total = allData.size
                // MA labels (left) + pan/zoom controls (right) — same row, top of price pane
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectedMaRow(
                        settings = maSettings,
                        values = selectedMaValues,
                        modifier = Modifier.weight(1f),
                    )
                    ChartNavBar(
                        visibleCount = visible.size,
                        canPanLeft = viewport.canPanLeft(total),
                        canPanRight = viewport.canPanRight(total),
                        canZoomIn = viewport.canZoomIn && visible.size > MIN_VISIBLE_COUNT,
                        canZoomOut = viewport.canZoomOut(total),
                        onPanLeft = { applyViewport(viewport.panLeft(total)) },
                        onPanRight = { applyViewport(viewport.panRight(total)) },
                        onZoomIn = { applyViewport(viewport.zoomIn(total)) },
                        onZoomOut = { applyViewport(viewport.zoomOut(total)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                PriceCanvas(
                    data = visible,
                    selectedIndex = selIndex,
                    maSeries = visibleMa,
                    onSelectIndex = { selectedVisibleIndex = it },
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f),
                )

                VolumeCanvas(
                    data = visible,
                    selectedIndex = selIndex,
                    onSelectIndex = { selectedVisibleIndex = it },
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(0.16f),
                )

                SelectedKdRow(
                    settings = kdSettings,
                    kValue = selectedK,
                    dValue = selectedD,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )

                KdCanvas(
                    data = visible,
                    kd = visibleKd,
                    selectedIndex = selIndex,
                    onSelectIndex = { selectedVisibleIndex = it },
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(0.16f),
                )

                SelectedMacdRow(
                    settings = macdSettings,
                    diffValue = selectedDiff,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )

                MacdCanvas(
                    data = visible,
                    macd = visibleMacd,
                    selectedIndex = selIndex,
                    onSelectIndex = { selectedVisibleIndex = it },
                    canvasModifier = Modifier
                        .fillMaxWidth()
                        .weight(0.16f),
                )
            }
        }
    }
}

// ─── Selected-day MA strip (between OHLCV header and price pane) ─────────────

@Composable
private fun SelectedMaRow(
    settings: MovingAverageSettings,
    values: List<Double?>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        settings.periods.forEachIndexed { slot, period ->
            val color = MaColors.colorForSlot(slot)
            val raw = values.getOrNull(slot)
            val text = if (raw != null) formatPrice(raw) else "—"
            Text(
                text = "均價 $period : $text",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

// ─── Selected-day KD strip (above KD pane) ───────────────────────────────────

@Composable
private fun SelectedKdRow(
    settings: KdSettings,
    kValue: Double?,
    dValue: Double?,
    modifier: Modifier = Modifier,
) {
    val params = settings.paramLabel()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "K$params : ${kValue?.let { formatKd(it) } ?: "—"}",
            color = KdColors.K,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = "D$params : ${dValue?.let { formatKd(it) } ?: "—"}",
            color = KdColors.D,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// ─── Selected-day MACD strip (above MACD pane) ───────────────────────────────

@Composable
private fun SelectedMacdRow(
    settings: MacdSettings,
    diffValue: Double?,
    modifier: Modifier = Modifier,
) {
    val color = when {
        diffValue == null -> ChartColors.HeaderText
        diffValue > 0 -> MacdColors.Positive
        diffValue < 0 -> MacdColors.Negative
        else -> ChartColors.HeaderText
    }
    val valueText = diffValue?.let { formatDiff(it) } ?: "—"
    Text(
        text = "DIFF ${settings.paramLabel()} = $valueText",
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

// ─── Nav bar: < + - > ────────────────────────────────────────────────────────

/**
 * Compact pan/zoom control: sits on the MA row, right-aligned (price pane top-right).
 */
@Composable
private fun ChartNavBar(
    visibleCount: Int,
    canPanLeft: Boolean,
    canPanRight: Boolean,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onPanLeft: () -> Unit,
    onPanRight: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "${visibleCount}日",
            color = ChartColors.AxisText,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp),
        )

        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ChartColors.NavBarBg)
                .border(1.dp, ChartColors.NavBarBorder, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(label = "‹", enabled = canPanLeft, onClick = onPanLeft)
            NavDivider()
            NavButton(label = "＋", enabled = canZoomIn, onClick = onZoomIn)
            NavDivider()
            NavButton(label = "－", enabled = canZoomOut, onClick = onZoomOut)
            NavDivider()
            NavButton(label = "›", enabled = canPanRight, onClick = onPanRight)
        }
    }
}

@Composable
private fun NavDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ChartColors.NavBarBorder),
    )
}

@Composable
private fun NavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        !enabled -> Color.Transparent
        pressed -> ChartColors.NavButtonPress
        else -> Color.Transparent
    }
    val fg = if (enabled) ChartColors.NavButtonEnabled else ChartColors.NavButtonDisabled

    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

/**
 * Selected-day OHLCV header as a 2×3 grid:
 * ```
 * | 日期     | 開  xxx | 低  xxx |
 * | 量  xxx  | 高  xxx | 收  xxx |
 * ```
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

private val LABEL_WIDTH = 18.dp

// ─── Price canvas ────────────────────────────────────────────────────────────

@Composable
private fun PriceCanvas(
    data: List<DailyOhlcv>,
    selectedIndex: Int,
    maSeries: List<List<Double?>>,
    onSelectIndex: (Int) -> Unit,
    canvasModifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = ChartColors.AxisText,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
    val scale = remember(data, maSeries) {
        PriceScale.from(data, extraValues = maValuesForScale(maSeries))
    }

    Canvas(
        modifier = canvasModifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val slots = SlotGeometry(size.width.toFloat(), data.size)
                    slots.indexAtX(offset.x)?.let(onSelectIndex)
                }
            },
    ) {
        val layout = PricePaneLayout(
            canvasWidth = size.width,
            canvasHeight = size.height,
            barCount = data.size,
            scale = scale,
        )
        val slots = layout.slots
        val bodyWidth = slots.bodyWidth()
        val wickStroke = slots.wickStroke()

        for (tick in layout.priceTicks()) {
            val y = layout.priceToY(tick)
            drawLine(
                color = ChartColors.Grid,
                start = Offset(slots.chartLeft, y),
                end = Offset(slots.chartRight, y),
                strokeWidth = 1f,
            )
            val label = formatPrice(tick)
            val measured = textMeasurer.measure(label, labelStyle)
            val labelY = (y - measured.size.height / 2f)
                .coerceIn(layout.priceTop, layout.priceBottom - measured.size.height)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (slots.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                    y = labelY,
                ),
            )
        }

        data.forEachIndexed { index, bar ->
            val color = bar.candleColor()
            val cx = slots.slotCenterX(index)

            val highY = layout.priceToY(bar.high)
            val lowY = layout.priceToY(bar.low)
            drawLine(
                color = color,
                start = Offset(cx, highY),
                end = Offset(cx, lowY),
                strokeWidth = wickStroke,
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
        }

        // SMA overlays (after candles so lines sit on top)
        maSeries.forEachIndexed { slot, series ->
            val color = MaColors.colorForSlot(slot)
            var prev: Offset? = null
            series.forEachIndexed { index, value ->
                if (value == null) {
                    prev = null
                    return@forEachIndexed
                }
                val pt = Offset(slots.slotCenterX(index), layout.priceToY(value))
                val p = prev
                if (p != null) {
                    drawLine(
                        color = color,
                        start = p,
                        end = pt,
                        strokeWidth = 1.75f,
                    )
                }
                prev = pt
            }
        }

        if (selectedIndex in data.indices) {
            val bar = data[selectedIndex]
            val cx = slots.slotCenterX(selectedIndex)
            val openY = layout.priceToY(bar.open)
            val closeY = layout.priceToY(bar.close)

            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.pricePlotTop),
                end = Offset(cx, layout.pricePlotBottom),
                strokeWidth = 1f,
            )
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
    }
}

// ─── Volume canvas ───────────────────────────────────────────────────────────

@Composable
private fun VolumeCanvas(
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
    val volumeMax = remember(data) { VolumePaneLayout.volumeMaxOf(data) }

    Canvas(
        modifier = canvasModifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val slots = SlotGeometry(size.width.toFloat(), data.size)
                    slots.indexAtX(offset.x)?.let(onSelectIndex)
                }
            },
    ) {
        val layout = VolumePaneLayout(
            canvasWidth = size.width,
            canvasHeight = size.height,
            barCount = data.size,
            volumeMax = volumeMax,
        )
        val slots = layout.slots
        val bodyWidth = slots.bodyWidth()

        // Top edge line (under nav bar)
        drawLine(
            color = ChartColors.Grid,
            start = Offset(slots.chartLeft, layout.volumeTop),
            end = Offset(slots.chartRight, layout.volumeTop),
            strokeWidth = 1f,
        )

        val volSteps = 3
        for (i in 0..volSteps) {
            val ratio = i.toFloat() / volSteps
            val y = layout.volumePlotBottom - ratio * layout.volumePlotHeight
            drawLine(
                color = ChartColors.Grid,
                start = Offset(slots.chartLeft, y),
                end = Offset(slots.chartRight, y),
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
                        x = (slots.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                        y = labelY,
                    ),
                )
            }
        }

        data.forEachIndexed { index, bar ->
            val color = bar.candleColor()
            val cx = slots.slotCenterX(index)
            val volTop = layout.volumeBarTop(bar.volume)
            val volH = layout.volumePlotBottom - volTop
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, volTop),
                size = Size(bodyWidth, volH.coerceAtLeast(1f)),
            )
        }

        if (selectedIndex in data.indices) {
            val cx = slots.slotCenterX(selectedIndex)
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.volumePlotTop),
                end = Offset(cx, layout.volumePlotBottom),
                strokeWidth = 1f,
            )
        }

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
                    x = slots.chartLeft + 4f,
                    y = layout.volumeTop +
                        ((18f - measured.size.height) / 2f).coerceAtLeast(0f),
                ),
            )
        }
    }
}

// ─── KD canvas ───────────────────────────────────────────────────────────────

@Composable
private fun KdCanvas(
    data: List<DailyOhlcv>,
    kd: KdSeries,
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
                    val slots = SlotGeometry(size.width.toFloat(), data.size)
                    slots.indexAtX(offset.x)?.let(onSelectIndex)
                }
            },
    ) {
        val layout = KdPaneLayout(
            canvasWidth = size.width,
            canvasHeight = size.height,
            barCount = data.size,
        )
        val slots = layout.slots

        // Horizontal grid + left labels (0–100 step 20)
        for (tick in layout.ticks) {
            val y = layout.valueToY(tick)
            val isBand = tick == 20.0 || tick == 80.0
            drawLine(
                color = if (isBand) {
                    ChartColors.HeaderText.copy(alpha = 0.28f)
                } else {
                    ChartColors.Grid
                },
                start = Offset(slots.chartLeft, y),
                end = Offset(slots.chartRight, y),
                strokeWidth = if (isBand) 1.25f else 1f,
            )
            val label = tick.toInt().toString()
            val measured = textMeasurer.measure(label, labelStyle)
            val labelY = (y - measured.size.height / 2f)
                .coerceIn(layout.paneTop, layout.paneBottom - measured.size.height)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (slots.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                    y = labelY,
                ),
            )
        }

        fun drawKdLine(series: List<Double?>, color: Color) {
            var prev: Offset? = null
            series.forEachIndexed { index, value ->
                if (value == null) {
                    prev = null
                    return@forEachIndexed
                }
                val pt = Offset(slots.slotCenterX(index), layout.valueToY(value))
                val p = prev
                if (p != null) {
                    drawLine(
                        color = color,
                        start = p,
                        end = pt,
                        strokeWidth = 1.75f,
                    )
                }
                prev = pt
            }
        }
        drawKdLine(kd.k, KdColors.K)
        drawKdLine(kd.d, KdColors.D)

        // Crosshair: vertical @ day; horizontal @ K value
        if (selectedIndex in data.indices) {
            val cx = slots.slotCenterX(selectedIndex)
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.plotTop),
                end = Offset(cx, layout.plotBottom),
                strokeWidth = 1f,
            )
            val kAtSel = kd.k.getOrNull(selectedIndex)
            if (kAtSel != null) {
                val ky = layout.valueToY(kAtSel)
                drawLine(
                    color = ChartColors.Crosshair,
                    start = Offset(0f, ky),
                    end = Offset(size.width, ky),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

// ─── MACD (DIFF histogram) canvas ────────────────────────────────────────────

@Composable
private fun MacdCanvas(
    data: List<DailyOhlcv>,
    macd: MacdSeries,
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
    val scaleMax = remember(macd.diff) { macdScaleMax(macd.diff) }

    Canvas(
        modifier = canvasModifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val slots = SlotGeometry(size.width.toFloat(), data.size)
                    slots.indexAtX(offset.x)?.let(onSelectIndex)
                }
            },
    ) {
        val layout = MacdPaneLayout(
            canvasWidth = size.width,
            canvasHeight = size.height,
            barCount = data.size,
            scaleMax = scaleMax,
        )
        val slots = layout.slots
        val bodyWidth = slots.bodyWidth()

        // Grid + left DIFF scale (0 centered)
        for (tick in layout.ticks()) {
            val y = layout.valueToY(tick)
            val isZero = abs(tick) < 1e-12
            drawLine(
                color = if (isZero) {
                    ChartColors.HeaderText.copy(alpha = 0.40f)
                } else {
                    ChartColors.Grid
                },
                start = Offset(slots.chartLeft, y),
                end = Offset(slots.chartRight, y),
                strokeWidth = if (isZero) 1.5f else 1f,
            )
            val label = formatDiff(tick)
            val measured = textMeasurer.measure(label, labelStyle)
            val labelY = (y - measured.size.height / 2f)
                .coerceIn(layout.paneTop, layout.paneBottom - measured.size.height)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (slots.chartLeft - measured.size.width - 6f).coerceAtLeast(0f),
                    y = labelY,
                ),
            )
        }

        // DIFF bars: + red up from 0, − green down from 0
        macd.diff.forEachIndexed { index, diff ->
            if (diff == null) return@forEachIndexed
            val cx = slots.slotCenterX(index)
            val yDiff = layout.valueToY(diff)
            val top = minOf(layout.zeroY, yDiff)
            val bottom = maxOf(layout.zeroY, yDiff)
            val h = (bottom - top).coerceAtLeast(1f)
            val color = when {
                diff > 0 -> MacdColors.Positive
                diff < 0 -> MacdColors.Negative
                else -> ChartColors.AxisText
            }
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, top),
                size = Size(bodyWidth, h),
            )
        }

        // Crosshair: vertical @ day; horizontal @ DIFF
        if (selectedIndex in data.indices) {
            val cx = slots.slotCenterX(selectedIndex)
            drawLine(
                color = ChartColors.Crosshair,
                start = Offset(cx, layout.plotTop),
                end = Offset(cx, layout.plotBottom),
                strokeWidth = 1f,
            )
            val dAtSel = macd.diff.getOrNull(selectedIndex)
            if (dAtSel != null) {
                val dy = layout.valueToY(dAtSel)
                drawLine(
                    color = ChartColors.Crosshair,
                    start = Offset(0f, dy),
                    end = Offset(size.width, dy),
                    strokeWidth = 1f,
                )
            }
        }

        // Date labels (bottom of chart stack)
        val labelEvery = maxOf(1, data.size / 6)
        data.forEachIndexed { index, bar ->
            if (index % labelEvery == 0 || index == data.lastIndex) {
                val label = formatAxisDate(bar.date)
                val measured = textMeasurer.measure(label, labelStyle)
                val cx = slots.slotCenterX(index)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (cx - measured.size.width / 2f)
                            .coerceIn(slots.chartLeft, slots.chartRight - measured.size.width),
                        y = layout.paneBottom + 4f,
                    ),
                )
            }
        }
    }
}
