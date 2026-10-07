package tech.tetengo.api.camaras.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;
import tech.tetengo.api.hogares.ConsentimientoOtorgado;
import tech.tetengo.api.hogares.ConsentimientoRevocado;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** Consent events of {@code hogares} turn capture on (CA-05.1) and off (CA-09.1) in the household. */
@Service
public class ActualizarEstadoDeCaptura {

    private final EstadoDeCapturaRepository estados;
    private final EjecutorEnHogar enHogar;

    public ActualizarEstadoDeCaptura(EstadoDeCapturaRepository estados, EjecutorEnHogar enHogar) {
        this.estados = estados;
        this.enHogar = enHogar;
    }

    /** US-09 / CA-09.1: capture stops in the household. */
    @Async
    @TransactionalEventListener
    public void alRevocarseConsentimiento(ConsentimientoRevocado evento) {
        enHogar.ejecutar(
                evento.hogarId(),
                () -> estados.actual().ifPresent(estado -> {
                    estado.revocarConsentimiento();
                    estados.guardar(estado);
                }));
    }

    @Async
    @TransactionalEventListener
    public void alOtorgarseConsentimiento(ConsentimientoOtorgado evento) {
        enHogar.ejecutar(evento.hogarId(), () -> {
            EstadoDeCaptura estado = estados.actual().orElseGet(EstadoDeCaptura::new);
            estado.otorgarConsentimiento();
            estados.guardar(estado);
        });
    }
}
