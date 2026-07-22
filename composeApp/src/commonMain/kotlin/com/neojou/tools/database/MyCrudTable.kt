package com.neojou.tools.database

import kotlinx.coroutines.flow.Flow

/**
 * Optional generic CRUD contract for a single table.
 *
 * Host apps implement this for each table (e.g. OHLCV). Domain-specific queries
 * (range, recent, joins) stay as **extra methods on the concrete table class**
 * or on a domain Repository — not on this interface.
 *
 * @param Key Primary-key type
 * @param Row Domain (or DTO) row type
 */
interface MyCrudTable<Key, Row> {
    suspend fun get(key: Key): Result<Row?>

    suspend fun upsert(row: Row): Result<Unit>

    suspend fun delete(key: Key): Result<Unit>

    suspend fun listAll(): Result<List<Row>>

    /** Reactive stream of all rows; implementations may use SQLDelight [Flow] adapters. */
    fun observeAll(): Flow<List<Row>>
}

/**
 * Lightweight table identity (optional marker for multi-table apps).
 */
interface MyTable {
    val tableName: String
    val db: MyDb
}
