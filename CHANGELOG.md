# Changelog

Format based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- Shared API contract with the mobile app (`docs/API_CONTRACT.md`), the work plan with its autonomous loop (`docs/WORK_PLAN.md`) and the `/work` command.
### Changed
- Documentation translated to English, with English file names.

## [0.1.0] - 2026-10-07
### Added
- **Modular monolith base:** Spring Boot 4.1, Java 25 and Spring Modulith, with modules `cuentas`, `hogares`, `camaras`, `alertas`, `monitoreo`, `historial` and `shared`.
- **Per-household multi-tenancy:** `@TenantId`, the `hogar_id` JWT claim, fail closed.
- **API conventions:** RFC 9457 `ProblemDetail` errors and `Api-Version` header versioning.
- **Reference slice `camaras`:** list and rename cameras (US-06), with domain, multi-tenancy integration and architecture tests.
- **Tooling:** Flyway, Testcontainers, Spotless with lefthook, JaCoCo and GitHub Actions CI.
