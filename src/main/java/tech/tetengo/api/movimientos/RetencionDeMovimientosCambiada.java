package tech.tetengo.api.movimientos;

import java.time.Instant;
import java.util.UUID;

/** US-37 / CA-37.1: the owner changed how long movements are kept in the cloud (null: not stored). */
public record RetencionDeMovimientosCambiada(
        UUID hogarId, Integer diasAnteriores, Integer diasNuevos, UUID usuarioId, Instant ocurridaEn) {}
