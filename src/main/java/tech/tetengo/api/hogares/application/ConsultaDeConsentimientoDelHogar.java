package tech.tetengo.api.hogares.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.ConsentimientoDelHogar;
import tech.tetengo.api.hogares.application.port.ConsentimientoRepository;
import tech.tetengo.api.hogares.domain.model.Consentimiento;

/** {@link ConsentimientoDelHogar} for other modules (the movement consent requires it, CA-35.1). */
@Service
public class ConsultaDeConsentimientoDelHogar implements ConsentimientoDelHogar {

    private final ConsentimientoRepository consentimientos;

    public ConsultaDeConsentimientoDelHogar(ConsentimientoRepository consentimientos) {
        this.consentimientos = consentimientos;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean vigente() {
        return consentimientos.ultimo().map(Consentimiento::vigente).orElse(false);
    }
}
