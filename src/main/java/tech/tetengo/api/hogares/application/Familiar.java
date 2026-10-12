package tech.tetengo.api.hogares.application;

import java.util.UUID;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;
import tech.tetengo.api.shared.domain.model.Rol;

/** A member of the household with their profile (API contract §3, §13); {@code foto} null without one. */
public record Familiar(
        UUID usuarioId, String nombre, String correo, Rol rol, String telefono, String parentesco, Foto foto) {}
