package com.neojou.stockviewer.data.table

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.neojou.stockviewer.data.mapper.dateIso
import com.neojou.stockviewer.data.mapper.toDomain
import com.neojou.stockviewer.database.StockViewerDatabase
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.database.MyCrudTable
import com.neojou.tools.database.MyDb
import com.neojou.tools.database.MyTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private const val TAG = "DailyOhlcvTable"

/**
 * Table adapter for `daily_ohlcv`: implements generic [MyCrudTable] plus OHLCV-specific queries.
 *
 * Domain UI still goes through [com.neojou.stockviewer.domain.repository.OhlcvRepository];
 * this type lives in the data layer only.
 */
class DailyOhlcvTable(
    override val db: MyDb,
    private val database: StockViewerDatabase,
) : MyTable, MyCrudTable<LocalDate, DailyOhlcv> {

    override val tableName: String = "daily_ohlcv"

    private val queries get() = database.dailyOhlcvQueries

    override fun observeAll(): Flow<List<DailyOhlcv>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>> =
        queries.selectByDateRange(start.toString(), end.toString())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(key: LocalDate): Result<DailyOhlcv?> =
        withContext(Dispatchers.Default) {
            runCatching {
                queries.selectByDate(key.toString())
                    .executeAsOneOrNull()
                    ?.toDomain()
            }
        }

    override suspend fun listAll(): Result<List<DailyOhlcv>> =
        withContext(Dispatchers.Default) {
            runCatching {
                queries.selectAll().executeAsList().map { it.toDomain() }
            }
        }

    /**
     * Most recent rows first (business helper beyond generic CRUD).
     */
    suspend fun getRecent(limit: Int): Result<List<DailyOhlcv>> =
        withContext(Dispatchers.Default) {
            runCatching {
                queries.selectRecent(limit.toLong())
                    .executeAsList()
                    .map { it.toDomain() }
                    .also {
                        MyLog.add(TAG, "getRecent limit=$limit count=${it.size}", LogLevel.DEBUG)
                    }
            }
        }

    override suspend fun upsert(row: DailyOhlcv): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                queries.insertOrReplace(
                    date = row.dateIso(),
                    open = row.open,
                    high = row.high,
                    low = row.low,
                    close = row.close,
                    volume = row.volume,
                )
                MyLog.add(TAG, "upsert ${row.dateIso()}", LogLevel.DEBUG)
            }
        }

    override suspend fun delete(key: LocalDate): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                queries.deleteByDate(key.toString())
                MyLog.add(TAG, "delete $key", LogLevel.DEBUG)
            }
        }
}
