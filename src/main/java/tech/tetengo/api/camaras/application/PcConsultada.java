package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.List;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.Pc;

/**
 * A PC as the app shows it (CA-30.2): online or «Sin conexión desde las HH:MM», its version and its
 * cameras.
 *
 * @param enLinea a heartbeat of the PC or of one of its cameras within the heartbeat timeout
 * @param ultimaSenal the latest of the PC's and its cameras' heartbeats
 * @param vinculadaPorNombre name of the owner who linked it; null for installations
 */
public record PcConsultada(
        Pc pc, List<Camara> camaras, boolean enLinea, Instant ultimaSenal, String vinculadaPorNombre) {}
