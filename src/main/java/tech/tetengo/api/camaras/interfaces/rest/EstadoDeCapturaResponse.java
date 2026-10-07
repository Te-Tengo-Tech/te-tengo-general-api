package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;

record EstadoDeCapturaResponse(boolean capturaPermitida, boolean consentimientoVigente, Instant pausadaHasta) {}
