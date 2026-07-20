package com.neojou.stockviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.neojou.stockviewer.presentation.toolbar.AppToolbar
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog

/**
 * Log tag used by [StockViewer] for logging UI events.
 */
private const val TAG = "StockViewer"

/**
 * Primary application shell.
 *
 * Hosts the top [AppToolbar] (Database / K Chart menus) and a placeholder content area.
 * Menu actions are intentionally no-ops until Input / View / Chart screens are implemented.
 */
@Composable
fun StockViewer() {
    LaunchedEffect(Unit) {
        MyLog.add(TAG, "Enter", LogLevel.DEBUG)
    }

    Scaffold(
        topBar = {
            AppToolbar(
                // Reserved for future navigation (P1+)
                onDatabaseSubMenuClick = { /* no-op */ },
                onKChartClick = { /* no-op */ },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Stock Viewer",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
