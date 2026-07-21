package com.neojou.stockviewer.domain.repository

import com.neojou.stockviewer.domain.model.DailyOhlcv
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository contract for daily OHLCV persistence.
 *
 * UI / ViewModel depend only on this interface (not SQLDelight).
 */
interface OhlcvRepository {
    fun observeAll(): Flow<List<DailyOhlcv>>
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>>

    /**
     * One-shot load of the most recent rows, ordered by date descending
     * (newest / closest to today first). Default limit is 100.
     */
    suspend fun getRecent(limit: Int = 100): Result<List<DailyOhlcv>>

    suspend fun upsert(entry: DailyOhlcv): Result<Unit>
    suspend fun delete(date: LocalDate): Result<Unit>
}
