package com.neojou.stockviewer.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.neojou.stockviewer.data.mapper.dateIso
import com.neojou.stockviewer.data.mapper.toDomain
import com.neojou.stockviewer.database.StockViewerDatabase
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private const val TAG = "OhlcvRepo"

/**
 * SQLDelight-backed [OhlcvRepository].
 */
class OhlcvRepositoryImpl(
    private val database: StockViewerDatabase,
) : OhlcvRepository {

    private val queries get() = database.dailyOhlcvQueries

    override fun observeAll(): Flow<List<DailyOhlcv>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>> =
        queries.selectByDateRange(start.toString(), end.toString())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(entry: DailyOhlcv): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            queries.insertOrReplace(
                date = entry.dateIso(),
                open = entry.open,
                high = entry.high,
                low = entry.low,
                close = entry.close,
                volume = entry.volume,
            )
            MyLog.add(TAG, "upsert ${entry.dateIso()}", LogLevel.DEBUG)
        }
    }

    override suspend fun delete(date: LocalDate): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            queries.deleteByDate(date.toString())
            MyLog.add(TAG, "delete $date", LogLevel.DEBUG)
        }
    }
}
