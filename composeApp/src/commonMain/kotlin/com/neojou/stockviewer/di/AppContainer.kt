package com.neojou.stockviewer.di

import com.neojou.stockviewer.data.repository.OhlcvRepositoryImpl
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.platform.StockViewerData
import com.neojou.stockviewer.platform.openStockViewerData
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.database.MyDb

private const val TAG = "AppContainer"

/**
 * Lightweight service locator for shared dependencies.
 *
 * Keeps initialization idempotent; does not pull in a DI framework.
 * UI must depend on [OhlcvRepository], never on SQLDelight types or [MyDb].
 */
object AppContainer {
    private var ohlcvRepository: OhlcvRepository? = null
    private var stockViewerData: StockViewerData? = null

    /**
     * Returns the singleton [OhlcvRepository], opening SQLite via [com.neojou.tools.database.MyDb] on first use.
     *
     * On WasmJS (Phase 1) open fails; callers should handle [Result.failure].
     */
    fun ohlcvRepository(): Result<OhlcvRepository> = runCatching {
        ohlcvRepository ?: run {
            val data = openStockViewerData()
            stockViewerData = data
            OhlcvRepositoryImpl(data.ohlcvTable).also {
                ohlcvRepository = it
                MyLog.add(
                    TAG,
                    "OhlcvRepository ready (MyDb=${data.myDb.displayName})",
                    LogLevel.DEBUG,
                )
            }
        }
    }

    /** Optional access for diagnostics/tests; prefer [ohlcvRepository] in UI. */
    fun myDb(): MyDb? = stockViewerData?.myDb
}
