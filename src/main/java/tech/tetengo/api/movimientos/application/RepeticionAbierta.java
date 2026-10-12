package tech.tetengo.api.movimientos.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * US-39: a replay and the movement files of the moment asked for, each with a short-lived URL.
 *
 * @param disponiblesDesde start of the oldest movement stored (CA-39.4: «Hay datos desde…»)
 * @param guardadoEnLaNube false when the owner did not choose a cloud retention
 */
public record RepeticionAbierta(
        UUID id,
        UUID camaraId,
        String habitacion,
        Instant desde,
        Instant hasta,
        Instant disponiblesDesde,
        boolean guardadoEnLaNube,
        List<Archivo> archivos,
        Instant urlsExpiranEn) {

    /**
     * One movement file.
     *
     * @param url pre-signed GET of the Parquet file
     * @param ligero the same frames in the light replay format (ADR 0011); null unless it was confirmed
     */
    public record Archivo(
            UUID id,
            Instant desde,
            Instant hasta,
            long tamanoBytes,
            int fotogramas,
            String sha256,
            URI url,
            Ligero ligero) {}

    /** ADR 0011: the light replay file, with a pre-signed GET of the same expiry as the Parquet file's. */
    public record Ligero(URI url, long tamanoBytes, String sha256) {}
}
