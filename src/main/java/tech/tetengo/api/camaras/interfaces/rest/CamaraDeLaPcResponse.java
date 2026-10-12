package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.camaras.application.CamaraDeLaPc;

/**
 * {@code CamaraDeLaPc} of AGENT_CONTRACT.md: a webcam of the PC, its room when it is in use, and its
 * per-camera token when one was issued.
 */
record CamaraDeLaPcResponse(
        UUID camaraId,
        String dispositivoId,
        String nombreDispositivo,
        boolean enUso,
        boolean conectada,
        UUID ambienteId,
        String nombreHabitacion,
        String estadoConexion,
        Instant pausadaHasta,
        String token,
        Instant expiraEn) {

    static CamaraDeLaPcResponse de(CamaraDeLaPc camaraDeLaPc) {
        var camara = camaraDeLaPc.camara();
        var token = camaraDeLaPc.token();
        boolean enUso = camara.enUso();
        return new CamaraDeLaPcResponse(
                camara.getId(),
                camara.getDispositivoId(),
                camara.getNombreDispositivo(),
                enUso,
                camara.isDetectadaConectada(),
                enUso ? camara.getAmbienteId() : null,
                enUso ? camara.getNombreHabitacion() : null,
                camara.getEstadoConexion().name(),
                enUso ? camara.getPausadaHasta() : null,
                token == null ? null : token.valor(),
                token == null ? null : token.expiraEn());
    }
}
