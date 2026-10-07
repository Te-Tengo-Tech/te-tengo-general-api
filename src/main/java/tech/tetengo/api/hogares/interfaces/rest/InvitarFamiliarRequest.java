package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record InvitarFamiliarRequest(
        @NotBlank(message = "Ingresa el correo del familiar.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 254)
        String correo) {}
