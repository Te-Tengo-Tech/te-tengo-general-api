package tech.tetengo.api.cuentas.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.CifradorDeContrasenas;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * API contract §13: a signed-in user changes their password with the current one. Unlike a recovery,
 * other sessions stay open: whoever changes it is already signed in.
 */
@Service
public class CambiarContrasena {

    private final CuentaRepository cuentas;
    private final CifradorDeContrasenas cifrador;

    public CambiarContrasena(CuentaRepository cuentas, CifradorDeContrasenas cifrador) {
        this.cuentas = cuentas;
        this.cifrador = cifrador;
    }

    @Transactional
    public void ejecutar(UUID usuarioId, String actual, String nueva) {
        Cuenta cuenta = cuentas.buscar(usuarioId).orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
        if (!cifrador.coincide(actual, cuenta.getContrasenaCifrada())) {
            throw new ErrorDeNegocio(CuentaError.CONTRASENA_ACTUAL_INCORRECTA);
        }
        cuenta.cambiarContrasena(cifrador.cifrar(nueva));
        cuentas.guardar(cuenta);
    }
}
