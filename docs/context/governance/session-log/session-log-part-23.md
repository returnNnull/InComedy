# Session Log Part 23

## 2026-03-25 16:41

- Context: Пользователь потребовал единое место, где будут храниться не только security vulnerabilities, но и активные ограничения/риски из commit messages и task memory, чтобы automation не оставляла их рассыпанными по разным документам.
- Changes: `docs/context/product/risk-log.md` переведён в канонический active risk register для product/delivery/technical/security risks, получил расширенный template и новую запись `R-013` для текущего realtime limitation profile из `TASK-084`. Executor runbook, checklist, policy, context protocol, README/navigation, quality/integrity rules и `00-current-state.md` синхронизированы так, чтобы automation была обязана обновлять risk log в том же work block при изменении risk posture. Security review verdict: docs/process-only sync; runtime surface не менялся.
- Decisions: Принят `D-080`: `risk-log.md` становится единым активным risk register-ом, а commit message `Ограничения и риски` больше не считается достаточным местом хранения таких ограничений.
- Next: Продолжать `TASK-085`, а во всех следующих meaningful tasks синхронно обновлять `product/risk-log.md`, если меняются residual limitations или active risks.
