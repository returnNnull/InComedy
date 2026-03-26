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
4. `decision-traceability/decision-traceability-part-04.md` (`D-052` -> `D-059`)
5. `decision-traceability/decision-traceability-part-05.md` (`D-060` -> `D-068`)
6. `decision-traceability/decision-traceability-part-06.md` (`D-067` -> `D-077`)
7. `decision-traceability/decision-traceability-part-07.md` (`D-078` and later entries)
8. `decision-traceability/decision-traceability-part-08.md` (`D-082` and later entries)

## Append Rule

- Append new decision entries to `decision-traceability/decision-traceability-part-08.md`.
- If the latest part grows above ~8,000 characters, create `decision-traceability/decision-traceability-part-09.md`, update this index, and continue appending there.
