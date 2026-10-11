package tech.tetengo.api.movimientos;

import java.time.Instant;
import java.util.UUID;

/** CA-35.4: the cloud movements of a revoked consent were deleted; the family is told. */
public record MovimientosEliminados(UUID hogarId, long archivos, Instant ocurridaEn) {}
