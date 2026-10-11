package tech.tetengo.api.monitoreo.application.port;

import java.net.URI;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;

/**
 * The control channel to the household agent: the WebSocket {@code /api/agente/transmision}
 * (AGENT_CONTRACT.md), one per camera (opened with the camera's token) and, for a linked PC, one of
 * the PC itself (opened with the PC's access token). A message to an agent that is not connected is
 * dropped: the agent gets the current state when it connects.
 */
public interface CanalDelAgente {

    /** @param canalId the camera, or the PC for its own channel */
    void enviar(UUID canalId, Mensaje mensaje);

    sealed interface Mensaje
            permits Preparar, Transmitir, CambiarModo, Detener, Desvincular, ParaCamara, CamaraDeLaPc, BuscarCamaras {}

    /** {@code {"buscarCamaras":true}} on the PC's channel: «Buscar de nuevo» (CA-29.6). */
    record BuscarCamaras() implements Mensaje {}

    /**
     * A live view message of a webcam that is not in use (a preview before assigning it a room, US-29),
     * sent on the PC's own channel: the message's fields plus {@code camaraId} and {@code dispositivoId},
     * e.g. {@code {"transmitir":true,…,"camaraId":"…","dispositivoId":"…"}}.
     */
    record ParaCamara(java.util.UUID camaraId, String dispositivoId, Mensaje mensaje) implements Mensaje {}

    /**
     * {@code {"camara":{camaraId, dispositivoId, nombreDispositivo, enUso, ambienteId, nombreHabitacion}}} on
     * the PC's channel: the owner assigned, moved or stopped using a webcam, or renamed its room (US-29).
     * The PC starts or stops that camera; it gets the camera's token from {@code POST
     * /api/agente/pc/camaras/tokens}.
     */
    record CamaraDeLaPc(
            java.util.UUID camaraId,
            String dispositivoId,
            String nombreDispositivo,
            boolean enUso,
            java.util.UUID ambienteId,
            String nombreHabitacion)
            implements Mensaje {}

    /**
     * {@code {"desvincular":true}}: the PC was unlinked (US-31). The agent stops capturing, deletes its
     * credentials and shows the link code again; the API closes the connection right after.
     */
    record Desvincular() implements Mensaje {}

    /**
     * {@code {"preparar":true}}: a member opened the camera screen, a live view may start soon. The agent
     * warms up what stays on the PC (capture, encoder, name resolution) for a while; no frame leaves the PC
     * until {@link Transmitir}. Agents that do not know it ignore it.
     */
    record Preparar() implements Mensaje {}

    /** {@code {"transmitir":true,"urlPublicacion":…,"usuario":"agente","clave":…,"modo":…}}. */
    record Transmitir(URI urlPublicacion, String usuario, String clave, ModoDeVista modo) implements Mensaje {}

    /** {@code {"modo":…}} during a transmission. */
    record CambiarModo(ModoDeVista modo) implements Mensaje {}

    /** {@code {"transmitir":false}}. */
    record Detener() implements Mensaje {}
}
