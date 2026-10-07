package tech.tetengo.api.shared.infrastructure.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/**
 * Fake adapter until Amazon SES is configured: it only logs. The body may carry one-time links, so it
 * is logged at DEBUG level only (enable it locally to follow the links).
 */
@Component
class NotificadorCorreoEnRegistro implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoEnRegistro.class);

    @Override
    public void enviar(Correo correo) {
        log.info("Correo (sin enviar, falta Amazon SES) para {}: {}", correo.para(), correo.asunto());
        log.debug("Cuerpo del correo para {}:\n{}", correo.para(), correo.cuerpo());
    }
}
