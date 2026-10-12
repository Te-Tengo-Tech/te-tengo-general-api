package tech.tetengo.api.alertas.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.ReporteDeCaidaRepository;
import tech.tetengo.api.alertas.domain.model.ReporteDeCaidaSinAlerta;

@Repository
class ReporteDeCaidaRepositoryAdapter implements ReporteDeCaidaRepository {

    private final ReporteDeCaidaJpaRepository jpa;

    ReporteDeCaidaRepositoryAdapter(ReporteDeCaidaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ReporteDeCaidaSinAlerta guardar(ReporteDeCaidaSinAlerta reporte) {
        return jpa.save(reporte);
    }
}
