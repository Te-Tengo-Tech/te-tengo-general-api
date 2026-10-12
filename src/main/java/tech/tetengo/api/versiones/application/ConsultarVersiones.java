package tech.tetengo.api.versiones.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.versiones.VersionesPublicadas;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos.Lectura;
import tech.tetengo.api.versiones.domain.model.Canal;
import tech.tetengo.api.versiones.domain.model.VersionPublicada;
import tech.tetengo.api.versiones.domain.model.Versiones;

/**
 * US-43: the latest version on each channel (CA-43.1 to CA-43.6) with the minimum the backend
 * supports (CA-43.8). Each channel's manifest is read again once {@code vigencia} has passed, by one
 * request while the others keep answering with what was read. A manifest that cannot be read, or
 * that is invalid, keeps its last good contents, so a network failure or a bad upload never hides a
 * published version; a missing one ({@code 404}) means nothing is published there. While no
 * manifest publishes Te Tengo Captura, the backend's fallback setting gives its version alone.
 */
@Service
public class ConsultarVersiones implements VersionesPublicadas {

    private static final Logger log = LoggerFactory.getLogger(ConsultarVersiones.class);

    private final FuenteDeManifiestos fuente;
    private final PropiedadesDeVersiones propiedades;
    private final Clock reloj;
    private final ReentrantLock lectura = new ReentrantLock();
    private volatile Map<Canal, VersionPublicada> publicadas = Map.of();
    private volatile Instant leidasEn;

    public ConsultarVersiones(FuenteDeManifiestos fuente, PropiedadesDeVersiones propiedades, Clock reloj) {
        this.fuente = fuente;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public Versiones consultar() {
        leerSiHaceFalta();
        EnumMap<Canal, VersionPublicada> canales = new EnumMap<>(Canal.class);
        canales.putAll(publicadas);
        propiedades.respaldo().ifPresent(respaldo -> {
            canales.putIfAbsent(Canal.WINDOWS, VersionPublicada.soloVersion(respaldo));
            canales.putIfAbsent(Canal.MAC, VersionPublicada.soloVersion(respaldo));
        });
        canales.replaceAll((canal, publicada) ->
                publicada.conMinima(propiedades.minima(canal).orElse(null)));
        return new Versiones(canales, propiedades.paginaDescargas());
    }

    @Override
    public Optional<String> ultimaDeEscritorio() {
        return consultar().canal(Canal.WINDOWS).map(v -> v.version().toString());
    }

    private void leerSiHaceFalta() {
        if (propiedades.manifiestos().isEmpty() || vigentes()) {
            return;
        }
        // The first read waits for the manifests; later ones are done by one request at a time.
        if (leidasEn == null) {
            lectura.lock();
        } else if (!lectura.tryLock()) {
            return;
        }
        try {
            if (vigentes()) {
                return;
            }
            EnumMap<Canal, VersionPublicada> leidas = new EnumMap<>(Canal.class);
            leidas.putAll(publicadas);
            propiedades.manifiestos().forEach((canal, manifiesto) -> leer(canal, manifiesto, leidas));
            publicadas = Map.copyOf(leidas);
            leidasEn = reloj.instant();
        } finally {
            lectura.unlock();
        }
    }

    private void leer(Canal canal, URI manifiesto, Map<Canal, VersionPublicada> leidas) {
        switch (fuente.leer(manifiesto)) {
            case Lectura.SinPublicar ignorada -> leidas.remove(canal);
            case Lectura.Fallida ignorada -> {
                // Keeps the last good read of the channel.
            }
            case Lectura.Publicada(var entrada) -> {
                try {
                    VersionPublicada publicada = VersionPublicada.de(canal, entrada);
                    propiedades
                            .minima(canal)
                            .filter(minima -> publicada.version().esAnteriorA(minima))
                            .ifPresent(minima -> log.warn(
                                    "La versión mínima de {} ({}) es mayor que la publicada ({}): se usa la publicada",
                                    canal.clave(),
                                    minima,
                                    publicada.version()));
                    leidas.put(canal, publicada);
                } catch (IllegalArgumentException e) {
                    log.warn("Manifiesto {} no válido, se conserva lo último leído: {}", manifiesto, e.getMessage());
                }
            }
        }
    }

    private boolean vigentes() {
        Instant ultima = leidasEn;
        return ultima != null && reloj.instant().isBefore(ultima.plus(propiedades.vigencia()));
    }
}
