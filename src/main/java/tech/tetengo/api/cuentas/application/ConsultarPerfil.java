package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: the caller's own profile («Tu perfil»). */
@Service
public class ConsultarPerfil {

    private final CuentaRepository cuentas;
    private final AlmacenamientoDeFotos fotos;
    private final Clock reloj;

    public ConsultarPerfil(CuentaRepository cuentas, AlmacenamientoDeFotos fotos, Clock reloj) {
        this.cuentas = cuentas;
        this.fotos = fotos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Perfil ejecutar(UUID usuarioId) {
        return perfil(cuentas.buscar(usuarioId).orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA)));
    }

    Perfil perfil(Cuenta cuenta) {
        return new Perfil(
                cuenta, cuenta.getFotoClave() == null ? null : fotos.foto(cuenta.getFotoClave(), reloj.instant()));
    }
}
