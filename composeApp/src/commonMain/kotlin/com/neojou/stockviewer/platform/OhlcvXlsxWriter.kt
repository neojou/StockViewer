package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.model.DailyOhlcv

/**
 * Writes OHLCV rows to a native Excel .xlsx file.
 *
 * - Desktop (JVM): Apache POI
 * - WasmJS: not supported (returns failure)
 */
expect suspend fun writeOhlcvXlsxFile(
    path: String,
    rows: List<DailyOhlcv>,
): Result<Unit>
