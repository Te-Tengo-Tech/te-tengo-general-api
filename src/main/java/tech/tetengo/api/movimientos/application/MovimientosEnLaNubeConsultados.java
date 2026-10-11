package tech.tetengo.api.movimientos.application;

import java.time.Instant;

/**
 * US-37 / CA-37.1: the cloud retention and what is stored.
 *
 * @param retencionDias null: not stored in the cloud
 * @param datosDesde start of the oldest movement stored («Hay movimientos desde»)
 */
public record MovimientosEnLaNubeConsultados(
        Integer retencionDias, boolean consentimientoVigente, Instant datosDesde, long archivos, long tamanoBytes) {}
