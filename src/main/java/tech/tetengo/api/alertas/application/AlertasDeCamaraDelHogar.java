package tech.tetengo.api.alertas.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.monitoreo.AlertasDeCamara;

/** CA-23.2: a live view opened from an alert must be of the alert's camera ({@code monitoreo}'s SPI). */
@Service
public class AlertasDeCamaraDelHogar implements AlertasDeCamara {

    private final AlertaRepository alertas;

    public AlertasDeCamaraDelHogar(AlertaRepository alertas) {
        this.alertas = alertas;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esDeLaCamara(UUID alertaId, UUID camaraId) {
        return alertas.buscar(alertaId)
                .map(alerta -> alerta.getCamaraId().equals(camaraId))
                .orElse(false);
    }
}
