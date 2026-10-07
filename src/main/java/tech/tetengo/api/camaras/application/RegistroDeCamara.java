package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.UUID;

/**
 * The camera registered by the agent, its per-camera token and the room name stored in the backend
 * (the family may have renamed it, CA-06.2).
 */
public record RegistroDeCamara(UUID camaraId, UUID hogarId, String token, Instant expiraEn, String nombreHabitacion) {}
