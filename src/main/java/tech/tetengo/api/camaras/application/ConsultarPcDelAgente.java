package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.AdultoMayorDelHogar;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * The PC reads its own link (D-28.4) and, with {@link #senal}, reports that it is on (US-30: a PC
 * with no camera in use yet still shows as online) with its version and system.
 */
@Service
public class ConsultarPcDelAgente {

    private final PcRepository pcs;
    private final EstadoDeCapturaRepository estados;
    private final AdultoMayorDelHogar adultoMayor;
    private final DirectorioDeUsuarios usuarios;
    private final Clock reloj;

    public ConsultarPcDelAgente(
            PcRepository pcs,
            EstadoDeCapturaRepository estados,
            AdultoMayorDelHogar adultoMayor,
            DirectorioDeUsuarios usuarios,
            Clock reloj) {
        this.pcs = pcs;
        this.estados = estados;
        this.adultoMayor = adultoMayor;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PcDelAgente ejecutar(UUID pcId) {
        return aVista(pcs.buscar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA)));
    }

    /**
     * The PC's heartbeat, every 30 s like the cameras', with its version, system and whether it
     * updates itself (US-43, CA-43.7); null values keep what it reported before.
     */
    @Transactional
    public PcDelAgente senal(
            UUID pcId, String versionAgente, String sistemaOperativo, Boolean actualizacionAutomatica) {
        Pc pc = pcs.buscarParaActualizar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
        pc.registrarSenal(reloj.instant());
        pc.actualizarDatos(versionAgente, sistemaOperativo, actualizacionAutomatica);
        return aVista(pcs.guardar(pc));
    }

    private PcDelAgente aVista(Pc pc) {
        String vinculadaPor = pc.getVinculadaPor() == null
                ? null
                : usuarios.buscar(pc.getVinculadaPor())
                        .map(DirectorioDeUsuarios.Usuario::nombre)
                        .orElse(null);
        return new PcDelAgente(
                pc.getId(),
                pc.getHogarId(),
                pc.getNombre(),
                adultoMayor.nombre().orElse(null),
                pc.getVinculadaEn(),
                vinculadaPor,
                estados.actual().map(EstadoDeCaptura::isConsentimientoVigente).orElse(false));
    }
}
