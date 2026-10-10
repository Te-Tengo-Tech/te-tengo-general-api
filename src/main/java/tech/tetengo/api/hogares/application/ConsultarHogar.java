package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.application.port.DispositivosDePush;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-04: a member reads the household of their session, with the older adult and their role, and
 * whether anyone in the family can receive push alerts.
 */
@Service
public class ConsultarHogar {

    private final HogarRepository hogares;
    private final MiembroActual miembroActual;
    private final ConsultarConsentimiento consultarConsentimiento;
    private final MiembrosDelHogar miembros;
    private final DispositivosDePush dispositivos;

    public ConsultarHogar(
            HogarRepository hogares,
            MiembroActual miembroActual,
            ConsultarConsentimiento consultarConsentimiento,
            MiembrosDelHogar miembros,
            DispositivosDePush dispositivos) {
        this.hogares = hogares;
        this.miembroActual = miembroActual;
        this.consultarConsentimiento = consultarConsentimiento;
        this.miembros = miembros;
        this.dispositivos = dispositivos;
    }

    @Transactional(readOnly = true)
    public HogarConsultado ejecutar(UUID usuarioId) {
        Membresia membresia = miembroActual.de(usuarioId);
        return hogares.buscar(membresia.getHogarId())
                .map(hogar -> new HogarConsultado(
                        hogar,
                        membresia.getRol(),
                        consultarConsentimiento.ultimo(),
                        dispositivos.activosDe(miembros.de(hogar.getId()).stream()
                                .map(MiembrosDelHogar.Miembro::usuarioId)
                                .toList())))
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
    }
}
