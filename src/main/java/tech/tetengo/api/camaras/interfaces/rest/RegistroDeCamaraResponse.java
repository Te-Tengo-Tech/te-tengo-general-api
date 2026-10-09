package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record RegistroDeCamaraResponse(UUID camaraId, UUID hogarId, String token, Instant expiraEn, String nombreHabitacion) {}
