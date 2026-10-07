package tech.tetengo.api.monitoreo;

import java.time.Instant;
import java.util.UUID;

/** US-22 / CA-22.3: a pause ended on its own and capture resumed. */
public record PausaFinalizada(UUID hogarId, UUID camaraId, String habitacion, Instant finalizadaEn) {}
