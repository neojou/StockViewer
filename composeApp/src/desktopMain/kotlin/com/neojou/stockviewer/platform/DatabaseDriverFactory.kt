package com.neojou.stockviewer.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neojou.stockviewer.database.StockViewerDatabase
import java.io.File
import java.util.Properties

/**
 * Desktop (JVM) SQLDelight driver: local SQLite file under the user home directory.
 *
 * Path: `{user.home}/.stockviewer/spacex.db`
 */
actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".stockviewer")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, "spacex.db")
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        return JdbcSqliteDriver(
            url = url,
            properties = Properties(),
            schema = StockViewerDatabase.Schema,
        )
    }
}
