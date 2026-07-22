package com.neojou.tools.database

import kotlinx.coroutines.flow.Flow

/**
 * Default repository façade that delegates CRUD / observe-all to a [MyCrudTable].
 *
 * Host apps extend this class and implement only **domain-specific** queries
 * (e.g. date range, “recent N rows”) on top of a product repository interface.
 *
 * Example:
 * ```
 * class FooRepositoryImpl(table: FooTable) :
 *     MyCrudRepository<String, Foo>(table), FooRepository {
 *     // extra methods only
 * }
 * ```
 *
 * Does not depend on any application package; safe to reuse across projects.
 */
open class MyCrudRepository<Key, Row>(
    protected val table: MyCrudTable<Key, Row>,
) {
    fun observeAll(): Flow<List<Row>> = table.observeAll()

    suspend fun get(key: Key): Result<Row?> = table.get(key)

    suspend fun listAll(): Result<List<Row>> = table.listAll()

    suspend fun upsert(row: Row): Result<Unit> = table.upsert(row)

    suspend fun delete(key: Key): Result<Unit> = table.delete(key)
}
