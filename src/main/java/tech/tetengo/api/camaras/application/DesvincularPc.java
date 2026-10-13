package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.DatosDeLaPcBorrados;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.application.port.TokenDeRenovacionRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.MotivoDesvinculacion;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-31: a PC leaves the household.
 *
 * <ul>
 *   <li>From the app, by the owner (CA-31.1, CA-31.2): the app stops showing it at once; the PC stops
 *       capturing, deletes its credentials and shows the link code again as soon as it hears it, on
 *       its control channel or at its next request ({@code 401 PC_DESVINCULADA}).
 *   <li>From the PC itself (CA-31.3), with its access token or by revoking its refresh token (RFC
 *       7009): the family is notified.
 * </ul>
 */
@Service
public class DesvincularPc {

    private final PcRepository pcs;
    private final TokenDeRenovacionRepository tokens;
    private final DesvinculacionDePcs desvinculacion;
    private final EjecutorEnHogar enHogar;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public DesvincularPc(
            PcRepository pcs,
            TokenDeRenovacionRepository tokens,
            DesvinculacionDePcs desvinculacion,
            EjecutorEnHogar enHogar,
            ApplicationEventPublisher eventos,
            Clock reloj) {
        this.pcs = pcs;
        this.tokens = tokens;
        this.desvinculacion = desvinculacion;
        this.enHogar = enHogar;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** CA-31.1, CA-31.2: the owner removes it from the app. */
    @Transactional
    public void desdeLaApp(UUID pcId, UUID titularId) {
        var pc = pcs.buscarParaActualizar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
        desvinculacion.desvincular(pc, MotivoDesvinculacion.DESDE_LA_APP, titularId, reloj.instant());
    }

    /** CA-31.3: «Desvincular esta PC» on the PC, with its access token. */
    @Transactional
    public void desdeLaPc(UUID pcId) {
        desdeLaPc(pcId, false);
    }

    /**
     * The same; with {@code datosBorrados} the PC also deletes its data folder (CA-31.6), which the
     * activity log records.
     */
    @Transactional
    public void desdeLaPc(UUID pcId, boolean datosBorrados) {
        desdeLaPc(pcId, datosBorrados, MotivoDesvinculacion.DESDE_LA_PC);
    }

    /**
     * The same, saying why: {@link MotivoDesvinculacion#RESTABLECIDA} («Restablecer esta PC») or
     * {@link MotivoDesvinculacion#DESINSTALADA} (the uninstaller), for the activity log.
     */
    @Transactional
    public void desdeLaPc(UUID pcId, boolean datosBorrados, MotivoDesvinculacion motivo) {
        var pc = pcs.buscarParaActualizar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
        var ahora = reloj.instant();
        if (desvinculacion.desvincular(pc, motivo, null, ahora) && datosBorrados) {
            eventos.publishEvent(new DatosDeLaPcBorrados(pc.getHogarId(), pc.getId(), pc.getNombre(), ahora));
        }
    }

    /**
     * RFC 7009: the PC revokes its refresh token, which unlinks it (CA-31.3). Unknown or already
     * revoked tokens change nothing, and the answer is the same (RFC 7009 §2.2).
     */
    public void revocandoToken(String cliente, String token, PropiedadesDeVinculacion propiedades) {
        revocandoToken(cliente, token, propiedades, false);
    }

    /** The same; {@code datosBorrados}: CA-31.6. */
    public void revocandoToken(
            String cliente, String token, PropiedadesDeVinculacion propiedades, boolean datosBorrados) {
        revocandoToken(cliente, token, propiedades, datosBorrados, MotivoDesvinculacion.DESDE_LA_PC);
    }

    /** The same, saying why ({@link #desdeLaPc(UUID, boolean, MotivoDesvinculacion)}). */
    public void revocandoToken(
            String cliente,
            String token,
            PropiedadesDeVinculacion propiedades,
            boolean datosBorrados,
            MotivoDesvinculacion motivo) {
        SolicitarVinculacion.exigirCliente(cliente, propiedades);
        if (token == null || token.isBlank()) {
            throw new ErrorOAuth(ErrorOAuth.SOLICITUD_INVALIDA, "Falta token.");
        }
        String huella = Secretos.huella(token);
        Optional<TokenDeRenovacionDePc> leido =
                tokens.porHuella(huella).filter(t -> t.getCliente().equals(cliente));
        leido.ifPresent(t -> enHogar.ejecutar(t.getHogarId(), () -> {
            var ahora = reloj.instant();
            tokens.revocarTodosDe(t.getPcId(), ahora);
            pcs.buscarParaActualizar(t.getPcId()).ifPresent(pc -> {
                if (desvinculacion.desvincular(pc, motivo, null, ahora) && datosBorrados) {
                    eventos.publishEvent(new DatosDeLaPcBorrados(pc.getHogarId(), pc.getId(), pc.getNombre(), ahora));
                }
            });
        }));
    }
}
