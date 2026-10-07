package tech.tetengo.api.hogares.application;

import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

public record HogarDelUsuario(UUID hogarId, String nombreAdultoMayor, Rol rol) {}
