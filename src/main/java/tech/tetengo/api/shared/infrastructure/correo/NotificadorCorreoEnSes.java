package tech.tetengo.api.shared.infrastructure.correo;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/**
 * E-mail through Amazon SES (API v2, {@code SendEmail}) as plain UTF-8 text. A failure is logged
 * and not propagated [implementation choice]: password recovery must answer the same whether or not
 * the account exists (CA-03.2), so the caller never learns about it. Locally the messages land in
 * Floci, listed at {@code GET http://localhost:4566/_aws/ses}.
 */
public class NotificadorCorreoEnSes implements NotificadorCorreo, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoEnSes.class);

    private final SesV2Client ses;
    private final String remitente;

    NotificadorCorreoEnSes(SesV2Client ses, String remitente) {
        if (!StringUtils.hasText(remitente)) {
            throw new IllegalStateException("tetengo.correo.ses.remitente (TT_SES_REMITENTE) es obligatorio con SES");
        }
        this.ses = ses;
        this.remitente = remitente;
    }

    static NotificadorCorreoEnSes crear(PropiedadesDeSes propiedades) {
        var cliente = propiedades.cliente();
        log.info("Correo por Amazon SES ({}) desde {}", cliente.destino(), propiedades.remitente());
        return new NotificadorCorreoEnSes(
                cliente.configurar(SesV2Client.builder()).build(), propiedades.remitente());
    }

    @Override
    public void enviar(Correo correo) {
        SendEmailRequest solicitud = SendEmailRequest.builder()
                .fromEmailAddress(remitente)
                .destination(Destination.builder().toAddresses(correo.para()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(texto(correo.asunto()))
                                .body(Body.builder()
                                        .text(texto(correo.cuerpo()))
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            String id = ses.sendEmail(solicitud).messageId();
            log.info("Correo enviado por SES a {} ({}): {}", correo.para(), id, correo.asunto());
        } catch (SdkException e) {
            log.error("SES no aceptó el correo para {}: {}", correo.para(), correo.asunto(), e);
        }
    }

    private static Content texto(String valor) {
        return Content.builder()
                .data(valor)
                .charset(StandardCharsets.UTF_8.name())
                .build();
    }

    @Override
    public void close() {
        ses.close();
    }
}
