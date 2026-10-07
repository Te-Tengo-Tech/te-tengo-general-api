package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record RegistroDeCamaraResponse(UUID camaraId, String token, Instant expiraEn) {}
