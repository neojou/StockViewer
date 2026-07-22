# 0005. Desktop-primary, Wasm-secondary platform strategy

## Status

Accepted

## Context

Compose Multiplatform enables Desktop and WasmJS targets. Local SQLite via JDBC is natural on JVM. Wasm persistence (sql.js / web worker driver, async SQLDelight, packaging) is substantially more complex. Product value today is strongest as a **desktop** tool with a local DB file.

## Decision

1. **Desktop (JVM) is the primary product platform** for persistence and day-to-day use.
2. **WasmJS is secondary**:
   - Phase 1: `DatabaseDriverFactory` **stub** fails fast when creating a driver.
   - Phase 2 (optional): sql.js / WebWorker driver **or** remote backend—requires a separate decision/ADR.
3. Feature development and agent “done” criteria default to **Desktop compile + run** (`./gradlew :composeApp:run`).
4. Ktor client engines exist for both platforms, but networking is not required for core OHLCV CRUD.

## Consequences

### Positive

- Honest scope: avoids blocking desktop delivery on Wasm DB parity.
- Clear UX expectation: Wasm may not open the repository until Phase 2.
- Simpler testing story focused on JVM.

### Negative

- Wasm users cannot use Input/View/K Chart data paths that require DB until Phase 2 or an in-memory repository is implemented.
- Dual-target CI cost remains even when Wasm features lag.

## Related evolution options (not chosen yet)

| Option | Notes |
|--------|-------|
| In-memory `OhlcvRepository` for Wasm | Faster parity for demo; no durable storage |
| SQLDelight web-worker + sql.js | Closer to Desktop semantics; async/`generateAsync` impact |
| Backend + API | Heavy; only if multi-device sync is a product goal |
