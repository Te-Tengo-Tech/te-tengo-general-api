package tech.tetengo.api.versiones.application;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tech.tetengo.api.versiones.domain.model.Canal;
import tech.tetengo.api.versiones.domain.model.NumeroDeVersion;

/**
 * Where the published versions come from (US-43, ADR 0012).
 *
 * @param urlBase public base address of the downloads (the R2 bucket the landing links to); the
 *     release workflows write one manifest per channel there, {@code versiones/<canal>.json}. Blank: no
 *     manifest is read
 * @param paginaDescargas the downloads page, «Ir a la descarga» (CA-43.3)
 * @param vigencia how long a read manifest is served before reading it again
 * @param espera connection and read timeout of each manifest
 * @param respaldoEscritorio Te Tengo Captura's version while no manifest publishes one (the former
 *     {@code TT_AGENTE_VERSION_PUBLICADA}); blank: none
 * @param minimas minimum supported version per channel key ({@code windows}, {@code apk}…), CA-43.8;
 *     blank: every version is supported
 */
@ConfigurationProperties("tetengo.versiones")
public record PropiedadesDeVersiones(
        String urlBase,
        URI paginaDescargas,
        Duration vigencia,
        Duration espera,
        String respaldoEscritorio,
        Map<String, String> minimas) {

    public PropiedadesDeVersiones {
        vigencia = vigencia == null ? Duration.ofMinutes(5) : vigencia;
        espera = espera == null ? Duration.ofSeconds(5) : espera;
        minimas = minimas == null ? Map.of() : new TreeMap<>(minimas);
    }

    /** The manifest of each channel, {@code <urlBase>/versiones/<canal>.json}; empty without {@code urlBase}. */
    public Map<Canal, URI> manifiestos() {
        if (urlBase == null || urlBase.isBlank()) {
            return Map.of();
        }
        String base = urlBase.strip().replaceAll("/+$", "");
        Map<Canal, URI> manifiestos = new EnumMap<>(Canal.class);
        for (Canal canal : Canal.values()) {
            manifiestos.put(canal, URI.create(base + "/versiones/" + canal.clave() + ".json"));
        }
        return manifiestos;
    }

    public Optional<NumeroDeVersion> respaldo() {
        return NumeroDeVersion.leer(respaldoEscritorio);
    }

    /** The minimum of a channel; a blank or malformed value means none. */
    public Optional<NumeroDeVersion> minima(Canal canal) {
        return NumeroDeVersion.leer(minimas.get(canal.clave()));
    }
}
