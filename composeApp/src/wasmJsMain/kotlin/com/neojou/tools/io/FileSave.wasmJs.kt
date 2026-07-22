package com.neojou.tools.io

actual suspend fun pickSaveFilePath(
    title: String,
    suggestedFileName: String,
    extensionFilterLabel: String,
    extension: String,
): String? = null

actual suspend fun writeTextFileUtf8(
    path: String,
    content: String,
): Result<Unit> = Result.failure(
    UnsupportedOperationException(
        "File export is not supported on WasmJS yet. Use the Desktop app.",
    ),
)
