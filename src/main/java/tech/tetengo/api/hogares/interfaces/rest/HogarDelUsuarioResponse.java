package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;

record HogarDelUsuarioResponse(UUID hogarId, String nombreAdultoMayor, String rol) {}
