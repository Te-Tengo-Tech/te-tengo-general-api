package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;

/** CA-28.4: the PC the owner is about to approve. */
record VinculacionConsultadaResponse(
        String codigo, String nombrePc, String sistemaOperativo, String versionAgente, Instant expiraEn) {}
