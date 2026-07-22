# 0003. Compose Multiplatform for UI

## Status

Accepted

## Context

Targets include Desktop (JVM) and WasmJS. A single UI codebase is preferred over separate Swing/JavaFX and web stacks. Charting must work without platform-native chart libraries that are unavailable on KMP.

## Decision

- Use **Compose Multiplatform** (Material3) for all user interface.
- Share UI in `commonMain`; keep platform entry points thin (`Main.kt`, Wasm bootstrap).
- Implement the K-line chart with **Compose Canvas** (custom drawing), not third-party native chart SDKs.

## Consequences

### Positive

- One UI model for Desktop and Wasm.
- Full control over candlestick / volume / crosshair UX (`docs/k_chart.jpg` alignment).
- Fits existing Kotlin skill set.

### Negative

- Canvas chart code is non-trivial to maintain (geometry, hit-testing, ticks).
- Some Material components differ slightly across platforms.
- Advanced desktop OS integration may still need expect/actual later.
