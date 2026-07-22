package com.neojou.stockviewer.platform

import com.neojou.stockviewer.database.StockViewerDatabase
import com.neojou.stockviewer.data.table.DailyOhlcvTable
import com.neojou.tools.database.MyDb
import com.neojou.tools.database.MyDbConfig
import com.neojou.tools.database.openMyDb

/**
 * StockViewer default SQLite location: `{user.home}/.stockviewer/spacex.db` on Desktop.
 */
fun stockViewerDbConfig(
    inMemory: Boolean = false,
): MyDbConfig = MyDbConfig(
    appName = "stockviewer",
    databaseFileName = "spacex.db",
    inMemory = inMemory,
)

/**
 * Opens [MyDb] + generated [StockViewerDatabase] + [DailyOhlcvTable] for the app container.
 */
fun openStockViewerData(
    config: MyDbConfig = stockViewerDbConfig(),
): StockViewerData {
    val myDb = openMyDb(
        config = config,
        schema = StockViewerDatabase.Schema,
    )
    val database = StockViewerDatabase(myDb.driver())
    val table = DailyOhlcvTable(db = myDb, database = database)
    return StockViewerData(myDb = myDb, database = database, ohlcvTable = table)
}

/**
 * Bundle of DB handles created at startup.
 */
data class StockViewerData(
    val myDb: MyDb,
    val database: StockViewerDatabase,
    val ohlcvTable: DailyOhlcvTable,
)
