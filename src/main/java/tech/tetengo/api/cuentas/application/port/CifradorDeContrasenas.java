package tech.tetengo.api.cuentas.application.port;

/** Password hashing port (BCrypt adapter). */
public interface CifradorDeContrasenas {

    String cifrar(String contrasena);

    boolean coincide(String contrasena, String cifrada);
}
