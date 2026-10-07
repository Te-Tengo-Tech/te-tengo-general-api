package tech.tetengo.api.monitoreo.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;

/** {@code Camara} of the API contract. */
record CamaraResponse(
        UUID id,
        String nombreHabitacion,
        String estadoConexion,
        Instant ultimaSenal,
        Instant pausadaHasta,
        boolean deteccionConfiable) {

    static CamaraResponse de(EstadoDeCamara camara) {
        return new CamaraResponse(
                camara.id(),
                camara.nombreHabitacion(),
                camara.estadoConexion(),
                camara.ultimaSenal(),
                camara.pausadaHasta(),
                camara.deteccionConfiable());
    }
}
