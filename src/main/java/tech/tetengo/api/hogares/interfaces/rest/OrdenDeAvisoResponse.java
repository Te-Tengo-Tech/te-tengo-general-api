package tech.tetengo.api.hogares.interfaces.rest;

import java.util.UUID;

record OrdenDeAvisoResponse(UUID principalId, UUID secundarioId, int esperaMinutos) {}
