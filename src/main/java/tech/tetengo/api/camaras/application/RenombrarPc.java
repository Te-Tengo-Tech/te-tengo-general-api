package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** The owner renames a PC (1 to 60 characters) so the family recognizes it (US-30). */
@Service
public class RenombrarPc {

    private final PcRepository pcs;
    private final ListarPcs listarPcs;
    private final Clock reloj;

    public RenombrarPc(PcRepository pcs, ListarPcs listarPcs, Clock reloj) {
        this.pcs = pcs;
        this.listarPcs = listarPcs;
        this.reloj = reloj;
    }

    @Transactional
    public PcConsultada ejecutar(UUID pcId, String nombre, UUID titularId) {
        Pc pc = pcs.buscarParaActualizar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
        pc.renombrar(nombre, titularId, reloj.instant());
        return listarPcs.consultada(pcs.guardar(pc));
    }
}
