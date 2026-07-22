package com.neojou.tools.io

actual suspend fun pickOpenFilePath(
    title: String,
    extensionFilterLabel: String,
    allowedExtensions: List<String>,
): String? = null

actual suspend fun readTextFileUtf8(path: String): Result<String> =
    Result.failure(
        UnsupportedOperationException("File import is not supported on WasmJS yet. Use the Desktop app."),
    )
