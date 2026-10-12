package tech.tetengo.api.hogares.application;

import java.util.Optional;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * The household of the request as seen by one of its members, with its latest consent, the number
 * of active push devices of its members (0: nobody in the family can receive alerts) and the deletion
 * of the recordings after the latest revocation, if any. {@code fotoAdultoMayor}: null without a photo
 * (API contract §13).
 */
public record HogarConsultado(
        Hogar hogar,
        Rol rol,
        Optional<ConsentimientoConsultado> consentimiento,
        long dispositivosActivos,
        Optional<EliminacionConsultada> eliminacion,
        Foto fotoAdultoMayor) {}
