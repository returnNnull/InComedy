# Decisions Log (Index)

ADR-style decision register for product and engineering governance.

## Summary

- Contains accepted architectural/process/product decisions in chronological order.
- Split into parts due to context-size threshold.

## Parts (Exact Order)

1. `decisions-log/decisions-log-part-01.md` (`D-001` -> `D-015`)
2. `decisions-log/decisions-log-part-02.md` (`D-016` -> `D-044`)
3. `decisions-log/decisions-log-part-03.md` (`D-045` -> `D-054`)
4. `decisions-log/decisions-log-part-04.md` (`D-055` -> `D-064`)
5. `decisions-log/decisions-log-part-05.md` (`D-065` -> `D-071`)
6. `decisions-log/decisions-log-part-06.md` (`D-072` and later entries)

## Append Rule

- Append new decisions to `decisions-log/decisions-log-part-06.md`.
- If the latest part grows above ~8,000 characters, create `decisions-log/decisions-log-part-07.md`, update this index, and continue appending there.
