package tech.tetengo.api.shared.application.port;

import java.util.EnumSet;
import java.util.Set;

/** Push notice types of the API contract (§7). */
public enum TipoAviso {
    ALERTA_CAIDA,
    ALERTA_MOVIMIENTO_INESTABLE,
    ALERTA_ACTUALIZADA_A_CAIDA,
    CAIDA_CONFIRMADA,
    SE_LEVANTO,
    ALERTA_ATENDIDA,
    ALERTA_ESCALADA,
    SIN_CONTACTO_SECUNDARIO,
    CAMARA_DESCONECTADA,
    CAMARA_RECONECTADA,
    DETECCION_NO_CONFIABLE,
    PAUSA_FINALIZADA,
    DATOS_ELIMINADOS;

    private static final Set<TipoAviso> ABREN_ALERTA =
            EnumSet.of(ALERTA_CAIDA, ALERTA_MOVIMIENTO_INESTABLE, ALERTA_ACTUALIZADA_A_CAIDA);

    private static final Set<TipoAviso> URGENTES = EnumSet.of(
            ALERTA_CAIDA,
            ALERTA_MOVIMIENTO_INESTABLE,
            ALERTA_ACTUALIZADA_A_CAIDA,
            CAIDA_CONFIRMADA,
            ALERTA_ESCALADA,
            SIN_CONTACTO_SECUNDARIO);

    /**
     * The notice that tells the family about a new alert, or that an alert became a fall: its delivery
     * is the alert's {@code notificadaEn} and {@code estadoAviso}.
     */
    public boolean abreAlerta() {
        return ABREN_ALERTA.contains(this);
    }

    /**
     * An active alert that somebody must look at now: it breaks through Focus on iOS
     * ({@code time-sensitive}), stays on screen in the browser until it is dismissed, and is retried
     * while nobody in the family can receive it, until the alert is closed or the retry window ends.
     */
    public boolean urgente() {
        return URGENTES.contains(this);
    }
}
