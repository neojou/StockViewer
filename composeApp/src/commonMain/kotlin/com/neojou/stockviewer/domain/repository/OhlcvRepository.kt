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

    /**
     * Load a single bar by trading date (primary key).
     * @return [Result] of null when no row exists for [key].
     */
    suspend fun get(key: LocalDate): Result<DailyOhlcv?>

    /**
     * Insert or replace a bar; [row] identity is [DailyOhlcv.date].
     * Same date never yields two rows (SQLite PRIMARY KEY + INSERT OR REPLACE).
     */
    suspend fun upsert(row: DailyOhlcv): Result<Unit>

    /** Delete by trading date (primary key). */
    suspend fun delete(key: LocalDate): Result<Unit>
}
