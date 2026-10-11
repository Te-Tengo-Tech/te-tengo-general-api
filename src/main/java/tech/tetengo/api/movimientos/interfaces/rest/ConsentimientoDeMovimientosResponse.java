package tech.tetengo.api.movimientos.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.movimientos.application.ConsentimientoDeMovimientosConsultado;

/** {@code ConsentimientoDeMovimientos} of the API contract (§11). */
record ConsentimientoDeMovimientosResponse(
        boolean vigente, String versionTextoVigente, Constancia constancia, Eliminacion eliminacion) {

    record Constancia(
            Instant otorgadoEn,
            String otorgadoPor,
            boolean representante,
            Usuario registradoPor,
            String versionTexto,
            Instant revocadoEn) {}

    record Usuario(UUID id, String nombre) {}

    record Eliminacion(String estado, long archivos, Instant programadaEn, Instant terminadaEn) {}

    static ConsentimientoDeMovimientosResponse de(ConsentimientoDeMovimientosConsultado c) {
        var consentimiento = c.consentimiento();
        var eliminacion = c.eliminacion();
        return new ConsentimientoDeMovimientosResponse(
                c.vigente(),
                c.versionTextoVigente(),
                consentimiento == null
                        ? null
                        : new Constancia(
                                consentimiento.getOtorgadoEn(),
                                consentimiento.getOtorgadoPor(),
                                consentimiento.isRepresentante(),
                                new Usuario(c.registradoPorId(), c.registradoPorNombre()),
                                consentimiento.getVersionTexto(),
                                consentimiento.getRevocadoEn()),
                eliminacion == null
                        ? null
                        : new Eliminacion(
                                eliminacion.estado(),
                                eliminacion.archivos(),
                                eliminacion.programadaEn(),
                                eliminacion.terminadaEn()));
    }
}
