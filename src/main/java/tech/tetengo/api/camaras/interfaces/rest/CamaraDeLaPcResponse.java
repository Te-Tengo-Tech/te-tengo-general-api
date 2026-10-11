package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.camaras.application.CamaraDeLaPc;

/** {@code CamaraDeLaPc} of AGENT_CONTRACT.md: the camera, and its token when one was issued. */
record CamaraDeLaPcResponse(
        UUID camaraId,
        String dispositivoId,
        String nombreDispositivo,
        UUID ambienteId,
        String nombreHabitacion,
        String estadoConexion,
        Instant pausadaHasta,
        String token,
        Instant expiraEn) {

    static CamaraDeLaPcResponse de(CamaraDeLaPc camaraDeLaPc) {
        var camara = camaraDeLaPc.camara();
        var token = camaraDeLaPc.token();
        return new CamaraDeLaPcResponse(
                camara.getId(),
                camara.getDispositivoId(),
                camara.getNombreDispositivo(),
                camara.getAmbienteId(),
                camara.getNombreHabitacion(),
                camara.getEstadoConexion().name(),
                camara.getPausadaHasta(),
                token == null ? null : token.valor(),
                token == null ? null : token.expiraEn());
    }
}
