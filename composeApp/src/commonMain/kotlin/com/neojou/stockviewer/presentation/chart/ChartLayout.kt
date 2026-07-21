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
    val volumeMax: Long,
) {
    val leftAxisWidth: Float = 52f
    val rightPadding: Float = 12f
    val topPadding: Float = 8f
    val bottomDateHeight: Float = 22f
    val volumePaneRatio: Float = 0.28f
    val paneGap: Float = 10f

    val chartLeft: Float = leftAxisWidth
    val chartRight: Float = canvasWidth - rightPadding
    val chartWidth: Float = (chartRight - chartLeft).coerceAtLeast(1f)

    val usableHeight: Float =
        (canvasHeight - topPadding - bottomDateHeight - paneGap).coerceAtLeast(1f)
    val volumeHeight: Float = usableHeight * volumePaneRatio
    val priceHeight: Float = usableHeight - volumeHeight

    val priceTop: Float = topPadding
    val priceBottom: Float = priceTop + priceHeight
    val volumeTop: Float = priceBottom + paneGap
    val volumeBottom: Float = volumeTop + volumeHeight

    val priceRect: Rect = Rect(
        offset = Offset(chartLeft, priceTop),
        size = Size(chartWidth, priceHeight),
    )
    val volumeRect: Rect = Rect(
        offset = Offset(chartLeft, volumeTop),
        size = Size(chartWidth, volumeHeight),
    )

    val slotWidth: Float = if (barCount > 0) chartWidth / barCount else chartWidth

    fun slotCenterX(index: Int): Float =
        chartLeft + slotWidth * index + slotWidth / 2f

    fun priceToY(price: Double): Float {
        val range = (priceMax - priceMin).takeIf { it > 0 } ?: 1.0
        val ratio = ((price - priceMin) / range).toFloat()
        return priceBottom - ratio * priceHeight
    }

    fun volumeToHeight(volume: Long): Float {
        val maxV = volumeMax.takeIf { it > 0 } ?: 1L
        return (volume.toFloat() / maxV.toFloat()) * volumeHeight * 0.92f
    }

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
            val pad = maxOf((maxHigh - minLow) * 0.08, maxHigh * 0.01).coerceAtLeast(0.01)
            val volMax = data.maxOfOrNull { it.volume } ?: 0L
            return ChartLayout(
                canvasWidth = width,
                canvasHeight = height,
                barCount = data.size,
                priceMin = (minLow - pad).coerceAtLeast(0.0),
                priceMax = maxHigh + pad,
                volumeMax = maxOf(volMax, 1L),
            )
        }
    }
}

fun DailyOhlcv.isUp(): Boolean = close >= open

fun DailyOhlcv.candleColor(): Color =
    if (isUp()) ChartColors.Up else ChartColors.Down

/**
 * Nice step values for Y-axis labels.
 */
fun niceTicks(min: Double, max: Double, targetCount: Int = 6): List<Double> {
    if (max <= min) return listOf(min)
    val range = niceNumber(max - min, round = false)
    val step = niceNumber(range / (targetCount - 1).coerceAtLeast(1), round = true)
    if (step <= 0.0) return listOf(min, max)
    val start = floor(min / step) * step
    val end = ceil(max / step) * step
    val ticks = mutableListOf<Double>()
    var v = start
    var guard = 0
    while (v <= end + step * 0.5 && guard < 40) {
        if (v >= min - step * 0.01 && v <= max + step * 0.01) {
            ticks += v
        }
        v += step
        guard++
    }
    return ticks.ifEmpty { listOf(min, max) }
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
