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
-- 實際檔案：OHLC 邏輯校驗在 domain/OhlcvValidator；SQLite 側以 NOT NULL 為主

CREATE TABLE daily_ohlcv (
    date   TEXT    NOT NULL PRIMARY KEY,  -- ISO-8601: YYYY-MM-DD
    open   REAL    NOT NULL,
    high   REAL    NOT NULL,
    low    REAL    NOT NULL,
    close  REAL    NOT NULL,
    volume INTEGER NOT NULL
);

CREATE INDEX idx_daily_ohlcv_date ON daily_ohlcv(date);

-- Queries
selectAll:
SELECT * FROM daily_ohlcv ORDER BY date ASC;

selectByDateRange:
SELECT * FROM daily_ohlcv
WHERE date >= :startDate AND date <= :endDate
ORDER BY date ASC;

-- View 彈窗 / 最近 N 筆：日期由近到遠
selectRecent:
SELECT * FROM daily_ohlcv
ORDER BY date DESC
LIMIT :limit;

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
│   ├── StockViewer.kt                  # ✅ 主殼層：Scaffold + AppToolbar + MainContent
│   │
│   ├── domain/                         # ✅ 領域層（純 Kotlin，無 Compose / DB 依賴）
│   │   ├── model/
│   │   │   └── DailyOhlcv.kt
│   │   ├── validation/
│   │   │   └── OhlcvValidator.kt
│   │   └── repository/
│   │       └── OhlcvRepository.kt      # observeAll / getRecent / upsert / delete
│   │
│   ├── data/                           # ✅ 資料層
│   │   ├── mapper/
│   │   │   └── OhlcvMapper.kt          # Daily_ohlcv ↔ DailyOhlcv
│   │   └── repository/
│   │       └── OhlcvRepositoryImpl.kt  # SQLDelight 實作
│   │
│   ├── presentation/                   # 展示層
│   │   ├── toolbar/
│   │   │   └── AppToolbar.kt           # ✅ Database / K Chart 選單
│   │   ├── form/
│   │   │   └── OhlcvInputForm.kt       # ✅ OhlcvInputDialog（Database → Input）
│   │   ├── list/
│   │   │   └── OhlcvDataTable.kt       # ✅ OhlcvDataTableDialog（Database → View）
│   │   ├── chart/
│   │   │   ├── CandlestickChart.kt     # ✅ K 線主畫面（K Chart）
│   │   │   └── ChartLayout.kt          # ✅ 座標、網格、紅漲綠跌、格式化
│   │   ├── OhlcvUiState.kt             # （可選擴充）畫面狀態集中管理
│   │   └── OhlcvViewModel.kt           # （可選擴充）目前以 Composable + Repository 為主
│   │
│   ├── platform/                       # ✅ expect：DB Driver
│   │   └── DatabaseDriverFactory.kt
│   ├── network/                        # ✅ expect：Ktor HttpClient
│   │   └── HttpClientFactory.kt
│   └── di/
│       └── AppContainer.kt             # ✅ Repository 單例（簡易 locator）
│
├── commonMain/sqldelight/              # ✅ SQLDelight schema
│   └── com/neojou/stockviewer/database/
│       └── DailyOhlcv.sq
│
├── desktopMain/kotlin/com/neojou/stockviewer/
│   ├── Main.kt
│   ├── platform/
│   │   └── DatabaseDriverFactory.kt    # ✅ JdbcSqliteDriver → ~/.stockviewer/spacex.db
│   └── network/
│       └── HttpClientFactory.desktop.kt  # ✅ CIO engine
│
└── wasmJsMain/kotlin/com/neojou/stockviewer/
    ├── platform/
    │   └── DatabaseDriverFactory.kt    # ✅ Phase 1 stub（error）；Phase 2 WebWorker/sql.js
    └── network/
        └── HttpClientFactory.wasmJs.kt # ✅ Js engine
```

### 模組職責

| 模組 | 職責 | 依賴方向 |
|------|------|----------|
| **domain** | 實體、校驗規則、Repository 介面 | 無外部依賴 |
| **data** | SQLDelight 存取、Mapper、Repository 實作 | → domain |
| **presentation** | Compose UI、圖表渲染、對話框 | → domain（**禁止**直接用 SQLDelight） |
| **di** | `AppContainer` 組裝 Repository | → data / platform |
| **platform** | DB Driver、檔案路徑等平台細節 | → data 使用端 |

### 資料流

```
Database → Input（OhlcvInputDialog）
    ↓
OhlcvValidator.parseAndValidate()
    ↓
OhlcvRepository.upsert()
    ↓
SQLDelight insertOrReplace → SQLite (~/.stockviewer/spacex.db)
    ↓
observeAll() Flow 通知
    ↓
CandlestickChart 自動重繪（若主畫面在 K Chart）

Database → View（OhlcvDataTableDialog）
    ↓
OhlcvRepository.getRecent(100)
    ↓
selectRecent ORDER BY date DESC LIMIT 100
```

### 頂部導覽 Toolbar（已實作並接線）

主殼層 `StockViewer` 使用 Material3 `Scaffold`，`topBar` 為 `AppToolbar`。

**選單結構（現況）：**

```
[ Database ]  [ K Chart ]
     ├─ Input      → OhlcvInputDialog（popup 單筆輸入 + 確認寫入 DB）
     └─ View       → OhlcvDataTableDialog（popup 可捲動表格，最近 100 筆）
  K Chart          → 主內容區切換為 CandlestickChart（最近 30 日 K 線）
```

| 元件 | 路徑 | 狀態 |
|------|------|------|
| `AppToolbar` | `presentation/toolbar/AppToolbar.kt` | ✅ |
| `DatabaseSubMenu` | `Input` / `View` | ✅ |
| `MainContent` | `Home` / `KChart`（`StockViewer` 內） | ✅ |
| Input 彈窗 | `presentation/form/OhlcvInputForm.kt` | ✅ |
| View 彈窗 | `presentation/list/OhlcvDataTable.kt` | ✅ |
| K 線圖 | `presentation/chart/CandlestickChart.kt` | ✅ |

**實作要點：**

- 導覽只經 `AppToolbar` callback → `StockViewer` 改 state；toolbar **不**直接呼叫 Repository
- 資料庫未就緒時以 Snackbar 提示（Wasm Phase 1 driver stub 會失敗）
- Input / View 為 **Dialog**；K Chart 為 **主內容區替換**（取代預設「Stock Viewer」文字）

---

## 三大功能對應

### 1. 資料庫系統（✅ 已實作）

- 使用 **SQLDelight** 定義 schema 與型別安全查詢。
- Desktop：`JdbcSqliteDriver`，資料庫路徑 `{userHome}/.stockviewer/spacex.db`。
- Repository：
  - `observeAll()` / `observeRange()`：`Flow`（SQLDelight coroutines `asFlow`）
  - `getRecent(limit)`：一次性查詢，`ORDER BY date DESC LIMIT n`
  - `upsert` / `delete`：`suspend` + `Result`
- `AppContainer.ohlcvRepository()` 懶加載單例；UI 只依賴 `OhlcvRepository` 介面。

**Gradle 依賴（已加入 `libs.versions.toml` / `composeApp/build.gradle.kts`）：**

```toml
sqldelight = "2.3.2"
ktor = "3.3.0"

# SQLDelight
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-web-worker-driver = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }

# Ktor（common 放 core + plugins；平台 engine 放 desktopMain / wasmJsMain）
ktor-client-core / content-negotiation / serialization-json / logging
ktor-client-cio   # Desktop
ktor-client-js    # WasmJS
```

**SQLDelight 資料庫名稱**：`StockViewerDatabase`（package：`com.neojou.stockviewer.database`）

**平台骨架：**

| 元件 | 路徑 | 狀態 |
|------|------|------|
| Schema | `commonMain/sqldelight/.../DailyOhlcv.sq` | ✅ |
| Driver factory | `platform/DatabaseDriverFactory`（expect/actual） | ✅ |
| Ktor factory | `network/HttpClientFactory`（expect/actual） | ✅ |
| AppContainer | `di/AppContainer.kt` | ✅ |
| Repository | `domain` + `data` 實作 | ✅ |

### 2. K 線欄位輸入操作介面（✅ Database → Input）

`OhlcvInputDialog`（`presentation/form/OhlcvInputForm.kt`）為 **popup**：

| UI 欄位 | 型別 | 驗證（`OhlcvValidator`） |
|---------|------|--------------------------|
| 日期 | TextField `YYYY-MM-DD` | 可 parse 的 `LocalDate` |
| 開盤價 | Decimal TextField | > 0 |
| 最高價 | Decimal TextField | ≥ 開/收/低 |
| 最低價 | Decimal TextField | ≤ 開/收/高 |
| 收盤價 | Decimal TextField | > 0 |
| 成交量 | Integer TextField | ≥ 0 |

| 按鈕 | 行為 |
|------|------|
| **確認** | 驗證 → `repository.upsert` → 成功關閉 + Snackbar「已儲存 {date}」；同日期 **覆寫** |
| **取消** | 不寫入、關閉 |

- 錯誤訊息顯示於 dialog 內（驗證失敗或 DB 寫入失敗）。
- 寫入中顯示 loading，避免重複提交。

### 3. 資料表檢視（✅ Database → View）

`OhlcvDataTableDialog`（`presentation/list/OhlcvDataTable.kt`）：

| 規則 | 說明 |
|------|------|
| 呈現 | Popup + 垂直/水平可捲動表格 |
| 筆數 | 最多 **100** 筆（`getRecent(100)`） |
| 排序 | **日期由近到遠**（`date DESC`，最近日期在第一資料列） |
| 表頭 | 日期、開盤、最高、最低、收盤、成交量 |
| 空資料 | 提示先用 Database → Input 新增 |
| 關閉 | 底部「關閉」 |

### 4. 日 K 線圖顯示（✅ K Chart）

參考視覺：`docs/k_chart.jpg`。主內容區以 `CandlestickChart` 取代預設「Stock Viewer」文字。

| 規則 | 說明 |
|------|------|
| 筆數 | 最近 **30** 天（`observeAll` → `sortedBy date` → `takeLast(30)`） |
| 橫軸方向 | **左舊右新**；最右 = 最接近今天 |
| 預設選取 | **最右側**那一根 |
| 點擊 | 點價量圖上某日 → 上方 header 改顯示該日 OHLCV；十字線高亮 |
| 上方 header | 日期、量、開、高、低、收 |
| 中間 | Canvas 蠟燭：實體（open–close）+ 影線（high–low） |
| 下方 | 成交量柱（與當日漲跌同色） |
| 配色 | **紅漲綠跌**（`close >= open` → 紅；否則綠；東亞慣例，對齊參考圖） |
| 資料刷新 | 訂閱 `observeAll()`；Input 儲存後圖表自動更新 |
| 空資料 | Empty State 提示先 Input |

**檔案：**

- `presentation/chart/ChartLayout.kt`：pane 幾何、Y 軸 ticks、`formatPrice` / `formatVolume`、顏色常數
- `presentation/chart/CandlestickChart.kt`：header + Canvas 繪製 + `pointerInput` 點選

**Phase 2（尚未做）：** 水平捲動 / 縮放、更多技術指標。

---

## 領域模型（現況）

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
    suspend fun getRecent(limit: Int = 100): Result<List<DailyOhlcv>>
    suspend fun upsert(entry: DailyOhlcv): Result<Unit>
    suspend fun delete(date: LocalDate): Result<Unit>
}
```

---

## 實作順序與進度

| 階段 | 內容 | 產出 | 狀態 |
|------|------|------|------|
| **P0a** | SQLDelight schema + Desktop/Wasm Driver + Ktor HttpClient | 可編譯的資料/網路骨架 | ✅ |
| **P0b** | AppToolbar 頂部下拉選單 | 主殼層導覽 UI | ✅ |
| **P0** | Repository + Mapper + AppContainer + Validator | 可 CRUD 的資料層 | ✅ |
| **P1** | `OhlcvInputDialog`；接 `Database > Input` | 單筆輸入並持久化 | ✅ |
| **P2** | `CandlestickChart`；接 `K Chart` | 最近 30 日 K 線 + 點選 | ✅ |
| **P3a** | `OhlcvDataTableDialog`；接 `Database > View` | 最近 100 筆表格檢視 | ✅ |
| **P3b** | 表格內刪除/編輯、欄位級 UX 強化 | 完整資料管理 | ⏳ 待做 |
| **P4** | CSV 匯入/匯出（可選） | 批次資料操作 | ⏳ 待做 |
| **P5** | Wasm 平台適配（WebWorker/sql.js 等） | Web 版上線 | ⏳ 待做 |

---

## 編碼慣例

- **套件根目錄**：`com.neojou.stockviewer`
- **日誌**：沿用 `com.neojou.tools.MyLog`，各模組獨立 TAG（`OhlcvRepo`、`CandleChart`、`OhlcvInputForm`、`OhlcvDataTable`、`AppToolbar`）
- **初始化**：`AppContainer` 懶加載 DB/Repository，idempotent；`SystemSettings.initOnce()` 負責 log 等全域設定
- **日期**：統一 `kotlinx.datetime.LocalDate`，禁止 `java.time`（commonMain）
- **金額**：`Double`；K 線圖場景足夠
- **Coroutine**：dialog / shell 用 `rememberCoroutineScope`；Repository 寫入在 `Dispatchers.Default`/`IO`
- **Compose 副作用**：資料載入用 `LaunchedEffect` + `Flow.collect` 或一次性 `suspend`
- **Compose 參數命名**：避免參數名 `modifier` 遮蔽 `Modifier` 物件（圖表元件使用 `chartModifier` / `canvasModifier`）
- **分層**：UI **不得** import `com.neojou.stockviewer.database.*`（SQLDelight 生成碼）

---

## 測試策略

| 層級 | 測試對象 | 方式 |
|------|----------|------|
| Unit | `OhlcvValidator` | 純 Kotlin 測試，覆蓋邊界值 |
| Integration | `OhlcvRepositoryImpl` | SQLDelight in-memory driver |
| UI | `OhlcvInputDialog` / 表格 / 圖表 | Compose UI Test（Desktop） |

---

## Agent 工作指引

1. **修改前先讀本文件**，確認變更符合分層邊界（UI 不直接存取 SQLDelight）。
2. **新增功能時**先擴充 domain 介面，再實作 data，最後接 presentation。
3. **不要**在 `commonMain` 引入 `java.*` 或平台專屬 API；平台差異放 `desktopMain` / `wasmJsMain`。
4. **圖表變更**保持 Canvas 與領域模型解耦；資料來自 `List<DailyOhlcv>` / Repository Flow。
5. **導覽變更**經由 `AppToolbar` callback 與 `StockViewer` 狀態切換；不要在 toolbar 內直接呼叫 Repository。
6. **K 線配色**維持紅漲綠跌（與 `docs/k_chart.jpg` 一致），除非產品明確要求改西式綠漲紅跌。
7. **提交前**確認 Desktop 可編譯執行（`./gradlew :composeApp:run`）。
8. 用戶要求「規劃」時更新本文件；要求「實作」時推進待辦階段；**已驗證可行的變更應同步回寫本文件**。

---

## 相關文件

- 參考 K 線視覺：`docs/k_chart.jpg`
- [Ktor + SQLDelight KMP 教學](https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html)
- [SQLDelight KMP 文件](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)
