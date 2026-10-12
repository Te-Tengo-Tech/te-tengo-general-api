package tech.tetengo.api.cuentas.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: each person edits their own name, phone and relationship. */
@Service
public class ActualizarPerfil {

    private final CuentaRepository cuentas;
    private final ConsultarPerfil consultarPerfil;

    public ActualizarPerfil(CuentaRepository cuentas, ConsultarPerfil consultarPerfil) {
        this.cuentas = cuentas;
        this.consultarPerfil = consultarPerfil;
    }

    @Transactional
    public Perfil ejecutar(UUID usuarioId, String nombre, String telefono, String parentesco) {
        Cuenta cuenta = cuentas.buscar(usuarioId).orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
        cuenta.actualizarPerfil(nombre, telefono, parentesco);
        return consultarPerfil.perfil(cuentas.guardar(cuenta));
    }
}
