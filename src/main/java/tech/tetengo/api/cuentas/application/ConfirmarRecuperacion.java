package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.CifradorDeContrasenas;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.application.port.RecuperacionRepository;
import tech.tetengo.api.cuentas.application.port.SesionRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.cuentas.domain.model.Recuperacion;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-03: sets a new password with a valid link. Links last 30 minutes (CA-03.3) and work once. Open
 * sessions are closed, so a stolen session does not survive the reset.
 */
@Service
public class ConfirmarRecuperacion {

    private final RecuperacionRepository recuperaciones;
    private final CuentaRepository cuentas;
    private final SesionRepository sesiones;
    private final CifradorDeContrasenas cifrador;
    private final Clock reloj;

    public ConfirmarRecuperacion(
            RecuperacionRepository recuperaciones,
            CuentaRepository cuentas,
            SesionRepository sesiones,
            CifradorDeContrasenas cifrador,
            Clock reloj) {
        this.recuperaciones = recuperaciones;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(String token, String nuevaContrasena) {
        Instant ahora = reloj.instant();
        Recuperacion recuperacion = recuperaciones
                .buscarPorToken(Secretos.huella(token))
                .orElseThrow(() -> new ErrorDeNegocio(CuentaError.ENLACE_VENCIDO));
        recuperacion.usar(ahora);
        Cuenta cuenta = cuentas.buscar(recuperacion.getUsuarioId())
                .orElseThrow(() -> new ErrorDeNegocio(CuentaError.ENLACE_VENCIDO));
        cuenta.cambiarContrasena(cifrador.cifrar(nuevaContrasena));
        sesiones.cerrarTodas(cuenta.getId(), ahora);
    }
}
