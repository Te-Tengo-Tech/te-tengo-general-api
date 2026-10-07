package tech.tetengo.api.hogares.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record ConsentimientoResponse(
        Instant otorgadoEn,
        String otorgadoPor,
        RegistradoPor registradoPor,
        boolean vistaEnVivoAceptada,
        boolean vigente) {

    record RegistradoPor(UUID id, String nombre) {}
}
