package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Camara;

/**
 * {@code CamaraDetectada} of the API contract (§9): a webcam the PC detected, in use in a room or
 * «sin usar» («Elige las cámaras», US-29).
 */
record CamaraDetectadaResponse(
        UUID id,
        UUID pcId,
        String nombreDispositivo,
        boolean enUso,
        boolean conectada,
        UUID ambienteId,
        String nombreHabitacion,
        String estadoConexion,
        Instant pausadaHasta,
        Instant detectadaEn) {

    static CamaraDetectadaResponse de(Camara camara) {
        boolean enUso = camara.enUso();
        return new CamaraDetectadaResponse(
                camara.getId(),
                camara.getPcId(),
                camara.getNombreDispositivo(),
                enUso,
                enUso ? camara.getEstadoConexion().name().equals("EN_LINEA") : camara.isDetectadaConectada(),
                enUso ? camara.getAmbienteId() : null,
                enUso ? camara.getNombreHabitacion() : null,
                enUso ? camara.getEstadoConexion().name() : null,
                enUso ? camara.getPausadaHasta() : null,
                camara.getDetectadaEn());
    }
}
