package tech.tetengo.api.camaras.application;

import java.time.Instant;

/** CA-28.4: what the approval screen shows before the owner approves the PC. */
public record VinculacionConsultada(
        String codigo, String nombrePc, String sistemaOperativo, String versionAgente, Instant expiraEn) {}
