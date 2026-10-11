package tech.tetengo.api.movimientos;

import java.time.Instant;
import java.util.UUID;

/** US-35 / CA-35.2: the owner registered the older adult's consent to store movements. */
public record ConsentimientoDeMovimientosOtorgado(
        UUID hogarId, String otorgadoPor, UUID registradoPor, String versionTexto, Instant ocurridoEn) {}
