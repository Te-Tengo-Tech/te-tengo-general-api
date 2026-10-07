# Blockers

Tasks that cannot be finished without an outside decision or credential. Agents add an entry here, keep working on the next task, and never invent the missing value.

| Task | What is missing | Who decides | Date |
|---|---|---|---|
| Push delivery in production | AWS account, SNS platform applications (FCM server key, APNs certificate) | Team | — |
| Clip storage in production | Amazon S3 bucket and credentials for the pre-signed URLs; a fake `AlmacenamientoDeClips` adapter is used until then | Team | 2026-10-07 |
| Email delivery in production | Amazon SES verified domain or sender | Team | — |
| Live view transport | Team confirmation of the WebSocket JPEG relay proposed in the API contract. Implemented as proposed (T19): app side at `urlTransmision`, agent side at `/api/agente/transmision` (see AGENT_CONTRACT.md); the relay is in memory, so it needs a single instance or sticky routing | Team | — |
| T24 Error codes beyond the API contract | Found in the final contract review; the mobile contract should list them: `403 SIN_MEMBRESIA` on household endpoints when the token's user no longer belongs to the household (CA-08.3), `404 SIN_CONSENTIMIENTO` on `DELETE /api/hogar/consentimiento` without a current consent, and `401 CREDENCIAL_INVALIDA` / `404 EVENTO_NO_ENCONTRADO` on agent endpoints (AGENT_CONTRACT.md) | Team, with the mobile app | 2026-10-07 |
| T19 Live view without consent | Not in the API contract. Without a current consent the camera does not stream (CA-05.2), so `POST /api/camaras/{id}/vista-en-vivo` answers `409 SIN_CONSENTIMIENTO`; the mobile contract should list it | Team, with the mobile app | 2026-10-07 |
| T22 Clip retention period | CA-26.3 mentions a retention policy but not how long clips are kept. The job exists and runs only when `TT_RETENCION_CLIPS` is set | Team | 2026-10-07 |
| T10 CA-17.3 time window | How long after an unstable-movement alert a fall in the same room still "updates it to a fall". The backlog gives no window, so any still-active (unattended) unstable alert of the camera is updated | Team | 2026-10-07 |
| T09 Invitation validity | The backlog does not say how long an invitation link lasts. `tetengo.invitaciones.vigencia` uses 7 days as a placeholder | Team | 2026-10-07 |
| T03, T09 Links in e-mails | Deep-link format of the mobile app for password-reset and invitation links. Configurable with `TT_ENLACE_RECUPERACION` / `TT_ENLACE_INVITACION`; `{token}` is replaced by the one-time token | Team, with the mobile app | 2026-10-07 |
| T01, T03 Password policy | The backlog only requires a "valid" password; minimum length or complexity is not defined. The API only requires a non-blank password of at most 72 characters (the BCrypt limit) | Team | 2026-10-07 |
| T07 Installation credentials | How the project team issues installation credentials in production (an admin flow is not in any contract). For now `scripts/create-installation.sh` inserts one per webcam | Team | 2026-10-07 |
