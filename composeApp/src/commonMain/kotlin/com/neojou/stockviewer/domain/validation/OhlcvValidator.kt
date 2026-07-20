package com.neojou.stockviewer.domain.validation

import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlinx.datetime.LocalDate

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
 * - high ≥ low, high ≥ open, high ≥ close
 * - low ≤ open, low ≤ close
 * - date must be valid `YYYY-MM-DD`
 */
object OhlcvValidator {

    /**
     * Validates a fully parsed [DailyOhlcv] domain object.
     */
    fun validate(entry: DailyOhlcv): OhlcvValidationResult {
        if (entry.open <= 0.0) return OhlcvValidationResult.Error("開盤價必須大於 0")
        if (entry.high <= 0.0) return OhlcvValidationResult.Error("最高價必須大於 0")
        if (entry.low <= 0.0) return OhlcvValidationResult.Error("最低價必須大於 0")
        if (entry.close <= 0.0) return OhlcvValidationResult.Error("收盤價必須大於 0")
        if (entry.volume < 0L) return OhlcvValidationResult.Error("成交量不可為負數")
        if (entry.high < entry.low) return OhlcvValidationResult.Error("最高價不可低於最低價")
        if (entry.high < entry.open || entry.high < entry.close) {
            return OhlcvValidationResult.Error("最高價必須 ≥ 開盤價與收盤價")
        }
        if (entry.low > entry.open || entry.low > entry.close) {
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

        val open = openText.trim().toDoubleOrNull()
            ?: return null to OhlcvValidationResult.Error("開盤價格式錯誤")
        val high = highText.trim().toDoubleOrNull()
            ?: return null to OhlcvValidationResult.Error("最高價格式錯誤")
        val low = lowText.trim().toDoubleOrNull()
            ?: return null to OhlcvValidationResult.Error("最低價格式錯誤")
        val close = closeText.trim().toDoubleOrNull()
            ?: return null to OhlcvValidationResult.Error("收盤價格式錯誤")
        val volume = volumeText.trim().toLongOrNull()
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
}
