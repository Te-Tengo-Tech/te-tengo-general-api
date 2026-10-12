package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.application.port.IntentosDeVinculacionRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.CodigoDeUsuario;
import tech.tetengo.api.camaras.domain.model.IntentosDeVinculacion;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * The owner typed or scanned a PC's code (CA-28.1, CA-28.2). Shared by looking it up, approving and
 * rejecting it:
 *
 * <ul>
 *   <li>CA-28.6 (RFC 8628 §5.1): 5 wrong codes in a row from one account block code entry for 15
 *       minutes ({@code 423 VINCULACION_BLOQUEADA {bloqueadaHasta}}); each wrong one answers {@code
 *       404 CODIGO_INCORRECTO {intentosRestantes}}.
 *   <li>CA-28.3: an expired or used code answers {@code 410 CODIGO_VENCIDO}; it does not count as
 *       wrong.
 * </ul>
 *
 * The callers' transactions must not roll back on {@code ErrorDeNegocio}, so wrong codes are counted.
 */
@Service
public class ResolucionDeCodigos {

    private final AutorizacionDeDispositivoRepository autorizaciones;
    private final IntentosDeVinculacionRepository intentos;
    private final Clock reloj;

    public ResolucionDeCodigos(
            AutorizacionDeDispositivoRepository autorizaciones, IntentosDeVinculacionRepository intentos, Clock reloj) {
        this.autorizaciones = autorizaciones;
        this.intentos = intentos;
        this.reloj = reloj;
    }

    /** The pending request of that code, locked; or the error to answer. */
    public AutorizacionDeDispositivo resoluble(UUID usuarioId, String escrito) {
        Instant ahora = reloj.instant();
        IntentosDeVinculacion delUsuario =
                intentos.deUsuarioParaActualizar(usuarioId).orElseGet(() -> new IntentosDeVinculacion(usuarioId));
        if (delUsuario.bloqueada(ahora)) {
            throw bloqueada(delUsuario);
        }
        CodigoDeUsuario codigo = CodigoDeUsuario.leer(escrito)
                .orElseThrow(() -> new IllegalArgumentException("El controlador valida el formato del código"));
        var encontrada = autorizaciones.porCodigoDeUsuarioParaActualizar(Secretos.huella(codigo.valor()));
        if (encontrada.isEmpty()) {
            int restantes = delUsuario.registrarFallo(ahora);
            intentos.guardar(delUsuario);
            if (delUsuario.bloqueada(ahora)) {
                throw bloqueada(delUsuario);
            }
            throw new ErrorDeNegocio(CamaraError.CODIGO_INCORRECTO, Map.of("intentosRestantes", restantes));
        }
        if (delUsuario.getFallidos() > 0) {
            delUsuario.reiniciar();
            intentos.guardar(delUsuario);
        }
        AutorizacionDeDispositivo autorizacion = encontrada.get();
        if (!autorizacion.resoluble(ahora)) {
            throw new ErrorDeNegocio(CamaraError.CODIGO_VENCIDO);
        }
        return autorizacion;
    }

    private static ErrorDeNegocio bloqueada(IntentosDeVinculacion intentos) {
        return new ErrorDeNegocio(
                CamaraError.VINCULACION_BLOQUEADA, Map.of("bloqueadaHasta", intentos.getBloqueadaHasta()));
    }
}
