package tech.tetengo.api.hogares.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.AdultoMayorDelHogar;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/** The older adult of the household in context, for the text of push notices. Fails closed. */
@Service
public class ConsultaDeAdultoMayor implements AdultoMayorDelHogar {

    private final HogarRepository hogares;

    public ConsultaDeAdultoMayor(HogarRepository hogares) {
        this.hogares = hogares;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nombreDePila() {
        return HogarActual.obtener()
                .flatMap(hogares::buscar)
                .map(Hogar::getAdultoMayor)
                .map(AdultoMayor::nombreDePila);
    }
}
