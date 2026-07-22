# Architecture Decision Records (ADR)

This directory records **significant architecture decisions** for StockViewer.

- **Index of decisions** is listed below and linked from [`ARCHITECTURE.md`](../../ARCHITECTURE.md).
- **Implementation how-to** lives in [`AGENTS.md`](../../AGENTS.md).

## Format

Each ADR uses a short Markdown file:

```text
# NNNN. Title

## Status
Proposed | Accepted | Deprecated | Superseded

## Context
## Decision
## Consequences
```

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](./0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](./0002-sqldelight-sqlite-persistence.md) | SQLDelight + local SQLite | Accepted |
| [0003](./0003-compose-multiplatform-ui.md) | Compose Multiplatform UI | Accepted |
| [0004](./0004-repository-package-layering.md) | Repository + package layering | Accepted |
| [0005](./0005-desktop-primary-wasm-secondary.md) | Desktop-primary, Wasm-secondary | Accepted |

## When to add an ADR

Add an ADR when you:

- Change persistence, platforms, or UI framework assumptions
- Introduce a new integration boundary (e.g. remote API)
- Split Gradle modules or adopt a DI framework
- Change data identity / upsert / migration policy

Skip ADRs for pure UI polish that does not change system structure.
