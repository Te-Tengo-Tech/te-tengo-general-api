package tech.tetengo.api.hogares.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.application.port.DispositivosDePush;
import tech.tetengo.api.shared.application.port.EliminacionesDeGrabaciones;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-04: a member reads the household of their session, with the older adult and their role,
 * whether anyone in the family can receive push alerts and, after a revocation, whether its
 * recordings are deleted yet (US-09).
 */
@Service
public class ConsultarHogar {

    private final HogarRepository hogares;
    private final MiembroActual miembroActual;
    private final ConsultarConsentimiento consultarConsentimiento;
    private final MiembrosDelHogar miembros;
    private final DispositivosDePush dispositivos;
    private final EliminacionesDeGrabaciones eliminaciones;

    public ConsultarHogar(
            HogarRepository hogares,
            MiembroActual miembroActual,
            ConsultarConsentimiento consultarConsentimiento,
            MiembrosDelHogar miembros,
            DispositivosDePush dispositivos,
            EliminacionesDeGrabaciones eliminaciones) {
        this.hogares = hogares;
        this.miembroActual = miembroActual;
        this.consultarConsentimiento = consultarConsentimiento;
        this.miembros = miembros;
        this.dispositivos = dispositivos;
        this.eliminaciones = eliminaciones;
    }

    @Transactional(readOnly = true)
    public HogarConsultado ejecutar(UUID usuarioId) {
        Membresia membresia = miembroActual.de(usuarioId);
        return hogares.buscar(membresia.getHogarId())
                .map(hogar -> {
                    Optional<ConsentimientoConsultado> consentimiento = consultarConsentimiento.ultimo();
                    return new HogarConsultado(
                            hogar,
                            membresia.getRol(),
                            consentimiento,
                            dispositivos.activosDe(miembros.de(hogar.getId()).stream()
                                    .map(MiembrosDelHogar.Miembro::usuarioId)
                                    .toList()),
                            EliminacionConsultada.de(
                                    consentimiento.map(c -> c.consentimiento().getRevocadoEn()),
                                    eliminaciones.ultima(),
                                    eliminaciones::clipsGuardados));
                })
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
    }
}
