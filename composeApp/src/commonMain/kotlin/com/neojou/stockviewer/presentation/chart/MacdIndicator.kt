package com.neojou.stockviewer.presentation.chart

import androidx.compose.ui.graphics.Color
import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * MACD parameters: short EMA, long EMA, signal (DEA) period.
 *
 * Default **(5, 13, 8)**. UI draws **DIFF** bars only;
 * [signalPeriod] is used for DEA series (reserved / future line).
 */
data class MacdSettings(
    val shortPeriod: Int = 5,
    val longPeriod: Int = 13,
    val signalPeriod: Int = 8,
) {
    /** e.g. `(5, 13, 8)` for labels like `DIFF (5, 13, 8) = …`. */
    fun paramLabel(): String = "($shortPeriod, $longPeriod, $signalPeriod)"

    companion object {
        val Default = MacdSettings()

        const val MIN_PARAM = 1
        const val MAX_PARAM = 100

        fun parse(short: String, long: String, signal: String): Result<MacdSettings> {
            fun one(raw: String, label: String): Result<Int> {
                val n = raw.trim().toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("$label 必須是整數"))
                if (n < MIN_PARAM || n > MAX_PARAM) {
                    return Result.failure(
                        IllegalArgumentException("$label 須介於 $MIN_PARAM–$MAX_PARAM"),
                    )
                }
                return Result.success(n)
            }
            val s = one(short, "MACD 短期").getOrElse { return Result.failure(it) }
            val l = one(long, "MACD 長期").getOrElse { return Result.failure(it) }
            val sig = one(signal, "MACD 信號").getOrElse { return Result.failure(it) }
            if (s >= l) {
                return Result.failure(IllegalArgumentException("MACD 短期須小於長期"))
            }
            return Result.success(MacdSettings(shortPeriod = s, longPeriod = l, signalPeriod = sig))
        }
    }
}

object MacdColors {
    /** DIFF > 0 bars */
    val Positive = Color(0xFFFF2D55)
    /** DIFF < 0 bars */
    val Negative = Color(0xFF00C853)
}

/**
 * Full-series MACD aligned with OHLCV indices.
 * [diff] / [dea] are null until enough history exists.
 */
data class MacdSeries(
    val diff: List<Double?>,
    val dea: List<Double?>,
)

/**
 * DIFF = EMA(close, short) − EMA(close, long)
 * DEA  = EMA(DIFF, signal)  (SMA-seeded EMA on non-null DIFF stream)
 */
fun computeMacd(all: List<DailyOhlcv>, settings: MacdSettings): MacdSeries {
    val n = all.size
    if (n == 0) {
        return MacdSeries(emptyList(), emptyList())
    }
    val closes = all.map { it.close }
    val emaShort = computeEma(closes, settings.shortPeriod)
    val emaLong = computeEma(closes, settings.longPeriod)

    val diff = ArrayList<Double?>(n)
    for (i in 0 until n) {
        val s = emaShort[i]
        val l = emaLong[i]
        if (s != null && l != null) {
            diff += s - l
        } else {
            diff += null
        }
    }
    val dea = computeEmaOnNullable(diff, settings.signalPeriod)
    return MacdSeries(diff = diff, dea = dea)
}

/**
 * EMA with SMA seed at index `period - 1`.
 * α = 2 / (period + 1)
 */
fun computeEma(values: List<Double>, period: Int): List<Double?> {
    val n = values.size
    if (period <= 0 || n == 0) return List(n) { null }
    val out = ArrayList<Double?>(n)
    val alpha = 2.0 / (period + 1)
    var sum = 0.0
    var ema = 0.0
    for (i in 0 until n) {
        if (i < period - 1) {
            sum += values[i]
            out += null
        } else if (i == period - 1) {
            sum += values[i]
            ema = sum / period
            out += ema
        } else {
            ema = alpha * values[i] + (1.0 - alpha) * ema
            out += ema
        }
    }
    return out
}

/**
 * EMA over a series that may contain leading nulls.
 * Seed = SMA of first [period] non-null values once enough exist.
 */
fun computeEmaOnNullable(values: List<Double?>, period: Int): List<Double?> {
    val n = values.size
    if (period <= 0 || n == 0) return List(n) { null }
    val out = ArrayList<Double?>(n)
    val alpha = 2.0 / (period + 1)
    val window = ArrayList<Double>(period)
    var ema: Double? = null
    for (i in 0 until n) {
        val v = values[i]
        if (v == null) {
            out += null
            continue
        }
        if (ema == null) {
            window += v
            if (window.size < period) {
                out += null
            } else {
                ema = window.sum() / period
                out += ema
            }
        } else {
            ema = alpha * v + (1.0 - alpha) * ema
            out += ema
        }
    }
    return out
}

fun sliceMacdSeries(full: MacdSeries, start: Int, endInclusive: Int): MacdSeries =
    MacdSeries(
        diff = sliceMaSeries(full.diff, start, endInclusive),
        dea = sliceMaSeries(full.dea, start, endInclusive),
    )

/** Symmetric scale half-range for visible DIFF (≥ max |diff|). */
fun macdScaleMax(diffVisible: List<Double?>): Double {
    var maxAbs = 0.0
    for (v in diffVisible) {
        if (v != null) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
    }
    if (maxAbs < 1e-9) return 1.0
    // Mild headroom so bars don't touch the plot edge.
    val padded = maxAbs * 1.08
    return niceMacdBound(padded)
}

/** Nice upper bound (1/2/5 × 10^n) ≥ [value]. */
private fun niceMacdBound(value: Double): Double {
    if (value <= 0.0) return 1.0
    val exp = floor(log10(value)).toInt()
    val base = 10.0.pow(exp.toDouble())
    val fraction = value / base
    val niceFrac = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceFrac * base
}

fun formatDiff(value: Double): String = formatPrice(value)
