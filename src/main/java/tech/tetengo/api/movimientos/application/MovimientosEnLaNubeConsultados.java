package tech.tetengo.api.movimientos.application;

import java.time.Instant;

/**
 * US-37 / CA-37.1: the cloud retention and what is stored.
 *
 * @param retencionDias null: not stored in the cloud
 * @param datosDesde start of the oldest movement stored («Hay movimientos desde»)
 * @param archivos movement files in the index
 * @param tamanoBytes bytes stored: the movement files plus their confirmed light replay files (ADR 0011)
 */
public record MovimientosEnLaNubeConsultados(
        Integer retencionDias, boolean consentimientoVigente, Instant datosDesde, long archivos, long tamanoBytes) {}
