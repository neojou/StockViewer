package com.neojou.stockviewer.platform

import app.cash.sqldelight.db.SqlDriver

/**
 * WasmJS SQLDelight driver (Phase 1).
 *
 * Persistence on Wasm is deferred (see AGENTS.md Phase 1 / Phase 2).
 * Creating a driver here fails fast so Desktop remains the primary DB target.
 *
 * Phase 2 options:
 * - [WebWorkerDriver] + sql.js (`app.cash.sqldelight:web-worker-driver`)
 * - Remote REST backend instead of local SQLite
 */
actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        error(
            "SQLDelight local persistence is not enabled on WasmJS yet (Phase 1). " +
                "Use the Desktop target for SQLite, or implement WebWorkerDriver in Phase 2."
        )
    }
}
