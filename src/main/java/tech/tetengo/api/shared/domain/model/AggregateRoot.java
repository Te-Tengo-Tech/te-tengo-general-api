package tech.tetengo.api.shared.domain.model;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

/**
 * Raíz de agregado: frontera de consistencia y punto de entrada de los repositorios. Los eventos
 * registrados con {@link #registrarEvento} se publican al guardar y otros módulos los escuchan con
 * {@code @ApplicationModuleListener}.
 */
@MappedSuperclass
public abstract class AggregateRoot extends AuditableEntity {

    @Transient
    private final transient List<Object> eventos = new ArrayList<>();

    protected AggregateRoot() {
        super();
    }

    protected AggregateRoot(UUID id) {
        super(id);
    }

    protected <E> E registrarEvento(E evento) {
        eventos.add(evento);
        return evento;
    }

    @DomainEvents
    Collection<Object> eventosDeDominio() {
        return Collections.unmodifiableList(eventos);
    }

    @AfterDomainEventPublication
    void limpiarEventos() {
        eventos.clear();
    }
}
