package tech.tetengo.api.alertas.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.camaras.CamaraDesconectada;
import tech.tetengo.api.camaras.CamaraReconectada;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;

/**
 * US-07: tells the family when a camera disconnects (CA-07.2; the app shows what to check: cable, PC
 * on and internet) and when monitoring is restored (CA-07.3).
 */
@Service
public class AvisosDeCamaras {

    private final EnvioDeAvisos avisos;

    public AvisosDeCamaras(EnvioDeAvisos avisos) {
        this.avisos = avisos;
    }

    @Async
    @TransactionalEventListener
    public void alDesconectarse(CamaraDesconectada evento) {
        avisos.alHogar(
                evento.hogarId(),
                new Aviso(
                        TipoAviso.CAMARA_DESCONECTADA,
                        null,
                        evento.camaraId(),
                        evento.habitacion(),
                        evento.ocurridaEn()));
    }

    @Async
    @TransactionalEventListener
    public void alReconectarse(CamaraReconectada evento) {
        avisos.alHogar(
                evento.hogarId(),
                new Aviso(
                        TipoAviso.CAMARA_RECONECTADA,
                        null,
                        evento.camaraId(),
                        evento.habitacion(),
                        evento.ocurridaEn()));
    }
}
