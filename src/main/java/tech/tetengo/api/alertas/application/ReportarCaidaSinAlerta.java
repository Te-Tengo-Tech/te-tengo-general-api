package tech.tetengo.api.alertas.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.ReporteDeCaidaRepository;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.ReporteDeCaidaSinAlerta;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.movimientos.DatosDeMovimiento;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-40 / CA-40.4: «Hubo una caída y no avisó». The report is stored as a possible false negative for
 * the evaluation and logged for the team (there is no team role in the API yet); the member is told it
 * was received. It creates no alert and notifies nobody.
 */
@Service
public class ReportarCaidaSinAlerta {

    private static final Logger log = LoggerFactory.getLogger(ReportarCaidaSinAlerta.class);

    /** The phone's clock may be a little ahead of the server's. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private final ReporteDeCaidaRepository reportes;
    private final CamarasDelHogar camaras;
    private final DatosDeMovimiento movimientos;
    private final Clock reloj;

    public ReportarCaidaSinAlerta(
            ReporteDeCaidaRepository reportes, CamarasDelHogar camaras, DatosDeMovimiento movimientos, Clock reloj) {
        this.reportes = reportes;
        this.camaras = camaras;
        this.movimientos = movimientos;
        this.reloj = reloj;
    }

    @Transactional
    public ReporteDeCaidaSinAlerta ejecutar(
            UUID usuarioId, UUID camaraId, String habitacion, Instant ocurridaAproximadamenteEn, String nota) {
        if (ocurridaAproximadamenteEn.isAfter(reloj.instant().plus(TOLERANCIA))) {
            throw new ErrorDeNegocio(
                    AlertaError.VALIDACION,
                    Map.of("campos", Map.of("ocurridaAproximadamenteEn", "Elige una hora que ya pasó.")));
        }
        String nombre = habitacion == null || habitacion.isBlank() ? null : habitacion.strip();
        if (camaraId != null) {
            nombre = camaras.buscar(camaraId)
                    .map(CamarasDelHogar.CamaraDelHogar::nombreHabitacion)
                    .orElseThrow(() -> new ErrorDeNegocio(AlertaError.CAMARA_NO_ENCONTRADA));
        }
        ReporteDeCaidaSinAlerta reporte = reportes.guardar(new ReporteDeCaidaSinAlerta(
                usuarioId, camaraId, nombre, ocurridaAproximadamenteEn, nota, movimientos.guardadoEnLaNube()));
        log.warn(
                "Posible falso negativo informado por la familia: reporte {} (cámara {}, {})",
                reporte.getId(),
                camaraId,
                ocurridaAproximadamenteEn);
        return reporte;
    }
}
