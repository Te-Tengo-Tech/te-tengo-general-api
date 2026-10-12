package tech.tetengo.api.camaras.application;

import java.util.List;
import java.util.UUID;

/**
 * A room with the cameras in use in it (CA-29.3: at most one, for now), so the PC can mark the rooms
 * that already have a camera («Ya tiene cámara») and say on which PC.
 */
public record AmbienteConsultado(UUID id, String nombre, List<CamaraEnAmbiente> camaras) {

    public record CamaraEnAmbiente(
            UUID camaraId, UUID pcId, String pcNombre, String nombreDispositivo, String estadoConexion) {}
}
