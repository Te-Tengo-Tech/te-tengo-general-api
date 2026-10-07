package tech.tetengo.api.hogares;

import java.time.Instant;
import java.util.UUID;

/** US-09 / CA-09.1: the consent was revoked: capture stops and every recording is to be deleted. */
public record ConsentimientoRevocado(UUID hogarId, Instant revocadoEn) {}
