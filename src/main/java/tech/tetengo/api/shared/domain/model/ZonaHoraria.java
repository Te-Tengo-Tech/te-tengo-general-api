package tech.tetengo.api.shared.domain.model;

import java.time.ZoneId;

/** Time zones of the system. */
public final class ZonaHoraria {

    /** Household time zone of the API contract (implementation choice): pauses and weekly summaries. */
    public static final ZoneId DEL_HOGAR = ZoneId.of("America/Lima");

    private ZonaHoraria() {}
}
