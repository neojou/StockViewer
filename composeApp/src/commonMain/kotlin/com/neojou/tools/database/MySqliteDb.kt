package com.neojou.tools.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Default [MyDb] implementation wrapping a SQLDelight [SqlDriver].
 */
class MySqliteDb internal constructor(
    override val config: MyDbConfig,
    override val displayName: String,
    private val sqlDriver: SqlDriver,
) : MyDb {

    private var open: Boolean = true

    override val isOpen: Boolean get() = open

    override fun driver(): SqlDriver {
        check(open) { "MyDb is closed: $displayName" }
        return sqlDriver
    }

    override fun <T> transaction(block: () -> T): T {
        check(open) { "MyDb is closed: $displayName" }
        // Prefer generated Database.transaction { } in app code for multi-statement atomicity.
        // Driver-level BEGIN/COMMIT varies by platform; this default runs the block as-is.
        return block()
    }

    override fun close() {
        if (!open) return
        open = false
        sqlDriver.close()
    }
}
