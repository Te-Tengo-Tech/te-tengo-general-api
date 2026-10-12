/**
 * The published versions of the apps (US-43, EP09): the latest version of Te Tengo Captura and of the
 * mobile app on each channel, their download and SHA-256, the release notes and the minimum version
 * the backend still supports. The release workflows publish them as one JSON manifest per channel
 * next to the downloads; this module reads them, caches them and serves {@code GET /api/versiones} (ADR 0012).
 * Public API: {@code VersionesPublicadas}. It depends on no other module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Versiones")
package tech.tetengo.api.versiones;
