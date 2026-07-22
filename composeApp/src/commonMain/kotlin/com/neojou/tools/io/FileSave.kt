package com.neojou.tools.io

/**
 * Platform file save helpers for desktop/web export flows.
 *
 * - Desktop: native save dialog + write path
 * - Wasm: may be unsupported (returns failure / null path)
 */

/**
 * Shows a native “Save as” dialog.
 *
 * @param title Dialog title
 * @param suggestedFileName Default file name (e.g. `ohlcv_export.csv`)
 * @param extensionFilterLabel Human label for the filter (e.g. `CSV files`)
 * @param extension Extension without dot (e.g. `csv`)
 * @return Absolute path chosen by the user, or null if cancelled / unsupported
 */
expect suspend fun pickSaveFilePath(
    title: String,
    suggestedFileName: String,
    extensionFilterLabel: String,
    extension: String,
): String?

/**
 * Writes [content] as UTF-8 text to [path], creating/overwriting the file.
 */
expect suspend fun writeTextFileUtf8(
    path: String,
    content: String,
): Result<Unit>
