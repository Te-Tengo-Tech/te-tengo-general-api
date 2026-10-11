# 0011. A light replay format next to each movement file

**Status:** proposed (part of the change request «PCs, cámaras y datos de movimiento», like ADR 0010).

## Context
- ADR 0010 stores the movements as Parquet with zstd: one file per camera every 5 minutes, and one per alert clip (US-36). The app must draw them as a skeleton in the camera's player (US-39), as the still frame of the last event, and over the clip («Puntos del cuerpo», CA-36.1), on Android, iOS and the web (PWA).
- There is no reliable Parquet reader for Dart: `dartframe`'s reader is a placeholder, `libbigdata` is native only, and `dart_duckdb` on the web means shipping duckdb-wasm. The app could not draw anything («Esta versión aún no dibuja los movimientos»; app BLOCKERS, «Replay decoding»). `package:archive` 4.3.0 has no zstd decoder either, so even a hand-written Parquet reader would need its own zstd.
- The options were: (1) the API converts each file; (2) the PC also uploads a light format the app can read; (3) a minimal Dart Parquet reader for one pinned schema; (4) parquet-wasm or duckdb-wasm on the web and native libraries on the phones. (1) breaks ADR 0010's rule that bytes never pass through the API; (3) and (4) tie the app to Parquet's encodings and to zstd, or weigh megabytes on the web.

## Decision
- **Option 2.** The PC writes, from the same rows in memory, a second file in the **TTMOV v1** format (specified byte by byte in the API and agent contracts, «Light replay format»): a 24-byte little-endian header (`TTMV`, version, flags, target fps, landmarks per pose, `t0Ms`, `duracionMs`, `fotogramas`), then per frame `dtMs` (i32), a state byte (pose, phase, event) and, with a pose, the 33 landmarks' `x`, `y` (u16) and visibility (u8) as deltas from the previous pose, all wrapped in one gzip member. `z` is optional (a flag) and the PC leaves it out: the drawing is 2D. θ, P and v stay in the Parquet file only.
- **Same integers as the Parquet file** (the quantization of ADR 0010), so both files describe exactly the same frames and a test can compare them.
- **gzip, not zstd.** gzip decodes on every target with what the app already has: `dart:io`'s zlib on the phones and `package:archive`'s pure-Dart inflate on the web (the browser's `DecompressionStream` would also do); Python and Java ship it in their standard libraries. zstd would need a pure-Dart decoder that `package:archive` 4.3.0 does not have. Measured on the same rows, zstd at level 3 saves 2 % (251 KB against 256 KB): not worth a dependency.
- **Deltas between poses** (u16 modulo 2¹⁶) instead of the raw values: about 17 % smaller after gzip for no decoding cost, and fixed-width, so a reader needs no variable-length integers.
- **Upload:** the same lane and pre-signed flow as the Parquet file, after it: `POST /api/agente/movimientos/{archivoId}/ligero` with its size and SHA-256, `PUT`, and `…/ligero/confirmacion` (`HeadObject`). The key is the Parquet file's with `.ttmov`. The index row of the Parquet file gains the light file's size, SHA-256 and upload time (migration), so the replay listing returns `ligero: {url, tamanoBytes, sha256}` without another query. A clip's light landmarks go to `clips/<alerta>.landmarks.ttmov` through `POST /api/agente/eventos/{eventoId}/clip/puntos/ligero`, and `GET /api/alertas/{id}/clip` returns them in `puntos.ligero`.
- **Deletion:** every place that deletes a movement file or a clip's landmarks deletes the light file too (retention, revocation of either consent, unconfirmed uploads, the clip's own retention).

## Consequences
- Measured on a real 5-minute run (the agent's capture loop over CAUCAFall videos at 8 fps, a person in the image in 86 % of the 2,400 frames): Parquet 459 KB, light file 257 KB. Per camera and day, 132 MB of Parquet plus 74 MB of light files: the cloud keeps about 56 % more. At R2's price (USD 0.015 per GB and month, past the free tier) a year of one camera goes from about 48 GB to 75 GB, about USD 0.40 more a month.
- A 10-minute window of the player downloads two or three light files, about 0.5–0.8 MB, instead of Parquet files it could not read.
- Movement files uploaded before this change, or by an older Te Tengo Captura, have no light file: the app shows them as a stretch without data. An older API answers `404` to the new paths, and the PC then uploads only the Parquet file.
- Two formats must stay consistent; the PC writes both from the same rows and its tests decode both, and the app decodes a fixture produced by the PC's encoder.

## References
- Deutsch, P. (1996). *GZIP file format specification version 4.3* (RFC 1952). IETF. https://www.rfc-editor.org/rfc/rfc1952
- Deutsch, P. (1996). *DEFLATE compressed data format specification version 1.3* (RFC 1951). IETF. https://www.rfc-editor.org/rfc/rfc1951
- Google. (n.d.). *Pose landmark detection guide*. MediaPipe. https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
