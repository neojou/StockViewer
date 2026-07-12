package com.neojou.stockviewer.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Desktop (JVM) Ktor client using the CIO engine.
 */
actual fun createHttpClient(): HttpClient = HttpClient(CIO) {
    installSharedPlugins()
}
