package tech.tetengo.api.alertas.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.movimientos.MovimientosEliminados;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/** CA-35.4: the family is told when the cloud movements of a revoked consent are deleted. */
@Service
public class AvisosDeMovimientos {

    private final EnvioDeAvisos avisos;
    private final EjecutorEnHogar enHogar;

    public AvisosDeMovimientos(EnvioDeAvisos avisos, EjecutorEnHogar enHogar) {
        this.avisos = avisos;
        this.enHogar = enHogar;
    }

    @Async
    @TransactionalEventListener
    public void alEliminarseMovimientos(MovimientosEliminados evento) {
        enHogar.ejecutar(
                evento.hogarId(),
                () -> avisos.alHogar(
                        new Aviso(TipoAviso.MOVIMIENTOS_ELIMINADOS, null, null, null, evento.ocurridaEn())));
    }
}
