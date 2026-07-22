# 0004. Repository pattern and package layering

## Status

Accepted

## Context

The project is a **single** Gradle module (`:composeApp`). We still need clear boundaries so UI does not couple to SQLDelight and so storage can be swapped (Desktop file DB, future in-memory or remote).

## Decision

1. **Logical layers by package** (not multi-module yet):
   - `domain` — models, validators, repository interfaces
   - `data` — SQLDelight implementations and mappers
   - `presentation` — Compose UI
   - `di` — composition root (`AppContainer`)
   - `platform` / `network` — expect/actual infrastructure
2. **Repository interface** (`OhlcvRepository`) is the only persistence API for features.
3. **Presentation must not** import SQLDelight generated types under `com.neojou.stockviewer.database`.
4. **DI stays manual** (`AppContainer`) until dependency graph complexity justifies a framework.
5. Navigation chrome (`AppToolbar`) emits **callbacks only**; shell owns repository access.

Detailed import matrix: [`docs/modules/boundaries.md`](../modules/boundaries.md).

## Consequences

### Positive

- Clear mental model for agents and contributors.
- Storage replaceable without rewriting dialogs/charts.
- Low ceremony vs full multi-module Clean Architecture.

### Negative

- Boundaries are **convention-based** (not enforced by the compiler).
- Risk of layering violations unless reviewed or static checks are added later.
- For a larger team, package rules may be insufficient—then split Gradle modules.

## Explicit non-decision

We do **not** require UseCase/Interactor classes per feature while there is essentially one aggregate (`DailyOhlcv`). Introduce application services only when orchestration complexity appears (e.g. CSV import + multi-step validation + progress).
