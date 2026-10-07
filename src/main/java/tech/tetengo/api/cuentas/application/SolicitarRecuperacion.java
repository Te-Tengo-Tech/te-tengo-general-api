package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.application.port.RecuperacionRepository;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.cuentas.domain.model.Recuperacion;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;
import tech.tetengo.api.shared.application.port.NotificadorCorreo.Correo;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-03: sends a reset link to a registered e-mail (CA-03.1). Unknown e-mails get no message and the
 * caller cannot tell the difference (CA-03.2).
 */
@Service
public class SolicitarRecuperacion {

    private final CuentaRepository cuentas;
    private final RecuperacionRepository recuperaciones;
    private final NotificadorCorreo correo;
    private final PropiedadesDeEnlaces enlaces;
    private final Clock reloj;

    public SolicitarRecuperacion(
            CuentaRepository cuentas,
            RecuperacionRepository recuperaciones,
            NotificadorCorreo correo,
            PropiedadesDeEnlaces enlaces,
            Clock reloj) {
        this.cuentas = cuentas;
        this.recuperaciones = recuperaciones;
        this.correo = correo;
        this.enlaces = enlaces;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(String direccion) {
        cuentas.buscarPorCorreo(Cuenta.normalizarCorreo(direccion)).ifPresent(this::enviarEnlace);
    }

    private void enviarEnlace(Cuenta cuenta) {
        String token = Secretos.generar();
        recuperaciones.guardar(new Recuperacion(cuenta.getId(), Secretos.huella(token), reloj.instant()));
        correo.enviar(new Correo(cuenta.getCorreo(), "Te Tengo: restablece tu contraseña", """
                Hola, %s:

                Recibimos una solicitud para restablecer tu contraseña de Te Tengo.
                Abre este enlace en tu celular para crear una nueva (vence en 30 minutos):

                %s

                Si no la solicitaste, ignora este correo.
                """.formatted(
                        cuenta.getNombre(), enlaces.recuperacion(token))));
    }
}
