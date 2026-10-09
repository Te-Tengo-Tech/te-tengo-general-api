package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;

interface EventoDeAgenteJpaRepository extends JpaRepository<EventoDeAgente, UUID> {

    Optional<EventoDeAgente> findByEventoId(UUID eventoId);
}
