package tech.tetengo.api.actividad.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** What changed (CA-33.1). */
public enum TipoDeActividad {
    PC_VINCULADA,
    PC_RENOMBRADA,
    PC_DESVINCULADA,
    CAMARA_ASIGNADA,
    CAMARA_RETIRADA,
    AMBIENTE_RENOMBRADO,
    CAMARA_PAUSADA,
    CAMARA_REANUDADA,
    CONSENTIMIENTO_OTORGADO,
    CONSENTIMIENTO_REVOCADO,
    CONSENTIMIENTO_MOVIMIENTOS_OTORGADO,
    CONSENTIMIENTO_MOVIMIENTOS_REVOCADO,
    RETENCION_MOVIMIENTOS_CAMBIADA,
    /** Reported by the PC (US-32, CA-32.1). */
    MODO_AVANZADO_ACTIVADO,
    MODO_AVANZADO_DESACTIVADO,
    /** Reported by the PC: folder, a type on or off, a period or the space limit (US-38). */
    GUARDADO_LOCAL_CAMBIADO,
    /** Reported by the PC: it deleted data stored on it (e.g. after a revocation, CA-35.4). */
    DATOS_LOCALES_ELIMINADOS,
    /**
     * Reported by the PC: someone replayed a camera's movements on the PC itself, only when it was
     * played or scrubbed (US-39, CA-39.3). Replays in the app are in the access log instead.
     */
    REPETICION_VISTA,
    /** Reported by the PC: it installed an update, from the old version to the new one (US-43, CA-43.2). */
    PC_ACTUALIZADA,
    /** Reported by the PC: «Actualizar automáticamente» was turned on or off (US-43, CA-43.7). */
    ACTUALIZACION_AUTOMATICA_ACTIVADA,
    ACTUALIZACION_AUTOMATICA_DESACTIVADA;

    private static final Set<TipoDeActividad> DE_LA_PC = EnumSet.of(
            MODO_AVANZADO_ACTIVADO,
            MODO_AVANZADO_DESACTIVADO,
            GUARDADO_LOCAL_CAMBIADO,
            DATOS_LOCALES_ELIMINADOS,
            REPETICION_VISTA,
            PC_ACTUALIZADA,
            ACTUALIZACION_AUTOMATICA_ACTIVADA,
            ACTUALIZACION_AUTOMATICA_DESACTIVADA);

    /** The changes only the PC knows about, which it reports itself. */
    public boolean reportadoPorLaPc() {
        return DE_LA_PC.contains(this);
    }
}
