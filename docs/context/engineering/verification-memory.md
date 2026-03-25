# Память проверок

Этот документ хранит текущую executable coverage map и недавние verification outcomes.

Используй его, когда нужно:

- быстро понять, какие платформенные UI/integration проверки уже существуют;
- найти недавние успешные команды/таргеты для повторного запуска;
- не смешивать strategy-level правила с history-level verification memory.

## Части (точный порядок)

1. `verification-memory/verification-memory-part-01.md` (current platform coverage and recent outcomes)
2. `verification-memory/verification-memory-part-02.md` (EPIC-070 donations/payout backend verification outcomes from 2026-03-25)

## Правило добавления

- Append new verification history to `verification-memory/verification-memory-part-02.md`.
- If the latest part grows above ~8,000 characters, create `verification-memory/verification-memory-part-03.md`, update this index, and continue appending there.
