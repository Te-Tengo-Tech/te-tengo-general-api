package tech.tetengo.api.camaras.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.shared.application.port.ComprobadorDeAgentes;

/**
 * US-31: the tokens of an unlinked PC, and of its retired cameras, stop working at once, even while
 * still signed and unexpired. Two indexed lookups per agent request; the token is checked before its
 * household is bound, so they are native queries.
 */
@Service
public class ComprobacionDeAgentes implements ComprobadorDeAgentes {

    private final PcRepository pcs;
    private final CamaraRepository camaras;

    public ComprobacionDeAgentes(PcRepository pcs, CamaraRepository camaras) {
        this.pcs = pcs;
        this.camaras = camaras;
    }

    @Override
    public boolean autorizado(UUID hogarId, UUID pcId, UUID camaraId) {
        if (pcId != null && !pcs.vinculada(hogarId, pcId)) {
            return false;
        }
        if (camaraId == null) {
            return pcId != null;
        }
        return camaras.deAgente(hogarId, camaraId)
                .map(camara ->
                        !camara.retirada() && camara.pcVinculada() && (pcId == null || pcId.equals(camara.pcId())))
                // An installation token of an unknown camera keeps its old 404; a PC's never matches.
                .orElse(pcId == null);
    }
}
