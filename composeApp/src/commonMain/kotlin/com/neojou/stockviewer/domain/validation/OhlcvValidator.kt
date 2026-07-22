package com.neojou.stockviewer.domain.validation

import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Result of validating an OHLCV entry or raw form fields.
 */
sealed class OhlcvValidationResult {
    data object Ok : OhlcvValidationResult()
    data class Error(val message: String) : OhlcvValidationResult()
}

/**
 * Pure OHLC(V) validation rules (no DB / UI dependency).
 *
 * Rules (aligned with AGENTS.md):
 * - open / high / low / close > 0
 * - volume ≥ 0
 * - high ≥ low, high ≥ open, high ≥ close (with float epsilon)
 * - low ≤ open, low ≤ close
 * - date must be valid `YYYY-MM-DD`
 */
object OhlcvValidator {

    /** Tolerance for REAL / Double round-trip from SQLite. */
    private const val EPS = 1e-6

    private fun ge(a: Double, b: Double): Boolean = a >= b - EPS
    private fun le(a: Double, b: Double): Boolean = a <= b + EPS

    /**
     * Validates a fully parsed [DailyOhlcv] domain object.
     */
    fun validate(entry: DailyOhlcv): OhlcvValidationResult {
        if (entry.open <= 0.0) return OhlcvValidationResult.Error("開盤價必須大於 0")
        if (entry.high <= 0.0) return OhlcvValidationResult.Error("最高價必須大於 0")
        if (entry.low <= 0.0) return OhlcvValidationResult.Error("最低價必須大於 0")
        if (entry.close <= 0.0) return OhlcvValidationResult.Error("收盤價必須大於 0")
        if (entry.volume < 0L) return OhlcvValidationResult.Error("成交量不可為負數")
        if (!ge(entry.high, entry.low)) {
            return OhlcvValidationResult.Error("最高價不可低於最低價")
        }
        if (!ge(entry.high, entry.open) || !ge(entry.high, entry.close)) {
            return OhlcvValidationResult.Error("最高價必須 ≥ 開盤價與收盤價")
        }
        if (!le(entry.low, entry.open) || !le(entry.low, entry.close)) {
            return OhlcvValidationResult.Error("最低價必須 ≤ 開盤價與收盤價")
        }
        return OhlcvValidationResult.Ok
    }

    /**
     * Parses and validates raw form strings into a [DailyOhlcv], or returns a field-level error.
     */
    fun parseAndValidate(
        dateText: String,
        openText: String,
        highText: String,
        lowText: String,
        closeText: String,
        volumeText: String,
    ): Pair<DailyOhlcv?, OhlcvValidationResult> {
        val date = try {
            LocalDate.parse(dateText.trim())
        } catch (_: Exception) {
            return null to OhlcvValidationResult.Error("日期格式須為 YYYY-MM-DD")
        }

        val open = parseDouble(openText)
            ?: return null to OhlcvValidationResult.Error("開盤價格式錯誤")
        val high = parseDouble(highText)
            ?: return null to OhlcvValidationResult.Error("最高價格式錯誤")
        val low = parseDouble(lowText)
            ?: return null to OhlcvValidationResult.Error("最低價格式錯誤")
        val close = parseDouble(closeText)
            ?: return null to OhlcvValidationResult.Error("收盤價格式錯誤")
        val volume = parseLong(volumeText)
            ?: return null to OhlcvValidationResult.Error("成交量須為整數")

        val entry = DailyOhlcv(
            date = date,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
        )
        return when (val result = validate(entry)) {
            OhlcvValidationResult.Ok -> entry to OhlcvValidationResult.Ok
            is OhlcvValidationResult.Error -> null to result
        }
    }

    /** Accepts plain and scientific notation; trims whitespace. */
    private fun parseDouble(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()
    }

    private fun parseLong(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { return it }
        // Allow "123.0" style from float formatting
        val d = t.replace(',', '.').toDoubleOrNull() ?: return null
        val asLong = d.toLong()
        return if (abs(d - asLong) < EPS) asLong else null
    }
}
