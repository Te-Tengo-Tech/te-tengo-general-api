package tech.tetengo.api.shared.infrastructure.push;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/**
 * Default push adapter ({@code tetengo.push.proveedor=registro}): it only logs. The data payload goes
 * at {@code INFO}; the title and body, which name the older adult, only at {@code DEBUG}.
 */
class NotificadorPushEnRegistro implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushEnRegistro.class);

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        ContenidoDelAviso contenido = ContenidoDelAviso.de(aviso);
        log.info(
                "Push {} (sin enviar, proveedor registro) a {} dispositivo(s): {}",
                aviso.tipo(),
                destinos.size(),
                contenido.datos());
        log.debug("Push {}: «{}» «{}»", aviso.tipo(), contenido.titulo(), contenido.cuerpo());
        return Resultado.aceptados(destinos);
    }
}
