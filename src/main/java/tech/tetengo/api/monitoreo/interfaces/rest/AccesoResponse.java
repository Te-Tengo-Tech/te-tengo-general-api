package tech.tetengo.api.monitoreo.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record AccesoResponse(Usuario usuario, Instant inicio, long duracionSegundos, boolean desdeAlerta) {

    record Usuario(UUID id, String nombre) {}
}
