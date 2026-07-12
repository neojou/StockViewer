package com.neojou.stockviewer.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Creates a platform-specific Ktor [HttpClient].
 *
 * - Desktop (JVM): CIO engine
 * - WasmJS: Js engine
 *
 * Shared plugins (JSON serialization, logging) are installed via [installSharedPlugins].
 *
 * @see <a href="https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html">Ktor + SQLDelight tutorial</a>
 */
expect fun createHttpClient(): HttpClient

/**
 * Shared Ktor client plugins for all platforms.
 *
 * - [ContentNegotiation] + kotlinx.serialization JSON
 * - [Logging] for request/response diagnostics during development
 */
internal fun HttpClientConfig<*>.installSharedPlugins() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                useAlternativeNames = false
            }
        )
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                // Keep network layer free of MyLog; swap in later if needed.
                println("[Ktor] $message")
            }
        }
        level = LogLevel.INFO
    }
}
