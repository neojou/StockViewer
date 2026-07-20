package com.neojou.stockviewer.presentation.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog

private const val TAG = "AppToolbar"

/**
 * Second-level items under the Database menu.
 *
 * - [Input] → opens OHLCV input dialog (wired in [com.neojou.stockviewer.StockViewer])
 * - [View] → reserved for data table (P3)
 */
enum class DatabaseSubMenu {
    Input,
    View,
}

/**
 * Top application toolbar with nested dropdown menus.
 *
 * Structure:
 * ```
 * [ Database ▾ ]  [ K Chart ]
 *      ├─ Input   → OhlcvInputDialog
 *      └─ View    → (P3)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolbar(
    modifier: Modifier = Modifier,
    onDatabaseSubMenuClick: (DatabaseSubMenu) -> Unit = {},
    onKChartClick: () -> Unit = {},
) {
    var databaseMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = {
            Row {
                // Level 1: Database → Level 2: Input / View
                Box {
                    TextButton(
                        onClick = { databaseMenuExpanded = true },
                    ) {
                        Text("Database")
                    }
                    DropdownMenu(
                        expanded = databaseMenuExpanded,
                        onDismissRequest = { databaseMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Input") },
                            onClick = {
                                databaseMenuExpanded = false
                                MyLog.add(TAG, "Database > Input", LogLevel.DEBUG)
                                onDatabaseSubMenuClick(DatabaseSubMenu.Input)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("View") },
                            onClick = {
                                databaseMenuExpanded = false
                                MyLog.add(TAG, "Database > View (no-op)", LogLevel.DEBUG)
                                onDatabaseSubMenuClick(DatabaseSubMenu.View)
                            },
                        )
                    }
                }

                // Level 1: K Chart (no submenu yet)
                TextButton(
                    onClick = {
                        MyLog.add(TAG, "K Chart (no-op)", LogLevel.DEBUG)
                        onKChartClick()
                    },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("K Chart")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
