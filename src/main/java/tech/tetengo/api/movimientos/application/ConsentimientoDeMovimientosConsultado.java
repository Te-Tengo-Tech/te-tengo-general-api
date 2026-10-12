package tech.tetengo.api.movimientos.application;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;

/**
 * US-35: the household's latest consent to store movements (null if never registered), whether it is in
 * force, the text version the app must show, and the deletion after the latest revocation.
 */
public record ConsentimientoDeMovimientosConsultado(
        boolean vigente,
        String versionTextoVigente,
        ConsentimientoDeMovimientos consentimiento,
        UUID registradoPorId,
        String registradoPorNombre,
        Eliminacion eliminacion) {

    /** CA-35.4: {@code PROGRAMADA} until the cloud movements are deleted, then {@code TERMINADA}. */
    public record Eliminacion(String estado, long archivos, Instant programadaEn, Instant terminadaEn) {}
}
