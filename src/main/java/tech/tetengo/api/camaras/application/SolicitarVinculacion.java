package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.CodigoDeUsuario;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-28 / CA-28.1: a freshly installed PC asks to be linked (RFC 8628 §3.1, the device authorization
 * request). It gets a 256-bit device code it keeps secret and an 8-letter user code it shows with a
 * QR, valid for 10 minutes; when it expires the PC asks again on its own (CA-28.3). Both codes are
 * stored hashed.
 */
@Service
public class SolicitarVinculacion {

    private static final int INTENTOS_DE_CODIGO_LIBRE = 10;

    private final AutorizacionDeDispositivoRepository autorizaciones;
    private final PropiedadesDeVinculacion propiedades;
    private final Clock reloj;

    public SolicitarVinculacion(
            AutorizacionDeDispositivoRepository autorizaciones, PropiedadesDeVinculacion propiedades, Clock reloj) {
        this.autorizaciones = autorizaciones;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional
    public SolicitudDeVinculacion ejecutar(
            String cliente, String nombrePc, String sistemaOperativo, String versionAgente, String idInstalacion) {
        exigirCliente(cliente, propiedades);
        if (nombrePc == null || nombrePc.isBlank()) {
            throw new ErrorOAuth(ErrorOAuth.SOLICITUD_INVALIDA, "Falta nombre_pc, el nombre de la PC.");
        }
        UUID instalacion = idInstalacion(idInstalacion);
        Instant ahora = reloj.instant();
        CodigoDeUsuario codigo = codigoLibre(ahora);
        String codigoDispositivo = Secretos.generar();
        AutorizacionDeDispositivo autorizacion;
        try {
            autorizacion = new AutorizacionDeDispositivo(
                    Secretos.huella(codigoDispositivo),
                    Secretos.huella(codigo.valor()),
                    cliente,
                    nombrePc,
                    sistemaOperativo,
                    versionAgente,
                    instalacion,
                    ahora,
                    propiedades.vigenciaCodigo(),
                    propiedades.intervalo());
        } catch (ErrorDeNegocio e) {
            throw new ErrorOAuth(ErrorOAuth.SOLICITUD_INVALIDA, "nombre_pc: " + e.getMessage());
        }
        autorizaciones.guardar(autorizacion);
        return new SolicitudDeVinculacion(
                codigoDispositivo,
                codigo.legible(),
                propiedades.uriVerificacion(),
                propiedades.uriCompleta(codigo.legible()),
                propiedades.vigenciaCodigo().toSeconds(),
                propiedades.intervalo().toSeconds());
    }

    /** A user code no pending request uses, so the owner never approves someone else's PC by chance. */
    private CodigoDeUsuario codigoLibre(Instant ahora) {
        for (int intento = 0; intento < INTENTOS_DE_CODIGO_LIBRE; intento++) {
            CodigoDeUsuario codigo = CodigoDeUsuario.generar();
            if (!autorizaciones.codigoDeUsuarioEnUso(Secretos.huella(codigo.valor()), ahora)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se encontró un código de vinculación libre");
    }

    /** Optional {@code id_instalacion}: a UUID, or nothing for older agents. */
    private static UUID idInstalacion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            throw new ErrorOAuth(ErrorOAuth.SOLICITUD_INVALIDA, "id_instalacion debe ser un UUID.");
        }
    }

    static void exigirCliente(String cliente, PropiedadesDeVinculacion propiedades) {
        if (cliente == null || cliente.isBlank()) {
            throw new ErrorOAuth(ErrorOAuth.SOLICITUD_INVALIDA, "Falta client_id.");
        }
        if (!propiedades.clientes().contains(cliente)) {
            throw new ErrorOAuth(ErrorOAuth.CLIENTE_INVALIDO, "client_id desconocido.");
        }
    }
}
