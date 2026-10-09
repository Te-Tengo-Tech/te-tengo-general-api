package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.alertas.application.port.EventoDeAgenteRepository;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;

@Repository
class EventoDeAgenteRepositoryAdapter implements EventoDeAgenteRepository {

    private final EventoDeAgenteJpaRepository jpa;

    EventoDeAgenteRepositoryAdapter(EventoDeAgenteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EventoDeAgente guardar(EventoDeAgente evento) {
        return jpa.save(evento);
    }

    @Override
    public Optional<EventoDeAgente> buscarPorEventoId(UUID eventoId) {
        return jpa.findByEventoId(eventoId);
    }
}
