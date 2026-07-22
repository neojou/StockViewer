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
import com.neojou.stockviewer.presentation.chart.CandlestickChart
import com.neojou.stockviewer.domain.export.OhlcvExportFormat
import com.neojou.stockviewer.presentation.export.OhlcvExportFormatDialog
import com.neojou.stockviewer.presentation.export.OhlcvExportResult
import com.neojou.stockviewer.presentation.export.exportAllOhlcv
import com.neojou.stockviewer.presentation.form.OhlcvInputDialog
import com.neojou.stockviewer.presentation.import.OhlcvImportConfirmDialog
import com.neojou.stockviewer.presentation.import.OhlcvImportPickResult
import com.neojou.stockviewer.presentation.import.OhlcvImportPreview
import com.neojou.stockviewer.presentation.import.applyOhlcvImport
import com.neojou.stockviewer.presentation.import.pickAndParseOhlcvImport
import com.neojou.stockviewer.presentation.list.OhlcvDataTableDialog
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.ui.menu.MyTopMenuBar
import com.neojou.tools.ui.menu.MyTopMenuItem
import kotlinx.coroutines.launch

/**
 * Log tag used by [StockViewer] for logging UI events.
 */
private const val TAG = "StockViewer"

/**
 * Main content modes for the shell area below the toolbar.
 */
private enum class MainContent {
    /** Default placeholder until a feature is chosen. */
    Home,

    /** Daily candlestick + volume chart (recent 30 days). */
    KChart,
}

/**
 * Primary application shell.
 *
 * Hosts a product-configured [MyTopMenuBar] and content area.
 * - Database → Input / View / Export / Import
 * - K Chart shows [CandlestickChart] in the main content area
 */
@Composable
fun StockViewer() {
    var showInputDialog by remember { mutableStateOf(false) }
    var showViewDialog by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<OhlcvImportPreview?>(null) }
    var mainContent by remember { mutableStateOf(MainContent.Home) }
    var repository by remember { mutableStateOf<OhlcvRepository?>(null) }
    var repositoryError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun requireRepository(onReady: (OhlcvRepository) -> Unit) {
        val repo = repository
        if (repo != null) {
            onReady(repo)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(repositoryError ?: "資料庫尚未就緒")
            }
        }
    }

    fun runExport(repo: OhlcvRepository, format: OhlcvExportFormat) {
        scope.launch {
            MyLog.add(TAG, "Database > Export (${format.extension})", LogLevel.DEBUG)
            when (val result = exportAllOhlcv(repo, format)) {
                is OhlcvExportResult.Success -> {
                    snackbarHostState.showSnackbar(
                        "已匯出 ${result.rowCount} 筆 (${result.format.extension}) → ${result.path}",
                    )
                }
                OhlcvExportResult.Cancelled -> {
                    snackbarHostState.showSnackbar("已取消匯出")
                }
                is OhlcvExportResult.Failure -> {
                    snackbarHostState.showSnackbar("匯出失敗：${result.message}")
                }
            }
        }
    }

    fun runImportPick() {
        scope.launch {
            MyLog.add(TAG, "Database > Import", LogLevel.DEBUG)
            when (val result = pickAndParseOhlcvImport()) {
                is OhlcvImportPickResult.Ready -> {
                    importPreview = result.preview
                }
                OhlcvImportPickResult.Cancelled -> {
                    snackbarHostState.showSnackbar("已取消匯入")
                }
                is OhlcvImportPickResult.Failure -> {
                    snackbarHostState.showSnackbar("匯入失敗：${result.message}")
                }
            }
        }
    }

    fun runImportApply(repo: OhlcvRepository, preview: OhlcvImportPreview) {
        scope.launch {
            val stats = applyOhlcvImport(
                repository = repo,
                rows = preview.rows,
                skippedInvalid = preview.skippedInvalid,
            )
            snackbarHostState.showSnackbar("匯入完成：${stats.summary}")
        }
    }

    // Product-specific menu tree only; [MyTopMenuBar] stays app-agnostic.
    // Rebuilt each composition so callbacks always see current shell state.
    val topMenus = listOf(
        MyTopMenuItem(
            id = "database",
            label = "Database",
            children = listOf(
                MyTopMenuItem(
                    id = "db.input",
                    label = "Input",
                    onClick = {
                        MyLog.add(TAG, "Database > Input", LogLevel.DEBUG)
                        requireRepository { showInputDialog = true }
                    },
                ),
                MyTopMenuItem(
                    id = "db.view",
                    label = "View",
                    onClick = {
                        MyLog.add(TAG, "Database > View", LogLevel.DEBUG)
                        requireRepository { showViewDialog = true }
                    },
                ),
                MyTopMenuItem(
                    id = "db.export",
                    label = "Export",
                    onClick = {
                        MyLog.add(TAG, "Database > Export", LogLevel.DEBUG)
                        requireRepository { showExportFormatDialog = true }
                    },
                ),
                MyTopMenuItem(
                    id = "db.import",
                    label = "Import",
                    onClick = {
                        requireRepository { runImportPick() }
                    },
                ),
            ),
        ),
        MyTopMenuItem(
            id = "kchart",
            label = "K Chart",
            onClick = {
                requireRepository {
                    mainContent = MainContent.KChart
                    MyLog.add(TAG, "Show K Chart", LogLevel.DEBUG)
                }
            },
        ),
    )

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
            MyTopMenuBar(items = topMenus)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val repo = repository
            when (mainContent) {
                MainContent.Home -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Stock Viewer",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                MainContent.KChart -> {
                    if (repo != null) {
                        CandlestickChart(
                            repository = repo,
                            chartModifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = repositoryError ?: "資料庫尚未就緒",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
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
    if (showExportFormatDialog && repo != null) {
        OhlcvExportFormatDialog(
            onDismiss = { showExportFormatDialog = false },
            onConfirm = { format ->
                showExportFormatDialog = false
                runExport(repo, format)
            },
        )
    }
    val preview = importPreview
    if (preview != null && repo != null) {
        OhlcvImportConfirmDialog(
            preview = preview,
            onDismiss = { importPreview = null },
            onConfirm = {
                importPreview = null
                runImportApply(repo, preview)
            },
        )
    }
}
