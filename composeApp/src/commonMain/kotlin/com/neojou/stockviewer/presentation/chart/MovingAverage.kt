package com.neojou.stockviewer.presentation.chart

import androidx.compose.ui.graphics.Color
import com.neojou.stockviewer.domain.model.DailyOhlcv

/**
 * Three configurable simple moving averages (SMA of close).
 *
 * Defaults match common East-Asian day-chart practice: 5 / 10 / 20.
 * Colors are fixed per slot (short / mid / long), independent of period numbers.
 */
data class MovingAverageSettings(
    /** First line period (default 5) — drawn in [MaColors.Short]. */
    val period1: Int = 5,
    /** Second line period (default 10) — drawn in [MaColors.Mid]. */
    val period2: Int = 10,
    /** Third line period (default 20) — drawn in [MaColors.Long]. */
    val period3: Int = 20,
) {
    val periods: List<Int> get() = listOf(period1, period2, period3)

    fun withPeriods(p1: Int, p2: Int, p3: Int): MovingAverageSettings =
        copy(period1 = p1, period2 = p2, period3 = p3)

    companion object {
        val Default = MovingAverageSettings()

        const val MIN_PERIOD = 1
        const val MAX_PERIOD = 250

        /**
         * Parse and validate three period strings.
         * @return validated settings or error message
         */
        fun parse(p1: String, p2: String, p3: String): Result<MovingAverageSettings> {
            fun one(raw: String, label: String): Result<Int> {
                val n = raw.trim().toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("$label 必須是整數"))
                if (n < MIN_PERIOD || n > MAX_PERIOD) {
                    return Result.failure(
                        IllegalArgumentException("$label 須介於 $MIN_PERIOD–$MAX_PERIOD"),
                    )
                }
                return Result.success(n)
            }
            val a = one(p1, "均線 1").getOrElse { return Result.failure(it) }
            val b = one(p2, "均線 2").getOrElse { return Result.failure(it) }
            val c = one(p3, "均線 3").getOrElse { return Result.failure(it) }
            return Result.success(MovingAverageSettings(a, b, c))
        }
    }
}

/** Fixed colors for the three MA slots. */
object MaColors {
    /** 均線 1（預設 5 日）— 黃 */
    val Short = Color(0xFFFFD60A)
    /** 均線 2（預設 10 日）— 紫 */
    val Mid = Color(0xFFBF5AF2)
    /** 均線 3（預設 20 日）— 藍 */
    val Long = Color(0xFF0A84FF)

    fun colorForSlot(index: Int): Color = when (index) {
        0 -> Short
        1 -> Mid
        else -> Long
    }
}

/**
 * Simple moving average of [closes] with window [period].
 *
 * Index `i` is non-null only when `i >= period - 1` (enough history including day i).
 * Uses arithmetic mean of closes in `[i - period + 1, i]`.
 */
fun computeSma(closes: List<Double>, period: Int): List<Double?> {
    if (period <= 0 || closes.isEmpty()) {
        return List(closes.size) { null }
    }
    val n = closes.size
    val out = ArrayList<Double?>(n)
    var windowSum = 0.0
    for (i in 0 until n) {
        windowSum += closes[i]
        if (i >= period) {
            windowSum -= closes[i - period]
        }
        if (i >= period - 1) {
            out += windowSum / period
        } else {
            out += null
        }
    }
    return out
}

/**
 * Compute the three SMA series for a full ascending OHLCV series.
 * Each list is aligned with [all] indices; null until enough bars exist.
 */
fun computeMovingAverages(
    all: List<DailyOhlcv>,
    settings: MovingAverageSettings,
): List<List<Double?>> {
    val closes = all.map { it.close }
    return settings.periods.map { period -> computeSma(closes, period) }
}

/** Slice full MA series to the same window as visible candles. */
fun sliceMaSeries(full: List<Double?>, start: Int, endInclusive: Int): List<Double?> {
    if (full.isEmpty() || start < 0 || endInclusive < start) return emptyList()
    val lo = start.coerceIn(0, full.lastIndex)
    val hi = endInclusive.coerceIn(0, full.lastIndex)
    if (lo > hi) return emptyList()
    return full.subList(lo, hi + 1)
}

/** Non-null MA values in a (visible) series — used to expand price scale. */
fun maValuesForScale(seriesList: List<List<Double?>>): List<Double> =
    seriesList.flatMap { series -> series.mapNotNull { it } }
