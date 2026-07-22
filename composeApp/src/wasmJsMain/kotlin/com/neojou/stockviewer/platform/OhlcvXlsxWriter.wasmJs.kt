package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.model.DailyOhlcv

actual suspend fun writeOhlcvXlsxFile(
    path: String,
    rows: List<DailyOhlcv>,
): Result<Unit> = Result.failure(
    UnsupportedOperationException(
        "Excel (.xlsx) export is only available on the Desktop app.",
    ),
)
