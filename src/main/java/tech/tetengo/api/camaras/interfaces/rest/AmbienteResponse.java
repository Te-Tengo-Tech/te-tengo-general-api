package tech.tetengo.api.camaras.interfaces.rest;

import java.util.List;
import java.util.UUID;
import tech.tetengo.api.camaras.application.AmbienteConsultado;

/** {@code Ambiente} of the API contract. */
record AmbienteResponse(UUID id, String nombre, List<Camara> camaras) {

    record Camara(UUID id, UUID pcId, String pcNombre, String nombreDispositivo, String estadoConexion) {}

    static AmbienteResponse de(AmbienteConsultado ambiente) {
        return new AmbienteResponse(
                ambiente.id(),
                ambiente.nombre(),
                ambiente.camaras().stream()
                        .map(c -> new Camara(
                                c.camaraId(), c.pcId(), c.pcNombre(), c.nombreDispositivo(), c.estadoConexion()))
                        .toList());
    }
}
