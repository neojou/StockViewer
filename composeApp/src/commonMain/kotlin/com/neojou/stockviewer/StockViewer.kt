package com.neojou.stockviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog

/**
 * Log tag used by [CurrencyViewer] for logging UI events.
 */
private const val TAG = "StockViewer"

/**
 * The primary UI screen for the CurrencyViewer application.
 *
 * Responsibilities (current stage):
 * - Logs a one-time "Enter" event when the screen first enters the Composition.
 * - Displays a centered placeholder UI for the upcoming data layer integration.
 * - Exposes a user action entry point ("Fetch Exchange Rates") for the next task.
 *
 * Note:
 * Logging is executed in [LaunchedEffect] to avoid being triggered on every recomposition.
 * Compose recommends using Effect APIs for side effects to ensure predictable execution timing. [web:99]
 */
@Composable
fun StockViewer() {
    LaunchedEffect(Unit) {
        MyLog.add(TAG, "Enter", LogLevel.DEBUG)
        //test_web_access()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Stock Viewer",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
