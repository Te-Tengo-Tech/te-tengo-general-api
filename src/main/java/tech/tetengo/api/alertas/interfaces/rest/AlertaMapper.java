package tech.tetengo.api.alertas.interfaces.rest;

import tech.tetengo.api.alertas.application.AlertaConsultada;
import tech.tetengo.api.alertas.domain.model.Alerta;

final class AlertaMapper {

    private AlertaMapper() {}

    static AlertaResponse aRespuesta(AlertaConsultada consultada) {
        Alerta a = consultada.alerta();
        AlertaResponse.AtendidaPor atendidaPor = a.getAtendidaPor() == null
                ? null
                : new AlertaResponse.AtendidaPor(
                        a.getAtendidaPor(),
                        consultada.atendidaPor() == null
                                ? null
                                : consultada.atendidaPor().nombre());
        return new AlertaResponse(
                a.getId(),
                a.getTipo().name(),
                a.getSeveridad().name(),
                a.getEstado().name(),
                a.isConfirmada(),
                a.getCamaraId(),
                a.getHabitacion(),
                a.getOcurridaEn(),
                a.getNotificadaEn(),
                a.getEstadoAviso().name(),
                a.getRecuperadaEn(),
                atendidaPor,
                a.getAtendidaEn(),
                a.getEscaladaEn(),
                a.isOrigenInestable(),
                consultada.clip().name());
    }
}
