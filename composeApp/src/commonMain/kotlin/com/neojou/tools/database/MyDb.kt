package com.neojou.tools.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Database-level handle: lifecycle, identity, and access to the underlying [SqlDriver].
 *
 * Product code should still expose domain repositories to UI;
 * [MyDb] is for data-layer / tools reuse across apps.
 *
 * Swap backends by providing another [MyDb] implementation + factory, without changing UI.
 */
interface MyDb {
    val config: MyDbConfig

    /** Human-readable location (file path or `in-memory`) for logs/diagnostics. */
    val displayName: String

    val isOpen: Boolean

    /**
     * SQLDelight driver. Callers in the data layer use this to construct generated Database types.
     * Do not expose to presentation.
     */
    fun driver(): SqlDriver

    /**
     * Runs [block] inside a SQLite transaction when supported by the driver.
     * Nested usage should follow SQLDelight transaction rules.
     */
    fun <T> transaction(block: () -> T): T

    fun close()
}

/**
 * Opens a SQLite [MyDb] for the given [config].
 *
 * @param schema When non-null, applied on open (create/migrate) — typically
 *   `SomeDatabase.Schema` from SQLDelight.
 */
fun openMyDb(
    config: MyDbConfig,
    schema: SqlSchema<QueryResult.Value<Unit>>? = null,
): MyDb = openMySqliteDb(config, schema)

/**
 * Platform factory for SQLite [SqlDriver] used by [openMyDb].
 */
internal expect fun openMySqliteDb(
    config: MyDbConfig,
    schema: SqlSchema<QueryResult.Value<Unit>>?,
): MyDb
