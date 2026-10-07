package tech.tetengo.api.cuentas.infrastructure.seguridad;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import tech.tetengo.api.cuentas.application.port.CifradorDeContrasenas;

/** BCrypt with Spring Security's default strength. */
@Component
class CifradorBCrypt implements CifradorDeContrasenas {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String cifrar(String contrasena) {
        return bcrypt.encode(contrasena);
    }

    @Override
    public boolean coincide(String contrasena, String cifrada) {
        return contrasena != null && cifrada != null && bcrypt.matches(contrasena, cifrada);
    }
}
