package tech.tetengo.api.hogares.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;

interface SubidaDeFotoJpaRepository extends JpaRepository<SubidaDeFoto, UUID> {

    List<SubidaDeFoto> findByPedidaEnBeforeOrderByPedidaEn(Instant instante, Limit limite);
}
