package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.application.port.ConsentimientoRepository;
import tech.tetengo.api.hogares.domain.model.Consentimiento;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-05: the owner registers the older adult's consent (CA-05.4), stored with its date and time
 * (CA-05.3). The {@code ConsentimientoOtorgado} event lets {@code camaras} start capture (CA-05.1).
 */
@Service
public class OtorgarConsentimiento {

    private final ConsentimientoRepository consentimientos;
    private final MiembroActual miembroActual;
    private final DirectorioDeUsuarios usuarios;
    private final Clock reloj;

    public OtorgarConsentimiento(
            ConsentimientoRepository consentimientos,
            MiembroActual miembroActual,
            DirectorioDeUsuarios usuarios,
            Clock reloj) {
        this.consentimientos = consentimientos;
        this.miembroActual = miembroActual;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional
    public ConsentimientoConsultado ejecutar(
            UUID usuarioId, String otorgadoPor, Boolean aceptadoPorAdultoMayor, Boolean vistaEnVivoAceptada) {
        Membresia membresia = miembroActual.de(usuarioId);
        if (!membresia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        Instant ahora = reloj.instant();
        Consentimiento nuevo = Consentimiento.otorgar(
                membresia.getHogarId(), otorgadoPor, usuarioId, aceptadoPorAdultoMayor, vistaEnVivoAceptada, ahora);
        consentimientos.ultimo().ifPresent(anterior -> anterior.reemplazar(ahora));
        Consentimiento guardado = consentimientos.guardar(nuevo);
        return new ConsentimientoConsultado(guardado, usuarios.buscar(usuarioId).orElse(null));
    }
}
