package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.constraints.NotNull;

record RenombrarCamaraRequest(@NotNull String nombreHabitacion) {}
