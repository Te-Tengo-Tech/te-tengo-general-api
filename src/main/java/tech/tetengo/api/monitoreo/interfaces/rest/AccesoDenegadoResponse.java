package tech.tetengo.api.monitoreo.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** {@code usuario}: only when {@code miembroDelHogar}; null for anyone else. */
record AccesoDenegadoResponse(
        Instant momento,
        String canal,
        String motivo,
        String accion,
        String protocolo,
        UUID camaraId,
        String nombreHabitacion,
        boolean miembroDelHogar,
        AccesoResponse.Usuario usuario) {}
