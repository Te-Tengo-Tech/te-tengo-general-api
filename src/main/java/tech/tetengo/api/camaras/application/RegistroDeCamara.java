package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.UUID;

/** The camera registered by the agent and its per-camera token. */
public record RegistroDeCamara(UUID camaraId, String token, Instant expiraEn) {}
