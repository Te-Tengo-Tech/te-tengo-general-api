# Household agent contract (Te Tengo Captura)

> The team decided to process video on the household PC (MediaPipe on CPU), so the agent sends **events**, not video (ADR 0004; the change request to the project charter is pending). Event names and fields come from the validated classifier in `te-tengo-desktop-pywebview` (`docs/classification-spec.md`).

## Authentication
- **Installation:** the project team installs the agent with a fixed configuration (household, webcam and room) and an installation credential.
- **Registration:** at startup the agent registers its camera and receives a **per-camera JWT** with the claims `hogar_id`, `camara_id` and `rol = AGENTE`. The token only works for that household (see `MULTITENANCY.md`).

## Endpoints (version 1, header `Api-Version: 1`)
| Method and path | Purpose | Stories |
|---|---|---|
| `POST /api/agente/camaras/registro` | Registers the camera when the agent starts, using the installation credential, and returns the camera token | US-06 (CA-06.1) |
| `GET /api/agente/estado-captura` | Current consent and pauses: the agent does not process without consent or during a pause | US-05 (CA-05.2), US-22 |
| `POST /api/agente/senal` | Periodic heartbeat; updates the time of the last signal | US-07 |
| `POST /api/agente/eventos` | Detected event (body below); idempotent by `eventoId` | US-11 to US-21 |
| `POST /api/agente/eventos/{eventoId}/clip` | Returns a pre-signed S3 PUT URL to upload the 6 s + 6 s clip | US-18 |
| `GET /api/agente/configuracion` | Current classification thresholds and agent version, so agents can update themselves | — |

## Request and response bodies [implementation choice]
The bodies below are not in the backlog; the backend defines them and the agent follows them.

- **`POST /api/agente/camaras/registro`** (public): `{credencial, nombreHabitacion}` → `200 {camaraId, token, expiraEn}`.
  - The first registration creates the camera with the room name of the installation (CA-06.1); later registrations return the same camera and keep a name the family changed (CA-06.2).
  - `401 CREDENCIAL_INVALIDA` for an unknown credential.
  - The token lasts 30 days; the agent registers again at startup or when it gets a `401`.
  - The project team creates the credential with `scripts/create-installation.sh <hogar-id>` (only its SHA-256 is stored).
- **`GET /api/agente/estado-captura`**: → `200 {capturaPermitida, consentimientoVigente, pausadaHasta | null}`. The agent processes video only when `capturaPermitida` is true: a current consent (CA-05.2) and no active pause (CA-22.1).
- Agent tokens only open `/api/agente/**`; family members' tokens get `403` there.

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
- `deteccion_no_confiable` (5 min without seeing the person)

**Backend rules:**
- **Disconnection:** if no heartbeat arrives in time, the camera becomes `DESCONECTADA` and push `CAMARA_DESCONECTADA` is sent (CA-07.2). The heartbeat timeout **[implementation choice]** is 3 missed heartbeats, with a heartbeat every 30 s.
- **Latency:** a fall must reach the family as a push **in less than 10 s** (CA-11.3, CA-16.1).
