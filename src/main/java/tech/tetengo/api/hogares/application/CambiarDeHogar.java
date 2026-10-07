package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.ServicioDeSesiones;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * Switches the session to another household of the user: the only case where the client sends a
 * household id. The membership is checked here ({@code 403 SIN_MEMBRESIA}).
 */
@Service
public class CambiarDeHogar {

    private final MembresiaRepository membresias;
    private final ServicioDeSesiones sesiones;

    public CambiarDeHogar(MembresiaRepository membresias, ServicioDeSesiones sesiones) {
        this.membresias = membresias;
        this.sesiones = sesiones;
    }

    @Transactional
    public Sesion ejecutar(UUID usuarioId, UUID hogarId, UUID sesionActual) {
        Membresia membresia =
                membresias.buscar(hogarId, usuarioId).orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
        return sesiones.abrir(usuarioId, membresia.getHogarId(), membresia.getRol(), sesionActual);
    }
}
