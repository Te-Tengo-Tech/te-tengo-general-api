@AGENTS.md

## Notes for Claude Code
- **Working mode:** this repository is meant to be built autonomously from [docs/WORK_PLAN.md](docs/WORK_PLAN.md). When asked to "work", "continue" or `/work`, follow the loop in that file **until every task is checked or only blocked tasks remain. Do not stop after one task.**
- **Cloud environment:** the `SessionStart` hook (`scripts/cloud/setup-environment.sh`) installs JDK 25 (the image ships Java 21) and starts Docker for Testcontainers.
  - If Docker is unavailable, run `./gradlew unitTest architectureTest`, note it in the commit body, and rely on CI for the integration lane.
- **User rules:**
  - English for docs and commits; Conventional Commits with **no co-author line**.
  - Never invent business values: they come from the backlog or the API contract.
  - Spanish stays only in domain identifiers and API resource names.
