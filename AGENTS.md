# StockViewer — Agent 開發指南

> 給 AI Agent 與開發者的**實作契約與作業指引**。  
> **系統架構 SSOT** 不在本文件，請先讀架構文件。

## 文件地圖

| 文件 | 用途 |
|------|------|
| **[ARCHITECTURE.md](./ARCHITECTURE.md)** | 系統架構、分層、資料流、平台策略、演進缺口 |
| **[docs/adr/](./docs/adr/)** | 架構決策紀錄（為何如此設計） |
| **[docs/modules/boundaries.md](./docs/modules/boundaries.md)** | 套件依賴矩陣與硬性邊界 |
| **本文件 (AGENTS.md)** | 功能行為契約、進度、編碼慣例、Agent 工作流程 |
| **docs/k_chart.jpg** | K 線 UI 視覺參考 |
| **docs/ai/** | AI 角色提示（非 runtime 架構） |

修改**架構／分層／平台策略** → 更新 `ARCHITECTURE.md` + 必要時新增 ADR。  
修改**功能行為／實作進度** → 更新本文件。

---

## 專案背景（摘要）

- **目標**：Desktop 為主的 SpaceX 相關日 K（OHLCV）儲存、手動輸入、表格與 K 線檢視。
- **技術棧**：KMP + Compose Multiplatform；目標 **Desktop (JVM)** + **WasmJS（次要）**。
- **資料前提**：無公開交易所代碼；以手動輸入／匯入為主。外部行情若接入，**只經 Repository**，不耦合 UI。
- **平台策略**：Desktop-primary / Wasm-secondary（Wasm Phase 1 DB stub）。詳見 [ARCHITECTURE.md §1–2](./ARCHITECTURE.md) 與 [ADR 0005](./docs/adr/0005-desktop-primary-wasm-secondary.md)。

---

## 架構速覽（細節見 ARCHITECTURE.md）

採 **單一 `:composeApp` 模組** + **package 分層** + **Repository**：

```
presentation → domain ← data
                 ↑
            di + platform
```

| 規則 | 說明 |
|------|------|
| UI 禁止 SQLDelight | 不得 `import com.neojou.stockviewer.database.*` |
| domain 純 Kotlin | 無 Compose / SQLDelight / `java.*` |
| Toolbar 只 callback | 不呼叫 Repository |
| 驗證在 domain | `OhlcvValidator`；持久化在 `OhlcvRepository` |

完整元件圖、資料流、已知缺口：**[ARCHITECTURE.md](./ARCHITECTURE.md)**。  
依賴矩陣：**[docs/modules/boundaries.md](./docs/modules/boundaries.md)**。

### 套件樹（實作現況）

```
composeApp/src/commonMain/kotlin/com/neojou/stockviewer/
├── App.kt, StockViewer.kt
├── domain/ model | validation | repository
├── data/ mapper | repository
├── presentation/ toolbar | form | list | chart
├── di/AppContainer.kt
├── platform/DatabaseDriverFactory.kt    # expect
└── network/HttpClientFactory.kt         # expect（功能尚未使用）
+ commonMain/sqldelight/.../DailyOhlcv.sq
+ desktopMain / wasmJsMain actuals
```

---

## 資料契約

### Schema（SQLDelight）

路徑：`composeApp/src/commonMain/sqldelight/com/neojou/stockviewer/database/DailyOhlcv.sq`  
資料庫：`StockViewerDatabase`；Desktop 檔案：`~/.stockviewer/spacex.db`  
（經 `com.neojou.tools.database.MyDb` + `MyDbConfig(appName="stockviewer", databaseFileName="spacex.db")` 開啟）。

| 欄位 | 型別 | 說明 |
|------|------|------|
| `date` | TEXT PK | `LocalDate` → `YYYY-MM-DD` |
| `open/high/low/close` | REAL | 價格 |
| `volume` | INTEGER | 成交量 |

查詢：`selectAll`、`selectByDateRange`、`selectRecent`（`date DESC LIMIT`）、`insertOrReplace`、`deleteByDate`。  
OHLC 邏輯校驗以 **domain validator** 為主。

### Domain API

```kotlin
data class DailyOhlcv(
    val date: LocalDate,
    val open: Double, val high: Double, val low: Double, val close: Double,
    val volume: Long,
)

interface OhlcvRepository {
    fun observeAll(): Flow<List<DailyOhlcv>>
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyOhlcv>>
    suspend fun getRecent(limit: Int = 100): Result<List<DailyOhlcv>>
    suspend fun upsert(entry: DailyOhlcv): Result<Unit>
    suspend fun delete(date: LocalDate): Result<Unit>
}
```

同日期 **upsert 覆寫**。取得實例：`AppContainer.ohlcvRepository(): Result`。

---

## 功能行為契約

### 導覽

```
[ Database ]  [ K Chart ]
     ├─ Input  → OhlcvInputDialog（popup）
     └─ View   → OhlcvDataTableDialog（popup）
  K Chart      → MainContent.KChart（主區替換）
```

殼層：`StockViewer`（Scaffold + 狀態）。頂部列使用通用 **`com.neojou.tools.ui.menu.MyTopMenuBar`**，由殼層組裝 `List<MyTopMenuItem>`（產品選單定義不在 tools 內）。詳見 ARCHITECTURE 導覽節。

### 1. Database → Input（✅）

`presentation/form/OhlcvInputForm.kt` → `OhlcvInputDialog`

| 欄位 | 驗證 |
|------|------|
| 日期 `YYYY-MM-DD` | 可 parse |
| 開/高/低/收 | > 0；high≥low；high≥open/close；low≤open/close |
| 量 | ≥ 0 整數 |

- **確認**：validate → `upsert` → 成功關閉 + Snackbar  
- **取消**：不寫入  

### 2. Database → View（✅）

`presentation/list/OhlcvDataTableDialog`（**主路徑**；勿與未使用的 `OhlcvDataViewDialog` 混淆）

| 規則 | 值 |
|------|-----|
| 筆數 | 最多 100（`getRecent`） |
| 排序 | `date DESC` |
| 欄位 | 日期、開、高、低、收、量（六欄皆可見） |
| 欄寬 | `Row` + `weight`，**禁止**固定寬裁切右側欄 |

### 3. K Chart（✅）

檔案：`CandlestickChart.kt` + `ChartLayout.kt`。

| 規則 | 值 |
|------|-----|
| 筆數 | 最近 **30** 日 |
| 方向 | 左舊右新；預設選最右 |
| 配色 | **紅漲綠跌**（`close >= open` → 紅） |
| Header | 2×3 格線：日期/開/低；量/高/收；開↔高、低↔收對齊 |
| 刻度上界 | 第一個 **> max(high)** 的 nice tick |
| 刻度下界 | 第一個 **< min(low)** 的 nice tick |
| 版面 | 價格 plot + **分隔帶** + 成交量 caption/plot（刻度不可與量區重疊） |
| 十字線 | 垂直（價+量 slot）+ 水平（**收盤價**，拉滿寬） |
| 資料 | **現況** Chart 內部 `observeAll()`；**演進方向** List 注入（見 ARCHITECTURE G1，勿擅自大重構除非任務要求） |

---

## 實作進度

| 階段 | 內容 | 狀態 |
|------|------|------|
| P0a | SQLDelight + drivers + Ktor scaffold | ✅ |
| P0b | 頂部選單（現為可配置 `MyTopMenuBar`） | ✅ |
| P0 | Repository + Mapper + AppContainer + Validator | ✅ |
| P0-db | `tools.database` MyDb / MyCrudTable + DailyOhlcvTable 委派 | ✅ |
| P1-repo | `MyCrudRepository` 基類；`OhlcvRepositoryImpl` 只補時序查詢 | ✅ |
| P1 | Input dialog | ✅ |
| P2 / P2b | K Chart + UX（header/刻度/分隔/十字線） | ✅ |
| P3a / fix | View 表 + 六欄 weight | ✅ |
| P3b | 表內刪除/編輯 | ⏳ |
| P4 | CSV 匯入匯出 | ⏳ |
| P5 | Wasm 持久化對等 | ⏳ |
| Docs | ARCHITECTURE + ADR + boundaries | ✅ |

---

## 編碼慣例

- 套件根：`com.neojou.stockviewer`
- 日誌：`MyLog` + 獨立 TAG
- 日期：`kotlinx.datetime.LocalDate` only（commonMain）
- 金額：`Double`
- Coroutine：shell/dialog `rememberCoroutineScope`；repo 背景 dispatcher
- Compose：副作用用 `LaunchedEffect`；避免參數名 `modifier` 遮蔽 `Modifier`（用 `chartModifier` 等）
- **分層硬規則**：見 [boundaries.md](./docs/modules/boundaries.md)

---

## 測試策略（目標）

| 層級 | 對象 | 方式 |
|------|------|------|
| Unit | `OhlcvValidator` | 純 Kotlin |
| Integration | `OhlcvRepositoryImpl` | in-memory driver |
| UI | Input / View / Chart | Compose UI Test Desktop |

（覆蓋率現況仍薄；新邏輯應補測。）

---

## Agent 工作指引

1. **結構／分層／平台** → 先讀 `ARCHITECTURE.md` + 相關 ADR + `boundaries.md`。  
2. **功能實作** → 先 domain 介面 → data → presentation。  
3. **禁止** commonMain 的 `java.*`；平台放 `desktopMain` / `wasmJsMain`。  
4. **禁止** presentation 依賴 SQLDelight 生成碼。  
5. **導覽**只經 `MyTopMenuBar` 的 `MyTopMenuItem.onClick`（殼層組裝）+ `StockViewer` state；**不要**把產品選單寫死進 `MyTopMenuBar`。  
6. **K 線**維持紅漲綠跌與 §K Chart 契約，除非產品明確改規。  
7. **Desktop 為準**：`./gradlew :composeApp:compileKotlinDesktop` 或 `:composeApp:run`。  
8. 規劃更新 `ARCHITECTURE`/ADR；行為契約更新本文件；**已驗證變更要回寫對應文件**。  
9. 用戶未要求前，**不要**為架構而拆多 module / 上完整 UseCase 階層（見 ADR 0004）。

---

## 外部參考

- [Ktor + SQLDelight KMP](https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html)
- [SQLDelight multiplatform](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)
