package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.constraints.NotNull;

record NombreDeAmbienteRequest(
        @NotNull(message = "Escribe el nombre del ambiente.")
        String nombre) {}
