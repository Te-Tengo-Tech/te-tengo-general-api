package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record CamaraResponse(UUID id, String nombreHabitacion, String estadoConexion, Instant ultimaSenal) {}
