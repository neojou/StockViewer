package com.neojou.stockviewer.domain.model

import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Domain entity for a single daily OHLCV bar.
 *
 * Dates use [LocalDate] (ISO-8601 `YYYY-MM-DD` when persisted).
 */
data class DailyOhlcv(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

private const val PRICE_EPS = 1e-6

/**
 * True when [other] has the same trading date and effectively the same OHLCV values
 * (prices compared with a small epsilon for SQLite/CSV/Excel float round-trips).
 */
fun DailyOhlcv.isSameContentAs(other: DailyOhlcv): Boolean {
    if (date != other.date) return false
    if (volume != other.volume) return false
    fun peq(a: Double, b: Double) = abs(a - b) < PRICE_EPS
    return peq(open, other.open) &&
        peq(high, other.high) &&
        peq(low, other.low) &&
        peq(close, other.close)
}
