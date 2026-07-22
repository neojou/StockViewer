# Module & package boundaries

StockViewer uses a **single Gradle module** (`:composeApp`) with **logical packages**.  
These rules are **conventions** (not yet compiler-enforced). Review PRs/agent diffs against this matrix.

See also: [`ARCHITECTURE.md`](../../ARCHITECTURE.md), [ADR 0004](../adr/0004-repository-package-layering.md).

---

## Package map

| Logical layer | Package prefix | Source set |
|---------------|----------------|------------|
| Shell | `com.neojou.stockviewer` (`App`, `StockViewer`) | commonMain |
| Domain | `com.neojou.stockviewer.domain.*` | commonMain |
| Data | `com.neojou.stockviewer.data.*` | commonMain |
| Presentation | `com.neojou.stockviewer.presentation.*` | commonMain |
| DI | `com.neojou.stockviewer.di.*` | commonMain |
| Platform DB | `com.neojou.stockviewer.platform.*` | commonMain + desktopMain + wasmJsMain |
| Network | `com.neojou.stockviewer.network.*` | commonMain + desktopMain + wasmJsMain |
| Tools (non-UI) | `com.neojou.tools` (`MyLog`, `SystemSettings`, …) | commonMain |
| Tools UI (shared) | `com.neojou.tools.ui.*` (e.g. `ui.menu`) | commonMain |
| Tools DB (shared) | `com.neojou.tools.database.*` (`MyDb`, `MyCrudTable`, …) | commonMain + platform actuals |
| SQLDelight generated | `com.neojou.stockviewer.database.*` | generated into build |

---

## Allowed dependency matrix

Rows depend on columns. `✅` allowed · `❌` forbidden · `△` limited · `—` N/A

| From \ To | domain | data | presentation | di | platform | network | tools | database (gen) |
|-----------|--------|------|--------------|-----|----------|---------|-------|----------------|
| **domain** | — | ❌ | ❌ | ❌ | ❌ | ❌ | △ log-free preferred | ❌ |
| **data** | ✅ | — | ❌ | ❌ | △ app wiring only | ❌ | ✅ MyLog + **database** (`MyDb`/`MyCrudTable`) | ✅ |
| **presentation** | ✅ | ❌ | — | ❌¹ | ❌ | ❌² | ✅ MyLog; ❌ database.* | ❌ |
| **di** | ✅ | ✅ | ❌ | — | ✅ | △ if wiring clients | ✅ | ❌ direct prefer factory |
| **shell (App/StockViewer)** | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **platform** | ❌ | ❌ | ❌ | ❌ | — | ❌ | △ | ✅ Schema only as needed |
| **network** | ❌ | ❌ | ❌ | ❌ | ❌ | — | △ | ❌ |
| **tools** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — | ❌ |

¹ Presentation may receive interfaces **created by** DI, but should not import `AppContainer` deeply from leaf widgets if avoidable. Today shell uses `AppContainer`; leaf dialogs take `OhlcvRepository` parameters (preferred).

² Network is unused by features; when introduced, presentation talks to domain/application ports—not raw Ktor in every composable.

---

## Hard rules

1. **Presentation → SQLDelight generated package is forbidden.**  
   No `import com.neojou.stockviewer.database...` in `presentation` or shell UI files.  
   Presentation also must **not** import `com.neojou.tools.database` (use domain repositories only).
2. **Domain stays pure Kotlin** for model/validation/repository interfaces.  
   No Compose, no SQLDelight, no `java.*`.
3. **Top menu chrome is app-agnostic in tools.**  
   `com.neojou.tools.ui.menu.MyTopMenuBar` only renders configured items; it must not import `stockviewer` or call repositories.  
   Product menus (labels + `onClick`) are assembled in the app shell (`StockViewer`).
4. **Platform code owns file paths and drivers.**  
   Desktop may use `java.io.File`; commonMain must not.
5. **Features depend on `OhlcvRepository`, not `OhlcvRepositoryImpl`.**  
   Only `di` (or tests) constructs the impl.

---

## expect / actual boundaries

| API | commonMain | desktopMain | wasmJsMain |
|-----|------------|-------------|------------|
| `DatabaseDriverFactory` | expect | `JdbcSqliteDriver` + `~/.stockviewer/spacex.db` | stub `error(...)` Phase 1 |
| `createHttpClient()` | expect + shared plugins | CIO engine | Js engine |

Do not put engine- or JDBC-specific types in `commonMain` domain/presentation.

---

## Current structural smells (document, not auto-fixed)

| Item | Boundary note |
|------|----------------|
| `CandlestickChart(repository)` | Presentation performs I/O subscription; evolution: inject `List<DailyOhlcv>` ([ARCHITECTURE §10 G1](../../ARCHITECTURE.md)) |
| `OhlcvDataViewDialog.kt` | Unreferenced alternate View; avoid second Write path without ADR |
| Ktor scaffold | `network` layer unused; OK as infrastructure placeholder |

---

## Future enforcement (optional)

- CI grep / detekt rule: ban `presentation` imports of `.database.`
- Gradle multi-module split: `domain` / `data` / `app` if team size grows
