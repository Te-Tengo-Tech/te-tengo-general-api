# 0012. Published versions as manifests next to the downloads

**Status:** proposed (US-43, change request «Vista de varias cámaras, audio y actualizaciones», pending approval).

## Context
- US-43 asks Te Tengo Captura and the mobile app to tell the family when there is a new version and to update with few steps (CA-43.1 to CA-43.6), Windows to update itself at night (CA-43.7), and every app to stop politely when the backend no longer supports its version (CA-43.8). CA-43.1 names `GET /api/versiones` or «el ajuste de versión publicada del backend».
- Until now the only published version was `TT_AGENTE_VERSION_PUBLICADA`, set by hand in `te-tengo-infra` (`ansible/prod.yml`) after each desktop release and returned by `GET /api/agente/configuracion`, which the agent only logs. Nothing knew the installer's address or SHA-256, nor the mobile app's versions.
- Binaries reach production through the `produccion.yml` workflows of each app repository: they promote a tested release candidate to the Cloudflare R2 bucket the landing links to (`te-tengo-captura-setup.exe`, `te-tengo-captura.dmg`, `te-tengo.apk`), deploy the PWA to Cloudflare Pages and upload the AAB and IPA to Google Play and TestFlight. Those jobs already have the bucket's credentials and the candidate's `SHA256SUMS`.
- The options were: (1) each release dispatches a change of the API's configuration in `te-tengo-infra`, which redeploys the API; (2) the API reads the GitHub Releases of the two repositories; (3) each production run writes a small JSON manifest to the downloads bucket and the API reads it.
- (1) restarts the API for every app release and spreads one release across three repositories. (2) depends on GitHub's API rate limits (60 unauthenticated requests per hour) and does not say which channels were published (a release exists even when only the APK went out), nor where each file is served.

## Decision
- **Option 3, one manifest per channel.** Each production job that publishes a channel writes that channel's manifest to the public downloads bucket, next to the binaries, with `Cache-Control: no-cache`: `versiones/windows.json` and `versiones/mac.json` (`produccion.yml` of `te-tengo-desktop-pywebview`), `versiones/apk.json`, `versiones/pwa.json`, `versiones/play.json` and `versiones/ios.json` (`produccion.yml` of `te-tengo-mobile-flutter`):
  ```json
  { "version": "0.5.0", "url": "https://<bucket>/te-tengo-captura-setup.exe",
    "sha256": "<64 hex from SHA256SUMS>", "tamanoBytes": 90177536,
    "publicadaEn": "2026-09-22T08:00:00Z", "notas": ["Detecta mejor las caídas con poca luz."] }
  ```
  One file per channel means the parallel jobs of the mobile repository never read and rewrite the same file, a channel that is switched off keeps its last manifest, and a rollback rewrites only its channel. The manifest goes up right after the channel's binary, in the same job, so it never announces a file that is not there. `notas` come from the release's section of the repository's `NOVEDADES.md` (short lines in Spanish for the family; the CHANGELOG stays technical).
- **The API reads the six manifests** from `TT_VERSIONES_URL_BASE` (the bucket's public URL) and serves `GET /api/versiones` (API contract §12): public, `Cache-Control: public, max-age=300` and an `ETag`. It reads them again every 5 minutes, one request at a time while the others are served what was read, with 5-second timeouts. A manifest that cannot be read, or that is invalid, keeps its last good contents; a `404` means nothing is published on that channel. Every manifest is checked (a `MAJOR.MINOR.PATCH` version, an HTTPS address, a 64-hex SHA-256 for a file, short notes) and an invalid one is logged.
- **The minimum supported version (CA-43.8) is the backend's setting** (`TT_VERSION_MINIMA_<CANAL>`): it is the backend that stops supporting a version, so it is decided with the API's configuration, and capped at the published version.
- **`TT_AGENTE_VERSION_PUBLICADA` becomes a fallback:** while no manifest publishes Te Tengo Captura, `GET /api/versiones` gives that version alone (no download), and `GET /api/agente/configuracion` returns the Windows version of the manifest, or that setting.

## Consequences
- A release reaches the apps within about 5 minutes of its production run (the API's cache), plus up to 5 more minutes of HTTP caching in clients, with no manual step and no API restart. `TT_VERSIONES_URL_BASE` is set once per environment.
- The binaries keep a fixed address (the landing's links), so for a few minutes between the binary's upload and the manifest's, `sha256` and the file may differ: clients check the SHA-256 before installing, fail safely and try again later (CA-43.2).
- The manifest is public, like the binaries; it holds no personal data.
- A rollback that republishes an older binary also republishes that channel's manifest (the rollback workflows do it), so clients are not told to «update» to a file that is no longer served.
- If the bucket is unreachable when the API starts, `GET /api/versiones` answers with the fallback only until the next read.

## References
- Fielding, R., Nottingham, M., & Reschke, J. (Eds.). (2022). *HTTP Semantics* (RFC 9110), §13.1.2 If-None-Match. IETF. https://www.rfc-editor.org/rfc/rfc9110
- Fielding, R., Nottingham, M., & Reschke, J. (Eds.). (2022). *HTTP Caching* (RFC 9111). IETF. https://www.rfc-editor.org/rfc/rfc9111
- Preston-Werner, T. (n.d.). *Semantic Versioning 2.0.0*. https://semver.org/spec/v2.0.0.html
