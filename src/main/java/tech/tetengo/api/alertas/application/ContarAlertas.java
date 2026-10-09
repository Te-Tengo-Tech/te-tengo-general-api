package tech.tetengo.api.alertas.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.ConteoDeAlertas;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;

@Service
public class ContarAlertas implements ConteoDeAlertas {

    private final AlertaRepository alertas;

    public ContarAlertas(AlertaRepository alertas) {
        this.alertas = alertas;
    }

    @Override
    @Transactional(readOnly = true)
    public Conteo contar(Instant desde, Instant hasta) {
        return new Conteo(
                alertas.contarSinFalsasAlarmas(TipoAlerta.CAIDA, desde, hasta),
                alertas.contarSinFalsasAlarmas(TipoAlerta.MOVIMIENTO_INESTABLE, desde, hasta),
                alertas.contarFalsasAlarmas(desde, hasta));
    }
}
