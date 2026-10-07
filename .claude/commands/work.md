---
description: Work through docs/WORK_PLAN.md autonomously until every task is done
---
Follow the loop in `docs/WORK_PLAN.md` exactly.

1. Read `AGENTS.md`, `docs/WORK_PLAN.md` and `docs/BLOCKERS.md`.
2. Take the first unchecked task, implement it with tests, and make `./gradlew spotlessApply test` green.
3. Check the task off, update `CHANGELOG.md`, then commit (Conventional Commits, English, no co-author line) and push.
4. **Immediately continue with the next task. Do not stop to ask for confirmation between tasks.**

Record missing credentials or decisions in `docs/BLOCKERS.md` and keep going with fakes. Finish only when no unchecked task remains, then open or update a pull request with a summary.

Extra instructions from the user, if any: $ARGUMENTS
