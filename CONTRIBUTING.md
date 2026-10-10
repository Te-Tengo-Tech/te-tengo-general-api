# Contributing

## Branches (git flow)

| Branch | From | Merges into | Use |
|---|---|---|---|
| `feature/<module>-<topic>` | `develop` | `develop` | New work, e.g. `feature/alertas-escalation` |
| `bugfix/<module>-<topic>` | `develop` | `develop` | Fixes found during development |
| `hotfix/<topic>` | `main` | `main` **and** `develop` | Urgent fixes to a release |
| `release/<version>` | `develop` | `main` and `develop` | Release preparation: a push runs the release pipeline (build once, verification, produccion, pull request to `main`; [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#release-flow-build-once-deploy-many)) |

`main` only receives releases and hotfixes. Branch prefixes follow git flow; commit messages keep their Conventional Commit types (`feat:`, `fix:`, `ci:`, `docs:` …).

## Workflow

1. **Branch from `develop`** with one of the prefixes above.
2. **Hooks:** run `lefthook install` once. It formats code before each commit and checks the message format.
3. **Before pushing:** `./gradlew spotlessApply test` must pass.
4. **Commits:** [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) in English, **no co-author line**. Example: `feat(alertas): receive events from the household agent`.
5. **Pull request to `develop`** using the template: it lists the definition of done from [AGENTS.md](AGENTS.md).
6. **CI must be green before merging.** The rulesets `proteger-develop` and `proteger-main` require one approval and the checks `ci-ok` (every CI job passed) and `pr-title` (Conventional Commits title); `main` also requires `release-gate` ([docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#release-flow-build-once-deploy-many)).

To add a feature, follow [docs/USE_CASE_GUIDE.md](docs/USE_CASE_GUIDE.md).

## Continuous integration

| Workflow | Trigger | Jobs |
|---|---|---|
| [CI](.github/workflows/ci.yml) | Every PR, push to `develop`, called by Release | `Unit tests` → `Integration tests` (Testcontainers) → `Coverage` (JaCoCo report artifact, summary on the run page, 80 % line gate on `domain` and `application`); `Format, architecture and build` (Spotless, ArchUnit and Spring Modulith, `bootJar`) in parallel; then `ci-ok`. PRs into `main` only run `ci-ok` |
| [PR title](.github/workflows/pr-title.yml) | Every PR | `pr-title`: Conventional Commits title |
| [Release gate](.github/workflows/release-gate.yml) | PRs into `main` | `release-gate`: the merge puts the tree of a verified candidate into `main` |
| [OSV-Scanner](.github/workflows/osv-scanner.yml) | Weekly, manual, PRs that change the build | Scans every resolved Gradle dependency; scheduled runs fail on high or critical |
| [End-to-end](.github/workflows/e2e.yml) | Weekly, manual, PRs that change the agent endpoints or the contract, called by Release | `scripts/e2e.sh`: the real desktop agent (its `develop`; the latest release when Release calls it), headless, against this API (a jar built here, or a given image) |
| [Container image](.github/workflows/image.yml) | PRs that change the image inputs, manual | Smoke-tests the amd64 image against PostgreSQL (`scripts/smoke-image.sh`), then builds `linux/amd64` and `linux/arm64`; a manual run with *push* pushes a test image (switch `ENABLE_API_IMAGE`). Never deploys |
| [Release](.github/workflows/release.yml) | Push to `release/**`, `hotfix/**` | `ci` (calls CI) and `build` (image once, GHCR `x.y.z-rc.N` + `sha-<short>`, provenance and SBOM attestations) → `candidate` (pre-release `vX.Y.Z-rc.N`: digest, tree hash, SBOM) → `verify` + `verify-e2e` (same digest; automatic) → pull request to `main` (release bot). Switch `ENABLE_API_IMAGE` ([docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#release-flow-build-once-deploy-many)) |
| [Produccion](.github/workflows/produccion.yml) | Push to `main` | Candidate whose tree hash equals `main`'s → `desplegar-api` to `te-tengo-infra` with its digest (release bot), waiting for the result (approval there; switch `ENABLE_API_DEPLOY`) → only after the deploy: image tags `x.y.z` + `latest`, tag `vX.Y.Z` + GitHub Release → back-merge pull request to `develop` (release bot, auto-merge) |
| [Rollback](.github/workflows/rollback.yml) | Manual, from `main` | Redeploys the digest of an earlier release `vX.Y.Z` through `te-tengo-infra` and moves `latest` back |

A new push cancels the superseded CI run of the same pull request. Dependabot opens weekly update PRs to `develop` (Gradle, GitHub Actions, base images, Compose). See [.github/SECURITY.md](.github/SECURITY.md) for vulnerability reporting.

## Community

- [Code of Conduct](.github/CODE_OF_CONDUCT.md)
- [Security policy](.github/SECURITY.md)
