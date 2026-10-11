package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.application.port.TokenDeRenovacionRepository;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.EstadoAutorizacion;
import tech.tetengo.api.camaras.domain.model.MotivoDesvinculacion;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.ResolvedorDeHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * The token endpoint of the PC link (RFC 6749 §3.2), with two grants:
 *
 * <ul>
 *   <li>{@code urn:ietf:params:oauth:grant-type:device_code} (RFC 8628 §3.4, §3.5): the PC polls with
 *       its device code and gets {@code authorization_pending}, {@code slow_down} (polled sooner than
 *       the interval; 5 s are added), {@code access_denied} (CA-28.4), {@code expired_token} (CA-28.3)
 *       or, once the owner approved it, its tokens. A device code is redeemed once.
 *   <li>{@code refresh_token} (RFC 6749 §6): a refresh token works once and its use returns the next
 *       one (RFC 9700 §2.2.2). A rotated token presented again revokes every token of the PC and
 *       unlinks it (RFC 9700 §4.14.2), and an expired one (90 days without connecting, CA-28.7)
 *       unlinks it too: the PC must be linked again.
 * </ul>
 *
 * Errors are returned, not thrown, so what they change (the polling interval, a revocation) is
 * committed. Each call runs in one transaction bound to the household of the PC, if it has one.
 */
@Service
public class CanjearTokenDePc {

    public static final String CONCESION_DISPOSITIVO = "urn:ietf:params:oauth:grant-type:device_code";
    public static final String CONCESION_RENOVACION = "refresh_token";

    private static final Logger log = LoggerFactory.getLogger(CanjearTokenDePc.class);

    private final AutorizacionDeDispositivoRepository autorizaciones;
    private final TokenDeRenovacionRepository tokens;
    private final PcRepository pcs;
    private final EmisionDeTokensDePc emision;
    private final DesvinculacionDePcs desvinculacion;
    private final PropiedadesDeVinculacion propiedades;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public CanjearTokenDePc(
            AutorizacionDeDispositivoRepository autorizaciones,
            TokenDeRenovacionRepository tokens,
            PcRepository pcs,
            EmisionDeTokensDePc emision,
            DesvinculacionDePcs desvinculacion,
            PropiedadesDeVinculacion propiedades,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.autorizaciones = autorizaciones;
        this.tokens = tokens;
        this.pcs = pcs;
        this.emision = emision;
        this.desvinculacion = desvinculacion;
        this.propiedades = propiedades;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    /** The tokens, or the OAuth error to answer. */
    public sealed interface Respuesta permits Emitidos, Rechazada {}

    public record Emitidos(TokensDePc tokens) implements Respuesta {}

    public record Rechazada(ErrorOAuth error) implements Respuesta {}

    public Respuesta ejecutar(String concesion, String cliente, String codigoDispositivo, String tokenRenovacion) {
        if (concesion == null || concesion.isBlank()) {
            return rechazo(ErrorOAuth.SOLICITUD_INVALIDA, "Falta grant_type.");
        }
        try {
            SolicitarVinculacion.exigirCliente(cliente, propiedades);
        } catch (ErrorOAuth e) {
            return new Rechazada(e);
        }
        return switch (concesion) {
            case CONCESION_DISPOSITIVO ->
                vacio(codigoDispositivo)
                        ? rechazo(ErrorOAuth.SOLICITUD_INVALIDA, "Falta device_code.")
                        : porCodigoDeDispositivo(cliente, codigoDispositivo);
            case CONCESION_RENOVACION ->
                vacio(tokenRenovacion)
                        ? rechazo(ErrorOAuth.SOLICITUD_INVALIDA, "Falta refresh_token.")
                        : porRenovacion(cliente, tokenRenovacion);
            default -> rechazo(ErrorOAuth.CONCESION_NO_SOPORTADA, "grant_type no soportado.");
        };
    }

    private Respuesta porCodigoDeDispositivo(String cliente, String codigo) {
        String huella = Secretos.huella(codigo);
        Optional<AutorizacionDeDispositivo> leida = autorizaciones.porCodigoDeDispositivo(huella);
        if (leida.isEmpty()) {
            return rechazo(ErrorOAuth.CONCESION_INVALIDA, "device_code desconocido.");
        }
        UUID hogar = Objects.requireNonNullElse(leida.get().getHogarId(), ResolvedorDeHogar.SIN_HOGAR);
        return enHogar.obtener(hogar, () -> {
            AutorizacionDeDispositivo autorizacion =
                    autorizaciones.porCodigoDeDispositivoParaActualizar(huella).orElseThrow();
            if (!autorizacion.getCliente().equals(cliente)) {
                return rechazo(ErrorOAuth.CONCESION_INVALIDA, "device_code de otro cliente.");
            }
            if (autorizacion.getEstado() == EstadoAutorizacion.APROBADA && !hogar.equals(autorizacion.getHogarId())) {
                // Approved between the read and the lock: the next poll is bound to its household.
                return rechazo(ErrorOAuth.AUTORIZACION_PENDIENTE, "Esperando la aprobación en el celular.");
            }
            Instant ahora = reloj.instant();
            AutorizacionDeDispositivo.Consulta consulta = autorizacion.consultar(ahora);
            autorizaciones.guardar(autorizacion);
            return switch (consulta) {
                case PENDIENTE -> rechazo(ErrorOAuth.AUTORIZACION_PENDIENTE, "Esperando la aprobación en el celular.");
                case MAS_LENTO ->
                    rechazo(ErrorOAuth.MAS_LENTO, "Consulta cada %d s.".formatted(autorizacion.getIntervaloSegundos()));
                case VENCIDA -> rechazo(ErrorOAuth.CODIGO_VENCIDO, "El código venció; pide uno nuevo.");
                case RECHAZADA -> rechazo(ErrorOAuth.ACCESO_DENEGADO, "La familia no reconoció esta PC.");
                case YA_CANJEADA -> {
                    log.warn("Código de dispositivo ya canjeado presentado otra vez (PC {})", autorizacion.getPcId());
                    yield rechazo(ErrorOAuth.CONCESION_INVALIDA, "device_code ya usado.");
                }
                case LISTA ->
                    pcs.buscarParaActualizar(autorizacion.getPcId())
                            .<Respuesta>map(pc -> new Emitidos(emision.emitir(pc, cliente)))
                            .orElseGet(() -> rechazo(ErrorOAuth.ACCESO_DENEGADO, "La PC se quitó de la vivienda."));
            };
        });
    }

    private Respuesta porRenovacion(String cliente, String token) {
        String huella = Secretos.huella(token);
        Optional<TokenDeRenovacionDePc> leido = tokens.porHuella(huella);
        if (leido.isEmpty()) {
            return rechazo(ErrorOAuth.CONCESION_INVALIDA, "refresh_token desconocido.");
        }
        return enHogar.obtener(leido.get().getHogarId(), () -> {
            TokenDeRenovacionDePc presentado =
                    tokens.porHuellaParaActualizar(huella).orElseThrow();
            Instant ahora = reloj.instant();
            return switch (presentado.evaluar(cliente, ahora)) {
                case REVOCADO, OTRO_CLIENTE -> rechazo(ErrorOAuth.CONCESION_INVALIDA, "refresh_token inválido.");
                case REUTILIZADO -> {
                    log.warn("Token de renovación reutilizado: se revoca y desvincula la PC {}", presentado.getPcId());
                    desvincular(presentado.getPcId(), MotivoDesvinculacion.REUSO_DE_TOKEN, ahora);
                    yield rechazo(ErrorOAuth.CONCESION_INVALIDA, "refresh_token ya usado; vuelve a vincular la PC.");
                }
                case VENCIDO -> {
                    desvincular(presentado.getPcId(), MotivoDesvinculacion.VENCIDA, ahora);
                    yield rechazo(ErrorOAuth.CONCESION_INVALIDA, "La vinculación venció; vuelve a vincular la PC.");
                }
                case VALIDO -> {
                    Optional<Pc> pc = pcs.buscarParaActualizar(presentado.getPcId());
                    presentado.usar(ahora);
                    tokens.guardar(presentado);
                    yield pc.<Respuesta>map(vinculada -> new Emitidos(emision.emitir(vinculada, cliente)))
                            .orElseGet(() -> rechazo(ErrorOAuth.CONCESION_INVALIDA, "La PC no está vinculada."));
                }
            };
        });
    }

    private void desvincular(UUID pcId, MotivoDesvinculacion motivo, Instant ahora) {
        tokens.revocarTodosDe(pcId, ahora);
        pcs.buscarParaActualizar(pcId).ifPresent(pc -> desvinculacion.desvincular(pc, motivo, null, ahora));
    }

    private static Respuesta rechazo(String codigo, String descripcion) {
        return new Rechazada(new ErrorOAuth(codigo, descripcion));
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
