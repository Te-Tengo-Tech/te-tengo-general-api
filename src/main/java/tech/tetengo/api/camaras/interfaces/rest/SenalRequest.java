package tech.tetengo.api.camaras.interfaces.rest;

/**
 * Heartbeat of AGENT_CONTRACT.md. A missing body or {@code webcamConectada} means the webcam is
 * connected. {@code deteccionConfiable} and {@code versionAgente} are informational: unreliable
 * detection reaches the backend as the {@code deteccion_no_confiable} event.
 */
record SenalRequest(Boolean webcamConectada, Boolean deteccionConfiable, String versionAgente) {

    boolean conectada() {
        return webcamConectada == null || webcamConectada;
    }
}
