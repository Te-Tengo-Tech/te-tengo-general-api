package tech.tetengo.api.monitoreo.interfaces.rest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

record SesionDeVistaEnVivoResponse(UUID sesionId, URI urlTransmision, Instant expiraEn) {}
