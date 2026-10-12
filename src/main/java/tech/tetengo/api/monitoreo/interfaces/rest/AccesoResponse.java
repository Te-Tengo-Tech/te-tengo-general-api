package tech.tetengo.api.monitoreo.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record AccesoResponse(
        Usuario usuario,
        Instant inicio,
        long duracionSegundos,
        boolean desdeAlerta,
        UUID camaraId,
        boolean vistaPrevia,
        List<UUID> camaraIds,
        boolean mosaico) {

    record Usuario(UUID id, String nombre) {}
}
