# Реестр рисков

Канонический реестр активных product/delivery/technical/security risks проекта.

Используй этот документ как входную точку, если нужно:

- быстро увидеть, где хранится актуальный risk register;
- понять, что automation обязана обновлять его в том же work block при изменении risk posture;
- не смешивать active risks с issue-resolution playbooks, session history или recovery checkpoint.

## Правила

- Активные риски, residual limitations и security vulnerabilities веди здесь, а не только в commit messages, session log или task memory.
- Если meaningful task создал, изменил или снял активный риск, обнови latest part этого реестра в том же work block.
- Если commit message содержит раздел `Ограничения и риски`, все still-active пункты из него должны быть отражены в latest part этого реестра или явно сняты в той же change.
- Не используй этот реестр вместо:
  - `../engineering/issue-resolution-log.md` для повторяемых technical problems и repair path;
  - `../governance/session-log.md` для хронологии сессий;
  - `../handoff/active-run.md` для crash-safe recovery checkpoint.

## Части (точный порядок)

1. `risk-log/risk-log-part-01.md` (шаблоны и активные риски `R-001` -> `R-007`)
2. `risk-log/risk-log-part-02.md` (активные риски `R-008` -> `R-013`)
3. `risk-log/risk-log-part-03.md` (security vulnerabilities `V-001` -> `V-004`)
4. `risk-log/risk-log-part-04.md` (новые и обновленные entries после split `D-080`)

## Правило добавления

- Append new or updated active risks to `risk-log/risk-log-part-04.md`.
- If the latest part grows above ~8,000 characters, create `risk-log/risk-log-part-05.md`, update this index, and continue appending there.
