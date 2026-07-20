package com.neojou.stockviewer.data.mapper

import com.neojou.stockviewer.database.Daily_ohlcv
import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlinx.datetime.LocalDate

/**
 * Maps between SQLDelight [Daily_ohlcv] rows and domain [DailyOhlcv].
 */
fun Daily_ohlcv.toDomain(): DailyOhlcv = DailyOhlcv(
    date = LocalDate.parse(date),
    open = open_,
    high = high,
    low = low,
    close = close,
    volume = volume,
)

/**
 * ISO-8601 date string for SQLite TEXT primary key.
 */
fun DailyOhlcv.dateIso(): String = date.toString()
