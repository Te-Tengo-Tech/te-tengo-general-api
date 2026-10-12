package tech.tetengo.api.camaras.interfaces.rest;

import tech.tetengo.api.camaras.application.PcConsultada;
import tech.tetengo.api.camaras.application.PcDelAgente;
import tech.tetengo.api.camaras.domain.model.EstadoConexion;

final class PcMapper {

    private PcMapper() {}

    static PcResponse aRespuesta(PcConsultada consultada) {
        var pc = consultada.pc();
        return new PcResponse(
                pc.getId(),
                pc.getNombre(),
                pc.getSistemaOperativo(),
                pc.getVersionAgente(),
                pc.getActualizacionAutomatica(),
                (consultada.enLinea() ? EstadoConexion.EN_LINEA : EstadoConexion.DESCONECTADA).name(),
                consultada.ultimaSenal(),
                pc.getVinculadaEn(),
                pc.getVinculadaPor() == null
                        ? null
                        : new PcResponse.Usuario(pc.getVinculadaPor(), consultada.vinculadaPorNombre()),
                pc.getOrigen().name(),
                consultada.camaras().stream().map(CamaraMapper::aRespuesta).toList(),
                consultada.camarasSinUsar());
    }

    static PcDelAgenteResponse aRespuesta(PcDelAgente pc) {
        return new PcDelAgenteResponse(
                pc.pcId(),
                pc.hogarId(),
                pc.nombre(),
                pc.nombreAdultoMayor(),
                pc.vinculadaEn(),
                pc.vinculadaPor(),
                pc.consentimientoVigente());
    }
}
