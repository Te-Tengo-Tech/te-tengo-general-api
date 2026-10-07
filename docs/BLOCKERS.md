# Blockers

Tasks that cannot be finished without an outside decision or credential. Agents add an entry here, keep working on the next task, and never invent the missing value.

| Task | What is missing | Who decides | Date |
|---|---|---|---|
| Push delivery in production | AWS account, SNS platform applications (FCM server key, APNs certificate) | Team | — |
| Email delivery in production | Amazon SES verified domain or sender | Team | — |
| Live view transport | Team confirmation of the WebSocket JPEG relay proposed in the API contract | Team | — |
| T03, T09 Links in e-mails | Deep-link format of the mobile app for password-reset and invitation links. Configurable with `TT_ENLACE_RECUPERACION` / `TT_ENLACE_INVITACION`; `{token}` is replaced by the one-time token | Team, with the mobile app | 2026-10-07 |
| T01, T03 Password policy | The backlog only requires a "valid" password; minimum length or complexity is not defined. The API only requires a non-blank password of at most 72 characters (the BCrypt limit) | Team | 2026-10-07 |
| T07 Installation credentials | How the project team issues installation credentials in production (an admin flow is not in any contract). For now `scripts/create-installation.sh` inserts one per webcam | Team | 2026-10-07 |
| `GET /api/agente/configuracion` | Classification thresholds and agent version come from the desktop classifier (`te-tengo-desktop-pywebview`, `docs/classification-spec.md`) and are not in the backlog; the endpoint is not implemented and is assigned to no task | Team, with the desktop agent | 2026-10-07 |
