package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;

record FamiliarResponse(UUID usuarioId, String nombre, String correo, String rol) {}
