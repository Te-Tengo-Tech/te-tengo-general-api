# Changelog

Format based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.3.4] - 2026-10-10

### Fixed
- After a consent revocation the app could not know that the recordings were deleted unless the push `DATOS_ELIMINADOS` reached its screen, which does not happen while the app is in the background or the PWA's window is hidden: the revocation screen stayed on «Eliminando las grabaciones…». `GET /api/hogar` now returns `eliminacion: {estado: PROGRAMADA | TERMINADA, clips, programadaEn, terminadaEn} | null`, the deletion of the latest revocation, for any member, so the app can poll it. It is `PROGRAMADA` from the moment the revocation answers, also before `alertas` records the deletion on its own thread, and never shows an earlier revocation's deletion as the current one. `hogares` reads it through the shared port `EliminacionesDeGrabaciones`, implemented by `alertas` (like `dispositivosActivos`).
- The app could not tell how many recordings a revocation deletes: it counted them with `GET /api/alertas?tamano=200`, which this API rejects (`tamano` is at most 100), and showed «0 clips» (in production 2 recordings were deleted). `DELETE /api/hogar/consentimiento` now answers `202 {eliminacionProgramada: true, clips}`, counted at the revocation, and a completed deletion stores how many it deleted (`eliminaciones_de_grabaciones.clips_eliminados`, migration `V23`; earlier deletions are filled from the clips they marked).

### Changed
- `docs/API_CONTRACT.md` §2 (`eliminacion`, `clips` of the revocation) and §5 (`tamano` at most 100, which the API already enforced).

## [0.3.3] - 2026-10-10

### Added
- A weekly `branch-cleanup.yml` (Mondays 04:00 UTC, or by hand with a dry run) deletes branches merged 7+ days ago and unmerged branches with no commits for 30+ days; it never touches `main`, `develop`, `release/*`, `hotfix/*`, branches with an open pull request or pull requests labelled `do-not-delete`, and `BRANCH_CLEANUP_ENABLED=false` turns it off.

### Fixed
- The back-merge job of `produccion.yml` runs whenever the release job succeeded, even if a switched-off job earlier in its chain was skipped (GitHub skips a job whose implicit `success()` sees a skipped ancestor; te-tengo-mobile-flutter 0.3.2 lost its back-merge that way).

## [0.3.2] - 2026-10-10

### Changed
- CI tests each commit once: `ci.yml` runs on pull requests, on pushes to `develop` and when the Release workflow calls it (`workflow_call`) on the release commit, next to the image build; no candidate is recorded unless it passes. It no longer runs on pushes to `main`, `release/**` or `hotfix/**`, and pull requests into `main` only run `ci-ok`. Only `develop` writes the Gradle cache; pull requests of `image.yml` only read the Docker layer cache.
- The release pull request, the back-merge and the `desplegar-api` dispatch to te-tengo-infra use the GitHub App te-tengo-release-bot (one-hour tokens minted per job) instead of `GITHUB_TOKEN` and the personal `DISPATCH_TOKEN`; the deploy run is polled with the workflow token. The back-merge turns on auto-merge (merge commit) and, after a hotfix, opens `main → release/*` for newer release branches.
- `produccion.yml` finds its candidate with `.github/scripts/find-candidate.sh`, the same search as the release gate, and tags `x.y.z`, `latest` and `vX.Y.Z` only after a deploy: with `ENABLE_API_DEPLOY` off nothing is tagged.
- The end-to-end verification of a candidate runs against the latest release of the desktop agent instead of its `develop`.
- A release branch whose name differs from `version` in `build.gradle.kts` is an error.
- The Dockerfile pins its Temurin JDK and JRE base images by digest.

### Added
- `ci-ok`, the single required check of the rulesets; `release-gate` (pull requests into `main`: the merge must put the tree of a verified candidate into `main`); `pr-title` (Conventional Commits titles).
- Release candidates carry an SPDX SBOM of the image (attached to the pre-release and the final release), a build provenance attestation and an SBOM attestation in GHCR, and an OSV-Scanner report of the SBOM in the run summary.
- Dependabot also updates the Dockerfile base images and the images of `compose.yaml`.
- docs/DEPLOYMENT.md: release gate, release bot, rollback rehearsal.

### Removed
- `owasp.yml`: without the `NVD_API_KEY` secret it skipped itself and passed while checking nothing. OSV-Scanner covers the dependencies; `./gradlew dependencyCheckAnalyze` still runs on demand.
- The `DISPATCH_TOKEN` and `E2E_REPO_TOKEN` secrets are no longer read.

### Security
- Every action is pinned by commit SHA; no permissions at workflow level and the minimum per job; checkouts without persisted credentials.

## [0.3.1] - 2026-10-10
### Fixed
- Fall notices that reached nobody were treated as done. When no member had an active device, or the push service rejected every token, the API logged it at `INFO`, never retried and left `notificadaEn` empty. Urgent alert notices (`ALERTA_CAIDA`, `ALERTA_MOVIMIENTO_INESTABLE`, `ALERTA_ACTUALIZADA_A_CAIDA`, `CAIDA_CONFIRMADA`, `ALERTA_ESCALADA`, `SIN_CONTACTO_SECUNDARIO`) are now logged at `ERROR` and retried every 15 s for 30 min (`tetengo.push.ventana-urgente`) until a device accepts them, so a phone that registers again still gets the fall; retries stop when the alert is attended. `notificadaEn` is set when one is delivered.
- Alert delivery state: `Alerta.estadoAviso` (`ENVIANDO`, `ENTREGADO`, `REINTENTANDO`, `NO_ENTREGADO`) says whether the family got the alert's notice and whether the API is still trying (migration `V22`; earlier alerts become `ENTREGADO` or `NO_ENTREGADO`).
- Pushes were sent inside the household agent's request transaction (and the escalation job's). Every notice is now saved in the outbox (`avisos_pendientes`) with the change that calls for it and sent after the commit, on another thread, with no database transaction open while the push service answers (`DespachoDeAvisos`). A rolled-back change sends nothing; a notice is not lost if the API stops.
- FCM `INVALID_ARGUMENT` no longer deactivates devices (Firebase also returns it for a payload problem); only `UNREGISTERED` and `SENDER_ID_MISMATCH` do, and every deactivation is logged with the device id and `desactivado_en`.
- Transient FCM errors (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`) are retried for the failed devices only, up to 3 sends, honouring `Retry-After` (at most 10 s). Partial failures are logged per device.
- Every FCM send is logged with the device's platform and token fingerprint (never the token) and FCM's message id or error code.
- Urgency of the push payload: Android channel `alertas_caida` for alert notices, `tag` per alert and `ttl` 1 h; APNs `apns-push-type: alert`, `apns-expiration`, `apns-collapse-id` and `interruption-level: time-sensitive` for urgent notices; web push `TTL`, `tag` and `requireInteraction` for urgent notices, and a link to the alert's screen (`<TT_PWA_URL>#/alerta/<id>`).
- `POST /api/dispositivos` with an unchanged token did not record anything, so a phone's last registration was unknown. It now records `vistoEn` every time and answers `201 Dispositivo {id, plataforma, activo, vistoEn, desactivadoEn}`. A registration also sends the household's queued notices right away.
- The app could not know that the API had stopped sending to it. New `GET /api/dispositivos/{id}` (the caller's own devices; `404 DISPOSITIVO_NO_ENCONTRADO`) returns whether the device is active, and `GET /api/hogar` returns `dispositivosActivos`, the active push devices of the household's members (0: nobody in the family can receive alerts).
- A late delivery could overwrite the agent's next change to the same alert (e.g. its confirmation): alerts are now updated column by column.
- `scripts/e2e.sh` waits for the push sent after the commit and checks `estadoAviso`.

### Changed
- «Se levantó» after a confirmed fall: the recovery of a confirmed fall now sends `SE_LEVANTO` too, with the CA-21.1 copy, while the alert stays active and confirmed until a member attends it. Product decision of 2026-10-10, which changes CA-21.2 (docs/BLOCKERS.md).
- `docs/API_CONTRACT.md` (§2 `dispositivosActivos`, §5 `estadoAviso`, §7 device registration, `Dispositivo`, delivery and urgency) and `docs/NOTIFICATIONS.md` (delivery outbox, urgency, token handling).

## [0.3.0] - 2026-10-09
### Added
- Live view v3, WebRTC playback (ADR 0008): `POST /api/camaras/{id}/vista-en-vivo` also answers `urlWebrtc`, the camera's WHEP endpoint on MediaMTX with the session's viewer token in the query (`TT_VIVO_URL_WEBRTC`, e.g. `https://<host>/vivo-webrtc/camaras/{camaraId}/whep`; null when blank, the default, so the app keeps LL-HLS). `urlTransmision` (LL-HLS) stays as the fallback.
- `POST /api/vista-en-vivo/preparar` (`{camaraId}` → `204`, same rules as opening a session): sends the new control message `{"preparar":true}` to the camera's agent, so it warms up capture and encoder (no frame leaves the PC) before `transmitir`; nothing is sent while the camera already streams. Earlier agents ignore it.
- MediaMTX authorizes WebRTC reads (`protocol: webrtc`) with the same viewer token as HLS, taken from the query. The session job also counts WebRTC readers (`/v3/webrtc/sessions/list`, `outboundBytes`) as activity, and ending a session kicks its WebRTC readers too.
- Local stack: `mediamtx.yml` turns WebRTC on (WHEP on 8889, ICE on 8189 UDP and TCP, `webrtcAdditionalHosts` 127.0.0.1); `compose.yaml` publishes them (`TT_MEDIAMTX_PUERTO_WEBRTC`, `TT_MEDIAMTX_PUERTO_ICE`, `TT_MEDIAMTX_WEBRTC_HOSTS`) and the `local` profile sets `urlWebrtc`. `MediaMtxIntegrationTest` posts an SDP offer to the real MediaMTX (`201` with the answer, `401` without the token) and `scripts/e2e.sh` checks WHEP too.
- `docs/API_CONTRACT.md` (§4, playback, `preparar`) and `docs/AGENT_CONTRACT.md` (`preparar`, live view v3 video: Constrained Baseline, about 15 fps, keyframe every 0.5 s).

### Changed
- MediaMTX `read` requests are allowed only over HLS or WebRTC, the readers the API can find and end; a read over RTSP, RTMP or SRT with a viewer token is now denied.

## [0.2.0] - 2026-10-09
### Added
- Release flow with release candidates (git flow, "build once, deploy many", tag at the end; Mermaid diagram in `docs/DEPLOYMENT.md`). `release.yml`, on pushes to `release/**` and `hotfix/**`: `build` pushes the image once (linux/amd64 + linux/arm64, final version inside) to GHCR as `x.y.z-rc.N` and `sha-<short commit>` (N never reuses an earlier candidate); `candidate` records it as the GitHub pre-release `vX.Y.Z-rc.N` with the commit, git tree hash, digest and build number; `verify` and `verify-e2e` (automatic, no environment: the API has no staging target) run that same digest in an ephemeral stack with PostgreSQL 18 and the end-to-end test; `pull-request` marks the candidate verified and opens or updates the pull request `release/x.y.z → main`. Nothing is deployed from a release branch.
- Publishing switches: organization Actions variables, explicit opt-in (`true` turns a stage on, unset means off): `ENABLE_API_IMAGE` (push of new images to GHCR, i.e. the release candidate) and `ENABLE_API_DEPLOY` (production through `te-tengo-infra`, which gates its deploy with the same variable). The summaries say when a stage is off. Table in `docs/DEPLOYMENT.md`.
- `produccion.yml` on pushes to `main`: finds the verified candidate whose tree hash equals `main`'s (fails when `main` differs from what was tested), sends `repository_dispatch` `desplegar-api` to `te-tengo-infra` with its digest and waits for that Deploy run (`.github/scripts/request-deploy.sh`; approval on infra's `produccion`), and only if production succeeded adds the tags `x.y.z` and `latest` to the same digest (no rebuild), creates the tag `vX.Y.Z` with its GitHub Release (CHANGELOG section and the candidate's digest) and opens the back-merge pull request `main → develop`. `rollback.yml` (manual) redeploys the digest of an earlier release `vX.Y.Z` the same way and moves `latest` back. `DISPATCH_TOKEN` also needs *Actions: Read* on `te-tengo-infra`.
- `scripts/smoke-image.sh <image>`: the image smoke test (empty PostgreSQL 18, healthy and ready, non-root), shared by the image workflow and the release verification. `scripts/e2e.sh` runs the API from a container image with `TT_E2E_IMAGEN`, and the `End-to-end` workflow is reusable with an `imagen` input.
- Web client (the app's PWA build, served from any configured origin, e.g. Cloudflare Pages):
  - CORS for `/api/**` with the origins of `TT_CORS_ORIGENES` (patterns allowed; off when empty, the default; `http://localhost:*` with the `local` profile), handled by Spring Security before authentication so preflights work; allowed headers `Authorization`, `Api-Version`, `Content-Type`, exposed `WWW-Authenticate`.
  - Push devices with `plataforma: "WEB"` (FCM web push tokens). `fcm` sends them a `webpush` block (title, body, `Urgency: high`, `fcm_options.link` to `TT_PWA_URL`, HTTPS only); `sns` reaches them only with `TT_SNS_ARN_WEB` and skips them with a warning otherwise; `simulador` skips them.
  - `TT_ENLACE_BASE` sets the base of the e-mailed links: `tetengo://app` by default, or the PWA's hash URL (`https://<pwa>/#/nueva-contrasena?token=…`, `…/#/invitacion/{token}`).
  - MediaMTX's `hlsAllowOrigins` can be set from the environment (`MTX_HLSALLOWORIGINS`; `TT_MEDIAMTX_HLS_ORIGENES` in `compose.yaml`, `*` by default).
- Container image: multi-stage `Dockerfile` (Temurin 25 JRE, non-root user, Spring Boot layers, liveness `HEALTHCHECK`) for `linux/arm64` and `linux/amd64`, and the `Container image` workflow, which builds and smoke-tests it on pull requests and pushes a test image to GHCR on a manual run (docs/DEPLOYMENT.md).
- End-to-end smoke test of the agent ↔ API contract, `scripts/e2e.sh`. It runs an isolated stack, seeds the demo household and plays a URFD fall clip through the real desktop agent, headless. It then asserts that the CAIDA alert is created, pushed and confirmed, and that its clip is available from Floci's S3. It prints a PASS/FAIL summary with the detection → push latency. The `End-to-end` workflow runs it on demand, weekly and on pull requests that touch the agent endpoints; it checks out the public desktop agent with its own token (`E2E_REPO_TOKEN` only if that repository becomes private). `compose.yaml` host ports are now overridable (`TT_POSTGRES_PUERTO`, `TT_FLOCI_PUERTO`), and the `local` profile follows `TT_FLOCI_PUERTO`.
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
- CI also runs on pushes to `release/**` and `hotfix/**`, so the release pull request opened by the workflow has its required checks.
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
