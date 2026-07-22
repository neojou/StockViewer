# 0002. SQLDelight + local SQLite for OHLCV persistence

## Status

Accepted

## Context

The app stores structured daily OHLCV time series for offline use on a desktop machine. Requirements:

- Type-safe queries on Kotlin Multiplatform
- Local file storage without installing a database server
- Replaceable storage behind a repository (future Wasm / remote)

Alternatives considered: pure JSON files, Room, full PostgreSQL/MySQL, Realm.

## Decision

- Use **SQLDelight** with a **SQLite** dialect.
- Database name: `StockViewerDatabase`.
- Desktop: `JdbcSqliteDriver` → `~/.stockviewer/spacex.db`.
- Access from features only via `OhlcvRepository` (domain interface).
- Schema and queries live in `commonMain/sqldelight/.../DailyOhlcv.sq`.

## Consequences

### Positive

- Compile-time verified SQL and generated Kotlin APIs.
- Adequate performance for day-bar scale data.
- Zero external DB service for end users.
- In-memory driver possible later for tests / Wasm.

### Negative

- Schema migrations must be planned before shipping breaking changes to existing user DB files.
- Wasm needs a different driver strategy (see ADR 0005).
- Generated `database` package must stay out of presentation imports (ADR 0004).
