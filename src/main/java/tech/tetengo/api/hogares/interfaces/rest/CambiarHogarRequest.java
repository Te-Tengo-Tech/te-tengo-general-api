package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record CambiarHogarRequest(
        @NotNull(message = "Indica el hogar.") UUID hogarId) {}
