package tech.tetengo.api.hogares;

import java.time.Instant;
import java.util.UUID;

/** US-05 / CA-05.1: the older adult's consent was registered, so camera capture may start. */
public record ConsentimientoOtorgado(UUID hogarId, Instant otorgadoEn) {}
