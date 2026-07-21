package com.neojou.stockviewer.presentation.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
}

/**
 * Geometric layout for price pane + volume pane inside a single Canvas.
 */
data class ChartLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val barCount: Int,
    val priceMin: Double,
    val priceMax: Double,
    /** Nice step used for left price scale (same grid as [priceMin]/[priceMax]). */
    val priceStep: Double,
    val volumeMax: Long,
) {
    val leftAxisWidth: Float = 56f
    val rightPadding: Float = 12f
    val topPadding: Float = 10f
    val bottomDateHeight: Float = 24f
    /**
     * Clear band between price pane and volume pane (separator line sits in the middle).
     * Large enough so bottom price-tick labels never overlap volume.
     */
    val separatorHeight: Float = 32f
    /** Fraction of remaining height (after separator) used by the volume pane. */
    val volumePaneRatio: Float = 0.26f
    /**
     * Vertical inset inside the price pane so top/bottom tick labels
     * (drawn centered on the tick Y) stay fully inside the price area.
     */
    val priceLabelInset: Float = 11f
    /** Space at top of volume pane for the "成交量 …" caption. */
    val volumeCaptionHeight: Float = 18f

    val chartLeft: Float = leftAxisWidth
    val chartRight: Float = canvasWidth - rightPadding
    val chartWidth: Float = (chartRight - chartLeft).coerceAtLeast(1f)

    val usableHeight: Float =
        (canvasHeight - topPadding - bottomDateHeight - separatorHeight).coerceAtLeast(1f)
    val volumePaneHeight: Float = usableHeight * volumePaneRatio
    val pricePaneHeight: Float = usableHeight - volumePaneHeight

    val priceTop: Float = topPadding
    val priceBottom: Float = priceTop + pricePaneHeight
    /** Center Y of the separator band between price and volume. */
    val separatorY: Float = priceBottom + separatorHeight / 2f
    val volumeTop: Float = priceBottom + separatorHeight
    val volumeBottom: Float = volumeTop + volumePaneHeight

    /** Candle plot area (inside price pane, clear of tick-label overflow). */
    val pricePlotTop: Float = priceTop + priceLabelInset
    val pricePlotBottom: Float = priceBottom - priceLabelInset
    val pricePlotHeight: Float = (pricePlotBottom - pricePlotTop).coerceAtLeast(1f)

    /** Volume bars plot area (below caption). */
    val volumePlotTop: Float = volumeTop + volumeCaptionHeight
    val volumePlotBottom: Float = volumeBottom
    val volumePlotHeight: Float = (volumePlotBottom - volumePlotTop).coerceAtLeast(1f)

    val priceRect: Rect = Rect(
        offset = Offset(chartLeft, priceTop),
        size = Size(chartWidth, pricePaneHeight),
    )
    val volumeRect: Rect = Rect(
        offset = Offset(chartLeft, volumeTop),
        size = Size(chartWidth, volumePaneHeight),
    )

    val slotWidth: Float = if (barCount > 0) chartWidth / barCount else chartWidth

    fun slotCenterX(index: Int): Float =
        chartLeft + slotWidth * index + slotWidth / 2f

    fun priceToY(price: Double): Float {
        val range = (priceMax - priceMin).takeIf { it > 0 } ?: 1.0
        val ratio = ((price - priceMin) / range).toFloat()
        return pricePlotBottom - ratio * pricePlotHeight
    }

    /**
     * Left price scale ticks from [priceMin] (first below data low)
     * to [priceMax] (first above data high), stepping by [priceStep].
     */
    fun priceTicks(): List<Double> {
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
        // Ensure top bound is present (float drift).
        if (ticks.isEmpty() || abs(ticks.last() - priceMax) > step * 0.01) {
            ticks += priceMax
        }
        return ticks
    }

    fun volumeToHeight(volume: Long): Float {
        val maxV = volumeMax.takeIf { it > 0 } ?: 1L
        return (volume.toFloat() / maxV.toFloat()) * volumePlotHeight * 0.92f
    }

    fun volumeBarTop(volume: Long): Float =
        volumePlotBottom - volumeToHeight(volume)

    fun indexAtX(x: Float): Int? {
        if (barCount <= 0) return null
        if (x < chartLeft || x > chartRight) return null
        val idx = ((x - chartLeft) / slotWidth).toInt()
        return idx.coerceIn(0, barCount - 1)
    }

    companion object {
        fun from(
            width: Float,
            height: Float,
            data: List<DailyOhlcv>,
        ): ChartLayout {
            val minLow = data.minOfOrNull { it.low } ?: 0.0
            val maxHigh = data.maxOfOrNull { it.high } ?: 1.0
            // Step from actual data span.
            val spanMin = minLow
            val spanMax = maxOf(maxHigh, minLow + 1e-9)
            val step = niceStep(spanMin, spanMax, targetCount = 7)

            // Top: first nice tick strictly greater than data high.
            var topTick = floor(maxHigh / step) * step + step
            if (topTick <= maxHigh) topTick += step

            // Bottom: first nice tick strictly less than data low.
            var bottomTick = ceil(minLow / step) * step - step
            if (bottomTick >= minLow) bottomTick -= step
            // Prices are non-negative in this app; clamp only when it remains strictly below minLow.
            if (bottomTick < 0.0 && minLow > 0.0) {
                bottomTick = 0.0
                // If 0 is not strictly below minLow (minLow == 0), keep previous negative then force one step.
            }
            if (bottomTick >= minLow) {
                bottomTick = minLow - step
            }

            val priceMin = bottomTick
            val priceMax = maxOf(topTick, bottomTick + step)

            val volMax = data.maxOfOrNull { it.volume } ?: 0L
            return ChartLayout(
                canvasWidth = width,
                canvasHeight = height,
                barCount = data.size,
                priceMin = priceMin,
                priceMax = priceMax,
                priceStep = step,
                volumeMax = maxOf(volMax, 1L),
            )
        }
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
