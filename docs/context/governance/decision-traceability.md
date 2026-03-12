# Decision Traceability (Index)

Track how decisions map to implementation and tests.

## Template

| Decision ID | Requirement/Rule | Implementation Paths | Test Coverage | Status |
|---|---|---|---|---|
| D-XXX | ... | ... | ... | planned/in-progress/done |

## Summary

- Contains traceability from accepted decisions to code, contracts, and tests.
- Split into parts due to context-size threshold.

## Parts (Exact Order)

1. `decision-traceability/decision-traceability-part-01.md` (`D-011` -> `D-030`)
2. `decision-traceability/decision-traceability-part-02.md` (`D-031` -> `D-040`)
3. `decision-traceability/decision-traceability-part-03.md` (`D-041` -> `D-051`)
4. `decision-traceability/decision-traceability-part-04.md` (`D-052` -> present)

## Append Rule

- Append new decision entries to `decision-traceability/decision-traceability-part-04.md`.
- If the latest part grows above ~8,000 characters, create `decision-traceability/decision-traceability-part-05.md`, update this index, and continue appending there.
