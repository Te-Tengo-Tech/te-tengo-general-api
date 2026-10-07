package tech.tetengo.api.camaras.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.model.Camara;

/** US-06 / CA-06.1: el familiar ve las cámaras de su hogar. */
@Service
public class ListarCamaras {

    private final CamaraRepository camaras;

    public ListarCamaras(CamaraRepository camaras) {
        this.camaras = camaras;
    }

    @Transactional(readOnly = true)
    public List<Camara> ejecutar() {
        return camaras.listar();
    }
}
