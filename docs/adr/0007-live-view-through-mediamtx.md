# 0007. Live view through MediaMTX

**Status:** accepted (project owner, 2026-10-07). Supersedes the WebSocket JPEG relay proposed in the API contract (T19).

## Context
- US-23 asks for live view of a camera, from the home screen or from an alert. The architecture has a "Servicio de transmisión en vivo" between the household and the app (`integracion.puml`).
- The first implementation relayed binary JPEG frames from the agent to the app through two WebSockets of this API. That was a proposal pending confirmation. It carried video through the API, had no real codec or player, and kept every frame in this process.
- The household agent processes video on the PC (ADR 0004) and already computes MediaPipe landmarks, so it can draw a skeleton on the frames, or show only the skeleton.
- The app is Flutter. `video_player` plays HLS on Android and iOS without extra native code.

## Decision
- **MediaMTX** (`bluenviron/mediamtx`, pinned to `1.21.1`) is the live streaming service.
  - One path per camera: `camaras/<camaraId>`.
  - The agent publishes H.264 over RTSP (TCP locally, RTSPS in production).
  - The app plays LL-HLS: `<HLS base>/camaras/<camaraId>/index.m3u8?token=<viewer token>`.
  - Config: `mediamtx.yml` (committed); local service in `compose.yaml`.
- **The API authorizes MediaMTX** (`authMethod: http`): `POST /api/interno/mediamtx/autorizar?secreto=<TT_VIVO_SECRETO_AUTORIZACION>`.
  - Publish: only the camera's path, user `agente`, and the publish token of the camera's current transmission.
  - Read and playback: only with the viewer token of an open session of that camera.
  - Neither while capture is not allowed (pause, no consent).
  - Anything else gets `401`, and so does a request without the secret.
  - Tokens are 256-bit random secrets, stored as SHA-256 hashes: viewer tokens on the session row (`accesos_vista_en_vivo`), publish tokens on `transmisiones_en_vivo`.
- **The agent's WebSocket becomes a text-only control channel** (`/api/agente/transmision`).
  - `transmitir:true` carries the publish URL, the credentials and the mode.
  - The other messages are `modo` changes and `transmitir:false`.
  - Messages go out after the transaction commits, so MediaMTX never asks for a token that is not stored yet.
- **Transmission lifecycle.**
  - A camera has a transmission while it has open sessions: the first one starts it, the last one stops it.
  - Changes to one camera's sessions and transmission are serialized with a PostgreSQL transaction-level advisory lock, so a session that opens while the last one closes does not lose the stream.
  - Pause (CA-22.1) and revoked consent (CA-09.1, from `ConsentimientoRevocado`) end the sessions, stop the agent and kick the publisher and the readers through MediaMTX's control API.
- **Sessions end on their own** [implementation choice]: a job every 10 s (`FinalizarSesionesDeVistaEnVivo`) ends them.
  - After **30 s without reads**, the session ends at the last read.
  - At **10 min**, the session ends at its maximum.
  - MediaMTX 1.21 authorizes an HLS reader once, when its HLS session starts, and then serves it by session id. So a read is either an authorization, or an HLS session of that token whose `outboundBytes` grew since the previous run (control API `GET /v3/hls/sessions/list`). An HLS session lingers in MediaMTX for about 30 s after its last request, so the byte count, not the session's presence, tells whether the viewer is still watching.
- **Modes** `VIDEO`, `VIDEO_CON_POSTURA` and `SOLO_POSTURA` are drawn by the agent and apply to the camera's stream, so all viewers see the same mode [implementation choice]. `SOLO_POSTURA` sends no camera pixels.

## Consequences
- **Video never passes through the API.** The API only issues tokens, answers MediaMTX and controls the agent. US-24 keeps its access log with real durations.
- **Single instance.** The agent's control channel is in memory, like the old relay: the API needs a single instance or sticky routing of `/api/agente/transmision`. The tokens and the transmission state are in PostgreSQL.
- **Production** (infra repository):
  - The reverse proxy (Caddy) serves HLS at `https://<host>/vivo/…`. LL-HLS on Apple devices needs HTTPS.
  - Agents publish on RTSPS 8322, with certificates the infrastructure provides.
  - `/api/interno/**` and the control API (9997) are never exposed.
  - `TT_VIVO_SECRETO_AUTORIZACION` must be set; blank denies everything.
- **Local runs.** `./gradlew bootRun` starts MediaMTX from `compose.yaml`. Its hook reaches the API on the host through `host.docker.internal`.
- **Tests.**
  - `MediaMtxIntegrationTest` runs the committed `mediamtx.yml` in a container: an ffmpeg container publishes, and the test reads HLS and kicks clients.
  - Other tests use a recording double of the control API (`TransmisionDePrueba`).
- **The thesis diagrams** show WebRTC between the service and the app. The service is the same; the protocol is LL-HLS, because it is what `video_player` plays.
