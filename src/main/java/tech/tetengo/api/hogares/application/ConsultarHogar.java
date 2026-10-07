package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-04: a member reads the household of their session, with the older adult and their role. */
@Service
public class ConsultarHogar {

    private final HogarRepository hogares;
    private final MiembroActual miembroActual;

    public ConsultarHogar(HogarRepository hogares, MiembroActual miembroActual) {
        this.hogares = hogares;
        this.miembroActual = miembroActual;
    }

    @Transactional(readOnly = true)
    public HogarConsultado ejecutar(UUID usuarioId) {
        Membresia membresia = miembroActual.de(usuarioId);
        return hogares.buscar(membresia.getHogarId())
                .map(hogar -> new HogarConsultado(hogar, membresia.getRol()))
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
    }
}
