package tech.tetengo.api.hogares.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.application.port.ConsentimientoRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-05: any member reads the household's consent; without one the app asks to complete it (CA-05.2). */
@Service
public class ConsultarConsentimiento {

    private final ConsentimientoRepository consentimientos;
    private final MiembroActual miembroActual;
    private final DirectorioDeUsuarios usuarios;

    public ConsultarConsentimiento(
            ConsentimientoRepository consentimientos, MiembroActual miembroActual, DirectorioDeUsuarios usuarios) {
        this.consentimientos = consentimientos;
        this.miembroActual = miembroActual;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public ConsentimientoConsultado ejecutar(UUID usuarioId) {
        miembroActual.de(usuarioId);
        return ultimo().orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_CONSENTIMIENTO));
    }

    /** The latest consent, if any, without checking the caller (for {@code GET /api/hogar}). */
    @Transactional(readOnly = true)
    public Optional<ConsentimientoConsultado> ultimo() {
        return consentimientos
                .ultimo()
                .map(c -> new ConsentimientoConsultado(
                        c, usuarios.buscar(c.getRegistradoPor()).orElse(null)));
    }
}
