package com.neojou.stockviewer.platform

import com.neojou.stockviewer.domain.import.OhlcvParseResult

/**
 * Reads OHLCV rows from an Excel .xlsx file.
 * Desktop: Apache POI. Wasm: unsupported.
 */
expect suspend fun readOhlcvFromXlsx(path: String): Result<OhlcvParseResult>
