package tech.tetengo.api.actividad.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.actividad.application.ActividadConsultada;

/** {@code Actividad} of the API contract (§10). */
record ActividadResponse(
        UUID id,
        Instant ocurridaEn,
        Instant registradaEn,
        String tipo,
        Actor actor,
        Pc pc,
        UUID camaraId,
        String objeto,
        String valorAnterior,
        String valorNuevo) {

    /** {@code FAMILIAR} with {@code usuario}, {@code PC} (see {@code pc}) or {@code SISTEMA}. */
    record Actor(String tipo, Usuario usuario) {}

    record Usuario(UUID id, String nombre) {}

    record Pc(UUID id, String nombre) {}

    static ActividadResponse de(ActividadConsultada consultada) {
        var a = consultada.actividad();
        return new ActividadResponse(
                a.getId(),
                a.getOcurridaEn(),
                a.getCreadoEn(),
                a.getTipo().name(),
                new Actor(
                        a.getOrigen().name(),
                        a.getUsuarioId() == null ? null : new Usuario(a.getUsuarioId(), consultada.nombreDelUsuario())),
                a.getPcId() == null ? null : new Pc(a.getPcId(), a.getPcNombre()),
                a.getCamaraId(),
                a.getObjeto(),
                a.getValorAnterior(),
                a.getValorNuevo());
    }
}
