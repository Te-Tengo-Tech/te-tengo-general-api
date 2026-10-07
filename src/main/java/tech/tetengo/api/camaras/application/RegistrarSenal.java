package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-07: the agent's periodic heartbeat keeps the camera online (CA-07.1) or brings it back (CA-07.3). */
@Service
public class RegistrarSenal {

    private final CamaraRepository camaras;
    private final Clock reloj;

    public RegistrarSenal(CamaraRepository camaras, Clock reloj) {
        this.camaras = camaras;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID camaraId) {
        Camara camara = camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        camara.registrarSenal(reloj.instant());
        camaras.guardar(camara);
    }
}
