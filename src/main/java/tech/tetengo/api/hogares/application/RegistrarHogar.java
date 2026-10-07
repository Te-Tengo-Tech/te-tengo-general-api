package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.ServicioDeSesiones;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * US-04: registers the older adult, which creates the household with the caller as {@code TITULAR}
 * (CA-04.1). An account manages a single older adult (CA-04.2). Returns a new session whose tokens
 * carry the household.
 */
@Service
public class RegistrarHogar {

    private final HogarRepository hogares;
    private final MembresiaRepository membresias;
    private final ServicioDeSesiones sesiones;

    public RegistrarHogar(HogarRepository hogares, MembresiaRepository membresias, ServicioDeSesiones sesiones) {
        this.hogares = hogares;
        this.membresias = membresias;
        this.sesiones = sesiones;
    }

    @Transactional
    public Sesion ejecutar(UUID usuarioId, AdultoMayor adultoMayor, UUID sesionActual) {
        if (membresias.esTitularDeAlgunHogar(usuarioId)) {
            throw new ErrorDeNegocio(HogarError.HOGAR_YA_REGISTRADO);
        }
        Hogar hogar = hogares.guardar(new Hogar(usuarioId, adultoMayor));
        membresias.guardar(new Membresia(hogar.getId(), usuarioId, Rol.TITULAR));
        return sesiones.abrir(usuarioId, hogar.getId(), Rol.TITULAR, sesionActual);
    }
}
