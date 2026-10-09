# Changelog

Format based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- Continuous deployment: a push to `main` smoke-tests the image before pushing it to GHCR (`sha-<short commit>`, `main` and the `build.gradle.kts` version), then sends `repository_dispatch` `desplegar-api` with that `sha-` tag to `te-tengo-infra`, whose deploy waits for approval on its `produccion` environment. Needs the `DISPATCH_TOKEN` secret (skipped with a notice without it) and a public GHCR package (docs/DEPLOYMENT.md). The `sha-` tag is now the short commit.
- Web client (the app's PWA build, served from any configured origin, e.g. Cloudflare Pages):
  - CORS for `/api/**` with the origins of `TT_CORS_ORIGENES` (patterns allowed; off when empty, the default; `http://localhost:*` with the `local` profile), handled by Spring Security before authentication so preflights work; allowed headers `Authorization`, `Api-Version`, `Content-Type`, exposed `WWW-Authenticate`.
  - Push devices with `plataforma: "WEB"` (FCM web push tokens). `fcm` sends them a `webpush` block (title, body, `Urgency: high`, `fcm_options.link` to `TT_PWA_URL`, HTTPS only); `sns` reaches them only with `TT_SNS_ARN_WEB` and skips them with a warning otherwise; `simulador` skips them.
  - `TT_ENLACE_BASE` sets the base of the e-mailed links: `tetengo://app` by default, or the PWA's hash URL (`https://<pwa>/#/nueva-contrasena?token=…`, `…/#/invitacion/{token}`).
  - MediaMTX's `hlsAllowOrigins` can be set from the environment (`MTX_HLSALLOWORIGINS`; `TT_MEDIAMTX_HLS_ORIGENES` in `compose.yaml`, `*` by default).
- Container image: multi-stage `Dockerfile` (Temurin 25 JRE, non-root user, Spring Boot layers, liveness `HEALTHCHECK`) for `linux/arm64` and `linux/amd64`, and the `Container image` workflow, which builds and smoke-tests it on pull requests and pushes it to GHCR on `api-v*` tags or a manual run (docs/DEPLOYMENT.md).
- End-to-end smoke test of the agent ↔ API contract, `scripts/e2e.sh`. It runs an isolated stack, seeds the demo household and plays a URFD fall clip through the real desktop agent, headless. It then asserts that the CAIDA alert is created, pushed and confirmed, and that its clip is available from Floci's S3. It prints a PASS/FAIL summary with the detection → push latency. The `End-to-end` workflow runs it on demand, weekly and on pull requests that touch the agent endpoints; it needs the `E2E_REPO_TOKEN` secret and is skipped with a notice without it. `compose.yaml` host ports are now overridable (`TT_POSTGRES_PUERTO`, `TT_FLOCI_PUERTO`), and the `local` profile follows `TT_FLOCI_PUERTO`.
- CI: separate unit, integration (Testcontainers), architecture/format/build and coverage jobs with JaCoCo report artifacts and step summaries; weekly OWASP Dependency-Check and OSV-Scanner scans; Dependabot, CODEOWNERS, issue and pull request templates, security policy and code of conduct.
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
- `hogares`: alert routing `GET` and `PUT /api/hogar/aviso` (owner only; 3, 5 or 10 minutes, default 5; `422 ESPERA_INVALIDA`, `422 CONTACTO_NO_ES_FAMILIAR`); with a single member there is no secondary contact (US-10).
- `alertas`: an idempotent escalation job sends `ALERTA_ESCALADA` to the secondary contact once the household's wait is over, or `SIN_CONTACTO_SECUNDARIO` to the primary; alerts attended in time are left alone (US-20).
- `monitoreo`: camera pauses `POST` and `DELETE /api/camaras/{id}/pausa` (`MIN_30`, `HORA_1`, `HORAS_2`, `HASTA_MANANA` = next 07:00 America/Lima; `422 DURACION_INVALIDA`), with an automatic resume job and push `PAUSA_FINALIZADA` (US-22).
- `monitoreo`: live view `POST /api/camaras/{id}/vista-en-vivo` (`409 CAMARA_DESCONECTADA`, `409 CAMARA_EN_PAUSA {pausadaHasta}`) and `DELETE /api/vista-en-vivo/{sesionId}`, with the WebSocket JPEG relay of the contract proposal between the agent and the app (US-23).
- `monitoreo`: `GET /api/accesos-vista-en-vivo` lists who watched live, when, for how long and whether from an alert, newest first (US-24).
- `alertas`: recovery notice — push `SE_LEVANTO` when the person gets up after a fall, none when the fall was confirmed (US-21).
- `historial`: recordings — `GET /api/alertas/{id}/clip?descarga=true` returns a download URL, and a retention job deletes clips older than `TT_RETENCION_CLIPS` (`410 CLIP_ELIMINADO`); the period is pending in BLOCKERS (US-26).
- `historial`: `GET /api/resumen-semanal?semana=2026-W41` (current ISO week by default, in America/Lima) counts falls, unstable movements and false alarms, excludes false alarms from falls and compares each type with the previous week (US-27).
- Hardening: OpenAPI annotations and a bearer-JWT scheme on every endpoint, a JaCoCo gate of 80 % line coverage on `domain` and `application`, and a test that checks every endpoint of both contracts exists; row-level security evaluated and deferred (ADR 0002).
- Shared API contract with the mobile app (`docs/API_CONTRACT.md`), the work plan with its autonomous loop (`docs/WORK_PLAN.md`) and the `/work` command.
- `camaras`: `GET /api/agente/configuracion` publishes the agent version and classification thresholds from `tetengo.agente.*`; no thresholds by default, so agents keep their calibrated local values.
- `alertas`: Amazon S3 adapter of `AlmacenamientoDeClips` (AWS SDK v2, pre-signed PUT and GET URLs, `headObject`, `deleteObject`), used when `TT_CLIPS_BUCKET` is set; `./gradlew bootRun` now runs the `local` profile with SeaweedFS from `compose.yaml` at http://localhost:8333, so the agent can upload clips and the app can play them locally.
- `shared`: Amazon SES v2 adapter of `NotificadorCorreo`, chosen with `TT_CORREO_PROVEEDOR=ses` (sender `TT_SES_REMITENTE`); the logging adapter stays the default.
- `shared`: push provider switch `TT_PUSH_PROVEEDOR` = `registro` (default), `fcm` (Firebase Admin SDK, FCM HTTP v1, key at `TT_FCM_CREDENCIALES`), `sns` (SNS mobile push with a platform endpoint per device, `TT_SNS_ARN_ANDROID` / `TT_SNS_ARN_IOS`) or `simulador` (`xcrun simctl push` to the booted iOS simulator). Every provider sends the same title, body and contract data payload with high priority (docs/NOTIFICATIONS.md, ADR 0006).
- `alertas`: devices whose token the push service rejects are deactivated until the phone registers again, and the SNS endpoint ARN is stored with the device (migration `V20`).
### Fixed
- Push notices use the prototype's copy word for word, with the older adult's first name, the room and the time (screens 28, 29, 36, 44, 49, 51 and 57 and the interactive prototype), and send its label (`URGENTE · CAÍDA`, `SEVERIDAD MEDIA`, `SEGUIMIENTO`) as the iOS subtitle and the data key `etiqueta`; `hogares` exposes the first name through `AdultoMayorDelHogar`. `DATOS_ELIMINADOS` has no prototype source yet (docs/BLOCKERS.md).
- E-mailed links default to the app's deep-link routes: `tetengo://app/nueva-contrasena?token={token}` and `tetengo://app/invitacion/{token}`.
- A missing, expired or invalid access token answers `401 application/problem+json` with `codigo: SESION_EXPIRADA` on every protected endpoint, keeping `WWW-Authenticate`, so the app knows when to refresh; the API contract also lists `409 SIN_CONSENTIMIENTO` on the live view and `404 SIN_CONSENTIMIENTO` on revoking a missing consent.
- An unsupported `Api-Version` header answers `400 VALIDACION` with `codigo` like every other error.
- `hogares`: `adultoMayor` carries the age (`edad`, required, 50 to 120, as in the prototype's profile form) and an optional `telefono` that «Llamar a Rosa» dials; the app already read both (migration `V18`).
- `camaras`: `Camara` carries `instaladaEn` («Instalada el», the agent's first registration) and `noConfiableDesde` («La detección no es confiable desde las …», the time of the `deteccion_no_confiable` event, cleared when the agent sees the person again); the app already read both (migration `V19`).
### Changed
- `monitoreo`: live view through MediaMTX (ADR 0007), replacing the WebSocket JPEG relay.
  - `POST /api/camaras/{id}/vista-en-vivo` takes an optional `modo` and answers `modo`. `urlTransmision` is the camera's LL-HLS playlist with a viewer token. An `alertaId` of another camera is `400 VALIDACION`.
  - New `PATCH /api/vista-en-vivo/{sesionId}` `{modo}`: `VIDEO`, `VIDEO_CON_POSTURA` or `SOLO_POSTURA`.
  - The agent's `/api/agente/transmision` is a text-only control channel: `transmitir:true` with the publish URL, credentials and mode; `modo`; `transmitir:false`.
  - MediaMTX authorizes publishes and reads through `POST /api/interno/mediamtx/autorizar` and a shared secret. Viewer and publish tokens are stored hashed.
  - Sessions end after 30 s without reads or at 10 min, with the access duration recorded.
  - A pause or a revoked consent ends the sessions, stops the agent and kicks every MediaMTX client.
  - `compose.yaml` runs MediaMTX 1.21.1 with `mediamtx.yml`. New `TT_VIVO_*` settings replace `TT_URL_TRANSMISION`. Migration `V21`.
- Agent endpoints follow the bodies of `docs/AGENT_CONTRACT.md` shared with `te-tengo-desktop-pywebview`: registration takes `credencialInstalacion` and answers `hogarId` and the stored `nombreHabitacion`; the capture state carries `motivo` (`SIN_CONSENTIMIENTO`, `EN_PAUSA`) and `nombreHabitacion`; the heartbeat takes `{webcamConectada, deteccionConfiable, versionAgente}`, answers the capture state and disconnects the camera at once when the webcam is unavailable; the clip upload answers `201 {urlSubida, cabeceras, expiraEn}`.
- Documentation translated to English, with English file names.
- Local runs use Floci, a local AWS emulator, instead of SeaweedFS: `compose.yaml` runs it on port 4566 and the `local` profile sends clips, e-mail and push to it (ADR 0005). Sent e-mails are listed at `/_aws/ses` and pushes at `/_aws/sns/push-notifications`.
### Security
- Tomcat 11.0.24 → 11.0.26 (override of Spring Boot's `tomcat.version`): fixes the critical CVE-2026-65905, CVE-2026-68525 and CVE-2026-65182.
- Jackson 3.1.5 → 3.1.7 and 2.21.5 → 2.21.7 (overrides of `jackson-bom.version` and `jackson-2-bom.version`): fixes the high CVE-2026-91777, CVE-2026-91776, CVE-2026-68497, CVE-2026-89425 and CVE-2026-89407, and the medium CVE-2026-83557 and CVE-2026-19032.

## [0.1.0] - 2026-10-07
### Added
- **Modular monolith base:** Spring Boot 4.1, Java 25 and Spring Modulith, with modules `cuentas`, `hogares`, `camaras`, `alertas`, `monitoreo`, `historial` and `shared`.
- **Per-household multi-tenancy:** `@TenantId`, the `hogar_id` JWT claim, fail closed.
- **API conventions:** RFC 9457 `ProblemDetail` errors and `Api-Version` header versioning.
- **Reference slice `camaras`:** list and rename cameras (US-06), with domain, multi-tenancy integration and architecture tests.
- **Tooling:** Flyway, Testcontainers, Spotless with lefthook, JaCoCo and GitHub Actions CI.
