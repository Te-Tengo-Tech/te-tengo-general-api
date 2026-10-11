package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.UUID;

/**
 * What the PC shows about its link (D-28.4, «Esta PC quedó vinculada a la casa de Rosa Huamán ·
 * Aprobada por Carmen a las 10:31») and whether it may capture at all (CA-28.1: without the
 * consent it waits without sending video).
 */
public record PcDelAgente(
        UUID pcId,
        UUID hogarId,
        String nombre,
        String nombreAdultoMayor,
        Instant vinculadaEn,
        String vinculadaPor,
        boolean consentimientoVigente) {}
