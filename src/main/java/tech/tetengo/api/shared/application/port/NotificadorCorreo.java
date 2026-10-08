package tech.tetengo.api.shared.application.port;

/**
 * Outgoing e-mail port (password recovery, invitations). {@code tetengo.correo.proveedor} chooses
 * the adapter: Amazon SES, any SMTP relay, or a logging one by default (docs/NOTIFICATIONS.md). Tests record the
 * messages.
 */
public interface NotificadorCorreo {

    void enviar(Correo correo);

    record Correo(String para, String asunto, String cuerpo) {}
}
