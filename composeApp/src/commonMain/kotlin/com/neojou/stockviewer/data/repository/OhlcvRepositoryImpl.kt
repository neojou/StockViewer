package com.neojou.stockviewer.data.repository

import com.neojou.stockviewer.data.table.DailyOhlcvTable
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.tools.database.MyCrudRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * [OhlcvRepository] implementation:
 * - CRUD / [observeAll] via [MyCrudRepository] → [DailyOhlcvTable]
 * - OHLCV-only queries implemented here (range, recent)
 */
class OhlcvRepositoryImpl(
    private val ohlcvTable: DailyOhlcvTable,
) : MyCrudRepository<LocalDate, DailyOhlcv>(ohlcvTable), OhlcvRepository {

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>> =
        ohlcvTable.observeRange(start, end)

    override suspend fun getRecent(limit: Int): Result<List<DailyOhlcv>> =
        ohlcvTable.getRecent(limit)
}
