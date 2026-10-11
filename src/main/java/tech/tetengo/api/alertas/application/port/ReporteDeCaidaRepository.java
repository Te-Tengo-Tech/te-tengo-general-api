package tech.tetengo.api.alertas.application.port;

import tech.tetengo.api.alertas.domain.model.ReporteDeCaidaSinAlerta;

public interface ReporteDeCaidaRepository {

    ReporteDeCaidaSinAlerta guardar(ReporteDeCaidaSinAlerta reporte);
}
