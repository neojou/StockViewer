package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.import.OhlcvParseResult

actual suspend fun readOhlcvFromXlsx(path: String): Result<OhlcvParseResult> =
    Result.failure(
        UnsupportedOperationException("Excel (.xlsx) import is only available on the Desktop app."),
    )
