package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.CamaraRepository.CamaraDeUnHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-07 / CA-07.2: scheduled job that marks cameras {@code DESCONECTADA} after 3 missed heartbeats.
 * The {@code CamaraDesconectada} event triggers the push. Idempotent: an already disconnected camera
 * is left alone.
 */
@Service
public class DetectarCamarasDesconectadas {

    private static final Logger log = LoggerFactory.getLogger(DetectarCamarasDesconectadas.class);

    private final CamaraRepository camaras;
    private final EjecutorEnHogar enHogar;
    private final PropiedadesDelAgente propiedades;
    private final Clock reloj;

    public DetectarCamarasDesconectadas(
            CamaraRepository camaras, EjecutorEnHogar enHogar, PropiedadesDelAgente propiedades, Clock reloj) {
        this.camaras = camaras;
        this.enHogar = enHogar;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${tetengo.agente.intervalo-senal}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(propiedades.esperaSinSenal());
        for (CamaraDeUnHogar candidata : camaras.enLineaSinSenalDesde(limite)) {
            try {
                enHogar.ejecutar(
                        candidata.hogarId(),
                        () -> camaras.buscar(candidata.camaraId())
                                .filter(camara -> camara.desconectarSiNoHaySenalDesde(limite, ahora))
                                .ifPresent(camaras::guardar));
            } catch (RuntimeException e) {
                log.error("No se pudo revisar la conexión de la cámara {}", candidata.camaraId(), e);
            }
        }
    }
}
