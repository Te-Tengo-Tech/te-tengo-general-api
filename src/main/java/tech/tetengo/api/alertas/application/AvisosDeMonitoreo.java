package tech.tetengo.api.alertas.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.monitoreo.PausaFinalizada;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** US-22 / CA-22.3: tells the family that a pause ended and capture resumed. */
@Service
public class AvisosDeMonitoreo {

    private final EnvioDeAvisos avisos;
    private final EjecutorEnHogar enHogar;

    public AvisosDeMonitoreo(EnvioDeAvisos avisos, EjecutorEnHogar enHogar) {
        this.avisos = avisos;
        this.enHogar = enHogar;
    }

    @Async
    @TransactionalEventListener
    public void alFinalizarPausa(PausaFinalizada evento) {
        enHogar.ejecutar(
                evento.hogarId(),
                () -> avisos.alHogar(new Aviso(
                        TipoAviso.PAUSA_FINALIZADA,
                        null,
                        evento.camaraId(),
                        evento.habitacion(),
                        evento.finalizadaEn())));
    }
}
