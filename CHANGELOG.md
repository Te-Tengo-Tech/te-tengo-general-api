# Changelog

Format based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- `cuentas`: account registration `POST /api/cuentas` with BCrypt passwords, unique e-mail and `400 VALIDACION` with `campos` (US-01).
- `cuentas`: sessions `POST /api/sesiones`, `POST /api/sesiones/refresco` and `DELETE /api/sesiones/actual`; RS256 access tokens with `sub`, `hogar_id`, `rol` and `sid`, rotating persisted refresh tokens, and a 15-minute lock after 5 consecutive failures (US-02).
- `cuentas`: password recovery `POST /api/recuperaciones` (always `202`) and `POST /api/recuperaciones/confirmacion` with one-time links valid for 30 minutes (`410 ENLACE_VENCIDO`), sent through the `NotificadorCorreo` port with a logging adapter until SES is configured (US-03).
- `hogares`: `POST /api/hogar` registers the older adult and creates the household with the caller as `TITULAR` (one per account, `409 HOGAR_YA_REGISTRADO`), plus `GET /api/hogar`, `PUT /api/hogar/adulto-mayor` (owner only, `403 SOLO_TITULAR`), `GET /api/hogares` and `POST /api/sesiones/hogar` (`403 SIN_MEMBRESIA`); global membership table and default household at sign-in (US-04).
- `hogares`: consent `POST` (owner only, `422 CONSENTIMIENTO_NO_ACEPTADO`) and `GET /api/hogar/consentimiento` (`404 SIN_CONSENTIMIENTO`), stored with its date and time; the `ConsentimientoOtorgado` event updates the capture state kept by `camaras` (US-05).
- `camaras`: renaming a camera is owner only (`403 SOLO_TITULAR`) and `Camara` carries `pausadaHasta` and `deteccionConfiable`, as in the API contract (US-06).
- `camaras`: household agent registration `POST /api/agente/camaras/registro` with an installation credential (per-camera JWT with `rol = AGENTE`) and `GET /api/agente/estado-captura` (consent and pause); agent tokens only open `/api/agente/**` (CA-06.1, CA-05.2).
- `camaras`: heartbeat `POST /api/agente/senal` and a scheduled job that marks cameras `DESCONECTADA` after 3 missed heartbeats; `alertas` delivers `CAMARA_DESCONECTADA` and `CAMARA_RECONECTADA` to the members' devices through the `NotificadorPush` port (fake adapter until SNS) (US-07).
- `hogares`: family members — `POST /api/invitaciones` (owner only, `409 YA_ES_FAMILIAR`) e-mails a link, public `POST /api/invitaciones/{token}/aceptacion` with a new or existing account (`410 INVITACION_VENCIDA`), `GET /api/familiares` and `DELETE /api/familiares/{usuarioId}` (`409 NO_SE_PUEDE_RETIRAR_TITULAR`); a removed member loses access at once because the household filter checks the membership (US-08).
- `alertas`: `POST /api/agente/eventos`, idempotent by `eventoId`, creates or updates alerts: falls, unstable movements, an unstable movement becoming a fall, confirmation after 30 s, recovery and unreliable detection; events without consent or during a pause create no alert (US-11 to US-17, CA-13.1, CA-13.2, CA-15.3).
- `alertas`: push alerts `ALERTA_CAIDA`, `ALERTA_MOVIMIENTO_INESTABLE`, `ALERTA_ACTUALIZADA_A_CAIDA`, `CAIDA_CONFIRMADA` and `DETECCION_NO_CONFIABLE` to every member device, sent within the agent's request (< 10 s) and retried by a job when the push service fails; device registration `POST` and `DELETE /api/dispositivos` (US-16, US-17, CA-16.4).
- `alertas`: event clips — the agent gets a pre-signed PUT URL from `POST /api/agente/eventos/{id}/clip` and the app reads through `GET /api/alertas/{id}/clip` (`404 CLIP_NO_DISPONIBLE`, `410 CLIP_ELIMINADO`); object storage behind the `AlmacenamientoDeClips` port with a fake adapter until S3 (US-18).
- `alertas`: `GET /api/alertas` with `tipo`, `estado`, `desde`, `hasta`, `pagina` and `tamano`, newest first, and `GET /api/alertas/{id}` (`404 ALERTA_NO_ENCONTRADA`); alerts whose push failed are listed too (CA-16.4, CA-25.1 to CA-25.3).
- `hogares`: `DELETE /api/hogar/consentimiento` (owner only) revokes the consent; capture stops, a job deletes every clip of the household and pushes `DATOS_ELIMINADOS` when done (US-09).
- `alertas`: `POST /api/alertas/{id}/atencion` and `/falsa-alarma` (any member, `409 ALERTA_CERRADA`), recording who and when; `ALERTA_ATENDIDA` is pushed to the other members (US-19).
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
