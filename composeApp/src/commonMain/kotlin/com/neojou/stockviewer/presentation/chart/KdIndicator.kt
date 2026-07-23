package com.neojou.stockviewer.presentation.chart

import androidx.compose.ui.graphics.Color
import com.neojou.stockviewer.domain.model.DailyOhlcv

/**
 * Stochastic KD parameters (Taiwan / XQ-style RSV + recursive smooth).
 *
 * - [period]: RSV lookback (n)
 * - [k]: K smoothing factor
 * - [d]: D smoothing factor
 *
 * Default **(6, 3, 3)**.
 */
data class KdSettings(
    val period: Int = 6,
    val k: Int = 3,
    val d: Int = 3,
) {
    fun paramLabel(): String = "($period,$k,$d)"

    companion object {
        val Default = KdSettings()

        const val MIN_PARAM = 1
        const val MAX_PARAM = 100

        fun parse(period: String, k: String, d: String): Result<KdSettings> {
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
            val p = one(period, "KD 期間").getOrElse { return Result.failure(it) }
            val kk = one(k, "K 平滑").getOrElse { return Result.failure(it) }
            val dd = one(d, "D 平滑").getOrElse { return Result.failure(it) }
            return Result.success(KdSettings(period = p, k = kk, d = dd))
        }
    }
}

object KdColors {
    /** K line / label — red */
    val K = Color(0xFFFF2D55)
    /** D line / label — green */
    val D = Color(0xFF00C853)
}

/**
 * Result of [computeKd]: parallel series aligned with the input OHLCV list.
 * Entries are null until enough history exists for RSV (`index >= period - 1`).
 */
data class KdSeries(
    val k: List<Double?>,
    val d: List<Double?>,
)

/**
 * Compute recursive KD on a date-ascending full series.
 *
 * ```
 * RSV = 100 * (C - LL) / (HH - LL)   // 50 if HH == LL
 * K_t = RSV/k + K_{t-1}*(k-1)/k     // first: K = RSV
 * D_t = K/d   + D_{t-1}*(d-1)/d     // first: D = RSV
 * ```
 */
fun computeKd(all: List<DailyOhlcv>, settings: KdSettings): KdSeries {
    val n = all.size
    if (n == 0 || settings.period <= 0 || settings.k <= 0 || settings.d <= 0) {
        return KdSeries(k = List(n) { null }, d = List(n) { null })
    }
    val period = settings.period
    val kFactor = settings.k.toDouble()
    val dFactor = settings.d.toDouble()

    val kOut = ArrayList<Double?>(n)
    val dOut = ArrayList<Double?>(n)
    var prevK: Double? = null
    var prevD: Double? = null

    for (t in 0 until n) {
        if (t < period - 1) {
            kOut += null
            dOut += null
            continue
        }
        val from = t - period + 1
        var ll = Double.POSITIVE_INFINITY
        var hh = Double.NEGATIVE_INFINITY
        for (i in from..t) {
            val bar = all[i]
            if (bar.low < ll) ll = bar.low
            if (bar.high > hh) hh = bar.high
        }
        val close = all[t].close
        val rsv = if (hh <= ll) {
            50.0
        } else {
            100.0 * (close - ll) / (hh - ll)
        }

        val kVal = if (prevK == null) {
            rsv
        } else {
            rsv / kFactor + prevK * (kFactor - 1.0) / kFactor
        }
        val dVal = if (prevD == null) {
            // Seed D with RSV on first valid day (same as common chart packages).
            rsv
        } else {
            kVal / dFactor + prevD * (dFactor - 1.0) / dFactor
        }
        // Clamp numerically into a sensible band (floating drift only).
        val kClamped = kVal.coerceIn(-5.0, 105.0)
        val dClamped = dVal.coerceIn(-5.0, 105.0)
        kOut += kClamped
        dOut += dClamped
        prevK = kClamped
        prevD = dClamped
    }
    return KdSeries(k = kOut, d = dOut)
}

fun sliceKdSeries(full: KdSeries, start: Int, endInclusive: Int): KdSeries =
    KdSeries(
        k = sliceMaSeries(full.k, start, endInclusive),
        d = sliceMaSeries(full.d, start, endInclusive),
    )

/** Format KD value for labels (1–2 decimal style via [formatPrice]). */
fun formatKd(value: Double): String = formatPrice(value)
