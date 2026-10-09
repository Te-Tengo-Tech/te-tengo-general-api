# 0008. Live view over WebRTC (WHEP), LL-HLS as the fallback

**Status:** accepted (project owner, 2026-10-09). Extends ADR 0007: MediaMTX, the tokens, the hook and the control channel stay; the app gets a faster way to play the same stream.

## Context
- Production (2026-10-09): LL-HLS adds about 2–5 s of latency, the agent needs 1–7 s after `transmitir` before MediaMTX has the stream, and the 8 fps live view looks choppy.
- WebRTC delivers the same stream in about 0.2–0.5 s ([Fora Soft](https://www.forasoft.com/blog/article/webrtc-video-steaming-app-vs-hls), [OpenVidu](https://openvidu.io/blog/2026/09/01/low-latency-live-streaming/)). MediaMTX serves a published stream over LL-HLS and WebRTC at once, without transcoding.
- MediaMTX 1.21 (the pinned version) reads over WebRTC with **WHEP**: `POST /<path>/whep` with an SDP offer, `201` with the SDP answer and a `Location` for `PATCH` (trickle ICE) and `DELETE` (MediaMTX docs, `docs/4-read/03-webrtc.md` and `internal/servers/webrtc/http_server.go`, v1.21.1).
- Its external HTTP authorization receives `action`, `path`, `protocol` (`webrtc` here), the raw `query` of the request and the credentials (`user`, `password`, `token` from `Authorization: Basic`/`Bearer`). With `authMethod: http` the query is passed verbatim and is not parsed for tokens (`docs/2-features/06-authentication.md`, `internal/auth/manager.go`, v1.21.1).
- Its control API lists WebRTC sessions with their `query`, `state` (`read`/`publish`) and `outboundBytes`, and kicks them (`/v3/webrtc/sessions/list`, `/v3/webrtc/sessions/kick/{id}`).
- The thesis diagrams already show WebRTC between the streaming service and the app (see the last consequence of ADR 0007).

## Decision
- **`urlWebrtc`**, an additive field of the session (`POST /api/camaras/{id}/vista-en-vivo`): `<TT_VIVO_URL_WEBRTC with {camaraId}>?token=<viewer token>`. Null when `TT_VIVO_URL_WEBRTC` is blank (WebRTC off). `urlTransmision` (LL-HLS) is unchanged and remains the fallback.
- **One viewer token, in the query, for both protocols.** The hook authorizes `read` over `hls` or `webrtc` with the `token` query parameter of an open session of that camera. An `Authorization` header is not accepted: MediaMTX does not list a session's credentials, so the API could not find that reader to end it. A `read` over any other protocol (RTSP, RTMP, SRT) is denied for the same reason.
- **Activity and kicks cover WebRTC.** The session job counts a WebRTC reader whose `outboundBytes` grows as reading, like an HLS session; ending a session kicks its HLS and WebRTC readers.
- **`POST /api/vista-en-vivo/preparar`** (`{camaraId}` → `204`, the rules of opening a session) sends `{"preparar":true}` to the camera's agent, so it warms up what stays on the PC (capture, encoder, name resolution) while the member is on the camera screen. No frame leaves the PC before `transmitir`. Nothing is sent while the camera already streams. The message has no `modo` key, so earlier agents ignore it.
- **MediaMTX:** WHEP signalling on 8889 (behind Caddy at `/vivo-webrtc/` in production); media over one fixed ICE port, 8189, over UDP and TCP (for networks that block UDP); the container's interface addresses are not announced, only `webrtcAdditionalHosts` (the public address); no STUN or TURN; `webrtcAllowOrigins` = the PWA's origins, like `hlsAllowOrigins`.

## Consequences
- **Additive and backward compatible.** An old app ignores `urlWebrtc` and plays HLS; a new app with an old API (no field) plays HLS; an old agent ignores `preparar`.
- **Rollout order:** infrastructure (ports, MediaMTX, Caddy) → API (with `TT_VIVO_URL_WEBRTC`) → agent → app.
- **Firewall:** production opens 8189/UDP and 8189/TCP to everyone. MediaMTX accepts ICE only for sessions it negotiated after an authorized WHEP request.
- **Networks that block both UDP and TCP 8189** get no WebRTC; the app falls back to LL-HLS after about 4 s. A TURN server would cover them, at a cost; not now.
- **Tests.** `MediaMtxIntegrationTest` posts a recvonly SDP offer to MediaMTX's WHEP endpoint with the session's token (`201` and an answer with the 8189 UDP and TCP candidates), without it (`401`), finds the WebRTC reader through the control API and kicks it when the session closes. Unit and integration tests cover the hook with protocol `webrtc` and `preparar`.
