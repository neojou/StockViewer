package com.neojou.stockviewer.platform

import app.cash.sqldelight.db.SqlDriver
import com.neojou.stockviewer.database.StockViewerDatabase

/**
 * Platform-specific factory that creates a SQLDelight [SqlDriver].
 *
 * - Desktop (JVM): [JdbcSqliteDriver] writing to `~/.stockviewer/spacex.db`
 * - WasmJS: Phase 1 stub (persistence deferred); Phase 2 may use WebWorkerDriver + sql.js
 *
 * Follows the expect/actual driver pattern from the official KMP SQLDelight docs.
 *
 * @see <a href="https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html">Ktor + SQLDelight tutorial</a>
 */
expect class DatabaseDriverFactory() {
    fun createDriver(): SqlDriver
}

/**
 * Creates the shared [StockViewerDatabase] using the platform driver.
 *
 * Callers in the data layer should prefer this helper (or inject the result via [AppContainer])
 * rather than constructing the database directly in UI code.
 */
fun createStockViewerDatabase(driverFactory: DatabaseDriverFactory = DatabaseDriverFactory()): StockViewerDatabase {
    return StockViewerDatabase(driverFactory.createDriver())
}
