package tech.tetengo.api.versiones.interfaces.rest;

import java.time.Instant;
import java.util.List;

/** {@code Versiones} of the API contract (§12): one entry per channel, null when unpublished. */
record VersionesResponse(
        VersionResponse windows,
        VersionResponse mac,
        VersionResponse apk,
        VersionResponse play,
        VersionResponse ios,
        VersionResponse pwa,
        String paginaDescargas) {

    /** {@code Version} of the API contract. */
    record VersionResponse(
            String version,
            String versionMinima,
            String url,
            String sha256,
            Long tamanoBytes,
            Instant publicadaEn,
            List<String> notas) {}
}
