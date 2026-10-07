package tech.tetengo.api.monitoreo.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;
import tech.tetengo.api.shared.domain.model.ZonaHoraria;

/** Pause durations of the prototype's pause screen (API contract §4). */
public enum DuracionDePausa {
    MIN_30,
    HORA_1,
    HORAS_2,
    HASTA_MANANA;

    public static final ZoneId ZONA_DEL_HOGAR = ZonaHoraria.DEL_HOGAR;

    static final LocalTime HORA_DE_MANANA = LocalTime.of(7, 0);

    /** When a pause starting at {@code ahora} ends. {@code HASTA_MANANA} is the next 07:00 in Lima. */
    public Instant hasta(Instant ahora) {
        return switch (this) {
            case MIN_30 -> ahora.plus(Duration.ofMinutes(30));
            case HORA_1 -> ahora.plus(Duration.ofHours(1));
            case HORAS_2 -> ahora.plus(Duration.ofHours(2));
            case HASTA_MANANA -> {
                ZonedDateTime local = ahora.atZone(ZONA_DEL_HOGAR);
                ZonedDateTime siete = local.with(HORA_DE_MANANA).withSecond(0).withNano(0);
                yield (siete.isAfter(local) ? siete : siete.plusDays(1)).toInstant();
            }
        };
    }

    public static Optional<DuracionDePausa> desde(String valor) {
        return Arrays.stream(values()).filter(d -> d.name().equals(valor)).findFirst();
    }
}
