# AGENTS.md

## Purpose
Backend API of **Te Tengo**, a system that detects falls of older adults at home. This repository is the "Backend API del sistema" container of the C4 model. It serves two clients:
- **the household agent** (Te Tengo Captura, `te-tengo-desktop-pywebview`), which processes video on the household PC and sends **events**;
- **the mobile app** of the family member or caregiver (`te-tengo-mobile-flutter`), native or its web build (PWA, served from another origin: see CORS in the API contract).

It stores data in PostgreSQL and clips in S3, and sends push alerts through Amazon SNS.

## Where to look
| Question | Source |
|---|---|
| What to build, and in which order | [docs/WORK_PLAN.md](docs/WORK_PLAN.md) — a checklist; follow the autonomous loop described there |
| Exact HTTP API (paths, bodies, error codes, push types) | [docs/API_CONTRACT.md](docs/API_CONTRACT.md) — **shared with the mobile app; implement it exactly** |
| What the household agent sends | [docs/AGENT_CONTRACT.md](docs/AGENT_CONTRACT.md) |
| Acceptance criteria (Given/When/Then) | [docs/references/PRODUCT_BACKLOG.md](docs/references/PRODUCT_BACKLOG.md) — Spanish source document; every business value comes from here |
| How each flow moves through the components | `docs/references/diagrams/integracion.puml` (sequence per flow), `arquitectura_logica_v2.puml`, `c4.dsl` |
| Module layout and patterns | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/USE_CASE_GUIDE.md](docs/USE_CASE_GUIDE.md), and the reference slice `tech.tetengo.api.camaras` |
| Multi-tenancy | [docs/MULTITENANCY.md](docs/MULTITENANCY.md) — the most important rule |

## Big picture
- **Modular monolith:** Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18 and Flyway. The base package is `tech.tetengo.api`.
- **Modules** (one per backlog epic): `cuentas`, `hogares`, `camaras`, `alertas`, `monitoreo` and `historial`, plus `shared`, the only module others may depend on.
- **Hexagonal layers inside each module:** `domain`, `application` (use cases and ports), `infrastructure` (adapters) and `interfaces/rest`.
- **Enforced boundaries:** `ModularidadTest` (Spring Modulith) and `ArquitecturaTest` (ArchUnit) fail the build when a boundary is broken. Modules talk through domain events or UUIDs: no cross-module foreign keys, no imports of another module's internals.

## Multi-tenancy (the most important rule)
- **The tenant is the household** (`hogar`): the home, its older adult and the linked family members.
- **Discriminator column:** every household table has `hogar_id`, and its entity extends `EntidadDelHogar`, whose `@TenantId` makes Hibernate fill and filter it automatically.
- **Never write `WHERE hogar_id = ...` and never accept a household id as a parameter.**
- **Where the household comes from:** the `hogar_id` JWT claim, set by `FiltroHogarActual`.
- **Fail closed:** with no household in context, queries return nothing.
- **Global tables** (`hogares`, accounts, memberships) extend `AggregateRoot` instead.
- **Every feature that reads or writes household data needs an integration test with two households,** proving isolation. Model it on `CamarasMultitenancyIntegrationTest`.

## API, security and errors
- **Routes and versioning:** routes live under `/api` (`ApiVersioning.BASE`), and every mapping declares `version = ApiVersioning.V1` (header `Api-Version`).
- **JWT RS256 resource server:** the public key comes from `spring.security.oauth2.resourceserver.jwt.public-key-location`. The `cuentas` module issues tokens (claims `sub`, `hogar_id`, `rol`). For local development, run `scripts/generate-keys.sh`.
- **Roles:** `TITULAR` (owner) and `INVITADO` (invited member). Owner-only endpoints return `403 SOLO_TITULAR` (see the API contract).
- **Errors:** RFC 9457 `ProblemDetail` with a `codigo` property. Each module has an `enum` implementing `CodigoError`, and business rules throw `ErrorDeNegocio`. Validation errors use `400 VALIDACION` with `campos`.
- **DTOs and mappers:** DTOs are `record`s; mappers are static `*Mapper` classes.

## Code conventions
- **Language:** domain identifiers, resource paths and JSON fields are **Spanish** (the ubiquitous language of the thesis architecture: `Camara`, `hogar`, `nombreHabitacion`). Code comments, Javadoc, documentation and commit messages are **English**.
- **IDs:** UUID v7, generated in `AuditableEntity` constructors; never `@GeneratedValue`.
- **Rules live in aggregates;** use cases stay thin.
- **Ports and adapters:** ports go in `application/port`; adapters in `infrastructure/...`; `JpaRepository` interfaces are package-private.
- **External services sit behind ports with a fake adapter for tests:** email (Amazon SES), push (Amazon SNS or Firebase, chosen by configuration), object storage (S3). Never call AWS from tests; adapter tests and local runs use Floci, a local AWS emulator (ADR 0005, docs/NOTIFICATIONS.md).
- **Migrations:** Flyway `src/main/resources/db/migration/V<n>__<snake_case>.sql`. Never edit a published migration.
- **Scheduled jobs** (escalation, pause end, disconnection, deletion) use `@Scheduled` and must be idempotent.

## Commands
| Command | Purpose |
|---|---|
| `./gradlew bootRun` | Run the API (starts PostgreSQL from `compose.yaml`) |
| `./gradlew test` | Everything: unit, integration (Testcontainers) and architecture tests |
| `./gradlew unitTest` / `integrationTest` / `architectureTest` | Single test lanes |
| `./gradlew spotlessApply` | Format (Palantir Java Format) |

## Definition of done (every task)
1. **Every acceptance criterion of the story is covered by a test.**
2. **The endpoints match `docs/API_CONTRACT.md` exactly:** paths, fields and error codes.
3. **A two-household isolation test exists** when the feature touches household data.
4. **`./gradlew spotlessApply test` passes,** including the architecture tests.
5. **One Conventional Commit per task,** in English and with no co-author line (e.g. `feat(cuentas): register accounts (US-01)`), and the task is checked off in `docs/WORK_PLAN.md`.
