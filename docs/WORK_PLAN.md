# Work plan

The backend is built task by task from this checklist, in backlog-sprint order. An agent working autonomously follows **the loop** below until every task is checked or only blocked tasks remain.

## The loop
1. **Sync:** `git pull --rebase` if a remote branch exists. Read `AGENTS.md`, this file and `docs/BLOCKERS.md`.
2. **Pick** the first unchecked task (`- [ ]`) whose dependencies are checked.
3. **Read** its stories and criteria in `docs/references/PRODUCT_BACKLOG.md`, its endpoints in `docs/API_CONTRACT.md` (or `docs/AGENT_CONTRACT.md`), and the matching flow in `docs/references/diagrams/integracion.puml`.
4. **Implement** following `docs/USE_CASE_GUIDE.md`, tests first where practical. **Every acceptance criterion needs a test,** plus a two-household isolation test for household data.
5. **Verify:** `./gradlew spotlessApply test` must be green. Fix failures; never skip, disable or weaken a test to make it pass.
6. **Record:**
   - check the task here as `- [x]`, adding the commit's short description;
   - add a line under `[Unreleased]` in `CHANGELOG.md`.
7. **Commit:** one Conventional Commit in English, no co-author line, e.g. `feat(cuentas): register accounts and issue JWTs (US-01, US-02)`. Then **push**.
8. **Next:** go back to step 2 **without waiting for confirmation**.

**When something is missing,** such as a credential, an outside decision or a contract change:
- write it in `docs/BLOCKERS.md`;
- implement against a fake adapter or the current contract;
- mark the task `- [~]` with a note, and continue.

**Stop only** when no `- [ ]` remains. Then:
- run the full suite once more;
- update `docs/ARCHITECTURE.md` (module status);
- open or update a pull request summarizing the completed tasks and any blockers.

## Tasks

### Sprint 3
- [x] **T01 `cuentas` — US-01 register.** `POST /api/cuentas`; BCrypt passwords; unique email (`409 CORREO_EN_USO`); `400 VALIDACION` with `campos`. — *register accounts (US-01)*
- [x] **T02 `cuentas` — US-02 sessions.** — *sign in, refresh and sign out with JWTs and lockout (US-02)*
  - `POST /api/sesiones`, `POST /api/sesiones/refresco` and `DELETE /api/sesiones/actual`.
  - Issue JWT RS256 access tokens (claims `sub`, `hogar_id`, `rol`) and persisted refresh tokens.
  - Lock for 15 min after 5 consecutive failures (`423 CUENTA_BLOQUEADA {bloqueadaHasta}`).
  - The private key is configured like the public key; tests use `JwtDePrueba`.
- [x] **T03 `cuentas` — US-03 password recovery.** `POST /api/recuperaciones` (always `202`) and `.../confirmacion`. Links are valid for 30 min (`410 ENLACE_VENCIDO`). Email goes through a `NotificadorCorreo` port with a logging/fake adapter (SES later; see BLOCKERS). — *password recovery links valid for 30 min (US-03)*
- [x] **T04 `hogares` — US-04 household and older adult.** `POST /api/hogar` creates the household with the caller as `TITULAR` and returns a `Sesion` with `hogar_id`. One per account (`409 HOGAR_YA_REGISTRADO`). Also `GET /api/hogar`, `PUT /api/hogar/adulto-mayor`, `GET /api/hogares` and `POST /api/sesiones/hogar`. Owner-only checks return `403 SOLO_TITULAR`. — *household, older adult and household switching (US-04)*
- [x] **T05 `hogares` — US-05 consent.** `POST` and `GET /api/hogar/consentimiento`, storing the date and time. Publish a domain event that `camaras` listens to for the capture state. — *register the older adult's consent and enable capture (US-05)*
- [ ] **T06 `camaras` — align with the contract.**
  - Renaming becomes owner-only.
  - Add `pausadaHasta` and `deteccionConfiable` to `Camara`.
  - Keep `CamarasMultitenancyIntegrationTest` green.
- [ ] **T07 `camaras` — agent registration and capture state.** Endpoints from `docs/AGENT_CONTRACT.md`: registration with the installation credential issues the per-camera token; `GET /api/agente/estado-captura` reports consent and pauses (CA-05.2).
- [ ] **T08 `camaras` — US-07 connection status.**
  - `POST /api/agente/senal`.
  - A scheduled job marks cameras `DESCONECTADA` after the heartbeat timeout and sends push `CAMARA_DESCONECTADA`; `CAMARA_RECONECTADA` goes out on return (CA-07.2, CA-07.3).
  - Push goes through a `NotificadorPush` port with a fake adapter (SNS later).
- [ ] **T09 `hogares` — US-08 family.** Invitations (`POST /api/invitaciones`, public acceptance), `GET /api/familiares` and `DELETE /api/familiares/{usuarioId}`. The household–user membership table is global.
- [ ] **T10 `alertas` — agent events.**
  - `POST /api/agente/eventos`, idempotent by `eventoId`.
  - It creates or updates alerts: fall, unstable movement, unstable becoming a fall (CA-17.3), confirmation after 30 s (CA-13.1), recovery (CA-13.2) and unreliable detection (CA-15.3).
- [ ] **T11 `alertas` — US-16 and US-17 push alerts.** Push to every member device (`POST` and `DELETE /api/dispositivos`) with the payload types of the contract; retry on failure (CA-16.4). Add a test asserting the push is requested synchronously when the event is received (the less-than-10 s requirement).
- [ ] **T12 `alertas` — US-18 clips.** The agent uploads through `POST /api/agente/eventos/{id}/clip` (pre-signed PUT). The app reads through `GET /api/alertas/{id}/clip`, returning `404 CLIP_NO_DISPONIBLE` or `410 CLIP_ELIMINADO`. Object storage goes through a port with a fake adapter.
- [ ] **T13 `alertas` — list and detail.** `GET /api/alertas` with filters and paging, and `GET /api/alertas/{id}` (CA-16.4 visibility, CA-25.1 to CA-25.3).

### Sprint 4
- [ ] **T14 `hogares` — US-09 revocation.** `DELETE /api/hogar/consentimiento`: stop capture, schedule clip deletion, push `DATOS_ELIMINADOS` when done.
- [ ] **T15 `alertas` — US-19 alert state.** `POST /api/alertas/{id}/atencion` and `/falsa-alarma`; push `ALERTA_ATENDIDA` to the other members.
- [ ] **T16 `hogares` — US-10 alert routing.** `GET` and `PUT /api/hogar/aviso`: 3, 5 or 10 min, default 5, single-member case.
- [ ] **T17 `alertas` — US-20 escalation.** Scheduled job, idempotent: `ALERTA_ESCALADA` or `SIN_CONTACTO_SECUNDARIO`.
- [ ] **T18 `monitoreo` — US-22 pauses.** `POST` and `DELETE /api/camaras/{id}/pausa` with the contract durations (`HASTA_MANANA` = next 07:00 America/Lima); automatic resume with push `PAUSA_FINALIZADA`.
- [ ] **T19 `monitoreo` — US-23 live view sessions.** `POST /api/camaras/{id}/vista-en-vivo` (`409` when disconnected or paused) and `DELETE /api/vista-en-vivo/{sesionId}`. The WSS relay follows the contract proposal (see BLOCKERS).
- [ ] **T20 `monitoreo` — US-24 access log.** `GET /api/accesos-vista-en-vivo`, newest first.
- [ ] **T21 `alertas` — US-21 recovery notice.** Push `SE_LEVANTO`; none when the fall is confirmed (CA-21.2).
- [ ] **T22 `historial` — US-26 recordings.** Download disposition and the retention job.
- [ ] **T23 `historial` — US-27 weekly summary.** `GET /api/resumen-semanal`: false alarms excluded from falls, trend against the previous week.
- [ ] **T24 Hardening.**
  - OpenAPI annotations on every endpoint (springdoc).
  - JaCoCo report ≥ 80 % line coverage on `domain` and `application`.
  - Row-level security for household tables (optional, ADR 0002).
  - A final review of `docs/API_CONTRACT.md` against the code.
