package tech.tetengo.api.historial.application;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.ConteoDeAlertas;
import tech.tetengo.api.alertas.ConteoDeAlertas.Conteo;
import tech.tetengo.api.historial.domain.model.Semana;
import tech.tetengo.api.historial.domain.model.Tendencia;
import tech.tetengo.api.shared.domain.model.ZonaHoraria;

/**
 * US-27: alerts of a week by type (CA-27.1), zeros for a week without events (CA-27.2) and the trend
 * of each type against the previous week (CA-27.3). False alarms are not counted as falls (CA-19.2).
 * Weeks are ISO weeks in the household's time zone.
 */
@Service
public class ConsultarResumenSemanal {

    private static final ZoneId ZONA = ZonaHoraria.DEL_HOGAR;

    private final ConteoDeAlertas conteo;
    private final Clock reloj;

    public ConsultarResumenSemanal(ConteoDeAlertas conteo, Clock reloj) {
        this.conteo = conteo;
        this.reloj = reloj;
    }

    /** {@code semana} null means the current week. */
    @Transactional(readOnly = true)
    public ResumenSemanal ejecutar(String semana) {
        Semana elegida = semana == null ? Semana.de(reloj.instant(), ZONA) : Semana.desde(semana);
        Semana anterior = elegida.anterior();
        Conteo actual = conteo.contar(elegida.inicio(ZONA), elegida.fin(ZONA));
        Conteo previo = conteo.contar(anterior.inicio(ZONA), anterior.fin(ZONA));
        return new ResumenSemanal(
                elegida,
                actual,
                previo,
                new ResumenSemanal.Tendencias(
                        Tendencia.comparar(actual.caidas(), previo.caidas()),
                        Tendencia.comparar(actual.movimientosInestables(), previo.movimientosInestables()),
                        Tendencia.comparar(actual.falsasAlarmas(), previo.falsasAlarmas())));
    }
}
