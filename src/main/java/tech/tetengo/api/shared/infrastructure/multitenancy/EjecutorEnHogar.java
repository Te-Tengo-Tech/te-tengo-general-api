package tech.tetengo.api.shared.infrastructure.multitenancy;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work for one household outside a web request: event listeners and scheduled jobs. It binds the
 * household and only then opens a new transaction, because Hibernate fixes the tenant of a session
 * when the session starts. Jobs process each household in its own call.
 */
@Component
public class EjecutorEnHogar {

    private final TransactionTemplate transaccion;

    public EjecutorEnHogar(PlatformTransactionManager transacciones) {
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void ejecutar(UUID hogarId, Runnable trabajo) {
        obtener(hogarId, () -> {
            trabajo.run();
            return null;
        });
    }

    public <T> T obtener(UUID hogarId, Supplier<T> trabajo) {
        UUID anterior = HogarActual.obtener().orElse(null);
        HogarActual.fijar(hogarId);
        try {
            return transaccion.execute(estado -> trabajo.get());
        } finally {
            if (anterior == null) {
                HogarActual.limpiar();
            } else {
                HogarActual.fijar(anterior);
            }
        }
    }
}
