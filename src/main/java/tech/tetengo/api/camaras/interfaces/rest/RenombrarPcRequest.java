package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.constraints.NotNull;

record RenombrarPcRequest(
        @NotNull(message = "Escribe el nombre de la PC.") String nombre) {}
