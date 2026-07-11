# StockViewer — Agent 開發指南

> SpaceX 股票日 K 線圖專案。本文件供 AI Agent 與開發者理解架構決策、模組邊界與實作順序。

## 專案背景

- **目標**：建立桌面/Web 應用，儲存 SpaceX 相關股票的日 K 線（OHLCV）資料，提供資料輸入介面與 K 線圖顯示。
- **技術棧**：Kotlin Multiplatform (KMP) + Compose Multiplatform，目標平台為 **Desktop (JVM)** 與 **WasmJS**。
- **重要前提**：SpaceX 為未上市公司，無公開交易所代碼。本專案以**手動輸入或匯入二級市場/估值資料**為主，不依赖公開行情 API。若未來接入外部資料源，應透過 Repository 抽象層擴充，不直接耦合 UI。

---

## 資料庫選型建議

### 首選：SQLDelight + SQLite（Desktop）

| 考量 | 說明 |
|------|------|
| KMP 相容 | SQLDelight 為 JetBrains 官方推薦的 KMP 持久化方案，型別安全、以 `.sq` 檔定義 schema |
| 資料特性 | 日 K 線為結構化時序資料，SQLite 的 B-Tree 索引與 `ORDER BY date` 查詢效能充足 |
| 部署 | Desktop 使用本機 SQLite 檔案（如 `~/.stockviewer/spacex.db`），零外部依賴 |
| 測試 | 可用 in-memory driver 做 Repository 單元測試 |

### WasmJS 平台策略（第二階段）

Wasm 目標暫不優先實作資料庫持久化。建議：

1. **Phase 1**：僅 Desktop 啟用 SQLDelight；Wasm 使用 in-memory Repository 或 JSON 匯入。
2. **Phase 2**（可選）：Wasm 接入 [sql.js](https://sql.js.org/) 或改以 REST 後端 + PostgreSQL 同步。

### 不建議的方案（本專案情境）

| 方案 | 原因 |
|------|------|
| PostgreSQL / MySQL | 單機桌面 App 需額外安裝服務，過度工程 |
| Room | Android 導向，KMP 支援有限 |
| 純 JSON 檔 | 可行但缺乏索引、並發寫入與查詢能力，僅適合原型 |
| Realm | KMP 支援與 Compose 生態整合不如 SQLDelight 成熟 |

---

## 資料表 Schema

```sql
-- composeApp/src/commonMain/sqldelight/com/neojou/stockviewer/database/DailyOhlcv.sq

CREATE TABLE daily_ohlcv (
    date   TEXT    NOT NULL PRIMARY KEY,  -- ISO-8601: YYYY-MM-DD
    open   REAL    NOT NULL CHECK (open  >= 0),
    high   REAL    NOT NULL CHECK (high  >= 0),
    low    REAL    NOT NULL CHECK (low   >= 0),
    close  REAL    NOT NULL CHECK (close >= 0),
    volume INTEGER NOT NULL CHECK (volume >= 0),
    CHECK (high >= low),
    CHECK (high >= open AND high >= close),
    CHECK (low  <= open AND low  <= close)
);

CREATE INDEX idx_daily_ohlcv_date ON daily_ohlcv(date);

-- Queries
selectAll:
SELECT * FROM daily_ohlcv ORDER BY date ASC;

selectByDateRange:
SELECT * FROM daily_ohlcv
WHERE date >= :startDate AND date <= :endDate
ORDER BY date ASC;

insertOrReplace:
INSERT OR REPLACE INTO daily_ohlcv(date, open, high, low, close, volume)
VALUES (:date, :open, :high, :low, :close, :volume);

deleteByDate:
DELETE FROM daily_ohlcv WHERE date = :date;
```

### 欄位對照

| 欄位 | 型別 | 說明 |
|------|------|------|
| `date` | `TEXT` (PK) | 交易日，使用 `kotlinx.datetime.LocalDate`，序列化為 `YYYY-MM-DD` |
| `open` | `REAL` | 開盤價 |
| `high` | `REAL` | 最高價 |
| `low` | `REAL` | 最低價 |
| `close` | `REAL` | 收盤價 |
| `volume` | `INTEGER` | 成交量 |

---

## 程式架構

採 **分層架構 + Repository 模式**，所有平台共用 `commonMain` 業務邏輯，平台差異以 `expect/actual` 或 SQLDelight Driver 注入隔離。

```
composeApp/src/
├── commonMain/kotlin/com/neojou/stockviewer/
│   ├── App.kt                          # 根 Composable，負責初始化
│   ├── StockViewer.kt                  # → 重構為導向 StockViewerScreen
│   │
│   ├── domain/                         # 領域層（純 Kotlin，無 Compose / DB 依賴）
│   │   ├── model/
│   │   │   └── DailyOhlcv.kt           # 領域實體
│   │   ├── validation/
│   │   │   └── OhlcvValidator.kt       # OHLC 邏輯校驗（high >= low 等）
│   │   └── repository/
│   │       └── OhlcvRepository.kt      # 介面：CRUD + 區間查詢
│   │
│   ├── data/                           # 資料層
│   │   ├── mapper/
│   │   │   └── OhlcvMapper.kt          # DB Entity ↔ Domain Model 轉換
│   │   └── repository/
│   │       └── OhlcvRepositoryImpl.kt  # SQLDelight 實作
│   │
│   ├── presentation/                   # 展示層
│   │   ├── OhlcvUiState.kt             # 畫面狀態（列表、表單、圖表範圍、錯誤）
│   │   ├── OhlcvViewModel.kt           # 狀態管理 + 呼叫 Repository
│   │   ├── screen/
│   │   │   └── StockViewerScreen.kt      # 主畫面：表單 + 圖表 + 資料列表
│   │   ├── form/
│   │   │   └── OhlcvInputForm.kt       # 新增/編輯 OHLCV 欄位表單
│   │   ├── chart/
│   │   │   ├── CandlestickChart.kt     # Canvas 繪製日 K 線
│   │   │   └── ChartLayout.kt          # 價格軸、日期軸、網格
│   │   └── list/
│   │       └── OhlcvDataTable.kt       # 可選：表格檢視/刪除操作
│   │
│   └── di/                             # 依賴組裝（簡易 factory，不引入 DI 框架）
│       └── AppContainer.kt
│
├── commonMain/sqldelight/              # SQLDelight schema（見上方）
│   └── com/neojou/stockviewer/database/
│       └── DailyOhlcv.sq
│
├── desktopMain/kotlin/com/neojou/stockviewer/
│   ├── Main.kt
│   └── platform/
│       └── DatabaseDriverFactory.kt    # actual: JdbcSqliteDriver → 本機 .db 檔
│
└── wasmJsMain/kotlin/com/neojou/stockviewer/
    └── platform/
        └── DatabaseDriverFactory.kt    # actual: InMemory 或 Phase 2 sql.js
```

### 模組職責

| 模組 | 職責 | 依賴方向 |
|------|------|----------|
| **domain** | 實體、校驗規則、Repository 介面 | 無外部依賴 |
| **data** | SQLDelight 存取、Mapper、Repository 實作 | → domain |
| **presentation** | Compose UI、ViewModel、圖表渲染 | → domain |
| **platform** | DB Driver、檔案路徑等平台細節 | → data |

### 資料流

```
使用者輸入 (OhlcvInputForm)
    ↓
OhlcvViewModel.save()
    ↓
OhlcvValidator.validate()
    ↓
OhlcvRepository.insertOrReplace()
    ↓
SQLDelight → SQLite
    ↓
Flow<List<DailyOhlcv>> 回傳
    ↓
OhlcvUiState 更新 → CandlestickChart 重繪
```

---

## 三大功能對應

### 1. 資料庫系統

- 使用 **SQLDelight** 定義 schema 與型別安全查詢。
- Desktop：`JdbcSqliteDriver`，資料庫路徑 `{userHome}/.stockviewer/spacex.db`。
- Repository 以 `Flow` 暴露資料變更，UI 自動刷新。
- 在 `AppContainer` 初始化時建立 Database 與 Repository 單例。

**Gradle 依賴（待加入 `libs.versions.toml`）：**

```toml
sqldelight = "2.0.2"

sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
```

### 2. K 線欄位輸入操作介面

`OhlcvInputForm` 提供以下欄位：

| UI 欄位 | 型別 | 驗證 |
|---------|------|------|
| 日期 | DatePicker / TextField | 不可重複（或提示覆寫） |
| 開盤價 | Decimal TextField | > 0 |
| 最高價 | Decimal TextField | ≥ 開/收/低 |
| 最低價 | Decimal TextField | ≤ 開/收/高 |
| 收盤價 | Decimal TextField | > 0 |
| 成交量 | Integer TextField | ≥ 0 |

操作：**新增**、**更新**（同日期 upsert）、**刪除**、**清除表單**。

ViewModel 在提交前呼叫 `OhlcvValidator`，錯誤以 `Snackbar` 或欄位級錯誤顯示。

### 3. 日 K 線圖顯示

- 使用 **Compose Canvas** 自繪 K 線（紅漲綠跌或依 Material 配色），避免引入不支援 KMP 的原生圖表庫。
- `CandlestickChart` 接收 `List<DailyOhlcv>` 與可選 `dateRange`。
- 子元件：
  - **CandlestickBody**：實體（open-close）+ 影線（high-low）
  - **ChartLayout**：Y 軸價格刻度、X 軸日期標籤、背景網格
  - **VolumeBar**（可選第二子圖）：成交量柱狀圖
- 支援水平捲動 / 縮放（Phase 2）：`Modifier.pointerInput` + 可見窗口計算。
- 資料不足時顯示 Empty State 提示先輸入資料。

---

## 領域模型範例

```kotlin
// domain/model/DailyOhlcv.kt
data class DailyOhlcv(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)
```

```kotlin
// domain/repository/OhlcvRepository.kt
interface OhlcvRepository {
    fun observeAll(): Flow<List<DailyOhlcv>>
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>>
    suspend fun upsert(entry: DailyOhlcv): Result<Unit>
    suspend fun delete(date: LocalDate): Result<Unit>
}
```

---

## 實作順序（建議）

| 階段 | 內容 | 產出 |
|------|------|------|
| **P0** | SQLDelight schema + Desktop Driver + Repository | 可 CRUD 的資料層 |
| **P1** | OhlcvInputForm + ViewModel + 驗證 | 可輸入並持久化資料 |
| **P2** | CandlestickChart（Canvas） | 可視化日 K 線 |
| **P3** | OhlcvDataTable + 刪除/編輯 | 完整資料管理 |
| **P4** | CSV 匯入/匯出（可選） | 批次資料操作 |
| **P5** | Wasm 平台適配 | Web 版上線 |

---

## 編碼慣例

- **套件根目錄**：`com.neojou.stockviewer`
- **日誌**：沿用 `com.neojou.tools.MyLog`，各模組使用獨立 TAG（如 `OhlcvRepo`、`CandleChart`）
- **初始化**：資料庫建立放在 `SystemSettings.initOnce()` 或 `AppContainer` 中，保持 idempotent
- **日期**：統一使用 `kotlinx.datetime.LocalDate`，禁止混用 `java.time`（保持 commonMain 相容）
- **金額**：以 `Double` 儲存；若需高精度可改 `String` 或定點數，但 K 線圖場景 Double 足夠
- **Coroutine**：Repository 的 suspend 函式在 `ViewModel` 的 `viewModelScope`（或 `rememberCoroutineScope`）中呼叫
- **Compose 副作用**：資料載入使用 `LaunchedEffect` 或 `collectAsStateWithLifecycle` 模式

---

## 測試策略

| 層級 | 測試對象 | 方式 |
|------|----------|------|
| Unit | `OhlcvValidator` | 純 Kotlin 測試，覆蓋邊界值 |
| Integration | `OhlcvRepositoryImpl` | SQLDelight in-memory driver |
| UI | `OhlcvInputForm` | Compose UI Test（Desktop） |

---

## Agent 工作指引

1. **修改前先讀本文件**，確認變更符合分層邊界（UI 不直接存取 SQLDelight）。
2. **新增功能時**先擴充 domain 介面，再實作 data，最後接 presentation。
3. **不要**在 `commonMain` 引入 `java.*` 或平台專屬 API；平台差異放 `desktopMain` / `wasmJsMain`。
4. **圖表變更**保持 Canvas 邏輯與資料模型解耦，`CandlestickChart` 只接收 `List<DailyOhlcv>`。
5. **提交前**確認 Desktop 可編譯執行（`./gradlew :composeApp:run`）。
6. 用戶要求「規劃」時更新本文件；要求「實作」時按 P0→P5 順序推進。

---

## 相關文件

- [SQLDelight KMP 文件](https://cashapp.github.io/sqldelight/2.0.2/multiplatform_sqlite/)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)
