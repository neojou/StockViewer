package com.neojou.stockviewer.presentation.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * East-Asian market convention matching [docs/k_chart.jpg]:
 * red = up (close ≥ open), green = down (close < open).
 */
object ChartColors {
    val Up = Color(0xFFFF2D55)
    val Down = Color(0xFF00C853)
    val Background = Color(0xFF000000)
    val Grid = Color(0xFF2A2A2A)
    val AxisText = Color(0xFFB0B0B0)
    val HeaderText = Color(0xFFE8E8E8)
    val Crosshair = Color(0x66FFFFFF)
    val NavBarBg = Color(0xFF1A1A1A)
    val NavBarBorder = Color(0xFF2A2A2A)
    val NavButtonEnabled = Color(0xFFE8E8E8)
    val NavButtonDisabled = Color(0xFF555555)
    val NavButtonPress = Color(0xFF2E2E2E)
}

/** Shared horizontal metrics so price / volume panes stay slot-aligned. */
object ChartMetrics {
    const val LEFT_AXIS_WIDTH = 56f
    const val RIGHT_PADDING = 12f
    /** Body fills ~72% of slot (TradingView-like density). */
    const val BODY_SLOT_RATIO = 0.72f
    const val BODY_MIN_PX = 2f
    const val BODY_MAX_PX = 22f
}

/**
 * Price-scale range derived from visible OHLCV (nice ticks above high / below low).
 */
data class PriceScale(
    val priceMin: Double,
    val priceMax: Double,
    val priceStep: Double,
) {
    fun ticks(): List<Double> {
        if (priceMax <= priceMin) return listOf(priceMin)
        val step = priceStep.takeIf { it > 0.0 } ?: return listOf(priceMin, priceMax)
        val ticks = mutableListOf<Double>()
        var v = priceMin
        var guard = 0
        while (v <= priceMax + step * 0.01 && guard < 40) {
            ticks += v
            v += step
            guard++
        }
        if (ticks.isEmpty() || abs(ticks.last() - priceMax) > step * 0.01) {
            ticks += priceMax
        }
        return ticks
    }

    companion object {
        fun from(data: List<DailyOhlcv>): PriceScale {
            val minLow = data.minOfOrNull { it.low } ?: 0.0
            val maxHigh = data.maxOfOrNull { it.high } ?: 1.0
            val step = niceStep(minLow, maxOf(maxHigh, minLow + 1e-9), targetCount = 7)

            var topTick = floor(maxHigh / step) * step + step
            if (topTick <= maxHigh) topTick += step

            var bottomTick = ceil(minLow / step) * step - step
            if (bottomTick >= minLow) bottomTick -= step
            if (bottomTick < 0.0 && minLow > 0.0) {
                bottomTick = 0.0
            }
            if (bottomTick >= minLow) {
                bottomTick = minLow - step
            }

            return PriceScale(
                priceMin = bottomTick,
                priceMax = maxOf(topTick, bottomTick + step),
                priceStep = step,
            )
        }
    }
}

/**
 * Horizontal slot geometry shared by price and volume canvases.
 */
data class SlotGeometry(
    val canvasWidth: Float,
    val barCount: Int,
) {
    val chartLeft: Float = ChartMetrics.LEFT_AXIS_WIDTH
    val chartRight: Float = canvasWidth - ChartMetrics.RIGHT_PADDING
    val chartWidth: Float = (chartRight - chartLeft).coerceAtLeast(1f)
    val slotWidth: Float = if (barCount > 0) chartWidth / barCount else chartWidth

    fun slotCenterX(index: Int): Float =
        chartLeft + slotWidth * index + slotWidth / 2f

    fun bodyWidth(): Float =
        (slotWidth * ChartMetrics.BODY_SLOT_RATIO)
            .coerceIn(ChartMetrics.BODY_MIN_PX, ChartMetrics.BODY_MAX_PX)

    fun wickStroke(): Float =
        (bodyWidth() * 0.12f).coerceAtLeast(1f)

    fun indexAtX(x: Float): Int? {
        if (barCount <= 0) return null
        if (x < chartLeft || x > chartRight) return null
        val idx = ((x - chartLeft) / slotWidth).toInt()
        return idx.coerceIn(0, barCount - 1)
    }
}

/**
 * Price pane geometry (full canvas height is the price area).
 */
data class PricePaneLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val barCount: Int,
    val scale: PriceScale,
) {
    private val topPadding: Float = 10f
    private val bottomPadding: Float = 6f
    private val priceLabelInset: Float = 11f

    val slots: SlotGeometry = SlotGeometry(canvasWidth, barCount)

    val priceTop: Float = topPadding
    val priceBottom: Float = (canvasHeight - bottomPadding).coerceAtLeast(priceTop + 1f)
    val pricePlotTop: Float = priceTop + priceLabelInset
    val pricePlotBottom: Float = priceBottom - priceLabelInset
    val pricePlotHeight: Float = (pricePlotBottom - pricePlotTop).coerceAtLeast(1f)

    fun priceToY(price: Double): Float {
        val range = (scale.priceMax - scale.priceMin).takeIf { it > 0 } ?: 1.0
        val ratio = ((price - scale.priceMin) / range).toFloat()
        return pricePlotBottom - ratio * pricePlotHeight
    }

    fun priceTicks(): List<Double> = scale.ticks()
}

/**
 * Volume pane geometry (caption + bars + bottom date labels).
 */
data class VolumePaneLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val barCount: Int,
    val volumeMax: Long,
) {
    private val bottomDateHeight: Float = 24f
    private val volumeCaptionHeight: Float = 18f
    private val topEdge: Float = 2f

    val slots: SlotGeometry = SlotGeometry(canvasWidth, barCount)

    val volumeTop: Float = topEdge
    val volumeBottom: Float =
        (canvasHeight - bottomDateHeight).coerceAtLeast(volumeTop + volumeCaptionHeight + 1f)
    val volumePlotTop: Float = volumeTop + volumeCaptionHeight
    val volumePlotBottom: Float = volumeBottom
    val volumePlotHeight: Float = (volumePlotBottom - volumePlotTop).coerceAtLeast(1f)

    fun volumeToHeight(volume: Long): Float {
        val maxV = volumeMax.takeIf { it > 0 } ?: 1L
        return (volume.toFloat() / maxV.toFloat()) * volumePlotHeight * 0.92f
    }

    fun volumeBarTop(volume: Long): Float =
        volumePlotBottom - volumeToHeight(volume)

    companion object {
        fun volumeMaxOf(data: List<DailyOhlcv>): Long =
            maxOf(data.maxOfOrNull { it.volume } ?: 0L, 1L)
    }
}

// ─── Viewport (pan / zoom window over full series) ───────────────────────────

/** Default visible bars (~3 trading months). */
const val DEFAULT_VISIBLE_COUNT = 60
const val MIN_VISIBLE_COUNT = 10

/**
 * Sliding window over a sorted (ascending date) OHLCV series.
 *
 * - [visibleCount]: desired bars in the window (clamped to series length when slicing)
 * - [windowEnd]: inclusive right-edge index in the full series
 * - Zoom prefers keeping [windowEnd]; if the left side runs out of bars, the window
 *   grows to the right so the slice always has `min(visibleCount, total)` bars.
 */
data class ChartViewport(
    val visibleCount: Int = DEFAULT_VISIBLE_COUNT,
    val windowEnd: Int = -1,
) {
    /** Inclusive start index for a series of [total] bars. */
    fun windowStart(total: Int): Int {
        if (total <= 0) return 0
        val (start, _) = resolvedRange(total)
        return start
    }

    /** Inclusive end index after normalization for [total]. */
    fun resolvedEnd(total: Int): Int {
        if (total <= 0) return -1
        return resolvedRange(total).second
    }

    /**
     * Resolve (start, end) so the window always contains
     * `min(visibleCount, total)` bars when possible.
     */
    fun resolvedRange(total: Int): Pair<Int, Int> {
        if (total <= 0) return 0 to -1
        val count = visibleCount.coerceIn(1, total)
        var end = windowEnd.coerceIn(0, total - 1)
        var start = end - count + 1
        if (start < 0) {
            start = 0
            end = (count - 1).coerceAtMost(total - 1)
        }
        return start to end
    }

    fun slice(all: List<DailyOhlcv>): List<DailyOhlcv> {
        if (all.isEmpty()) return emptyList()
        val (start, end) = resolvedRange(all.size)
        return all.subList(start, end + 1)
    }

    val canZoomIn: Boolean get() = visibleCount > MIN_VISIBLE_COUNT
    fun canZoomOut(total: Int): Boolean = total > 0 && visibleCount < total
    fun canPanLeft(total: Int): Boolean =
        total > 0 && windowStart(total) > 0
    fun canPanRight(total: Int): Boolean {
        if (total <= 0) return false
        val (_, end) = resolvedRange(total)
        return end < total - 1
    }

    fun zoomIn(total: Int): ChartViewport {
        if (!canZoomIn || total <= 0) return this
        val step = zoomStep(visibleCount)
        val next = (visibleCount - step).coerceAtLeast(MIN_VISIBLE_COUNT).coerceAtMost(total)
        // Keep right edge fixed while shrinking from the left.
        val end = resolvedEnd(total).coerceAtLeast(0)
        return copy(visibleCount = next, windowEnd = end).normalized(total)
    }

    fun zoomOut(total: Int): ChartViewport {
        if (!canZoomOut(total)) return this
        val step = zoomStep(visibleCount)
        val next = (visibleCount + step).coerceAtMost(total)
        // Prefer fixed right edge; [normalized] extends right if left is exhausted.
        val end = resolvedEnd(total).coerceAtLeast(0)
        return copy(visibleCount = next, windowEnd = end).normalized(total)
    }

    fun panLeft(total: Int): ChartViewport {
        if (!canPanLeft(total)) return this
        val step = panStep(visibleCount)
        val (start, end) = resolvedRange(total)
        val count = end - start + 1
        val newStart = (start - step).coerceAtLeast(0)
        val newEnd = (newStart + count - 1).coerceAtMost(total - 1)
        return copy(windowEnd = newEnd, visibleCount = count).normalized(total)
    }

    fun panRight(total: Int): ChartViewport {
        if (!canPanRight(total)) return this
        val step = panStep(visibleCount)
        val (start, end) = resolvedRange(total)
        val count = end - start + 1
        val newEnd = (end + step).coerceAtMost(total - 1)
        val newStart = (newEnd - count + 1).coerceAtLeast(0)
        return copy(windowEnd = newEnd, visibleCount = newEnd - newStart + 1).normalized(total)
    }

    /** Clamp indices so slice length matches desired count. */
    fun normalized(total: Int): ChartViewport {
        if (total <= 0) {
            return ChartViewport(visibleCount = DEFAULT_VISIBLE_COUNT, windowEnd = -1)
        }
        val count = visibleCount.coerceIn(1, total)
        val (start, end) = copy(visibleCount = count).resolvedRange(total)
        return ChartViewport(visibleCount = end - start + 1, windowEnd = end)
    }

    companion object {
        fun initial(total: Int): ChartViewport {
            if (total <= 0) {
                return ChartViewport(visibleCount = DEFAULT_VISIBLE_COUNT, windowEnd = -1)
            }
            val count = minOf(DEFAULT_VISIBLE_COUNT, total)
            return ChartViewport(visibleCount = count, windowEnd = total - 1)
        }

        /**
         * Reconcile viewport after [all] changes.
         * - If previously pinned to the right edge, stay on the newest bar.
         * - Otherwise try to keep the same right-edge date; fall back to clamp.
         */
        fun reconcile(
            previous: ChartViewport,
            previousAll: List<DailyOhlcv>,
            newAll: List<DailyOhlcv>,
        ): ChartViewport {
            if (newAll.isEmpty()) {
                return ChartViewport(visibleCount = DEFAULT_VISIBLE_COUNT, windowEnd = -1)
            }
            val n = newAll.size
            if (previousAll.isEmpty() || previous.windowEnd < 0) {
                return initial(n)
            }
            val wasPinnedRight =
                previous.resolvedEnd(previousAll.size) >= previousAll.lastIndex
            val count = previous.visibleCount.coerceIn(1, n)
            val end = if (wasPinnedRight) {
                n - 1
            } else {
                val prevDate = previousAll.getOrNull(previous.resolvedEnd(previousAll.size))?.date
                val idx = if (prevDate != null) newAll.indexOfFirst { it.date == prevDate } else -1
                if (idx >= 0) idx else previous.windowEnd.coerceIn(0, n - 1)
            }
            return ChartViewport(visibleCount = count, windowEnd = end).normalized(n)
        }

        fun zoomStep(visibleCount: Int): Int =
            maxOf(2, (visibleCount * 0.2f).toInt().coerceAtLeast(1))

        fun panStep(visibleCount: Int): Int =
            maxOf(1, visibleCount / 5)
    }
}

fun DailyOhlcv.isUp(): Boolean = close >= open

fun DailyOhlcv.candleColor(): Color =
    if (isUp()) ChartColors.Up else ChartColors.Down

/**
 * Nice step size for a price range (used for axis ticks and top extension).
 */
fun niceStep(min: Double, max: Double, targetCount: Int = 6): Double {
    if (max <= min) return 1.0
    val range = niceNumber(max - min, round = false)
    val step = niceNumber(range / (targetCount - 1).coerceAtLeast(1), round = true)
    return step.takeIf { it > 0.0 } ?: 1.0
}

private fun niceNumber(range: Double, round: Boolean): Double {
    if (range <= 0.0) return 1.0
    val exp = floor(log10(range)).toInt()
    val fraction = range / 10.0.pow(exp.toDouble())
    val niceFraction = if (round) {
        when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else -> 10.0
        }
    } else {
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
    }
    return niceFraction * 10.0.pow(exp.toDouble())
}

fun formatPrice(value: Double): String {
    val scaled = round(value * 100.0) / 100.0
    val asLong = scaled.toLong()
    return if (abs(scaled - asLong.toDouble()) < 1e-9) {
        asLong.toString()
    } else {
        val raw = ((scaled * 100).toLong() / 100.0).toString()
        if (raw.contains('.')) raw.trimEnd('0').trimEnd('.') else raw
    }
}

fun formatVolume(volume: Long): String = when {
    volume >= 100_000_000L -> {
        val yi = volume / 100_000_000.0
        "${formatPrice(yi)}億"
    }
    volume >= 1_000_000L -> {
        val m = volume / 1_000_000.0
        "${formatPrice(m)}M"
    }
    volume >= 1_000L -> {
        val k = volume / 1_000.0
        "${formatPrice(k)}K"
    }
    else -> volume.toString()
}

fun formatChartDate(date: kotlinx.datetime.LocalDate): String {
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}/$m/$d"
}

fun formatAxisDate(date: kotlinx.datetime.LocalDate): String {
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "$m/$d"
}
