package tech.tetengo.api.movimientos.application;

import java.time.Instant;
import java.util.UUID;

/** CA-39.3: an entry of the access log: who replayed which camera and moment, when and how long. */
public record AccesoARepeticion(
        UUID id,
        UUID usuarioId,
        String nombre,
        UUID camaraId,
        String habitacion,
        Instant momentoDesde,
        Instant momentoHasta,
        Instant inicio,
        long duracionSegundos) {}
