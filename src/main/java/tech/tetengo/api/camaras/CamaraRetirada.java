package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/** US-29: «Dejar de usar» on the PC: the camera no longer watches its room. */
public record CamaraRetirada(
        UUID hogarId, UUID camaraId, UUID pcId, String nombreDispositivo, String ambiente, Instant ocurridaEn) {}
