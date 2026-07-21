package com.neojou.stockviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.neojou.stockviewer.di.AppContainer
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.presentation.form.OhlcvInputDialog
import com.neojou.stockviewer.presentation.list.OhlcvDataTableDialog
import com.neojou.stockviewer.presentation.toolbar.AppToolbar
import com.neojou.stockviewer.presentation.toolbar.DatabaseSubMenu
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.launch

/**
 * Log tag used by [StockViewer] for logging UI events.
 */
private const val TAG = "StockViewer"

/**
 * Primary application shell.
 *
 * Hosts the top [AppToolbar] and content area.
 * - Database → Input opens [OhlcvInputDialog]
 * - Database → View opens [OhlcvDataTableDialog]
 */
@Composable
fun StockViewer() {
    var showInputDialog by remember { mutableStateOf(false) }
    var showViewDialog by remember { mutableStateOf(false) }
    var repository by remember { mutableStateOf<OhlcvRepository?>(null) }
    var repositoryError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun requireRepository(onReady: () -> Unit) {
        if (repository != null) {
            onReady()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(repositoryError ?: "資料庫尚未就緒")
            }
        }
    }

    LaunchedEffect(Unit) {
        MyLog.add(TAG, "Enter", LogLevel.DEBUG)
        AppContainer.ohlcvRepository()
            .onSuccess { repository = it }
            .onFailure { e ->
                repositoryError = e.message ?: "資料庫初始化失敗"
                MyLog.add(TAG, "DB init failed: $repositoryError", LogLevel.ERROR)
            }
    }

    Scaffold(
        topBar = {
            AppToolbar(
                onDatabaseSubMenuClick = { menu ->
                    when (menu) {
                        DatabaseSubMenu.Input -> requireRepository { showInputDialog = true }
                        DatabaseSubMenu.View -> requireRepository { showViewDialog = true }
                    }
                },
                onKChartClick = {
                    // Reserved for P2 chart
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

    val repo = repository
    if (showInputDialog && repo != null) {
        OhlcvInputDialog(
            repository = repo,
            onDismiss = { showInputDialog = false },
            onSaved = { entry ->
                scope.launch {
                    snackbarHostState.showSnackbar("已儲存 ${entry.date}")
                }
            },
        )
    }
    if (showViewDialog && repo != null) {
        OhlcvDataTableDialog(
            repository = repo,
            onDismiss = { showViewDialog = false },
        )
    }
}
