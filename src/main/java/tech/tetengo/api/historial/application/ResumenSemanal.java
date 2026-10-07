package tech.tetengo.api.historial.application;

import tech.tetengo.api.alertas.ConteoDeAlertas.Conteo;
import tech.tetengo.api.historial.domain.model.Semana;
import tech.tetengo.api.historial.domain.model.Tendencia;

public record ResumenSemanal(Semana semana, Conteo conteos, Conteo semanaAnterior, Tendencias tendencia) {

    public record Tendencias(Tendencia caidas, Tendencia movimientosInestables, Tendencia falsasAlarmas) {}
}
