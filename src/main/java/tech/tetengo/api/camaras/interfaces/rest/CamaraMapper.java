package tech.tetengo.api.camaras.interfaces.rest;

import tech.tetengo.api.camaras.domain.model.Camara;

final class CamaraMapper {

    private CamaraMapper() {}

    static CamaraResponse aRespuesta(Camara camara) {
        return new CamaraResponse(
                camara.getId(),
                camara.getNombreHabitacion(),
                camara.getEstadoConexion().name(),
                camara.getUltimaSenal(),
                camara.getPausadaHasta(),
                camara.isDeteccionConfiable());
    }
}
