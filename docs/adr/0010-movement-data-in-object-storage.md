# 0010. Movement data in object storage, indexed in PostgreSQL

**Status:** proposed (change request «PCs, cámaras y datos de movimiento», pending the advisor's approval; built ahead of it, additively). The legal review of the Reglamento of Law 29733 (D.S. 016-2024-JUS) that the change request asks for before implementing is still pending (docs/BLOCKERS.md).

## Context
- US-37 stores, with a separate and optional consent (US-35), the older adult's body landmarks: per frame with a person, the 33 MediaPipe points quantized, the time and the classifier's θ, P, v and phase; no image. About 40–80 MB per camera and day (estimate, not measured). They serve two purposes: replaying what happened as a skeleton (US-39) and re-running the rules offline to evaluate the detection (US-40).
- The PC writes one Parquet file with zstd per camera every 5 minutes (change request 2.4). The API runs on one small VM; clips already live in Cloudflare R2, uploaded by the agent with pre-signed URLs (docs/DEPLOYMENT.md).
- The cloud retention is the owner's choice: off until chosen, 7, 30, 90 or 365 days, at most a year (proportionality). Revoking the consent deletes the cloud movements (CA-35.4); revoking the monitoring consent deletes every recording (CA-09.1), and movements are recordings too.

## Decision
- **Bytes never pass through the API.** The PC asks `POST /api/agente/movimientos` for a pre-signed PUT URL of one file, uploads it to R2 (S3 API), then confirms it (`POST /api/agente/movimientos/{id}/confirmacion`); the API checks the object with `HeadObject` and only then adds it to the index. The app replays with pre-signed GET URLs (10 minutes). R2 egress is free.
- **Key layout:** `movimientos/<hogar>/<camara>/YYYY/MM/DD/HH/mm.parquet`, in UTC from the file's start: one object per camera and start (a retry overwrites the same object), and a household's or a day's data can be listed by prefix if ever needed. The clips' landmarks go next to the clips as `clips/<alerta>.landmarks.parquet` (US-36).
- **PostgreSQL keeps an index, never landmarks** (CA-37.2): `archivos_de_movimiento`, one row per file (camera, `desde`, `hasta`, size, SHA-256 reported by the PC, frames, key, state), and `resumenes_de_movimiento`, counts per camera and day (files, frames, seconds). Replays, retention and deletion read only the index.
- **Same bucket as the clips by default**, its own `tetengo.movimientos.s3.*` settings so a separate bucket (e.g. with its own lifecycle rules or jurisdiction) is a configuration change. The adapter is a copy of the clips' S3 adapter in the `movimientos` module, because modules may not share infrastructure classes; it adds `DeleteObjects` in batches of 1000.
- **Retention and deletion are jobs over the index.** Daily (04:00 America/Lima): files that ended before the household's retention are deleted (all of them without a retention or without the consent), and uploads never confirmed after a day. Every minute: after a revocation of the movement consent, every file and summary of the household is deleted and push `MOVIMIENTOS_ELIMINADOS` is sent (CA-35.4). A monitoring consent revocation deletes movements and the clips' landmarks within the existing deletion of recordings, whose status (`GET /api/hogar` `eliminacion`) now also counts `movimientos`. Objects are deleted before their index rows, so a failure leaves a row to retry, never an orphan object the index forgot.
- **Consent gates everything**: uploads, confirmations (an upload finished after a revocation is deleted), replays and clip landmarks need the movement consent in force; the cloud also needs a retention. The consent records who gave it (the older adult or a representative), who registered it, when and the version of the text (`tetengo.movimientos.version-texto-vigente`).
- **Replays are logged** like live view (US-24): `repeticiones_de_movimiento` keeps who, which camera, which moment (grown as the member moves along the timeline), when it started and how long it lasted (closed by the app, at most 1 hour).

## Consequences
- The API's load does not grow with the data: one small request per 5-minute file and camera (288 a day), and a `HeadObject`. At 60 MB per camera and day and 365 days, a camera keeps about 22 GB, about USD 0.18 a month past R2's free tier (change request 5.3).
- The SHA-256 is recorded but not enforced by the storage (R2's checksum headers on pre-signed URLs were not verified for this setup); the reader verifies the file against the index. The upload's size limit (50 MB) is checked when the URL is issued, not by the storage.
- Cloudflare R2 has no South American location: storing movements there is a cross-border flow of sensitive data (Law 29733, art. 15), covered by the express, informed consent; the legal articles of the Reglamento are still to be reviewed (BLOCKERS).
- The summaries table is a placeholder: it holds counts now and can grow with the evaluation's needs (US-40) without touching the landmarks.

## References
- Apache Software Foundation. (n.d.). *Apache Parquet*. https://parquet.apache.org/
- Cloudflare. (n.d.). *Presigned URLs*. Cloudflare R2 docs. https://developers.cloudflare.com/r2/api/s3/presigned-urls/
- Congreso de la República del Perú. (2011). *Ley N.° 29733, Ley de Protección de Datos Personales*.
