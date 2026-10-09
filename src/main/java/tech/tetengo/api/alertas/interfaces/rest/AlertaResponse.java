package tech.tetengo.api.alertas.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** {@code Alerta} of the API contract. */
record AlertaResponse(
        UUID id,
        String tipo,
        String severidad,
        String estado,
        boolean confirmada,
        UUID camaraId,
        String habitacion,
        Instant ocurridaEn,
        Instant notificadaEn,
        Instant recuperadaEn,
        AtendidaPor atendidaPor,
        Instant atendidaEn,
        Instant escaladaEn,
        boolean origenInestable,
        String clip) {

    record AtendidaPor(UUID id, String nombre) {}
}
