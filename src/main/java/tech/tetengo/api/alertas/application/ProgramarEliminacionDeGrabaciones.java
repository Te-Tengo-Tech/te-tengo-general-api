package tech.tetengo.api.alertas.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.alertas.application.port.EliminacionDeGrabacionesRepository;
import tech.tetengo.api.alertas.domain.model.EliminacionDeGrabaciones;
import tech.tetengo.api.hogares.ConsentimientoRevocado;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** US-09 / CA-09.1: revoking the consent schedules the deletion of every recording of the household. */
@Service
public class ProgramarEliminacionDeGrabaciones {

    private final EliminacionDeGrabacionesRepository eliminaciones;
    private final EjecutorEnHogar enHogar;

    public ProgramarEliminacionDeGrabaciones(
            EliminacionDeGrabacionesRepository eliminaciones, EjecutorEnHogar enHogar) {
        this.eliminaciones = eliminaciones;
        this.enHogar = enHogar;
    }

    @Async
    @TransactionalEventListener
    public void alRevocarseConsentimiento(ConsentimientoRevocado evento) {
        enHogar.ejecutar(
                evento.hogarId(), () -> eliminaciones.guardar(new EliminacionDeGrabaciones(evento.revocadoEn())));
    }
}
