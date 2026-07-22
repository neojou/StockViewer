package com.neojou.stockviewer.data.repository

import com.neojou.stockviewer.data.table.DailyOhlcvTable
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * [OhlcvRepository] façade over [DailyOhlcvTable] (SQLDelight + [com.neojou.tools.database.MyDb]).
 */
class OhlcvRepositoryImpl(
    private val table: DailyOhlcvTable,
) : OhlcvRepository {

    override fun observeAll(): Flow<List<DailyOhlcv>> = table.observeAll()

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>> =
        table.observeRange(start, end)

    override suspend fun getRecent(limit: Int): Result<List<DailyOhlcv>> =
        table.getRecent(limit)

    override suspend fun upsert(entry: DailyOhlcv): Result<Unit> =
        table.upsert(entry)

    override suspend fun delete(date: LocalDate): Result<Unit> =
        table.delete(date)
}
