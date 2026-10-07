package tech.tetengo.api.historial.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tech.tetengo.api.historial.domain.HistorialError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** An ISO week ({@code 2026-W41}), from Monday 00:00 to the next Monday 00:00 in the household's zone. */
public record Semana(int anio, int numero) {

    private static final Pattern FORMATO = Pattern.compile("(\\d{4})-W(\\d{2})");

    public static Semana de(Instant instante, ZoneId zona) {
        LocalDate dia = instante.atZone(zona).toLocalDate();
        return new Semana(dia.get(IsoFields.WEEK_BASED_YEAR), dia.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** {@code 400 VALIDACION} for anything but an existing ISO week. */
    public static Semana desde(String texto) {
        Matcher m = FORMATO.matcher(Optional.ofNullable(texto).orElse(""));
        if (!m.matches()) {
            throw invalida();
        }
        int anio = Integer.parseInt(m.group(1));
        int numero = Integer.parseInt(m.group(2));
        LocalDate algunDia = LocalDate.of(anio, 6, 1);
        if (numero < 1
                || numero > algunDia.range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).getMaximum()) {
            throw invalida();
        }
        return new Semana(anio, numero);
    }

    public Instant inicio(ZoneId zona) {
        return lunes().atStartOfDay(zona).toInstant();
    }

    public Instant fin(ZoneId zona) {
        return lunes().plusWeeks(1).atStartOfDay(zona).toInstant();
    }

    public Semana anterior() {
        LocalDate lunesAnterior = lunes().minusWeeks(1);
        return new Semana(
                lunesAnterior.get(IsoFields.WEEK_BASED_YEAR), lunesAnterior.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private LocalDate lunes() {
        return LocalDate.of(anio, 6, 1)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, numero)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Override
    public String toString() {
        return "%04d-W%02d".formatted(anio, numero);
    }

    private static ErrorDeNegocio invalida() {
        return new ErrorDeNegocio(
                HistorialError.SEMANA_INVALIDA,
                Map.of("campos", Map.of("semana", HistorialError.SEMANA_INVALIDA.mensaje())));
    }
}
