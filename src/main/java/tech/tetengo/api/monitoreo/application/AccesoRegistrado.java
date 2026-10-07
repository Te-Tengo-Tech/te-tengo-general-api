package tech.tetengo.api.monitoreo.application;

import java.time.Instant;
import java.util.UUID;

public record AccesoRegistrado(
        UUID usuarioId, String nombre, Instant inicio, long duracionSegundos, boolean desdeAlerta) {}
