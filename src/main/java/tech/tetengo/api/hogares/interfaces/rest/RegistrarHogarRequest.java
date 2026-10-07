package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

record RegistrarHogarRequest(
        @Valid @NotNull(message = "Ingresa los datos del adulto mayor.")
        AdultoMayorRequest adultoMayor) {}
