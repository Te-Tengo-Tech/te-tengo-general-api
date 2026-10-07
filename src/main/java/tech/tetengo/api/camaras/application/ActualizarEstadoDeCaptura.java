package tech.tetengo.api.camaras.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;
import tech.tetengo.api.hogares.ConsentimientoOtorgado;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** US-05: consent events of {@code hogares} turn capture on in the household (CA-05.1). */
@Service
public class ActualizarEstadoDeCaptura {

    private final EstadoDeCapturaRepository estados;
    private final EjecutorEnHogar enHogar;

    public ActualizarEstadoDeCaptura(EstadoDeCapturaRepository estados, EjecutorEnHogar enHogar) {
        this.estados = estados;
        this.enHogar = enHogar;
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
