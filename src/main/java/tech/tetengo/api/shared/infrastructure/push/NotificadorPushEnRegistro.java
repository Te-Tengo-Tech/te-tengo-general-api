package tech.tetengo.api.shared.infrastructure.push;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.tetengo.api.shared.application.port.NotificadorPush;

/** Fake adapter until Amazon SNS is configured (see {@code docs/BLOCKERS.md}): it only logs. */
@Component
class NotificadorPushEnRegistro implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushEnRegistro.class);

    @Override
    public void enviar(List<Destino> destinos, Aviso aviso) {
        log.info(
                "Push {} (sin enviar, falta Amazon SNS) a {} dispositivo(s): {}", aviso.tipo(), destinos.size(), aviso);
    }
}
