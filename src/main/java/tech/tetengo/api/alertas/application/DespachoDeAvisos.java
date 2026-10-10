package tech.tetengo.api.alertas.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a queued push notice right after the transaction that queued it commits, on another thread:
 * the household agent's request answers without waiting for the push service, the alert is already
 * saved when the family opens it, and a rolled-back change sends nothing. If this attempt fails or
 * the API stops before it, the notice stays queued and {@link ReintentarAvisos} sends it.
 */
@Service
public class DespachoDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(DespachoDeAvisos.class);

    private final EnvioDeAvisos envio;

    public DespachoDeAvisos(EnvioDeAvisos envio) {
        this.envio = envio;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void alEncolar(AvisoEncolado evento) {
        try {
            envio.intentar(evento.hogarId(), evento.avisoId());
        } catch (RuntimeException e) {
            // Still queued: the retry job takes it from here.
            log.error("Falló el primer intento del push {}; queda para el reintento", evento.avisoId(), e);
        }
    }
}
