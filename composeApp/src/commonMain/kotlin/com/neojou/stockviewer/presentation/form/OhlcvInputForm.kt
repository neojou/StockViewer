package com.neojou.stockviewer.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neojou.stockviewer.domain.model.DailyOhlcv
import com.neojou.stockviewer.domain.repository.OhlcvRepository
import com.neojou.stockviewer.domain.validation.OhlcvValidationResult
import com.neojou.stockviewer.domain.validation.OhlcvValidator
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

private const val TAG = "OhlcvInputForm"

/**
 * Popup dialog for a single daily OHLCV row.
 *
 * Date is the primary key (at most one row per day).
 *
 * - **載入**: if date is valid and exists in DB, fill all fields
 * - **確認**: validate and insert-or-replace (new or overwrite same date)
 * - **取消**: dismiss without saving
 */
@Composable
fun OhlcvInputDialog(
    repository: OhlcvRepository,
    onDismiss: () -> Unit,
    onSaved: (DailyOhlcv) -> Unit = {},
) {
    var dateText by remember { mutableStateOf("") }
    var openText by remember { mutableStateOf("") }
    var highText by remember { mutableStateOf("") }
    var lowText by remember { mutableStateOf("") }
    var closeText by remember { mutableStateOf("") }
    var volumeText by remember { mutableStateOf("0") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun applyEntryToFields(entry: DailyOhlcv) {
        dateText = entry.date.toString()
        openText = formatFieldNumber(entry.open)
        highText = formatFieldNumber(entry.high)
        lowText = formatFieldNumber(entry.low)
        closeText = formatFieldNumber(entry.close)
        volumeText = entry.volume.toString()
    }

    fun loadFromDb() {
        if (isBusy) return
        val date = try {
            LocalDate.parse(dateText.trim())
        } catch (_: Exception) {
            errorMessage = "日期格式須為 YYYY-MM-DD"
            infoMessage = null
            return
        }

        isBusy = true
        errorMessage = null
        infoMessage = null
        scope.launch {
            try {
                val result = repository.get(date)
                result.fold(
                    onSuccess = { row ->
                        if (row == null) {
                            errorMessage = "資料庫中找不到日期 $date"
                            MyLog.add(TAG, "Load miss $date", LogLevel.DEBUG)
                        } else {
                            applyEntryToFields(row)
                            infoMessage = "已載入 $date"
                            errorMessage = null
                            MyLog.add(TAG, "Loaded OHLCV $date", LogLevel.INFO)
                        }
                    },
                    onFailure = { e ->
                        errorMessage = e.message ?: "讀取資料庫失敗"
                        MyLog.add(TAG, "Load failed: ${e.message}", LogLevel.ERROR)
                    },
                )
            } finally {
                isBusy = false
            }
        }
    }

    /**
     * Insert or replace by date PK (same date never creates a second row; overwrites if exists).
     *
     * Snapshots field strings at click time so validation always uses what the user sees.
     */
    fun confirmUpsert() {
        if (isBusy) return

        // Snapshot before any async / recomposition
        val dateSnap = dateText
        val openSnap = openText
        val highSnap = highText
        val lowSnap = lowText
        val closeSnap = closeText
        val volumeSnap = volumeText

        val (entry, validation) = OhlcvValidator.parseAndValidate(
            dateText = dateSnap,
            openText = openSnap,
            highText = highSnap,
            lowText = lowSnap,
            closeText = closeSnap,
            volumeText = volumeSnap,
        )
        if (validation is OhlcvValidationResult.Error || entry == null) {
            val reason = (validation as? OhlcvValidationResult.Error)?.message ?: "輸入無效"
            errorMessage = "無法確認：$reason"
            infoMessage = null
            MyLog.add(TAG, "Confirm blocked: $errorMessage", LogLevel.WARN)
            return
        }

        isBusy = true
        errorMessage = null
        infoMessage = null
        scope.launch {
            try {
                val result = repository.upsert(entry)
                result.fold(
                    onSuccess = {
                        MyLog.add(TAG, "Saved OHLCV ${entry.date}", LogLevel.INFO)
                        onSaved(entry)
                        onDismiss()
                    },
                    onFailure = { e ->
                        errorMessage = "無法確認：${e.message ?: "寫入資料庫失敗"}"
                        MyLog.add(TAG, "Save failed: ${e.message}", LogLevel.ERROR)
                    },
                )
            } catch (t: Throwable) {
                errorMessage = "無法確認：${t.message ?: "寫入時發生例外"}"
                MyLog.add(TAG, "Save exception: ${t.message}", LogLevel.ERROR)
            } finally {
                isBusy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        title = { Text("輸入 當日Ｋ線 資料") },
        text = {
            // Status lines stay below the form (outside scroll) so confirm errors stay visible.
            Column(
                modifier = Modifier.widthIn(min = 320.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "日期為主鍵：同一天僅一筆。載入＝依日期帶入欄位；確認＝新增，若日期已存在則覆蓋。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OhlcvField(
                        label = "日期 (YYYY-MM-DD)",
                        value = dateText,
                        onValueChange = {
                            dateText = it
                            errorMessage = null
                            infoMessage = null
                        },
                        enabled = !isBusy,
                    )
                    OhlcvField(
                        label = "開盤價 (Open)",
                        value = openText,
                        onValueChange = { openText = it; errorMessage = null; infoMessage = null },
                        enabled = !isBusy,
                        keyboardType = KeyboardType.Decimal,
                    )
                    OhlcvField(
                        label = "最高價 (High)",
                        value = highText,
                        onValueChange = { highText = it; errorMessage = null; infoMessage = null },
                        enabled = !isBusy,
                        keyboardType = KeyboardType.Decimal,
                    )
                    OhlcvField(
                        label = "最低價 (Low)",
                        value = lowText,
                        onValueChange = { lowText = it; errorMessage = null; infoMessage = null },
                        enabled = !isBusy,
                        keyboardType = KeyboardType.Decimal,
                    )
                    OhlcvField(
                        label = "收盤價 (Close)",
                        value = closeText,
                        onValueChange = { closeText = it; errorMessage = null; infoMessage = null },
                        enabled = !isBusy,
                        keyboardType = KeyboardType.Decimal,
                    )
                    OhlcvField(
                        label = "成交量 (Volume)",
                        value = volumeText,
                        onValueChange = { volumeText = it; errorMessage = null; infoMessage = null },
                        enabled = !isBusy,
                        keyboardType = KeyboardType.Number,
                    )
                }
                if (infoMessage != null) {
                    Text(
                        text = infoMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                ) {
                    Text("取消")
                }
                TextButton(
                    onClick = { loadFromDb() },
                    enabled = !isBusy,
                ) {
                    Text("載入")
                }
                Button(
                    onClick = { confirmUpsert() },
                    enabled = !isBusy,
                ) {
                    Text("確認")
                }
            }
        },
    )
}

@Composable
private fun OhlcvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * Stable decimal text for form fields (avoids long binary doubles / scientific notation
 * that break re-parse or OHLC comparisons after SQLite REAL round-trip).
 */
private fun formatFieldNumber(value: Double, decimals: Int = 6): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    val asLong = rounded.toLong()
    if (abs(rounded - asLong) < 1e-9) {
        return asLong.toString()
    }
    // Trim trailing zeros without locale issues
    val raw = rounded.toString()
    return if (raw.contains('E') || raw.contains('e')) {
        // Fallback for extreme magnitudes: fixed plain-ish form
        buildString {
            val s = ((rounded * factor).toLong() / factor).toString()
            append(s)
        }.let { s ->
            if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
        }
    } else if (raw.contains('.')) {
        raw.trimEnd('0').trimEnd('.')
    } else {
        raw
    }
}
