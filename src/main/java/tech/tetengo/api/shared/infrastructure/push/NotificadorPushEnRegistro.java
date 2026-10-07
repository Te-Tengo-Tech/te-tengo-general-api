package tech.tetengo.api.shared.infrastructure.push;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/** Default push adapter ({@code tetengo.push.proveedor=registro}): it only logs. */
class NotificadorPushEnRegistro implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushEnRegistro.class);

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        log.info(
                "Push {} (sin enviar, proveedor registro) a {} dispositivo(s): {}",
                aviso.tipo(),
                destinos.size(),
                aviso);
        return Resultado.aceptados(destinos);
    }
}
