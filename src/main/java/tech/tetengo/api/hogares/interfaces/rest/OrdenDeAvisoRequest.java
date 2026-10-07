package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record OrdenDeAvisoRequest(
        @NotNull(message = "Elige el contacto principal.") UUID principalId,
        UUID secundarioId,
        @NotNull(message = "Elige el tiempo de espera.") Integer esperaMinutos) {}
