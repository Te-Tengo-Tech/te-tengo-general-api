package tech.tetengo.api.alertas.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegistrarDispositivoRequest(
        @NotBlank(message = "Falta el token de notificaciones.") @Size(max = 512)
        String tokenPush,

        @NotBlank(message = "Falta la plataforma.")
        @Pattern(regexp = "ANDROID|IOS", message = "La plataforma debe ser ANDROID o IOS.")
        String plataforma) {}
