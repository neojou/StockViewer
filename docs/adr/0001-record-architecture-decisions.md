# 0001. Record architecture decisions

## Status

Accepted

## Context

StockViewer is developed with AI agents and humans sharing the same repository. Architecture intent was previously embedded mainly in `AGENTS.md`, which mixes **operational guidance** with **system design**. That increases the risk of drift and makes decision history hard to audit.

## Decision

- Adopt **Architecture Decision Records (ADRs)** under `docs/adr/`.
- Keep **system architecture SSOT** in root [`ARCHITECTURE.md`](../../ARCHITECTURE.md).
- Keep **agent/implementation contracts** in [`AGENTS.md`](../../AGENTS.md), linking to architecture docs instead of duplicating long design prose.

## Consequences

### Positive

- Decisions are dated, reviewable, and linkable.
- Agents can be instructed to read ARCHITECTURE + ADR before changing structure.
- Feature docs stay shorter.

### Negative

- Requires discipline to open a new ADR when structure changes.
- Slight documentation overhead for a small project (accepted).
