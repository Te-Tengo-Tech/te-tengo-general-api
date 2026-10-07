package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;

record HogarResponse(UUID hogarId, AdultoMayorResponse adultoMayor, String rol, Object consentimiento) {}
