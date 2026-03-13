# Session Log (Index)

Use session log parts as rolling operational memory.

## Summary

- Contains chronological session entries with `Context / Changes / Decisions / Next`.
- Split into parts due to size threshold from `docs/context/handoff/context-protocol.md`.

## Parts (Exact Order)

1. `session-log/session-log-part-01.md` (2026-02-23 00:00 -> 2026-02-23 12:24)
2. `session-log/session-log-part-02.md` (2026-02-23 12:30 -> 2026-03-05 21:02)
3. `session-log/session-log-part-03.md` (2026-03-06 13:35 -> 2026-03-13 00:19)
4. `session-log/session-log-part-04.md` (2026-03-13 00:20 -> 2026-03-13 03:40)
5. `session-log/session-log-part-05.md` (2026-03-13 12:15 -> present)

## Append Rule

- Append every new entry to the latest part file: `session-log/session-log-part-05.md`.
- If the latest part grows above ~8,000 characters, create `session-log/session-log-part-06.md`, update this index, and continue appending there.
