package tech.tetengo.api.cuentas.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.AltaDeCuentas;
import tech.tetengo.api.cuentas.application.port.CifradorDeContrasenas;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** US-01: a family member creates an account (CA-01.1); the e-mail must be unique (CA-01.2). */
@Service
public class RegistrarCuenta implements AltaDeCuentas {

    private final CuentaRepository cuentas;
    private final CifradorDeContrasenas cifrador;

    public RegistrarCuenta(CuentaRepository cuentas, CifradorDeContrasenas cifrador) {
        this.cuentas = cuentas;
        this.cifrador = cifrador;
    }

    @Transactional
    public Cuenta ejecutar(String correo, String contrasena, String nombre) {
        if (cuentas.existeCorreo(Cuenta.normalizarCorreo(correo))) {
            throw new ErrorDeNegocio(CuentaError.CORREO_EN_USO);
        }
        return cuentas.guardar(new Cuenta(correo, nombre, cifrador.cifrar(contrasena)));
    }

    @Override
    @Transactional
    public UUID registrar(String correo, String contrasena, String nombre) {
        return ejecutar(correo, contrasena, nombre).getId();
    }
}
