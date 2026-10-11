package tech.tetengo.api.camaras.interfaces.rest;

/** The PC's heartbeat: both fields are optional and update what the app shows. */
record SenalDePcRequest(String versionAgente, String sistemaOperativo) {}
