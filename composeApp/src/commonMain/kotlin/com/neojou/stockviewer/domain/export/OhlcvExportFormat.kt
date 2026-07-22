package com.neojou.stockviewer.domain.export

/**
 * Supported OHLCV export formats.
 *
 * - [Csv]: UTF-8 CSV (all platforms that can save files; Excel can open)
 * - [Xlsx]: Office Open XML spreadsheet (Desktop JVM via Apache POI only)
 */
enum class OhlcvExportFormat {
    Csv,
    Xlsx,
    ;

    val extension: String
        get() = when (this) {
            Csv -> "csv"
            Xlsx -> "xlsx"
        }

    /** Short label for UI radios / buttons. */
    val displayLabel: String
        get() = when (this) {
            Csv -> "CSV (*.csv) — 通用，Excel 可開"
            Xlsx -> "Excel (*.xlsx) — Desktop"
        }

    val fileFilterLabel: String
        get() = when (this) {
            Csv -> "CSV 檔案"
            Xlsx -> "Excel 活頁簿"
        }

    fun suggestedFileName(): String = "ohlcv_export.$extension"
}
