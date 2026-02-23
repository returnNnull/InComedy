# Session Log (Index)

Use session log parts as rolling operational memory.

## Summary

- Contains chronological session entries with `Context / Changes / Decisions / Next`.
- Split into parts due to size threshold from `docs/context/handoff/context-protocol.md`.

## Parts (Exact Order)

1. `session-log/session-log-part-01.md` (2026-02-23 00:00 -> 2026-02-23 12:24)
2. `session-log/session-log-part-02.md` (2026-02-23 12:30 -> present)

## Append Rule

- Append every new entry to the latest part file: `session-log/session-log-part-02.md`.
- If the latest part grows above ~8,000 characters, create `session-log/session-log-part-03.md`, update this index, and continue appending there.
