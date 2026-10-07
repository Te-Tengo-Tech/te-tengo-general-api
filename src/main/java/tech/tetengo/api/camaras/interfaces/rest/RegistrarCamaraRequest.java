package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * The agent's fixed configuration: installation credential and room name (CA-06.1), plus its
 * version, which is only logged.
 */
record RegistrarCamaraRequest(
        @NotBlank(message = "Falta la credencial de instalación.")
        String credencialInstalacion,

        @NotBlank(message = "Falta el nombre de la habitación.")
        String nombreHabitacion,

        String versionAgente) {}
