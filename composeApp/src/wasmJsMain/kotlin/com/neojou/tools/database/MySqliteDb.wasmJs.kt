package com.neojou.tools.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema

/**
 * Wasm Phase 1: no local SQLite file driver.
 * Phase 2 may use WebWorkerDriver + sql.js, or [MyDbConfig.inMemory] with a wasm-capable driver.
 */
internal actual fun openMySqliteDb(
    config: MyDbConfig,
    schema: SqlSchema<QueryResult.Value<Unit>>?,
): MyDb {
    error(
        "SQLite MyDb is not enabled on WasmJS yet (Phase 1). " +
            "Use Desktop, or implement a Wasm driver (e.g. sql.js) / in-memory backend in Phase 2. " +
            "config=${config.appName}/${config.databaseFileName}, inMemory=${config.inMemory}",
    )
}
