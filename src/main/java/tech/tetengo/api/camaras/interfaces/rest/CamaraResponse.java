package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

/** {@code Camara} of the API contract. */
record CamaraResponse(
        UUID id,
        String nombreHabitacion,
        String estadoConexion,
        Instant ultimaSenal,
        Instant pausadaHasta,
        boolean deteccionConfiable,
        Instant instaladaEn,
        Instant noConfiableDesde) {}
