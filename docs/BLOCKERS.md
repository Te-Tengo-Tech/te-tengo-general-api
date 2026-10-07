# Blockers

Tasks that cannot be finished without an outside decision or credential. Agents add an entry here, keep working on the next task, and never invent the missing value.

| Task | What is missing | Who decides | Date |
|---|---|---|---|
| Push delivery in production | AWS account, SNS platform applications (FCM server key, APNs certificate) | Team | — |
| Clip storage in production | Amazon S3 bucket and credentials for the pre-signed URLs; a fake `AlmacenamientoDeClips` adapter is used until then | Team | 2026-10-07 |
| Email delivery in production | Amazon SES verified domain or sender | Team | — |
| Live view transport | Team confirmation of the WebSocket JPEG relay proposed in the API contract | Team | — |
| T10 CA-17.3 time window | How long after an unstable-movement alert a fall in the same room still "updates it to a fall". The backlog gives no window, so any still-active (unattended) unstable alert of the camera is updated | Team | 2026-10-07 |
| T09 Invitation validity | The backlog does not say how long an invitation link lasts. `tetengo.invitaciones.vigencia` uses 7 days as a placeholder | Team | 2026-10-07 |
| T03, T09 Links in e-mails | Deep-link format of the mobile app for password-reset and invitation links. Configurable with `TT_ENLACE_RECUPERACION` / `TT_ENLACE_INVITACION`; `{token}` is replaced by the one-time token | Team, with the mobile app | 2026-10-07 |
| T01, T03 Password policy | The backlog only requires a "valid" password; minimum length or complexity is not defined. The API only requires a non-blank password of at most 72 characters (the BCrypt limit) | Team | 2026-10-07 |
| T07 Installation credentials | How the project team issues installation credentials in production (an admin flow is not in any contract). For now `scripts/create-installation.sh` inserts one per webcam | Team | 2026-10-07 |
| `GET /api/agente/configuracion` | Classification thresholds and agent version come from the desktop classifier (`te-tengo-desktop-pywebview`, `docs/classification-spec.md`) and are not in the backlog; the endpoint is not implemented and is assigned to no task | Team, with the desktop agent | 2026-10-07 |
