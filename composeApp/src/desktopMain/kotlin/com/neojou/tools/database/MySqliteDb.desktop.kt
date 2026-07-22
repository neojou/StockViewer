package com.neojou.tools.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

internal actual fun openMySqliteDb(
    config: MyDbConfig,
    schema: SqlSchema<QueryResult.Value<Unit>>?,
): MyDb {
    val properties = Properties()
    val (url, displayName) = if (config.inMemory) {
        JdbcSqliteDriver.IN_MEMORY to "in-memory:${config.appName}/${config.databaseFileName}"
    } else {
        val dbDir = File(System.getProperty("user.home"), ".${config.appName}")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, config.databaseFileName)
        val path = dbFile.absolutePath
        "jdbc:sqlite:$path" to path
    }

    val driver = if (schema != null) {
        JdbcSqliteDriver(
            url = url,
            properties = properties,
            schema = schema,
        )
    } else {
        JdbcSqliteDriver(
            url = url,
            properties = properties,
        )
    }

    return MySqliteDb(
        config = config,
        displayName = displayName,
        sqlDriver = driver,
    )
}
