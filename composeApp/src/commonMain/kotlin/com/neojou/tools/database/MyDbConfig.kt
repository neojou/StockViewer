package com.neojou.tools.database

/**
 * Portable configuration for opening a local SQLite database via [MyDb].
 *
 * Host apps supply names only; path resolution is platform-specific
 * (e.g. Desktop: `{user.home}/.{appName}/{databaseFileName}`).
 *
 * @property appName Logical app id used for the config directory (no path separators).
 * @property databaseFileName File name only, e.g. `"spacex.db"`.
 * @property inMemory When true, use an in-memory SQLite instance (tests / ephemeral).
 */
data class MyDbConfig(
    val appName: String,
    val databaseFileName: String,
    val inMemory: Boolean = false,
) {
    init {
        require(appName.isNotBlank()) { "appName must not be blank" }
        require(!appName.contains('/') && !appName.contains('\\')) {
            "appName must not contain path separators"
        }
        require(databaseFileName.isNotBlank()) { "databaseFileName must not be blank" }
        require(!databaseFileName.contains('/') && !databaseFileName.contains('\\')) {
            "databaseFileName must be a file name, not a path"
        }
    }
}
