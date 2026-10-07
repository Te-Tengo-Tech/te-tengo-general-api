# Te Tengo API contract (v1)

> **Shared contract between `te-tengo-general-api` (implements it) and `te-tengo-mobile-flutter` (consumes it).** An identical copy lives in both repositories at `docs/API_CONTRACT.md`. Neither side may change it unilaterally: if a task needs a change, record it in `docs/BLOCKERS.md` and keep implementing against the current version.
>
> Every business value comes from the product backlog (`docs/references/PRODUCT_BACKLOG.md`, cited as `CA-xx.y`). Values marked **[implementation choice]** are not in the backlog and can be adjusted by the team.

## Conventions
- **Base path** `/api`. **Version header** `Api-Version: 1` (optional; defaults to 1).
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
| `GET /api/hogar` | member | → `200 {hogarId, adultoMayor, rol, consentimiento: Consentimiento \| null}` | — |
| `PUT /api/hogar/adulto-mayor` | owner | `{nombre, edad, direccion, convivencia, telefono?}` → `200 adultoMayor`. Replaces the whole profile: an omitted `telefono` clears it | `400 VALIDACION` |
| `GET /api/hogares` | user | → `200 [{hogarId, nombreAdultoMayor, rol}]`, the households the user belongs to | — |
| `POST /api/sesiones/hogar` | user | `{hogarId}` → `200 Sesion` for that household | `403 SIN_MEMBRESIA` |
| `POST /api/hogar/consentimiento` | owner | `{otorgadoPor, aceptadoPorAdultoMayor: true, vistaEnVivoAceptada: true}` → `201 Consentimiento`. Stores the date and time (CA-05.3); camera capture may start (CA-05.1) | `422 CONSENTIMIENTO_NO_ACEPTADO`: both flags must be true (CA-05.4) |
| `GET /api/hogar/consentimiento` | member | → `200 Consentimiento` | `404 SIN_CONSENTIMIENTO` |
| `DELETE /api/hogar/consentimiento` | owner | → `202 {eliminacionProgramada: true}`. Stops capture and schedules deletion of every recording (CA-09.1); a push `DATOS_ELIMINADOS` is sent when done (CA-09.3) | `404 SIN_CONSENTIMIENTO`: there is no current consent to revoke |

`adultoMayor = {nombre, edad, direccion, convivencia, telefono | null}`
- `edad`: whole years, required, from 50 to 120. The prototype's profile form asks for «Edad» and rejects other values with «Escribe una edad válida, en años.»; the app shows «Rosa Huamán, 78 años». Households registered before this field existed answer `edad: null` until the owner saves the profile again.
- `telefono`: optional; the number that «Llamar a Rosa · 987 654 321» dials from the alert (prototype alert screen). Digits and spaces with an optional leading `+`, 6 to 20 characters **[implementation choice]**; a blank value is stored as null.
- `convivencia` values, from the prototype's profile screen:
  - `SOLO`: «Vive solo(a)»;
  - `CON_FAMILIAR`: «Vive conmigo», the older adult lives with the account owner;
  - `CON_CUIDADOR`: «Vive con otro cuidador».
- `Consentimiento = {otorgadoEn, otorgadoPor, registradoPor: {id, nombre}, vistaEnVivoAceptada, vigente}`

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
`Camara = {id, nombreHabitacion, estadoConexion: "EN_LINEA" | "DESCONECTADA", ultimaSenal | null, pausadaHasta | null, deteccionConfiable: boolean, instaladaEn, noConfiableDesde | null}`
- `instaladaEn`: when the project team installed the camera, that is, the agent's first registration (CA-06.1). The camera header shows it as «Instalada el».
- `noConfiableDesde`: when detection stopped being reliable, the time of the agent's `deteccion_no_confiable` event (CA-15.3); null while `deteccionConfiable` is true. The camera detail shows «La detección no es confiable desde las 10:36».

| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/camaras` | member | → `200 [Camara]`, the cameras installed in the household, with the room name set at installation (CA-06.1, CA-07.1, CA-22.2) | — |
| `PATCH /api/camaras/{id}` | owner | `{nombreHabitacion}` (1–40 chars) → `200 Camara`; later alerts use the new name (CA-06.2) | `422 CAMARA_NOMBRE_VACIO` (CA-06.3) · `422 CAMARA_NOMBRE_MUY_LARGO` · `404 CAMARA_NO_ENCONTRADA` |
| `POST /api/camaras/{id}/pausa` | member | `{duracion: "MIN_30" \| "HORA_1" \| "HORAS_2" \| "HASTA_MANANA"}`, the options of the prototype's pause screen; `HASTA_MANANA` means the next 07:00 in the household time zone, `America/Lima` **[implementation choice]**. → `200 Camara` with `pausadaHasta`. Stops capture and detection (CA-22.1); resumes automatically and sends push `PAUSA_FINALIZADA` (CA-22.3) | `422 DURACION_INVALIDA` |
| `DELETE /api/camaras/{id}/pausa` | member | → `200 Camara` (resume now) | — |
| `POST /api/camaras/{id}/vista-en-vivo` | member | `{alertaId \| null}` → `201 {sesionId, urlTransmision, expiraEn}` (CA-23.1, CA-23.2) | `409 CAMARA_DESCONECTADA` (CA-23.3) · `409 CAMARA_EN_PAUSA {pausadaHasta}` (CA-23.4) · `409 SIN_CONSENTIMIENTO`: without a current consent the camera does not stream (CA-05.2) |
| `DELETE /api/vista-en-vivo/{sesionId}` | member | → `204`. Records who watched, when it started and how long (CA-24.1) | — |
| `GET /api/accesos-vista-en-vivo` | member | → `200 [{usuario: {id, nombre}, inicio, duracionSegundos, desdeAlerta: boolean}]`, newest first (CA-24.2). Empty list when none (CA-24.3) | — |

**Live stream transport [implementation choice, pending team confirmation]:** `urlTransmision` is a `wss://` URL served by the backend that relays binary JPEG frames. Each frame has the same format as the desktop agent's ingestion protocol: `[8-byte big-endian ms timestamp][JPEG]`.

## 5. Alerts and clips (`alertas`) — US-13, US-16 to US-21, US-26
```
Alerta = {
  id, tipo: "CAIDA" | "MOVIMIENTO_INESTABLE", severidad: "ALTA" | "MEDIA",
  estado: "ACTIVA" | "ATENDIDA" | "FALSA_ALARMA",
  confirmada: boolean,             // still on the floor after 30 s (CA-13.1, CA-21.2)
  camaraId, habitacion, ocurridaEn, notificadaEn | null,
  recuperadaEn | null,             // "se levantó" (CA-13.2, CA-21.1)
  atendidaPor: {id, nombre} | null, atendidaEn | null,   // CA-19.1, CA-19.3
  escaladaEn | null,               // CA-20.1
  origenInestable: boolean,        // started as unstable movement, became a fall (CA-17.3)
  clip: "DISPONIBLE" | "NO_DISPONIBLE" | "ELIMINADO"     // CA-18.2, CA-26.3
}
```
| Method and path | Auth | Body → response | Errors |
|---|---|---|---|
| `GET /api/alertas` | member | Query: `tipo`, `estado`, `desde`, `hasta`, `pagina`, `tamano` → `200 {elementos: [Alerta], total}`, newest first (CA-25.1, CA-25.2). Empty list when none (CA-25.3) | — |
| `GET /api/alertas/{id}` | member | → `200 Alerta`. Active alerts must be visible when the app opens, even if the push failed (CA-16.4) | `404 ALERTA_NO_ENCONTRADA` |
| `POST /api/alertas/{id}/atencion` | member | → `200 Alerta` with `ATENDIDA`, `atendidaPor` and `atendidaEn`. Pushes `ALERTA_ATENDIDA` to the other members (CA-19.1, CA-19.3) | `409 ALERTA_CERRADA` |
| `POST /api/alertas/{id}/falsa-alarma` | member | → `200 Alerta` with `FALSA_ALARMA`; excluded from the fall count (CA-19.2) | `409 ALERTA_CERRADA` |
| `GET /api/alertas/{id}/clip` | member | Query `descarga=true` for the download disposition → `200 {url, expiraEn}`, a short-lived pre-signed URL of the clip covering 6 s before and 6 s after the event (CA-18.1, CA-26.1, CA-26.2) | `404 CLIP_NO_DISPONIBLE` (CA-18.2) · `410 CLIP_ELIMINADO` (CA-26.3) |

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
- **Device registration:** `POST /api/dispositivos {tokenPush, plataforma: "ANDROID" | "IOS"}` → `201`, and `DELETE /api/dispositivos/{tokenPush}` → `204` (member).
- **Delivery:** the backend sends through Amazon SNS (FCM on Android, APNs on iOS) to every member device. Fall pushes must arrive **in less than 10 s** from the moment the person is on the floor, with the room and the time (CA-16.1, CA-16.2). On failure the backend logs the error and retries (CA-16.4).

Data payload: `{tipo, alertaId?, camaraId?, habitacion?, ocurridaEn}`. `tipo` is one of:

| `tipo` | When | Backlog |
|---|---|---|
| `ALERTA_CAIDA` | Fall detected | CA-16.1 |
| `ALERTA_MOVIMIENTO_INESTABLE` | Unstable movement (medium severity) | CA-17.1 |
| `ALERTA_ACTUALIZADA_A_CAIDA` | An unstable alert became a fall | CA-17.3 |
| `CAIDA_CONFIRMADA` | 30 s on the floor | CA-13.1 |
| `SE_LEVANTO` | Recovery after a fall | CA-21.1 |
| `ALERTA_ATENDIDA` | Another member attended it | CA-19.3 |
| `ALERTA_ESCALADA` / `SIN_CONTACTO_SECUNDARIO` | Escalation | CA-20.1, CA-20.3 |
| `CAMARA_DESCONECTADA` / `CAMARA_RECONECTADA` | Connection status; the app shows what to check: cable, PC on and internet | CA-07.2, CA-07.3 |
| `DETECCION_NO_CONFIABLE` | Over 5 min with only discarded frames; the app shows what to check: light and framing | CA-15.3 |
| `PAUSA_FINALIZADA` | Automatic resume | CA-22.3 |
| `DATOS_ELIMINADOS` | Recordings deleted after revocation | CA-09.3 |

## 8. Household agent endpoints
Used only by the desktop agent (`te-tengo-desktop-pywebview`). They are described in the backend repository at `docs/AGENT_CONTRACT.md` and do not concern the mobile app.
