package com.neojou.stockviewer.domain.model

import kotlinx.datetime.LocalDate

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
