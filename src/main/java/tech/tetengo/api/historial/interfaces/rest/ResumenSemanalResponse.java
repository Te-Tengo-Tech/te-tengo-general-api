package tech.tetengo.api.historial.interfaces.rest;

record ResumenSemanalResponse(String semana, Conteos conteos, Conteos semanaAnterior, Tendencias tendencia) {

    record Conteos(long caidas, long movimientosInestables, long falsasAlarmas) {}

    record Tendencias(String caidas, String movimientosInestables, String falsasAlarmas) {}
}
