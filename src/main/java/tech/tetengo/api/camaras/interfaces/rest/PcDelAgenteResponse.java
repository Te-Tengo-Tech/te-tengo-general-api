package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** {@code PcDelAgente} of AGENT_CONTRACT.md (D-28.4). */
record PcDelAgenteResponse(
        UUID pcId,
        UUID hogarId,
        String nombre,
        String nombreAdultoMayor,
        Instant vinculadaEn,
        String vinculadaPor,
        boolean consentimientoVigente) {}
