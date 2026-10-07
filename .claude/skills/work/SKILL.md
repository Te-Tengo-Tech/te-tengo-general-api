---
name: work
description: Work through docs/WORK_PLAN.md autonomously until every task is done. Use when the user types /work, says "work" or "continue", or asks to finish the plan.
---
Follow the loop in `docs/WORK_PLAN.md` exactly.

1. Read `AGENTS.md`, `docs/WORK_PLAN.md` and `docs/BLOCKERS.md`.
2. Take the first unchecked task and implement it with tests, following `docs/USE_CASE_GUIDE.md` and `docs/API_CONTRACT.md`.
3. Make `./gradlew spotlessApply test` green. Never skip or weaken a test.
4. Check the task off, update `CHANGELOG.md`, then commit (Conventional Commits, English, no co-author line) and push.
5. **Immediately continue with the next task. Do not stop to ask for confirmation between tasks.**

Record missing credentials or decisions in `docs/BLOCKERS.md` and keep going with fakes. Finish only when no unchecked task remains, then open or update a pull request with a summary of the completed tasks and blockers.
