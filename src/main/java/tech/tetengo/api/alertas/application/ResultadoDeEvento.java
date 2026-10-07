package tech.tetengo.api.alertas.application;

import java.util.UUID;
import tech.tetengo.api.shared.application.port.TipoAviso;

/**
 * What an agent event did: the alert it created or updated (if any) and the push notice it calls
 * for (if any). A repeated {@code eventoId} returns the first result and no notice.
 */
public record ResultadoDeEvento(UUID eventoId, UUID alertaId, TipoAviso aviso) {}
