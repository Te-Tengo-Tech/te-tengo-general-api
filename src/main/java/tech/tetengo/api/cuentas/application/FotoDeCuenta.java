package tech.tetengo.api.cuentas.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.FotosDeCuentas;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: records the key of a user's photo. */
@Service
public class FotoDeCuenta implements FotosDeCuentas {

    private final CuentaRepository cuentas;

    public FotoDeCuenta(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    @Transactional
    public Optional<String> cambiar(UUID usuarioId, String clave) {
        Cuenta cuenta = buscar(usuarioId);
        Optional<String> anterior = cuenta.cambiarFoto(clave);
        cuentas.guardar(cuenta);
        return anterior;
    }

    @Override
    @Transactional
    public Optional<String> quitar(UUID usuarioId) {
        Cuenta cuenta = buscar(usuarioId);
        Optional<String> anterior = cuenta.quitarFoto();
        cuentas.guardar(cuenta);
        return anterior;
    }

    private Cuenta buscar(UUID usuarioId) {
        return cuentas.buscar(usuarioId).orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
    }
}
