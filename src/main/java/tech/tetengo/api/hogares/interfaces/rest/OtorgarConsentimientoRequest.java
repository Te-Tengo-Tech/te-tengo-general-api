package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record OtorgarConsentimientoRequest(
        @NotBlank(message = "Indica quién otorga el consentimiento.")
        @Size(max = 120, message = "El nombre es demasiado largo.")
        String otorgadoPor,

        Boolean aceptadoPorAdultoMayor,
        Boolean vistaEnVivoAceptada) {}
