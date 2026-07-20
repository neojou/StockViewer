package com.neojou.stockviewer.di

import com.neojou.stockviewer.data.repository.OhlcvRepositoryImpl
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.platform.createStockViewerDatabase
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog

private const val TAG = "AppContainer"

/**
 * Lightweight service locator for shared dependencies.
 *
 * Keeps initialization idempotent; does not pull in a DI framework.
 * UI must depend on [OhlcvRepository], never on SQLDelight types.
 */
object AppContainer {
    private var ohlcvRepository: OhlcvRepository? = null

    /**
     * Returns the singleton [OhlcvRepository], creating the SQLite database on first use.
     *
     * On WasmJS (Phase 1) driver creation fails; callers should handle [Result.failure].
     */
    fun ohlcvRepository(): Result<OhlcvRepository> = runCatching {
        ohlcvRepository ?: OhlcvRepositoryImpl(createStockViewerDatabase()).also {
            ohlcvRepository = it
            MyLog.add(TAG, "OhlcvRepository ready", LogLevel.DEBUG)
        }
    }
}
