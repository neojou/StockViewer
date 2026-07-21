package com.neojou.stockviewer.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private const val TAG = "OhlcvInputForm"

/**
 * Popup dialog for entering a single daily OHLCV row.
 *
 * - **確認**: validates fields, then [OhlcvRepository.upsert] into SQLite.
 * - **取消**: dismisses without saving.
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
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun submit() {
        if (isSaving) return
        val (entry, validation) = OhlcvValidator.parseAndValidate(
            dateText = dateText,
            openText = openText,
            highText = highText,
            lowText = lowText,
            closeText = closeText,
            volumeText = volumeText,
        )
        if (validation is OhlcvValidationResult.Error || entry == null) {
            errorMessage = (validation as? OhlcvValidationResult.Error)?.message ?: "輸入無效"
            return
        }

        isSaving = true
        errorMessage = null
        scope.launch {
            val result = repository.upsert(entry)
            isSaving = false
            result
                .onSuccess {
                    MyLog.add(TAG, "Saved OHLCV ${entry.date}", LogLevel.INFO)
                    onSaved(entry)
                    onDismiss()
                }
                .onFailure { e ->
                    val msg = e.message ?: "寫入資料庫失敗"
                    MyLog.add(TAG, "Save failed: $msg", LogLevel.ERROR)
                    errorMessage = msg
                }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = { Text("輸入 當日Ｋ線 資料") },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "請輸入單筆日 K 線資料（同日期將覆寫）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OhlcvField(
                    label = "日期 (YYYY-MM-DD)",
                    value = dateText,
                    onValueChange = { dateText = it; errorMessage = null },
                    enabled = !isSaving,
                )
                OhlcvField(
                    label = "開盤價 (Open)",
                    value = openText,
                    onValueChange = { openText = it; errorMessage = null },
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                )
                OhlcvField(
                    label = "最高價 (High)",
                    value = highText,
                    onValueChange = { highText = it; errorMessage = null },
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                )
                OhlcvField(
                    label = "最低價 (Low)",
                    value = lowText,
                    onValueChange = { lowText = it; errorMessage = null },
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                )
                OhlcvField(
                    label = "收盤價 (Close)",
                    value = closeText,
                    onValueChange = { closeText = it; errorMessage = null },
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Decimal,
                )
                OhlcvField(
                    label = "成交量 (Volume)",
                    value = volumeText,
                    onValueChange = { volumeText = it; errorMessage = null },
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Number,
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("確認")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text("取消")
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
