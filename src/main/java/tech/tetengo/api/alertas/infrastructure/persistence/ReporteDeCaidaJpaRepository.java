package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.alertas.domain.model.ReporteDeCaidaSinAlerta;

interface ReporteDeCaidaJpaRepository extends JpaRepository<ReporteDeCaidaSinAlerta, UUID> {}
