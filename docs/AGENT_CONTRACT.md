# Household agent contract (Te Tengo Captura)

> The team decided to process video on the household PC (MediaPipe on CPU), so the agent sends **events**, not video (ADR 0004; the only video it sends is live view, published to MediaMTX while a family member watches; the change request to the project charter is pending). Event names and fields come from the validated classifier in `te-tengo-desktop-pywebview` (`docs/classification-spec.md`).

## Authentication
Two ways, both supported (the change request «PCs, cámaras y datos de movimiento» is pending the advisor's approval, so nothing of the first one changes):
- **Installation (until now):** the project team installs the agent with a fixed configuration (household, webcam and room) and an installation credential. At startup the agent registers its camera and receives a **per-camera JWT** with the claims `hogar_id`, `camara_id` and `rol = AGENTE`. The token only works for that household (see `MULTITENANCY.md`). Since PCs exist, the installation runs on a PC of origin `INSTALACION` named «PC de la casa», which the owner can remove from the app; then its credential answers `401 CREDENCIAL_INVALIDA`.
- **PC link (US-28):** a freshly installed PC asks for a code, the owner approves it in the app, and the PC gets its own **access token** (`hogar_id`, `pc_id`, `rol = AGENTE`, 1 h) and a **refresh token** that rotates on every use. See "PC link" below.

Every agent token is checked on every request: the tokens of a PC that left the household (US-31), and of its cameras, answer **`401 PC_DESVINCULADA`** at once, before their expiry, also on the control channel's handshake. On `401 PC_DESVINCULADA` the agent stops capturing, deletes its credentials (tokens, and the installation credential if any) and shows the link code again (CA-31.1, CA-31.2).

## Endpoints (version 1, header `Api-Version: 1`)
| Method and path | Purpose | Stories |
|---|---|---|
| `POST /api/agente/camaras/registro` | Registers the camera when the agent starts, using the installation credential, and returns the camera token | US-06 (CA-06.1) |
| `GET /api/agente/estado-captura` | Current consent and pauses: the agent does not process without consent or during a pause | US-05 (CA-05.2), US-22 |
| `POST /api/agente/senal` | Periodic heartbeat; updates the time of the last signal | US-07 |
| `POST /api/agente/eventos` | Detected event (body below); idempotent by `eventoId` | US-11 to US-21 |
| `POST /api/agente/eventos/{eventoId}/clip` | Returns a pre-signed S3 PUT URL to upload the 6 s + 6 s clip | US-18 |
| `GET /api/agente/configuracion` | Current classification thresholds and agent version, so agents can update themselves | — |
| `POST /api/agente/vinculacion` | PC link: asks for a code (RFC 8628 device authorization; no token) | US-28 |
| `POST /api/agente/token` | PC link: polls for the tokens, and renews them (RFC 6749 token endpoint; no token) | US-28 |
| `POST /api/agente/token/revocacion` | PC link: revokes the refresh token, which unlinks the PC (RFC 7009; no token) | US-31 |
| `GET /api/agente/pc` | The PC's link: household, older adult, who approved it, consent (PC token) | US-28 |
| `POST /api/agente/pc/senal` | The PC's heartbeat, with its version and system (PC token) | US-30 |
| `DELETE /api/agente/pc` | «Desvincular esta PC» (PC token); the family is notified | US-31 |

## Detected event
```json
{
  "eventoId": "0192f6e4-...",
  "tipo": "caida",
  "ocurridoEn": "2026-10-07T15:04:31.250Z",
  "parametros": { "angulo_grados": 22.1, "razon_ancho_alto": 1.8, "velocidad": 0.41 }
}
```
`tipo` is one of:
- `caida`
- `caida_confirmada` (30 s on the floor)
- `movimiento_inestable`
- `recuperacion`
- `deteccion_no_confiable` (5 min without seeing the person); its `ocurridoEn` becomes the camera's `noConfiableDesde` (API contract)

**Backend rules:**
- **Disconnection:** if no heartbeat arrives in time, or a heartbeat reports `webcamConectada: false`, the camera becomes `DESCONECTADA` and push `CAMARA_DESCONECTADA` is sent (CA-07.2). The heartbeat timeout **[implementation choice]** is 3 missed heartbeats, with a heartbeat every 30 s.
- **Latency:** a fall must reach the family as a push **in less than 10 s** (CA-11.3, CA-16.1).

## Request and response bodies
> **[implementation choice]** The backlog fixes what each endpoint does, not its JSON. These shapes are shared by `te-tengo-general-api` and `te-tengo-desktop-pywebview`; change them in both repositories at once. JSON fields are camelCase, times are ISO-8601 UTC, errors are RFC 9457 `ProblemDetail` with `codigo`.

**Registration** — `POST /api/agente/camaras/registro` (no bearer token)
```json
{ "credencialInstalacion": "…", "nombreHabitacion": "Sala", "versionAgente": "1.0.0" }
```
→ `200 {camaraId, hogarId, token, expiraEn, nombreHabitacion}`.
- The first registration creates the camera with the room name of the installation (CA-06.1). Registering again with the same credential returns the same `camaraId`, a new token, and the room name stored in the backend (the app can rename it, CA-06.2).
- `versionAgente` is optional; the backend only logs it.
- The token lasts 30 days; the agent registers again at startup or when it gets a `401` (see "Token expiry").
- The project team creates the credential with `scripts/create-installation.sh <hogar-id>` (only its SHA-256 is stored).
- Errors: `401 CREDENCIAL_INVALIDA` for an unknown credential; `400 VALIDACION` with `campos.credencialInstalacion` or `campos.nombreHabitacion` when one is missing.

**Capture state** — `GET /api/agente/estado-captura`, and also the response of every heartbeat
```json
{ "capturaPermitida": false, "motivo": "EN_PAUSA", "pausadaHasta": "2026-10-07T20:00:00Z", "nombreHabitacion": "Sala" }
```
`motivo` is `SIN_CONSENTIMIENTO` (no current consent; it wins over a pause), `EN_PAUSA` or `null` (allowed); `pausadaHasta` is set only during an active pause. The agent does not capture nor classify while `capturaPermitida` is `false` (CA-05.2, CA-22.1) and resumes on its own when it turns `true` (CA-22.3).

**Heartbeat** — `POST /api/agente/senal` every 30 s
```json
{ "webcamConectada": true, "deteccionConfiable": true, "versionAgente": "1.0.0" }
```
→ `200 EstadoCaptura` (the body above). A missing body counts as `webcamConectada: true`; `deteccionConfiable` and `versionAgente` are informational (unreliable detection is reported with the `deteccion_no_confiable` event). With `webcamConectada: false` the backend treats the camera as disconnected right away (CA-07.2), without waiting for missed heartbeats, and sends push `CAMARA_DESCONECTADA` once; the next heartbeat with `webcamConectada: true` brings it back (`CAMARA_RECONECTADA`, CA-07.3).

**Event** — `POST /api/agente/eventos` with the body in "Detected event" → always `202 {eventoId, alertaId | null}`.
- A repeated `eventoId` returns `202` with the same body and changes nothing (the agent retries after network errors).
- `alertaId` is `null` when the event creates or updates no alert: `deteccion_no_confiable`, a recovery with no open fall, or an event received without a current consent or during a pause (it is stored, but ignored; there is no `409`).
- An unknown `tipo` answers `400 VALIDACION`.

**Clip upload** — `POST /api/agente/eventos/{eventoId}/clip` (the body is optional)
```json
{ "contentType": "video/mp4", "tamanoBytes": 734003 }
```
→ `201 {urlSubida, cabeceras: {nombre: valor}, expiraEn}`: a pre-signed URL valid for 10 minutes. The agent then sends `PUT urlSubida` with exactly those headers (e.g. `Content-Type`; the map may be empty) and the MP4 body. The clip belongs to the alert the event created or updated; the app sees it once the storage has the object. Errors: `404 EVENTO_NO_ENCONTRADO` if the event is unknown or created no alert.

**Configuration** — `GET /api/agente/configuracion` → `200 {versionAgente, umbrales: {…}}`, where `umbrales` uses the field names of `Umbrales` in the detection package (`docs/classification-spec.md`); unknown or missing fields keep the agent's local values.
- Both come from the backend configuration: `versionAgente` from `tetengo.agente.version-publicada` (`TT_AGENTE_VERSION_PUBLICADA`, default `0.2.0`, the current agent release; the agent only logs a difference) and `umbrales` from the `tetengo.agente.umbrales` map, **empty by default**, so every agent keeps the thresholds calibrated at installation until the team publishes calibrated values.

**Token expiry:** with an installation credential, any `401` other than `CREDENCIAL_INVALIDA` and `PC_DESVINCULADA` makes the agent register again with its installation credential (an expired or invalid token answers `401 SESION_EXPIRADA`). A linked PC renews its tokens instead (see "PC link"). Agent tokens only open `/api/agente/**`; family members' tokens get `403` there.

**Which token for which endpoint:** endpoints of one camera (`senal`, `estado-captura`, `eventos`, `clip`, and the control channel of a camera) need a per-camera token; a PC's own token gets `403 TOKEN_DE_CAMARA_REQUERIDO`. The `/api/agente/pc` endpoints need the PC's own token; any other gets `403 TOKEN_DE_PC_REQUERIDO`.

## Live view (MediaMTX)
Decided by the project owner on 2026-10-07; it replaces the WebSocket JPEG relay (binary frames are no longer accepted). The video goes to **MediaMTX**, the system's live streaming service; the API only controls the agent and authorizes MediaMTX (ADR 0007 of the backend).

**Control channel:** `wss://…/api/agente/transmision`, opened with `Authorization: Bearer <camera token>` in the handshake. A linked PC also opens one with its **own access token**, so it hears `desvincular` even while it has no camera in use; that channel only carries messages about the PC.
- The agent keeps one connection open, and reconnects with backoff. A `401` follows the "Token expiry" rule above: register again. A new connection of the same camera replaces the previous one.
- Messages are **text JSON only**, from the API to the agent:

| Message | When |
|---|---|
| `{"preparar":true}` | Live view v3: a member opened the camera screen in the app (`POST /api/vista-en-vivo/preparar`), so a live view may start soon. Never sent while the camera is already transmitting. Agents that do not know it ignore it (it carries neither `transmitir` nor `modo`) |
| `{"transmitir":true,"urlPublicacion":"rtsp://…/camaras/<camaraId>","usuario":"agente","clave":"<publish token>","modo":"VIDEO"}` | The first open live view session of the camera starts. Also on every connection while sessions are open, with a **new** `clave` (the previous one stops working) |
| `{"modo":"VIDEO_CON_POSTURA"}` | The mode changes during a transmission |
| `{"transmitir":false}` | The last session ends or expires, the camera is paused, or the consent is revoked. On pause and revocation the API also disconnects the publisher through MediaMTX |
| `{"desvincular":true}` | The PC was removed from the household (US-31). Sent on the PC's own channel and on each of its cameras' channels, which the API then closes with code `4001`. The agent stops capturing, deletes its credentials and shows the link code (CA-31.1) |

**Preparing (`preparar`, live view v3):** the agent warms up **only what stays on the PC**, so that the publication starts at once on `transmitir`: open the capture at the live view rate in memory, create the H.264 encoder, resolve the MediaMTX host and, if it can be done without publishing, open the TLS connection. **No frame leaves the PC before `transmitir`** (privacy rule of the product: video leaves the PC only during a live view); an RTSP publish session cannot be opened without sending media, so it is not opened early. The warm state lasts at most 60 s (implementation choice) and is discarded on timeout, on `{"transmitir":false}`, on a pause, on a revoked consent (`capturaPermitida: false`) and when the agent stops. A new `preparar` restarts the 60 s.

**Publishing:**
- The agent publishes to `urlPublicacion` with `usuario` and `clave` as the RTSP credentials.
  - Locally the URL is RTSP over TCP: `rtsp://localhost:8554/camaras/<camaraId>`.
  - Production may send RTSPS (`rtsps://<host>:8322/…`): the agent opens whatever URL it gets.
- Video: H.264, no audio, 480p, low latency (`zerolatency`), with PyAV. Live view v3: **Constrained Baseline** profile (no B-frames: every browser and WebRTC stack decodes it, and MediaMTX offers H.264 over WebRTC only as `profile-level-id=42e01f`), about **15 fps** at a constant rate taken from the webcam independently of the detection rate (detection keeps its own 480p, 8 fps sampling), and a **keyframe every 0.5 s** (GOP = fps / 2), the first frame of every publication being a keyframe: WebRTC and LL-HLS viewers start at a keyframe. Earlier agents publish the 8 fps detection frames with a GOP of about 1 s; MediaMTX serves either.
- The app plays the stream over WebRTC (WHEP) first and LL-HLS as the fallback; both come from the same publication, so nothing changes for the agent.
- The agent stops publishing at once on `{"transmitir":false}`, and also whenever its own capture state does not allow capture (`capturaPermitida: false`).
- MediaMTX asks the API before every publish (`POST /api/interno/mediamtx/autorizar`, internal). The API allows it only for the camera's own path, user `agente` and the current `clave`, while capture is allowed.

**Modes** (`modo`), drawn by the agent on the frames it publishes, from the MediaPipe landmarks it already computes. A mode applies to the camera's stream, so every viewer sees the same one (implementation choice):
- `VIDEO` (default): the camera frame.
- `VIDEO_CON_POSTURA`: the camera frame with the skeleton drawn on top.
- `SOLO_POSTURA`: the skeleton on a plain neutral background, in the brand colours. **No camera pixels**: no image of the home leaves the PC.

## PC link (US-28, US-30, US-31)
OAuth 2.0 Device Authorization Grant (RFC 8628) with rotating refresh tokens (RFC 9700), implemented by the API (ADR 0009). The three OAuth endpoints follow the RFCs, not the rest of this contract: **requests are `application/x-www-form-urlencoded`**, responses are snake_case JSON with `Cache-Control: no-store`, and **errors are `{"error": "…", "error_description": "…"}`** (RFC 6749 §5.2), HTTP 400 except `invalid_client` (401). No bearer token is sent to them. `client_id` is always `te-tengo-captura`.

**1. Ask for a code** — `POST /api/agente/vinculacion`
```
client_id=te-tengo-captura&nombre_pc=Sala-Rosa&sistema_operativo=Windows%2011%20Home&version_agente=0.5.0
```
- `nombre_pc` (required, 1–60 characters; the computer's name), `sistema_operativo` (optional, up to 60) and `version_agente` (optional, up to 40) are what the app shows before approving (CA-28.4). `scope` is ignored.
- → `200`
```json
{ "device_code": "<43 chars, secret>", "user_code": "KQWT-MXHD",
  "verification_uri": "https://app.tetengo.reqsai.tech/vincular",
  "verification_uri_complete": "https://app.tetengo.reqsai.tech/vincular?codigo=KQWT-MXHD",
  "expires_in": 600, "interval": 5 }
```
- The PC shows a QR of `verification_uri_complete`, the `user_code` in large type, `verification_uri`, and a countdown of `expires_in` (D-28.1). **`device_code` is a secret: never show it, log it or write it to disk.**
- When the code expires the PC asks for a new one on its own (CA-28.3). Errors: `invalid_request` (missing `nombre_pc` or `client_id`), `invalid_client`.

**2. Poll** — `POST /api/agente/token` every `interval` seconds
```
grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=<device_code>&client_id=te-tengo-captura
```
| Answer | Meaning | The PC |
|---|---|---|
| `400 {"error":"authorization_pending"}` | Nobody approved it yet | keeps polling every `interval` |
| `400 {"error":"slow_down"}` | Polled sooner than the interval | adds 5 s to its interval, for good (RFC 8628 §3.5) |
| `400 {"error":"access_denied"}` | «No reconozco esta PC» in the app | shows D-28.5 and «Mostrar otro código» |
| `400 {"error":"expired_token"}` | The 10 minutes ran out | asks for a new code (step 1) |
| `400 {"error":"invalid_grant"}` | Unknown or already used device code | asks for a new code |
| `200` (below) | Approved | stores the tokens and stops polling |

```json
{ "access_token": "<JWT>", "token_type": "Bearer", "expires_in": 3600,
  "refresh_token": "<43 chars>", "pc_id": "0192…", "hogar_id": "0192…" }
```
- **Store both tokens in the operating system's credential store** (Windows Credential Manager), never in files (change request 2.1). The device code works once.

**3. Use and renew.**
- The access token (claims `sub` = `pc_id`, `hogar_id`, `pc_id`, `rol = AGENTE`) lasts 1 h **[implementation choice]**; it opens the `/api/agente/pc` endpoints and the PC's control channel. The cameras of a linked PC use per-camera tokens with the same claims plus `camara_id` (camera registration for linked PCs comes with US-29).
- Renew it before it expires, or on `401 SESION_EXPIRADA`, with `POST /api/agente/token`:
```
grant_type=refresh_token&refresh_token=<refresh_token>&client_id=te-tengo-captura
```
  → `200` with the same body as step 2, **including a new `refresh_token`**: replace the stored one in the same step. Each refresh token works **once**: presenting an old one again revokes every token of the PC and unlinks it (RFC 9700 §4.14.2), so never retry a refresh with a token that already got an answer.
- Each refresh token lasts 90 days: a PC that renews at least once every 90 days stays linked, through restarts, network changes and outages under 90 days (CA-28.7). After 90 days without connecting, or once unlinked, renewing answers `invalid_grant`: delete the credentials and show the link code (step 1), with the banner «Esta PC estuvo más de 90 días sin conexión» when the PC knows it was offline that long.

**The PC's own endpoints** (its access token):
- `GET /api/agente/pc` → `200 PcDelAgente = {pcId, hogarId, nombre, nombreAdultoMayor, vinculadaEn, vinculadaPor | null, consentimientoVigente}`. D-28.4: «Esta PC quedó vinculada a la casa de Rosa Huamán · Aprobada por Carmen a las 10:31»; with `consentimientoVigente: false` the PC says it waits for the consent and sends no video (CA-28.1). `vinculadaPor` is the approver's name, null for installations.
- `POST /api/agente/pc/senal` every 30 s, like the cameras' heartbeat, with an optional `{versionAgente, sistemaOperativo}` → `200 PcDelAgente`. It keeps the PC «En línea» in the app even with no camera in use (CA-30.2); camera heartbeats count too.
- `DELETE /api/agente/pc` → `204`: «Desvincular esta PC» (CA-31.3). The API revokes the PC's tokens, retires its cameras and sends push `PC_DESVINCULADA` to the family; the PC then deletes its credentials and shows the link code. Revoking the refresh token does the same and works without a valid access token:

**Unlink with the refresh token** — `POST /api/agente/token/revocacion` (RFC 7009)
```
token=<refresh_token>&token_type_hint=refresh_token&client_id=te-tengo-captura
```
→ always `200` with an empty body, also for an unknown or already revoked token (RFC 7009 §2.2). Errors: `invalid_request`, `invalid_client`.

**Removed from the app (CA-31.1, CA-31.2).** The PC hears it as `{"desvincular":true}` on its control channels if it is online, or as `401 PC_DESVINCULADA` (or `invalid_grant` when renewing) at its next request. Either way: stop capturing at once, delete the credentials and show the link code, with D-31.2 («Esta PC se quitó de la casa de Rosa Huamán»).

