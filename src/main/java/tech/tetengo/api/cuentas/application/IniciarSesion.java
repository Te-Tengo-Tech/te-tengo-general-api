package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.MembresiasDeUsuario;
import tech.tetengo.api.cuentas.MembresiasDeUsuario.HogarDelUsuario;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.cuentas.application.port.CifradorDeContrasenas;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-02: sign-in with e-mail and password (CA-02.1, CA-02.2) and lockout after 5 consecutive
 * failures (CA-02.3). Failed attempts are committed even though the use case throws.
 */
@Service
public class IniciarSesion {

    private final CuentaRepository cuentas;
    private final CifradorDeContrasenas cifrador;
    private final AbrirSesion abrirSesion;
    private final ObjectProvider<MembresiasDeUsuario> membresias;
    private final Clock reloj;
    private volatile String cifradaFicticia;

    public IniciarSesion(
            CuentaRepository cuentas,
            CifradorDeContrasenas cifrador,
            AbrirSesion abrirSesion,
            ObjectProvider<MembresiasDeUsuario> membresias,
            Clock reloj) {
        this.cuentas = cuentas;
        this.cifrador = cifrador;
        this.abrirSesion = abrirSesion;
        this.membresias = membresias;
        this.reloj = reloj;
    }

    @Transactional(noRollbackFor = ErrorDeNegocio.class)
    public Sesion ejecutar(String correo, String contrasena) {
        Optional<Cuenta> encontrada = cuentas.buscarPorCorreo(Cuenta.normalizarCorreo(correo));
        if (encontrada.isEmpty()) {
            // Same cost as a real check, so response times do not reveal which e-mails exist.
            cifrador.coincide(contrasena, cifradaFicticia());
            throw new ErrorDeNegocio(CuentaError.CREDENCIALES_INVALIDAS);
        }
        Cuenta cuenta = encontrada.get();
        cuenta.autenticar(cifrador.coincide(contrasena, cuenta.getContrasenaCifrada()), reloj.instant());
        Optional<HogarDelUsuario> hogar =
                Optional.ofNullable(membresias.getIfAvailable()).flatMap(m -> m.hogarPredeterminado(cuenta.getId()));
        return abrirSesion.abrir(
                cuenta.getId(),
                hogar.map(HogarDelUsuario::hogarId).orElse(null),
                hogar.map(HogarDelUsuario::rol).orElse(null),
                null);
    }

    private String cifradaFicticia() {
        if (cifradaFicticia == null) {
            cifradaFicticia = cifrador.cifrar("contrasena-ficticia");
        }
        return cifradaFicticia;
    }
}
