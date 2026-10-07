package tech.tetengo.api.shared.application.port;

/**
 * Outgoing e-mail port (password recovery, invitations). Production will use Amazon SES (see
 * {@code docs/BLOCKERS.md}); until then a logging adapter is used, and tests record the messages.
 */
public interface NotificadorCorreo {

    void enviar(Correo correo);

    record Correo(String para, String asunto, String cuerpo) {}
}
