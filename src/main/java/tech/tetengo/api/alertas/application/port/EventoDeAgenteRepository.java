package tech.tetengo.api.alertas.application.port;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.EventoDeAgente;

public interface EventoDeAgenteRepository {

    EventoDeAgente guardar(EventoDeAgente evento);

    Optional<EventoDeAgente> buscarPorEventoId(UUID eventoId);
}
