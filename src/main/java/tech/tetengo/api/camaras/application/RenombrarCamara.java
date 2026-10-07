package tech.tetengo.api.camaras.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-06 / CA-06.2: el familiar cambia el nombre de la habitación. */
@Service
public class RenombrarCamara {

    private final CamaraRepository camaras;

    public RenombrarCamara(CamaraRepository camaras) {
        this.camaras = camaras;
    }

    @Transactional
    public Camara ejecutar(UUID camaraId, String nombre) {
        Camara camara = camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        camara.renombrar(nombre);
        return camaras.guardar(camara);
    }
}
