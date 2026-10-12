package tech.tetengo.api.versiones.domain.model;

import java.net.URI;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The latest version on every channel that has one, and the downloads page of «Ir a la descarga»
 * (CA-43.3).
 */
public record Versiones(Map<Canal, VersionPublicada> canales, URI paginaDescargas) {

    public Versiones {
        EnumMap<Canal, VersionPublicada> copia = new EnumMap<>(Canal.class);
        if (canales != null) {
            copia.putAll(canales);
        }
        canales = Collections.unmodifiableMap(copia);
    }

    public Optional<VersionPublicada> canal(Canal canal) {
        return Optional.ofNullable(canales.get(canal));
    }
}
