package tech.tetengo.api.versiones;

import java.util.Optional;

/** The published versions, for other modules (the agent's remote configuration). */
public interface VersionesPublicadas {

    /**
     * The latest published version of Te Tengo Captura for Windows, as {@code MAJOR.MINOR.PATCH};
     * empty while nothing is published.
     */
    Optional<String> ultimaDeEscritorio();
}
