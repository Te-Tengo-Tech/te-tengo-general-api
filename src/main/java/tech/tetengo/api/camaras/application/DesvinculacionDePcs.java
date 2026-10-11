package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.application.port.TokenDeRenovacionRepository;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.MotivoDesvinculacion;
import tech.tetengo.api.camaras.domain.model.Pc;

/**
 * US-31: what unlinking a PC means, whoever starts it. Every refresh token of the PC is revoked, its
 * cameras are retired (they leave the app and stop accepting their tokens), and the
 * {@code PcDesvinculada} event tells {@code monitoreo} to stop live view and notify the PC on its
 * control channel, and {@code alertas} to tell the family when it was not done from the app (CA-31.3).
 * Runs inside the caller's transaction, in the PC's household.
 */
@Service
public class DesvinculacionDePcs {

    private final PcRepository pcs;
    private final CamaraRepository camaras;
    private final TokenDeRenovacionRepository tokens;

    public DesvinculacionDePcs(PcRepository pcs, CamaraRepository camaras, TokenDeRenovacionRepository tokens) {
        this.pcs = pcs;
        this.camaras = camaras;
        this.tokens = tokens;
    }

    /** Returns false if the PC was already unlinked. */
    public boolean desvincular(Pc pc, MotivoDesvinculacion motivo, UUID usuarioId, Instant ahora) {
        tokens.revocarTodosDe(pc.getId(), ahora);
        List<Camara> suyas = camaras.deLaPc(pc.getId());
        List<UUID> ids = suyas.stream().map(Camara::getId).toList();
        List<String> habitaciones =
                suyas.stream().map(Camara::getNombreHabitacion).distinct().toList();
        if (!pc.desvincular(motivo, usuarioId, ids, habitaciones, ahora)) {
            return false;
        }
        suyas.forEach(camara -> {
            camara.retirar(ahora);
            camaras.guardar(camara);
        });
        pcs.guardar(pc);
        return true;
    }
}
