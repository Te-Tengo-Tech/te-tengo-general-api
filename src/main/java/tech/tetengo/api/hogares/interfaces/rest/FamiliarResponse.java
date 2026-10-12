package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** An item of {@code GET /api/familiares}, with the member's profile and photo (§13). */
record FamiliarResponse(
        UUID usuarioId, String nombre, String correo, String rol, String telefono, String parentesco, Foto foto) {}
