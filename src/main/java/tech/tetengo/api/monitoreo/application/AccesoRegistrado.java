package tech.tetengo.api.monitoreo.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry of the access log. {@code camaraIds}: the cameras watched, in the order they were first
 * watched; one, except for a «Ver todas» opening ({@code mosaico}, US-41), which lists every camera
 * seen and whose {@code camaraId} is the first of them.
 */
public record AccesoRegistrado(
        UUID usuarioId,
        String nombre,
        Instant inicio,
        long duracionSegundos,
        boolean desdeAlerta,
        UUID camaraId,
        boolean vistaPrevia,
        List<UUID> camaraIds,
        boolean mosaico) {}
