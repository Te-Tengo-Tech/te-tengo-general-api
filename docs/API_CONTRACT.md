# Te Tengo API contract (v1)

> **Shared contract between `te-tengo-general-api` (implements it) and `te-tengo-mobile-flutter` (consumes it).** An identical copy lives in both repositories at `docs/API_CONTRACT.md`. Neither side may change it unilaterally: if a task needs a change, record it in `docs/BLOCKERS.md` and keep implementing against the current version.
>
> Every business value comes from the product backlog (`docs/references/PRODUCT_BACKLOG.md`, cited as `CA-xx.y`). Values marked **[implementation choice]** are not in the backlog and can be adjusted by the team.

## Conventions
- **Base path** `/api`. **Version header** `Api-Version: 1` (optional; defaults to 1).
- **Clients:** the native app (Android, iOS) and its web build, a PWA served from another origin (e.g. Cloudflare Pages, `https://te-tengo.pages.dev/app/`, or a custom domain; the backend has no default origin).
  - **CORS** for `/api/**` is on only for the origins the backend is configured with (`TT_CORS_ORIGENES`; off by default, `http://localhost:*` locally). Allowed request headers: `Authorization`, `Api-Version`, `Content-Type`; exposed: `WWW-Authenticate`; no cookies or credentials; preflight answers are cached for 1 h **[implementation choice]**. A preflight from another origin, or with another header, answers `403` without CORS headers.
  - **E-mailed links** open the app's `/nueva-contrasena?token={token}` and `/invitacion/{token}` routes under the configured base: `tetengo://app/…` for the native app (default), or the PWA's hash URL, e.g. `https://te-tengo.pages.dev/app/#/nueva-contrasena?token=…` and `…/app/#/invitacion/{token}`.
- **Auth:** `Authorization: Bearer <accessToken>` (JWT RS256). Claims:
  - `sub`: user id;
  - `hogar_id`: active household, the tenant;
  - `rol`: `TITULAR` (owner) or `INVITADO` (invited family member).
  - Endpoints marked **owner** return `403 SOLO_TITULAR` for `INVITADO` (CA-08.4).
  - Per the prototype, an invited member **can** mark alerts, pause and resume the camera, and watch live. They cannot change the profile, the consent, the camera name, the family or the alert order.
- **Multi-tenancy:** the household always comes from the token. **The client never sends a household id in paths or bodies**, except when switching households.
- **IDs and times:** UUID v7 strings. Times are ISO-8601 in UTC (`2026-10-07T15:04:31Z`); the app shows them in local time.
- **Resource and field names** are Spanish: they are the domain language of the thesis architecture.
- **Errors:** RFC 9457 `application/problem+json` with `title`, `status`, `detail` and `instance`, plus:
  - `codigo`: a stable code the app branches on;
  - extra properties where stated.
  - Validation failures use `400 VALIDACION` with `campos: { "<field>": "<message>" }`, so the app can highlight the missing field (CA-01.3, CA-04.3).
  - A missing, expired or invalid access token on any protected endpoint answers `401 SESION_EXPIRADA` (with the `WWW-Authenticate: Bearer` header); the app refreshes its tokens only on that code.

## 1. Accounts and sessions (`cuentas`) — US-01, US-02, US-03
| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `POST /api/cuentas` | public | `{correo, contrasena, nombre}` → `201 {id, correo, nombre}` (CA-01.1) | `409 CORREO_EN_USO` (CA-01.2) · `400 VALIDACION` (CA-01.3) |
| `POST /api/sesiones` | public | `{correo, contrasena}` → `200 Sesion` (CA-02.1) | `401 CREDENCIALES_INVALIDAS` (CA-02.2) · `423 CUENTA_BLOQUEADA {bloqueadaHasta}`: 5 consecutive failures lock the account for 15 min (CA-02.3) |
| `POST /api/sesiones/refresco` | public | `{tokenRefresco}` → `200 Sesion` | `401 SESION_EXPIRADA` |
| `DELETE /api/sesiones/actual` | user | → `204`. Revokes the refresh token; a new login is required (CA-02.4) | — |
| `POST /api/recuperaciones` | public | `{correo}` → `202`, **always the same response** whether or not the account exists (CA-03.1, CA-03.2) | — |
| `POST /api/recuperaciones/confirmacion` | public | `{token, nuevaContrasena}` → `204` | `410 ENLACE_VENCIDO`: links are valid for 30 min (CA-03.3) |

`Sesion = {tokenAcceso, tokenRefresco, expiraEn, usuario: {id, nombre, correo}, hogarId | null, rol | null}`
- `hogarId` is null until the account creates or joins a household.
- Token lifetimes **[implementation choice]**: access token 60 min, refresh token 30 days.

## 2. Household, older adult and consent (`hogares`) — US-04, US-05, US-09
| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `POST /api/hogar` | user without a household | `{adultoMayor: {nombre, edad, direccion, convivencia, telefono?}}` → `201 Sesion`. Creates the household with the caller as `TITULAR` and returns tokens that carry its `hogar_id` (CA-04.1) | `409 HOGAR_YA_REGISTRADO`: one older adult per account (CA-04.2) · `400 VALIDACION` (CA-04.3) |
| `GET /api/hogar` | member | → `200 {hogarId, adultoMayor, rol, consentimiento: Consentimiento \| null, dispositivosActivos, eliminacion: Eliminacion \| null}`. `dispositivosActivos`: how many push devices of the household's members are active (§7); `0` means **nobody in the family can receive alerts** on a phone, and the app warns about it. An older backend does not send the field; the app then shows no warning. `eliminacion`: the deletion of the recordings after the latest revocation (below); null if the consent was never revoked | — |
| `PUT /api/hogar/adulto-mayor` | owner | `{nombre, edad, direccion, convivencia, telefono?}` → `200 adultoMayor`. Replaces the whole profile: an omitted `telefono` clears it | `400 VALIDACION` |
| `GET /api/hogares` | user | → `200 [{hogarId, nombreAdultoMayor, rol}]`, the households the user belongs to | — |
| `POST /api/sesiones/hogar` | user | `{hogarId}` → `200 Sesion` for that household | `403 SIN_MEMBRESIA` |
| `POST /api/hogar/consentimiento` | owner | `{otorgadoPor, aceptadoPorAdultoMayor: true, vistaEnVivoAceptada: true}` → `201 Consentimiento`. Stores the date and time (CA-05.3); camera capture may start (CA-05.1) | `422 CONSENTIMIENTO_NO_ACEPTADO`: both flags must be true (CA-05.4) |
| `GET /api/hogar/consentimiento` | member | → `200 Consentimiento` | `404 SIN_CONSENTIMIENTO` |
| `DELETE /api/hogar/consentimiento` | owner | → `202 {eliminacionProgramada: true, clips}`. Stops capture and schedules deletion of every recording (CA-09.1); `clips`: how many recordings it deletes, counted at the revocation. A push `DATOS_ELIMINADOS` is sent when done (CA-09.3), and `GET /api/hogar` `eliminacion` tells the progress. An older backend does not send `clips` | `404 SIN_CONSENTIMIENTO`: there is no current consent to revoke |

`adultoMayor = {nombre, edad, direccion, convivencia, telefono | null}`
- `eliminacion.movimientos` (US-37): movement files to delete or deleted with the recordings; a monitoring consent revocation also deletes the cloud movements and ends the movement consent (§11). An older backend does not send it.
- `edad`: whole years, required, from 50 to 120. The prototype's profile form asks for «Edad» and rejects other values with «Escribe una edad válida, en años.»; the app shows «Rosa Huamán, 78 años». Households registered before this field existed answer `edad: null` until the owner saves the profile again.
- `telefono`: optional; the number that «Llamar a Rosa · 987 654 321» dials from the alert (prototype alert screen). Digits and spaces with an optional leading `+`, 6 to 20 characters **[implementation choice]**; a blank value is stored as null.
- `convivencia` values, from the prototype's profile screen:
  - `SOLO`: «Vive solo(a)»;
  - `CON_FAMILIAR`: «Vive conmigo», the older adult lives with the account owner;
  - `CON_CUIDADOR`: «Vive con otro cuidador».
- `Consentimiento = {otorgadoEn, otorgadoPor, registradoPor: {id, nombre}, vistaEnVivoAceptada, vigente}`
- `Eliminacion = {estado: "PROGRAMADA" | "TERMINADA", clips, programadaEn, terminadaEn | null}`: the deletion of every recording after the **latest** revocation (CA-09.1, CA-09.3).
  - `PROGRAMADA` from the moment the revocation answers `202` until every recording is deleted; then `TERMINADA`, at the same time as the push `DATOS_ELIMINADOS`. It stays `TERMINADA` after a new consent, until the next revocation.
  - `clips`: recordings still to delete (`PROGRAMADA`) or deleted (`TERMINADA`). `programadaEn`: when the consent was revoked. `terminadaEn`: when the recordings were deleted.
  - **The app polls it** on the revocation screen: a push does not reach the app's code when the app is in the background, nor a PWA whose window is hidden. Push or poll, whichever comes first, completes the screen. An older backend does not send the field; the app then waits for the push only.

## 3. Family and alert routing (`hogares`) — US-08, US-10
| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/familiares` | member | → `200 [{usuarioId, nombre, correo, rol}]` | — |
| `POST /api/invitaciones` | owner | `{correo}` → `201 {id, correo, expiraEn}`. Emails a link to create access (CA-08.1) | `409 YA_ES_FAMILIAR` |
| `POST /api/invitaciones/{token}/aceptacion` | public | `{nombre, contrasena}` (new account) **or** a bearer token (existing account) → `201 Sesion` as `INVITADO` of that household (CA-08.2) | `410 INVITACION_VENCIDA` |
| `DELETE /api/familiares/{usuarioId}` | owner | → `204`. Removes access to alerts (CA-08.3) | `409 NO_SE_PUEDE_RETIRAR_TITULAR` |
| `GET /api/hogar/aviso` | member | → `200 {principalId, secundarioId \| null, esperaMinutos}`. Defaults to 5 min (CA-10.3); with a single member, `secundarioId` is null (CA-10.4) | — |
| `PUT /api/hogar/aviso` | owner | `{principalId, secundarioId \| null, esperaMinutos: 3 \| 5 \| 10}` → `200` (CA-10.1, CA-10.2) | `422 ESPERA_INVALIDA` · `422 CONTACTO_NO_ES_FAMILIAR` |

## 4. Cameras and monitoring (`camaras`, `monitoreo`) — US-06, US-07, US-15, US-22, US-23, US-24
`Camara = {id, nombreHabitacion, estadoConexion: "EN_LINEA" | "DESCONECTADA", ultimaSenal | null, pausadaHasta | null, deteccionConfiable: boolean, instaladaEn, noConfiableDesde | null, pcId | null, ambienteId | null, nombreDispositivo | null}`
- `nombreHabitacion` is the name of the camera's room (`ambienteId`, §9). **Terms:** the backlog and this API say «ambiente»; the apps keep labelling it «habitación» (design session of 2026-10-10), so `nombreHabitacion` keeps its name.
- `nombreDispositivo`: the webcam's name as the PC shows it («Webcam USB HD»); null for cameras installed by the project team.
- `pcId`: the PC the camera is connected to (§9). Every camera has one; it is null only for rows older than PCs that had no installation. An older backend does not send it.
- Cameras of a removed PC (US-31) are no longer listed and answer `404 CAMARA_NO_ENCONTRADA`; their alerts keep their room name.
- `instaladaEn`: when the project team installed the camera, that is, the agent's first registration (CA-06.1). The camera header shows it as «Instalada el».
- `noConfiableDesde`: when detection stopped being reliable, the time of the agent's `deteccion_no_confiable` event (CA-15.3); null while `deteccionConfiable` is true. The camera detail shows «La detección no es confiable desde las 10:36».

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/camaras` | member | → `200 [Camara]`, the cameras installed in the household, with the room name set at installation (CA-06.1, CA-07.1, CA-22.2) | — |
| `PATCH /api/camaras/{id}` | owner | `{nombreHabitacion}` (1–40 chars) **or** `{ambienteId}` → `200 Camara`; later alerts use the new name (CA-06.2). By name: the room of that name if there is one (ignoring case); otherwise, if the camera is alone in its room, the room is renamed; otherwise a new room is created. By id: the camera moves to that room (US-29) | `422 CAMARA_NOMBRE_VACIO` (CA-06.3) · `422 CAMARA_NOMBRE_MUY_LARGO` · `409 AMBIENTE_OCUPADO {ambiente, camara}`: the room has another camera (CA-29.3) · `404 AMBIENTE_NO_ENCONTRADO` · `404 CAMARA_NO_ENCONTRADA` · `400 VALIDACION` with neither field |
| `POST /api/camaras/{id}/pausa` | member | `{duracion: "MIN_30" \| "HORA_1" \| "HORAS_2" \| "HASTA_MANANA"}`, the options of the prototype's pause screen; `HASTA_MANANA` means the next 07:00 in the household time zone, `America/Lima` **[implementation choice]**. → `200 Camara` with `pausadaHasta`. Stops capture and detection (CA-22.1); resumes automatically and sends push `PAUSA_FINALIZADA` (CA-22.3) | `422 DURACION_INVALIDA` |
| `DELETE /api/camaras/{id}/pausa` | member | → `200 Camara` (resume now) | — |
| `POST /api/vista-en-vivo/preparar` | member | `{camaraId}` → `204`. The camera (or live view) screen opened: the camera's agent warms up so a live view opened next starts sooner (live view v3). No session is opened or recorded and nothing is streamed. Calling it is optional and may be repeated; nothing happens while the camera already streams | `400 VALIDACION` with `campos.camaraId` · `404 CAMARA_NO_ENCONTRADA` · `409 CAMARA_DESCONECTADA` · `409 CAMARA_EN_PAUSA {pausadaHasta}` · `409 SIN_CONSENTIMIENTO`: the same rules as opening a session |
| `POST /api/camaras/{id}/vista-en-vivo` | member | `{alertaId \| null, modo?}` → `201 {sesionId, urlTransmision, urlWebrtc, expiraEn, modo}` (CA-23.1, CA-23.2). `alertaId`: the alert the live view is opened from, which must be of that camera. `modo`: see "Live view" below; omitted keeps the camera's current mode (`VIDEO` when nobody is watching) | `409 CAMARA_DESCONECTADA` (CA-23.3) · `409 CAMARA_EN_PAUSA {pausadaHasta}` (CA-23.4) · `409 SIN_CONSENTIMIENTO`: without a current consent the camera does not stream (CA-05.2) · `400 VALIDACION` with `campos.alertaId` (alert of another camera or unknown) or `campos.modo` |
| `PATCH /api/vista-en-vivo/{sesionId}` | member | `{modo}` → `200 {sesionId, modo}`. Only the session's viewer, while it is open; the mode applies to the camera's stream, so every viewer sees it | `400 VALIDACION` with `campos.modo` · `404 SESION_NO_ENCONTRADA`: unknown, of another member, or already ended **[implementation choice]** |
| `DELETE /api/vista-en-vivo/{sesionId}` | member | → `204`. Ends the session and records who watched, when it started and how long (CA-24.1). Closing someone else's session, or one that already ended, changes nothing | — |
| `GET /api/accesos-vista-en-vivo` | member | → `200 [{usuario: {id, nombre}, inicio, duracionSegundos, desdeAlerta: boolean, camaraId, vistaPrevia: boolean}]`, newest first (CA-24.2). Empty list when none (CA-24.3). `vistaPrevia`: a preview of a webcam before assigning it a room (§9). An older backend does not send `camaraId` nor `vistaPrevia` | — |

**Live view (MediaMTX).** Decided by the project owner on 2026-10-07; it replaces the earlier WebSocket JPEG relay proposal.
- **Playback:** MediaMTX, the system's live streaming service, serves the same stream two ways, without transcoding:
  - **WebRTC (WHEP), first choice** (live view v3; about half a second behind instead of 2–5 s): `urlWebrtc` is the camera's WHEP endpoint with the viewer token, `<WebRTC base>/camaras/<camaraId>/whep?token=<viewer token>`. WebRTC base: `http://localhost:8889` locally; `https://<host>/vivo-webrtc` in production. **`urlWebrtc` is null when the backend has WebRTC playback off** (then the app plays `urlTransmision`); an older backend does not send the field at all, which the app treats the same way.
    - The app sends its SDP offer as is: `POST <urlWebrtc>` with `Content-Type: application/sdp`, a **recvonly video** transceiver and no audio (a standard SDP, every line ending in CRLF, as WebRTC stacks produce it). The answer is `201` with `Content-Type: application/sdp` and a `Location` header (the WHEP session, relative to the host, e.g. `/vivo-webrtc/camaras/<camaraId>/whep/<id>?token=…`) that takes `PATCH` (trickle ICE, `application/trickle-ice-sdpfrag`) and `DELETE` (leaving). Trickle is optional: the server's answer already carries its candidates, so the app may wait for its own ICE gathering and send a complete offer.
    - **The token goes only in the query, never in an `Authorization` header**: MediaMTX hands the query to the API, which finds each WebRTC reader by its token to end it with the session. A WHEP request without a valid token gets `401`; with a valid token but before the agent publishes, `404` (retry, as with HLS).
    - Video is H.264 Constrained Baseline, which every browser and WebRTC stack decodes. Media flows over ICE on port 8189, UDP, or TCP where UDP is blocked; no STUN or TURN server is used.
    - **Fallback:** when WebRTC fails, or gives no first frame within about 4 s **[implementation choice]**, the app plays `urlTransmision` with the same session.
  - **LL-HLS, fallback:** `urlTransmision` is an LL-HLS playlist: `<HLS base>/camaras/<camaraId>/index.m3u8?token=<viewer token>`. The app plays it with `video_player`. HLS base: `http://localhost:8888` locally; `https://<host>/vivo` in production. Apple's players only accept low-latency HLS over HTTPS, so the local stack serves standard (fMP4) HLS, a few seconds behind; production serves LL-HLS through Caddy's HTTPS.
- **Viewer token:** it belongs to one session and works for both URLs. It can be used many times (HLS makes many requests, the app may retry WHEP or switch to HLS) until the session ends. A read without a token, with an unknown token or after the session ended gets `401` from MediaMTX.
- **Preparing:** the app calls `POST /api/vista-en-vivo/preparar` when the camera screen opens, before the member taps to watch, so the agent can warm up while the member decides. It is a hint: a session opened without it works as before, only a little slower to start.
- **Video:** H.264 Constrained Baseline at 480p, about 15 fps with a keyframe every 0.5 s (live view v3; older agents publish about 8 fps), no audio.
- **PWA:** the browser reads the HLS playlist and posts the WHEP offer to another origin, so MediaMTX answers CORS for the configured origins (`hlsAllowOrigins` and `webrtcAllowOrigins`: any origin locally, the PWA's origins in production).
- **`expiraEn`:** the maximum end of the session, 10 min after it opened **[implementation choice]**. Then the app opens a new session.
- **Session end without `DELETE`:** the API ends a session when its viewer has not read the stream for 30 s over HLS or WebRTC **[implementation choice]**, and records the duration until the last read (US-24).
- **Pause or revoked consent:** every session of the camera ends at once, and MediaMTX disconnects the viewers (HLS and WebRTC).
- **`modo`:** what the household agent draws on the frames it publishes. It applies to the camera's stream, so all viewers see the same mode **[implementation choice]**.
  - `VIDEO` (default): the camera frame.
  - `VIDEO_CON_POSTURA`: the frame with the skeleton drawn on top.
  - `SOLO_POSTURA`: the skeleton on a plain neutral background, in the brand colours. No camera pixels: no image of the home leaves the PC.

## 5. Alerts and clips (`alertas`) — US-13, US-16 to US-21, US-26
```
Alerta = {
  id, tipo: "CAIDA" | "MOVIMIENTO_INESTABLE", severidad: "ALTA" | "MEDIA",
  estado: "ACTIVA" | "ATENDIDA" | "FALSA_ALARMA",
  confirmada: boolean,             // still on the floor after 30 s (CA-13.1, CA-21.2)
  camaraId, habitacion, ocurridaEn, notificadaEn | null,
  estadoAviso: "ENVIANDO" | "ENTREGADO" | "REINTENTANDO" | "NO_ENTREGADO",  // CA-16.1, CA-16.4, see §7
  recuperadaEn | null,             // "se levantó" (CA-13.2, CA-21.1)
  atendidaPor: {id, nombre} | null, atendidaEn | null,   // CA-19.1, CA-19.3
  escaladaEn | null,               // CA-20.1
  origenInestable: boolean,        // started as unstable movement, became a fall (CA-17.3)
  clip: "DISPONIBLE" | "NO_DISPONIBLE" | "ELIMINADO"     // CA-18.2, CA-26.3
}
```
| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/alertas` | member | Query: `tipo`, `estado`, `desde`, `hasta`, `pagina`, `tamano` → `200 {elementos: [Alerta], total}`, newest first (CA-25.1, CA-25.2). Empty list when none (CA-25.3). `pagina` starts at 0; `tamano` defaults to 20 and is at most 100 | `400 VALIDACION`: `tamano` above 100 |
| `GET /api/alertas/{id}` | member | → `200 Alerta`. Active alerts must be visible when the app opens, even if the push failed (CA-16.4) | `404 ALERTA_NO_ENCONTRADA` |
| `POST /api/alertas/{id}/atencion` | member | → `200 Alerta` with `ATENDIDA`, `atendidaPor` and `atendidaEn`. Pushes `ALERTA_ATENDIDA` to the other members (CA-19.1, CA-19.3) | `409 ALERTA_CERRADA` |
| `POST /api/alertas/{id}/falsa-alarma` | member | → `200 Alerta` with `FALSA_ALARMA`; excluded from the fall count (CA-19.2) | `409 ALERTA_CERRADA` |
| `GET /api/alertas/{id}/clip` | member | Query `descarga=true` for the download disposition → `200 {url, expiraEn, puntos: {url, expiraEn} \| null, puntosNoDisponibles}`, a short-lived pre-signed URL of the clip covering 6 s before and 6 s after the event (CA-18.1, CA-26.1, CA-26.2). `puntos` (US-36): the clip's body landmarks, a Parquet file to draw the skeleton over the clip, synchronized by time (CA-36.1); when null, `puntosNoDisponibles` is `SIN_CONSENTIMIENTO` (the consent to store movements is not in force) or `SIN_ARCHIVO` (this clip has none) and the switch shows that reason while the clip plays normally (CA-36.2). An older backend sends neither field | `404 CLIP_NO_DISPONIBLE` (CA-18.2) · `410 CLIP_ELIMINADO` (CA-26.3) |
| `POST /api/reportes-de-caida` | member | `{camaraId \| null, habitacion \| null, ocurridaAproximadamenteEn, nota \| null}` → `201 {id, recibidoEn, movimientosDisponibles}`: «¿Hubo una caída y no avisó?» (CA-40.4). Stored as a possible false negative for the team's evaluation; it creates no alert and notifies nobody. `camaraId` (its room is recorded) or `habitacion`, or neither for «No sé»; `nota` up to 300 characters. `movimientosDisponibles`: movements of that time may be in the cloud (consent and retention on): «Revisaremos los movimientos de ese momento»; otherwise «revisaremos lo que nos cuentes» | `400 VALIDACION` with `campos.ocurridaAproximadamenteEn` («Elige una hora que ya pasó.», up to 5 min ahead is tolerated) or `campos.nota` · `404 CAMARA_NO_ENCONTRADA` |

**Escalation (backend job):**
- If an alert is still `ACTIVA` after `esperaMinutos`, the secondary contact receives push `ALERTA_ESCALADA` (CA-20.1).
- If the alert was attended in time, nothing is sent (CA-20.2).
- If there is no secondary contact, the primary receives `SIN_CONTACTO_SECUNDARIO` (CA-20.3).

## 6. Weekly summary (`historial`) — US-27
`GET /api/resumen-semanal?semana=2026-W41` (member). Defaults to the current ISO week. Returns:
```
{ semana, conteos: {caidas, movimientosInestables, falsasAlarmas},
  semanaAnterior: {caidas, movimientosInestables, falsasAlarmas},
  tendencia: {caidas, movimientosInestables, falsasAlarmas} }   // each "AUMENTO" | "IGUAL" | "DISMINUCION"
```
- A week with no events returns zeros, not an error (CA-27.2).
- Falls exclude false alarms (CA-19.2).
- `tendencia` compares each type with the previous week (CA-27.3).

## 7. Push notifications
- **Device registration:** `POST /api/dispositivos {tokenPush, plataforma: "ANDROID" | "IOS" | "WEB"}` → `201 Dispositivo`, and `DELETE /api/dispositivos/{tokenPush}` → `204` (member). `WEB` is the PWA, with its FCM web push token (`getToken` with the project's VAPID key). Another `plataforma` is `400 VALIDACION` with `campos.plataforma`.
  - `POST` is an upsert by token: it reactivates the device, assigns it to the caller and records `vistoEn`, even when nothing else changed. **The app registers on every start and every return to the foreground.** Notices of the household still waiting for a device (see Delivery) are sent right after a registration.
  - `GET /api/dispositivos/{id}` (member, only the caller's own devices) → `200 Dispositivo`; `404 DISPOSITIVO_NO_ENCONTRADO` for an unknown id or another user's device. The app reads it with the `id` it got from `POST` to learn whether the backend still sends to this phone.
  - `Dispositivo = {id, plataforma, activo, vistoEn, desactivadoEn | null}`. The push token is never returned. `activo: false` means the push service said the token no longer exists (FCM `UNREGISTERED` or `SENDER_ID_MISMATCH`, SNS endpoint disabled): **registering the same token again does not help**; the app deletes its token, gets a new one and registers that.
- **Delivery:** the backend sends through Amazon SNS (FCM on Android, APNs on iOS) or Firebase Cloud Messaging to every member device. Web devices get an FCM web push with the same title, body and data payload; clicking it opens the PWA (the alert's screen, `#/alerta/{alertaId}`, for alert notices). Without a web configuration on the push service the backend skips web devices (they do not count as delivered). Fall pushes must arrive **in less than 10 s** from the moment the person is on the floor, with the room and the time (CA-16.1, CA-16.2).
  - Every notice is saved with the change that causes it and sent right after that change is committed, on another thread (the agent's request never waits for the push service). On failure the backend logs the error and retries every 15 s (CA-16.4) **[implementation choice]**.
  - **Urgent notices** (`ALERTA_CAIDA`, `ALERTA_MOVIMIENTO_INESTABLE`, `ALERTA_ACTUALIZADA_A_CAIDA`, `CAIDA_CONFIRMADA`, `ALERTA_ESCALADA`, `SIN_CONTACTO_SECUNDARIO`) are retried for 30 min **[implementation choice]**, also while no member has an active device, so a phone that registers again still gets them; retries stop when the alert is attended or marked a false alarm. Other notices are retried for 5 min and are dropped when nobody has an active device.
  - `Alerta.estadoAviso` follows the notice that opened the alert (or turned it into a fall): `ENVIANDO` (just created), `ENTREGADO` (the push service accepted it for at least one device; `notificadaEn` is set), `REINTENTANDO` (not delivered yet and still being retried: the service failed or nobody can receive it), `NO_ENTREGADO` (the retries ended without delivery). Alerts created before this field existed answer `ENTREGADO` or `NO_ENTREGADO`.
  - **On the phone:** notices about an alert go to the Android notification channel `alertas_caida`, which the app creates with high importance (others use the app's default channel); a later notice of the same alert replaces the earlier one (Android `tag`, APNs `apns-collapse-id`, web `tag` = `alertaId`; `camara-{camaraId}` for camera notices); urgent notices are iOS `time-sensitive` (the app needs the Time Sensitive Notifications entitlement) and web notifications that stay until dismissed (`requireInteraction`). Push services keep an undelivered notice for 1 h **[implementation choice]**.

Data payload: `{tipo, alertaId?, camaraId?, habitacion?, ocurridaEn}`. `tipo` is one of:

| `tipo` | When | Backlog |
|---|---|---|
| `ALERTA_CAIDA` | Fall detected | CA-16.1 |
| `ALERTA_MOVIMIENTO_INESTABLE` | Unstable movement (medium severity) | CA-17.1 |
| `ALERTA_ACTUALIZADA_A_CAIDA` | An unstable alert became a fall | CA-17.3 |
| `CAIDA_CONFIRMADA` | 30 s on the floor | CA-13.1 |
| `SE_LEVANTO` | Recovery after a fall, also a confirmed one: the alert stays active until a member attends it (product decision of 2026-10-10, changes CA-21.2) | CA-21.1 |
| `ALERTA_ATENDIDA` | Another member attended it | CA-19.3 |
| `ALERTA_ESCALADA` / `SIN_CONTACTO_SECUNDARIO` | Escalation | CA-20.1, CA-20.3 |
| `CAMARA_DESCONECTADA` / `CAMARA_RECONECTADA` | Connection status; the app shows what to check: cable, PC on and internet | CA-07.2, CA-07.3 |
| `DETECCION_NO_CONFIABLE` | Over 5 min with only discarded frames; the app shows what to check: light and framing | CA-15.3 |
| `PAUSA_FINALIZADA` | Automatic resume | CA-22.3 |
| `DATOS_ELIMINADOS` | Recordings deleted after revocation | CA-09.3 |
| `MOVIMIENTOS_ELIMINADOS` | The cloud movements were deleted after the consent to store them was revoked (§11) | CA-35.4 |
| `PC_DESVINCULADA` | A PC left the household without the app: from the PC itself, for security (a stolen refresh token) or after 90 days without connecting. Not sent when the owner removes it from the app. The app shows «Ver PCs y cámaras» (§9) | CA-31.3 |

## 8. Household agent endpoints
Used only by the desktop agent (`te-tengo-desktop-pywebview`). They are described in the backend repository at `docs/AGENT_CONTRACT.md` and do not concern the mobile app.

## 9. PCs and their link (`camaras`) — US-28, US-30, US-31
Change request «PCs, cámaras y datos de movimiento» (pending the advisor's approval; built ahead of it, additively). A household has one or more **PCs** running Te Tengo Captura, each with its own cameras. The owner links a PC from the app with the code the PC shows (OAuth 2.0 device grant, RFC 8628; ADR 0009 of the backend); installations made by the project team before this appear as a PC named «PC de la casa» of origin `INSTALACION`.

```
Pc = {
  id, nombre, sistemaOperativo | null, versionAgente | null,
  estadoConexion: "EN_LINEA" | "DESCONECTADA",   // a heartbeat of the PC or of one of its cameras in the last 90 s (CA-30.2, CA-30.3)
  ultimaSenal | null,                            // the latest of them: «Sin conexión desde las HH:MM»
  vinculadaEn, vinculadaPor: {id, nombre} | null, // null for installations
  origen: "VINCULACION" | "INSTALACION",
  camaras: [Camara],                             // the PC's cameras in use (§4)
  camarasSinUsar                                 // webcams the PC detected that have no room: «Falta elegir las cámaras»
}
```

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/pcs` | member | → `200 [Pc]`, the linked PCs, oldest first, each with its cameras (CA-30.2). The invited member sees them read-only | — |
| `POST /api/vinculaciones/consulta` | owner | `{codigo}` → `200 {codigo: "KQWT-MXHD", nombrePc, sistemaOperativo \| null, versionAgente \| null, expiraEn}`: the approval screen shows the PC before «Aprobar» (CA-28.4) | see below |
| `POST /api/vinculaciones/aprobacion` | owner | `{codigo}` → `201 Pc`: the PC joins the household; other PCs keep their cameras (CA-28.1, CA-30.1). The PC gets its tokens at its next poll, within 5 s | see below |
| `POST /api/vinculaciones/rechazo` | owner | `{codigo}` → `204`: «No reconozco esta PC»; the PC is not linked (CA-28.4) | see below |
| `PATCH /api/pcs/{id}` | owner | `{nombre}` (1–60 chars) → `200 Pc` | `422 PC_NOMBRE_VACIO` · `422 PC_NOMBRE_MUY_LARGO` · `404 PC_NO_ENCONTRADA` |
| `DELETE /api/pcs/{id}` | owner | → `204`: «Quitar esta PC». The app stops showing it and its cameras at once; the PC stops capturing, deletes its credentials and shows the QR again at once if online (CA-31.1), or as soon as it connects (CA-31.2). No push is sent for it | `404 PC_NO_ENCONTRADA` |

**Cameras and rooms («ambientes», US-29).** Product decision of 2026-10-10 (backlog US-29, CA-29.1 to CA-29.9): **the owner chooses the cameras and their rooms from the phone.** Invited members see the same, read-only (CA-29.9: `403 SOLO_TITULAR` on changes). The PC reports every webcam it detects; the app shows them in «Elige las cámaras» (after pairing, and in the PC's detail), the owner previews each one to recognise it, assigns it a room, renames rooms, or «Dejar de usar»; the PC hears every change at once and only cameras in use capture. (The PC can still assign a camera itself, as the installer's fallback.) For now a room has **one camera**, on any PC (CA-29.3); the data allows more later. **No cameras in the bathroom:** room names that mean a bathroom (baño, servicios higiénicos, SS.HH., WC, aseo…) answer `422 AMBIENTE_NO_PERMITIDO` («Por privacidad, Te Tengo no admite cámaras en el baño.»).

`CamaraDetectada = {id, pcId, nombreDispositivo, enUso, conectada, ambienteId | null, nombreHabitacion | null, estadoConexion | null, pausadaHasta | null, detectadaEn | null}`: a webcam the PC reported, known by the operating system's device id so it is always the same `id` (CA-29.4). `enUso: false` is «sin usar» (no room). `conectada`: for a camera in use, online by its heartbeats; otherwise plugged in at the PC's latest report. A camera in use that is unplugged keeps its room (CA-29.5).

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/pcs/{id}/camaras` | member | → `200 [CamaraDetectada]`, by device name: «Elegir las cámaras», with each webcam's name, whether it is plugged in and whether it is in use in a room (CA-29.1, CA-29.5). Empty: the PC finds no webcam (CA-29.6: ask someone at home to plug one in by USB). The invited member sees it read-only (CA-29.9) | `404 PC_NO_ENCONTRADA` |
| `POST /api/pcs/{id}/busqueda-de-camaras` | owner | → `202`: «Buscar de nuevo» (CA-29.6). The PC looks for its webcams and reports them again; read them again after a few seconds. Nothing happens while the PC is offline | `404 PC_NO_ENCONTRADA` |
| `PUT /api/camaras/{id}/ambiente` | owner | `{ambienteId}` or `{nombreAmbiente}` (a new name creates the room) → `200 Camara`: «Usar esta cámara», or a move. The PC receives it without anyone touching it and starts capturing; later alerts use the room's name (CA-29.2). If the PC also changed it («Elegir aquí», CA-29.8), the last change wins; the activity log tells which came from where | `409 AMBIENTE_OCUPADO {ambiente, camara}` (CA-29.3) · `422 AMBIENTE_NO_PERMITIDO` · `422 AMBIENTE_NOMBRE_VACIO` · `422 AMBIENTE_NOMBRE_MUY_LARGO` · `404 AMBIENTE_NO_ENCONTRADO` · `404 CAMARA_NO_ENCONTRADA` · `400 VALIDACION` with neither field |
| `DELETE /api/camaras/{id}/ambiente` | owner | → `204`: «Dejar de usar». The camera stops detecting and its room is free (CA-29.2); it stays «sin usar» among the PC's webcams | `404 CAMARA_NO_ENCONTRADA` |
| `POST /api/camaras/{id}/vista-previa` | member | → `201 {sesionId, urlTransmision, urlWebrtc, expiraEn, modo}`: a preview of a webcam, in use or «sin usar», to recognise it before assigning it. It **is a live view session**: same playback and rules (connected, not paused, the consent with its live view clause, CA-05.2), never recorded, closed with `DELETE /api/vista-en-vivo/{sesionId}`, logged in the access log with `vistaPrevia: true` (US-24). It lasts 2 minutes at most **[implementation choice]** (CA-29.7: «unos segundos»; the app may close it sooner). A regular live view of a webcam «sin usar» is `404`. The errors tell the app why it cannot be seen (CA-29.7) | `404 CAMARA_NO_ENCONTRADA` · `409 CAMARA_DESCONECTADA` · `409 CAMARA_EN_PAUSA {pausadaHasta}` · `409 SIN_CONSENTIMIENTO` |

`Ambiente = {id, nombre, camaras: [{id, pcId, pcNombre, nombreDispositivo, estadoConexion}]}`: the cameras in use in it (at most one, for now).

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/ambientes` | member | → `200 [Ambiente]`, by name: the chips of «¿En qué habitación está?», with «Ya tiene cámara» for those with one | — |
| `POST /api/ambientes` | owner | `{nombre}` (1–40 chars) → `201 Ambiente` | `422 AMBIENTE_NOMBRE_VACIO` («Escribe el nombre del ambiente.», CA-29.2) · `422 AMBIENTE_NOMBRE_MUY_LARGO` · `422 AMBIENTE_NO_PERMITIDO` · `409 AMBIENTE_YA_EXISTE`: names are unique ignoring case |
| `PATCH /api/ambientes/{id}` | owner | `{nombre}` → `200 Ambiente`; its camera and its later alerts use the new name, and the PC hears it | the same · `404 AMBIENTE_NO_ENCONTRADO` |
| `DELETE /api/ambientes/{id}` | owner | → `204` | `409 AMBIENTE_CON_CAMARA`: move or stop its camera first · `404 AMBIENTE_NO_ENCONTRADO` |

`409 AMBIENTE_OCUPADO` carries `ambiente` (its name) and `camara: {id, pcId, pcNombre, nombreDispositivo}`, for «Ya hay una cámara en el ambiente Sala (PC Sala-Rosa). Por ahora, cada ambiente usa una sola cámara.».

**The code (`codigo`).**
- 8 letters of `BCDFGHJKLMNPQRSTVWXZ` (no vowels, no digits), shown `XXXX-XXXX`. The app accepts it in lowercase, with or without the hyphen, and only those letters (CA-28.2); anything else is `400 VALIDACION` with `campos.codigo` («El código tiene 8 letras, solo consonantes y sin números.»).
- **QR:** the PC's QR is `https://app.tetengo.reqsai.tech/vincular?codigo=KQWT-MXHD` (`verification_uri_complete`; domain pending confirmation, BLOCKERS). The app reads `codigo` from it when it scans it, and should also open that link (app link or the PWA's `/vincular` route) when the phone's camera app scans it. «Escribir el código» is the fallback (CA-28.2).
- It lasts **10 minutes** and works **once**; the PC shows a new one on its own when it expires (CA-28.3).
- Only the owner may look it up, approve or reject it: `403 SOLO_TITULAR` for an invited member, who sees «Solo Carmen (titular) puede vincular una PC» (CA-28.5).

**Errors of the three `vinculaciones` endpoints:**
- `404 CODIGO_INCORRECTO {intentosRestantes}`: no such code; it counts as a wrong attempt («Ese código no existe. Revísalo letra por letra.»).
- `423 VINCULACION_BLOQUEADA {bloqueadaHasta}`: 5 wrong codes in a row from the same account block code entry for 15 minutes; the fifth wrong code already answers it (CA-28.6, «Podrás volver a intentarlo a las 10:57»). A code that exists resets the count.
- `410 CODIGO_VENCIDO`: expired or already used (approved or rejected) (CA-28.3); it does not count as wrong.
- `400 VALIDACION` · `403 SOLO_TITULAR`.

**Status and notices.** A PC with no heartbeat for 90 s shows as `DESCONECTADA` with its cameras (CA-30.3); each camera still sends `CAMARA_DESCONECTADA` (§7), and the app shows what to check (the PC on, internet, Te Tengo Captura open). A PC that went 90 days without connecting is removed and must be linked again (CA-28.7). Push `PC_DESVINCULADA` (§7) tells the family when a PC left without the app (CA-31.3).

## 11. Movement data (`movimientos`) — US-35 to US-39
Change request «PCs, cámaras y datos de movimiento» (pending approval; ADR 0010 of the backend). With a **separate, optional consent**, the PC stores the older adult's **movements**: the body landmarks it computes, never images or video. The alerts work the same without it (CA-35.1).

`ConsentimientoDeMovimientos = {vigente, versionTextoVigente, constancia: {otorgadoEn, otorgadoPor, representante, registradoPor: {id, nombre}, versionTexto, revocadoEn | null} | null, eliminacion: {estado: "PROGRAMADA" | "TERMINADA", archivos, programadaEn, terminadaEn | null} | null}`
- `constancia`: the latest consent, in force or revoked (CA-35.2: who gave it, who registered it, when, the text version); null if none was ever registered.
- `versionTextoVigente`: the version of the consent text the app must show (`"1"` for now); registering another version is refused.
- `eliminacion`: the deletion of the cloud movements after the latest revocation; the app polls it like the recordings' (§2).

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/hogar/consentimiento-movimientos` | member | → `200 ConsentimientoDeMovimientos` (always; `vigente: false` and `constancia: null` before the first one) | — |
| `POST /api/hogar/consentimiento-movimientos` | owner | `{otorgadoPor, representante: boolean, aceptado: true, versionTexto}` → `201 ConsentimientoDeMovimientos`. `otorgadoPor`: the older adult's name, or the representative's (`representante: true`, «Su representante»), up to 120 characters. Movements are stored only from now on (CA-35.2). Registering again replaces the one in force | `409 SIN_CONSENTIMIENTO`: the monitoring consent comes first (CA-35.1) · `422 CONSENTIMIENTO_NO_ACEPTADO` · `422 VERSION_DE_TEXTO_INVALIDA` · `400 VALIDACION` |
| `DELETE /api/hogar/consentimiento-movimientos` | owner | → `202 {eliminacionProgramada: true, archivos}`: nothing more is stored; every cloud movement, and the clips' landmarks, are deleted and push `MOVIMIENTOS_ELIMINADOS` is sent when done (CA-35.4); the cloud retention goes back to off. The monitoring consent and the alerts stay. The PC asks whether to delete its local movements too | `404 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS` |
| `GET /api/hogar/movimientos-en-la-nube` | member | → `200 {retencionDias: 7 \| 30 \| 90 \| 365 \| null, opciones: [7, 30, 90, 365], maximoRecomendadoDias: 365, mbPorCamaraPorDia: {minimo: 40, maximo: 80}, consentimientoVigente, datosDesde \| null, archivos, tamanoBytes}` (CA-37.1). `null`: not stored in the cloud (the default). `mbPorCamaraPorDia` is the change request's estimate | — |
| `PUT /api/hogar/movimientos-en-la-nube` | owner | `{retencionDias}` → `200` (same body). A shorter period deletes the files older than it at the next daily run (CA-37.4): before confirming, the app says «Borraremos los movimientos anteriores al {hoy − retencionDias}». `null` stops storing in the cloud and deletes them all at the next run | `422 RETENCION_INVALIDA` · `409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS` (a period needs the consent) |
| `POST /api/camaras/{id}/repeticiones` | member | `{desde, hasta}` (at most 1 h; `desde` not in the future) → `201 Repeticion` (CA-39.1). Logged in the access log (CA-39.3) | `409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS` (CA-39.4) · `404 CAMARA_NO_ENCONTRADA` · `400 VALIDACION` with `campos.desde` or `campos.hasta` |
| `GET /api/repeticiones/{id}/archivos` | member | Query `desde`, `hasta` (at most 1 h) → `200 Repeticion` with the files of that range: the member moved along the timeline; the same log entry, whose moment grows. Only the replay's own member, while it is open (at most 1 h) | `404 REPETICION_NO_ENCONTRADA` · `409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS` · `400 VALIDACION` |
| `DELETE /api/repeticiones/{id}` | member | → `204`: the member closed the replay; its duration is recorded (CA-39.3; the app shows the toast «Acceso registrado»). Closing someone else's or a closed one changes nothing | — |
| `GET /api/accesos-repeticiones` | member | → `200 [{id, usuario: {id, nombre}, camaraId, habitacion, momentoDesde, momentoHasta, inicio, duracionSegundos}]`, newest first. The app shows them with `GET /api/accesos-vista-en-vivo` in one log, with chips «Todo · Vista en vivo · Repeticiones» | — |

```
Repeticion = {
  id, camaraId, habitacion, desde, hasta,
  disponiblesDesde | null,   // start of the oldest movement stored: «Hay movimientos desde el 10/09/2026» (CA-39.4)
  guardadoEnLaNube: boolean, // false: the owner chose no cloud retention («No guardamos movimientos en la nube»)
  archivos: [{id, desde, hasta, tamanoBytes, fotogramas, sha256, url}],   // oldest first; url: pre-signed GET, until urlsExpiranEn
  urlsExpiranEn
}
```
- **Files:** Parquet compressed with zstd, one per camera and 5 minutes, written by Te Tengo Captura (its contract defines the columns). Check `sha256` before drawing. A moment outside the retention returns `archivos: []` with `disponiblesDesde` (CA-39.4).
- **Gaps** (CA-39.5): frames exist only while a person is in the image; time between frames inside a file is «Sin persona en la imagen», time without files is «PC apagada» or «En pausa» (the pause of the camera is known from the alerts and the camera; the API does not mark it). The timeline's events come from `GET /api/alertas?desde=&hasta=`, filtered by `camaraId` in the app.
- **Without internet** the app says «Sin internet. La repetición necesita conexión.»; a revoked consent while watching makes the next request answer `409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS` («Rosa ya no autoriza guardar sus movimientos.»).

