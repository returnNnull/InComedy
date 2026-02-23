# Decisions Log (Index)

ADR-style decision register for product and engineering governance.

## Summary

- Contains accepted architectural/process/product decisions in chronological order.
- Split into parts due to context-size threshold.

## Parts (Exact Order)

1. `decisions-log/decisions-log-part-01.md` (`D-001` -> `D-015`)
2. `decisions-log/decisions-log-part-02.md` (`D-016` -> present)

## Append Rule

- Append new decisions to `decisions-log/decisions-log-part-02.md`.
- If the latest part grows above ~8,000 characters, create `decisions-log/decisions-log-part-03.md`, update this index, and continue appending there.
