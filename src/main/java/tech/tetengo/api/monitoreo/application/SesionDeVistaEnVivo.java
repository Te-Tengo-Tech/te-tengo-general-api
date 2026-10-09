package tech.tetengo.api.monitoreo.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;

public record SesionDeVistaEnVivo(UUID sesionId, URI urlTransmision, Instant expiraEn, ModoDeVista modo) {}
