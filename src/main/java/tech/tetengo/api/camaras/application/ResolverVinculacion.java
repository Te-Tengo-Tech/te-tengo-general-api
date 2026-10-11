package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.CodigoDeUsuario;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * US-28: the owner looks up a PC's code, then approves it or rejects it. Only the owner may (CA-28.5;
 * the controller checks the role). Approving links the PC to the owner's household (CA-28.1, CA-30.1:
 * other PCs keep their cameras); rejecting («No reconozco esta PC», CA-28.4) answers {@code
 * access_denied} to the PC. Wrong codes are counted (CA-28.6), so these transactions do not roll back
 * on business errors.
 */
@Service
public class ResolverVinculacion {

    private final ResolucionDeCodigos codigos;
    private final AutorizacionDeDispositivoRepository autorizaciones;
    private final PcRepository pcs;
    private final ListarPcs listarPcs;
    private final Clock reloj;

    public ResolverVinculacion(
            ResolucionDeCodigos codigos,
            AutorizacionDeDispositivoRepository autorizaciones,
            PcRepository pcs,
            ListarPcs listarPcs,
            Clock reloj) {
        this.codigos = codigos;
        this.autorizaciones = autorizaciones;
        this.pcs = pcs;
        this.listarPcs = listarPcs;
        this.reloj = reloj;
    }

    /** CA-28.4: the PC's name, system and agent version, before approving. */
    @Transactional(noRollbackFor = ErrorDeNegocio.class)
    public VinculacionConsultada consultar(UUID titularId, String codigo) {
        AutorizacionDeDispositivo autorizacion = codigos.resoluble(titularId, codigo);
        return new VinculacionConsultada(
                CodigoDeUsuario.leer(codigo).orElseThrow().legible(),
                autorizacion.getNombrePc(),
                autorizacion.getSistemaOperativo(),
                autorizacion.getVersionAgente(),
                autorizacion.getExpiraEn());
    }

    /** CA-28.1: the PC joins the owner's household; its next poll gets its tokens. */
    @Transactional(noRollbackFor = ErrorDeNegocio.class)
    public PcConsultada aprobar(UUID titularId, String codigo) {
        UUID hogarId = HogarActual.obtener().orElseThrow(() -> new ErrorDeNegocio(ErrorComun.SOLO_TITULAR));
        AutorizacionDeDispositivo autorizacion = codigos.resoluble(titularId, codigo);
        Instant ahora = reloj.instant();
        Pc pc = pcs.guardar(Pc.vincular(
                hogarId,
                autorizacion.getNombrePc(),
                autorizacion.getSistemaOperativo(),
                autorizacion.getVersionAgente(),
                titularId,
                ahora));
        autorizacion.aprobar(hogarId, pc.getId(), titularId, ahora);
        autorizaciones.guardar(autorizacion);
        return listarPcs.consultada(pc);
    }

    /** CA-28.4: «No reconozco esta PC»; the PC is not linked. */
    @Transactional(noRollbackFor = ErrorDeNegocio.class)
    public void rechazar(UUID titularId, String codigo) {
        AutorizacionDeDispositivo autorizacion = codigos.resoluble(titularId, codigo);
        autorizacion.rechazar(titularId, reloj.instant());
        autorizaciones.guardar(autorizacion);
    }
}
