package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-04: the owner edits the older adult's profile; invited members cannot (CA-08.4). */
@Service
public class ActualizarAdultoMayor {

    private final HogarRepository hogares;
    private final MiembroActual miembroActual;

    public ActualizarAdultoMayor(HogarRepository hogares, MiembroActual miembroActual) {
        this.hogares = hogares;
        this.miembroActual = miembroActual;
    }

    @Transactional
    public AdultoMayor ejecutar(UUID usuarioId, AdultoMayor datos) {
        Membresia membresia = miembroActual.de(usuarioId);
        if (!membresia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        Hogar hogar =
                hogares.buscar(membresia.getHogarId()).orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
        hogar.actualizarAdultoMayor(datos);
        return hogares.guardar(hogar).getAdultoMayor();
    }
}
