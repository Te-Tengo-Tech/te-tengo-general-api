package tech.tetengo.api.monitoreo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A denied attempt to reach a camera's live view, as it is recorded (ADR 0015). Never carries a token, a
 * publish key or the hook's secret.
 *
 * @param accion MediaMTX's action ({@code read}, {@code publish}…), null for the API
 * @param protocolo MediaMTX's protocol ({@code hls}, {@code rtsp}…), null for the API
 * @param camaraId the camera asked for, null when the request named none
 * @param usuarioId who asked, when known: the member of the request, or the viewer of the session whose
 *     token was used
 * @param hogarDelUsuario the household that user asked from
 */
public record IntentoDenegado(
        Instant ocurridoEn,
        CanalDeAcceso canal,
        MotivoDeDenegacion motivo,
        String accion,
        String protocolo,
        UUID camaraId,
        UUID usuarioId,
        UUID hogarDelUsuario) {

    /** Longest {@code accion} or {@code protocolo} kept: MediaMTX's values are short, anything longer is cut. */
    public static final int LARGO_MAXIMO = 20;

    public IntentoDenegado {
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(motivo, "motivo");
        accion = recortar(accion);
        protocolo = recortar(protocolo);
    }

    private static String recortar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return null;
        }
        return valor.length() > LARGO_MAXIMO ? valor.substring(0, LARGO_MAXIMO) : valor;
    }
}
