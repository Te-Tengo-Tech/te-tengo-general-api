package tech.tetengo.api.monitoreo.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record SesionDeVistaEnVivo(UUID sesionId, URI urlTransmision, Instant expiraEn) {}
