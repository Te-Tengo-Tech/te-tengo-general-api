package tech.tetengo.api.shared.infrastructure.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/**
 * E-mail through any SMTP relay ({@code tetengo.correo.proveedor=smtp}) with Spring's {@link
 * JavaMailSender}, configured by the standard {@code spring.mail.*} properties. It sends the same
 * message as the SES adapter: plain UTF-8 text with the given subject and body. As with SES, a
 * failure is logged and not propagated [implementation choice]: password recovery must answer the
 * same whether or not the account exists (CA-03.2).
 */
class NotificadorCorreoEnSmtp implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoEnSmtp.class);

    private final JavaMailSender correo;
    private final String remitente;

    NotificadorCorreoEnSmtp(JavaMailSender correo, String remitente) {
        if (!StringUtils.hasText(remitente)) {
            throw new IllegalStateException(
                    "tetengo.correo.smtp.remitente (TT_SMTP_REMITENTE) es obligatorio con SMTP");
        }
        this.correo = correo;
        this.remitente = remitente;
        log.info("Correo por SMTP desde {}", remitente);
    }

    @Override
    public void enviar(Correo mensaje) {
        try {
            MimeMessage mime = correo.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            ayudante.setFrom(remitente);
            ayudante.setTo(mensaje.para());
            ayudante.setSubject(mensaje.asunto());
            ayudante.setText(mensaje.cuerpo(), false);
            correo.send(mime);
            log.info("Correo enviado por SMTP a {}: {}", mensaje.para(), mensaje.asunto());
        } catch (MailException | MessagingException e) {
            log.error("El servidor SMTP no aceptó el correo para {}: {}", mensaje.para(), mensaje.asunto(), e);
        }
    }
}
