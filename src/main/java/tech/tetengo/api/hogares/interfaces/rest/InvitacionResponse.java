package tech.tetengo.api.hogares.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

record InvitacionResponse(UUID id, String correo, Instant expiraEn) {}
