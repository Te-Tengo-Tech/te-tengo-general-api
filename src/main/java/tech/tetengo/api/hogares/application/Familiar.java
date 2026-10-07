package tech.tetengo.api.hogares.application;

import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

public record Familiar(UUID usuarioId, String nombre, String correo, Rol rol) {}
