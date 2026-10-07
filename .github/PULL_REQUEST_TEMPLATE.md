## What and why

<!-- What does this pull request change, and why? -->

**Module:** <!-- cuentas | hogares | camaras | alertas | monitoreo | historial | shared | build | ci | docs -->

**Story / task:** <!-- e.g. US-07, WORK_PLAN T12. Closes #… -->

## Type of change

- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `refactor` — no behavior change
- [ ] `test` — tests only
- [ ] `docs` — documentation only
- [ ] `build` / `ci` / `chore` — dependencies, build, CI or maintenance

## Definition of done

- [ ] The pull request targets `develop` (not `main`), from a `feature/*` or `bugfix/*` branch (`hotfix/*` targets `main`)
- [ ] Every acceptance criterion of the story is covered by a test
- [ ] Endpoints match [`docs/API_CONTRACT.md`](../docs/API_CONTRACT.md) exactly (paths, fields, error codes); contract changes are mirrored in the mobile app or the household agent
- [ ] A two-household isolation test exists if the change reads or writes household data
- [ ] `./gradlew spotlessApply test` passes locally, including the architecture tests
- [ ] Database changes are new Flyway migrations (no published migration was edited)
- [ ] Conventional Commits in English, with no co-author line
- [ ] The task is checked off in [`docs/WORK_PLAN.md`](../docs/WORK_PLAN.md) and `CHANGELOG.md` is updated
- [ ] No secrets, keys (`.claves/`) or credentials are committed
- [ ] CI is green (branch protection is not enforced on our plan: reviewers check it before merging)

## How to test

<!-- Steps or requests a reviewer can run to see the change working. -->
