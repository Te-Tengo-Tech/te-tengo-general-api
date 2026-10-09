package tech.tetengo.api.alertas.interfaces.rest;

import java.util.UUID;

record EventoDelAgenteResponse(UUID eventoId, UUID alertaId) {}
