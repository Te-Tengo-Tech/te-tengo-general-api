package tech.tetengo.api.cuentas;

import java.util.UUID;

/** Public API of {@code cuentas}: creates an account, e.g. when an invited family member accepts (CA-08.2). */
public interface AltaDeCuentas {

    /** Returns the new account's id; {@code 409 CORREO_EN_USO} if the e-mail is taken. */
    UUID registrar(String correo, String contrasena, String nombre);
}
