# Contributing

## Branches (git flow)

| Branch | From | Merges into | Use |
|---|---|---|---|
| `feature/<module>-<topic>` | `develop` | `develop` | New work, e.g. `feature/alertas-escalation` |
| `bugfix/<module>-<topic>` | `develop` | `develop` | Fixes found during development |
| `hotfix/<topic>` | `main` | `main` **and** `develop` | Urgent fixes to a release |
| `release/<version>` | `develop` | `main` and `develop` | Release preparation (no releases are cut yet) |

`main` only receives releases and hotfixes. Branch prefixes follow git flow; commit messages keep their Conventional Commit types (`feat:`, `fix:`, `ci:`, `docs:` …).

## Workflow

1. **Branch from `develop`** with one of the prefixes above.
2. **Hooks:** run `lefthook install` once. It formats code before each commit and checks the message format.
3. **Before pushing:** `./gradlew spotlessApply test` must pass.
4. **Commits:** [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) in English, **no co-author line**. Example: `feat(alertas): receive events from the household agent`.
5. **Pull request to `develop`** using the template: it lists the definition of done from [AGENTS.md](AGENTS.md).
6. **CI must be green before merging.** The organization is on the GitHub Free plan, where branch protection is not enforced for private repositories: nothing blocks a merge with failing checks, so **reviewers must open the checks tab and confirm every job passed** before merging.

To add a feature, follow [docs/USE_CASE_GUIDE.md](docs/USE_CASE_GUIDE.md).

## Continuous integration

| Workflow | Trigger | Jobs |
|---|---|---|
| [CI](.github/workflows/ci.yml) | Push to `main`/`develop`, every PR | `Unit tests` → `Integration tests` (Testcontainers) → `Coverage` (JaCoCo report artifact, summary on the run page, 80 % line gate on `domain` and `application`); `Format, architecture and build` (Spotless, ArchUnit and Spring Modulith, `bootJar`) in parallel |
| [OSV-Scanner](.github/workflows/osv-scanner.yml) | Weekly, manual, PRs that change the build | Scans every resolved Gradle dependency; scheduled runs fail on high or critical |
| [OWASP Dependency-Check](.github/workflows/owasp.yml) | Weekly, manual | NVD scan of runtime dependencies (needs the `NVD_API_KEY` secret); fails on CVSS ≥ 7.0 |
| [End-to-end](.github/workflows/e2e.yml) | Weekly, manual, PRs that change the agent endpoints or the contract | `scripts/e2e.sh`: the real desktop agent, headless, against this API (needs the `E2E_REPO_TOKEN` secret; skipped with a notice without it) |
| [Container image](.github/workflows/image.yml) | PRs that change the image inputs, push to `main`, manual, tags `api-v*` | Smoke-tests the amd64 image against PostgreSQL, then builds `linux/amd64` and `linux/arm64`; pushes to `ghcr.io/te-tengo-tech/te-tengo-general-api` on `main`, a tag or a manual run with *push*. On `main` it then requests the production deploy from `te-tengo-infra` (needs the `DISPATCH_TOKEN` secret; a notice without it), which waits for approval there ([docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)) |

A new push cancels the superseded CI run of the same branch. Dependabot opens weekly update PRs to `develop`. See [.github/SECURITY.md](.github/SECURITY.md) for vulnerability reporting.

## Community

- [Code of Conduct](.github/CODE_OF_CONDUCT.md)
- [Security policy](.github/SECURITY.md)
