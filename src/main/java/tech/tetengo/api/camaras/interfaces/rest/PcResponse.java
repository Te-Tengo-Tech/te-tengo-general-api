package tech.tetengo.api.camaras.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code Pc} of the API contract (CA-30.2). */
record PcResponse(
        UUID id,
        String nombre,
        String sistemaOperativo,
        String versionAgente,
        Boolean actualizacionAutomatica,
        String estadoConexion,
        Instant ultimaSenal,
        Instant vinculadaEn,
        Usuario vinculadaPor,
        String origen,
        List<CamaraResponse> camaras,
        int camarasSinUsar) {

    record Usuario(UUID id, String nombre) {}
}
