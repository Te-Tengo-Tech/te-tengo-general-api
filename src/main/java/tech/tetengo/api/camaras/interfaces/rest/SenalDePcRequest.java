package tech.tetengo.api.camaras.interfaces.rest;

/**
 * The PC's heartbeat: every field is optional and updates what the app shows; {@code
 * actualizacionAutomatica} is «Actualizar automáticamente» on the PC (US-43, CA-43.7).
 */
record SenalDePcRequest(String versionAgente, String sistemaOperativo, Boolean actualizacionAutomatica) {}
