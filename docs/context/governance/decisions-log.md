# Decisions Log (Index)

ADR-style decision register for product and engineering governance.

## Summary

- Contains accepted architectural/process/product decisions in chronological order.
- Split into parts due to context-size threshold.

## Parts (Exact Order)

1. `decisions-log/decisions-log-part-01.md` (`D-001` -> `D-015`)
2. `decisions-log/decisions-log-part-02.md` (`D-016` -> `D-044`)
3. `decisions-log/decisions-log-part-03.md` (`D-045` -> `D-054`)
4. `decisions-log/decisions-log-part-04.md` (`D-055` -> present)

## Append Rule

- Append new decisions to `decisions-log/decisions-log-part-04.md`.
- If the latest part grows above ~8,000 characters, create `decisions-log/decisions-log-part-05.md`, update this index, and continue appending there.
