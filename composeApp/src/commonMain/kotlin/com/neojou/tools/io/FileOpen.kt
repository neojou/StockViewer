package com.neojou.tools.io

/**
 * Platform “Open file” helpers for import flows.
 */

/**
 * Shows a native open-file dialog.
 *
 * @param allowedExtensions extensions without dot, e.g. `listOf("csv", "xlsx")`
 * @return Absolute path or null if cancelled / unsupported
 */
expect suspend fun pickOpenFilePath(
    title: String,
    extensionFilterLabel: String,
    allowedExtensions: List<String>,
): String?

/**
 * Reads entire file as UTF-8 text (strips BOM if present is caller's choice).
 */
expect suspend fun readTextFileUtf8(path: String): Result<String>
