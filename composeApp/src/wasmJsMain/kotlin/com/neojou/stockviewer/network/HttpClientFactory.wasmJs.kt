package com.neojou.stockviewer.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

/**
 * WasmJS Ktor client using the Js engine.
 */
actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    installSharedPlugins()
}
