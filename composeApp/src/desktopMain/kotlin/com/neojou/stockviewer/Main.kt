package com.neojou.stockviewer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neojou.stockviewer.App

/**
 * Desktop entry point for the Test Cursor app.
 *
 * Creates a Compose for Desktop [Window] within [application]
 * and hosts the shared [App] composable.
 */
fun main() { // 增加 args 以確保 JVM 兼容性
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Stock Viewer",
        ) {
            App()
        }
    }
}
