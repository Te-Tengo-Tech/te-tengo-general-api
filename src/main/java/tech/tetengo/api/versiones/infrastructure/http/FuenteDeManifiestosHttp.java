package tech.tetengo.api.versiones.infrastructure.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tech.tetengo.api.versiones.application.PropiedadesDeVersiones;
import tech.tetengo.api.versiones.application.port.FuenteDeManifiestos;
import tech.tetengo.api.versiones.domain.model.EntradaDeManifiesto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a channel's manifest over HTTPS from the public downloads bucket (ADR 0012):
 * {@code {"version", "url", "sha256", "tamanoBytes", "publicadaEn", "notas"}}. A {@code 404} means
 * nothing was published on that channel; any other failure keeps the last read.
 */
@Component
class FuenteDeManifiestosHttp implements FuenteDeManifiestos {

    private static final Logger log = LoggerFactory.getLogger(FuenteDeManifiestosHttp.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A manifest is a few hundred bytes; anything far larger is not one. */
    static final int TAMANO_MAXIMO = 64 * 1024;

    private final RestClient cliente;

    FuenteDeManifiestosHttp(PropiedadesDeVersiones propiedades) {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(propiedades.espera())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        fabrica.setReadTimeout(propiedades.espera());
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public Lectura leer(URI manifiesto) {
        try {
            return cliente.get()
                    .uri(manifiesto)
                    .accept(MediaType.APPLICATION_JSON)
                    // The bucket's CDN may cache it; ask for a fresh copy.
                    .header("Cache-Control", "no-cache")
                    .exchange((pedido, respuesta) -> {
                        if (respuesta.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return new Lectura.SinPublicar();
                        }
                        if (!respuesta.getStatusCode().is2xxSuccessful()) {
                            log.warn(
                                    "Manifiesto {}: respuesta {}",
                                    manifiesto,
                                    respuesta.getStatusCode().value());
                            return new Lectura.Fallida();
                        }
                        byte[] cuerpo = respuesta.getBody().readNBytes(TAMANO_MAXIMO + 1);
                        if (cuerpo.length > TAMANO_MAXIMO) {
                            log.warn("Manifiesto {}: más de {} bytes, se ignora", manifiesto, TAMANO_MAXIMO);
                            return new Lectura.Fallida();
                        }
                        return entrada(JSON.readTree(cuerpo));
                    });
        } catch (JacksonException e) {
            log.warn("Manifiesto {}: JSON no válido: {}", manifiesto, e.getOriginalMessage());
            return new Lectura.Fallida();
        } catch (RuntimeException e) {
            log.warn("Manifiesto {}: no se pudo leer: {}", manifiesto, e.getMessage());
            return new Lectura.Fallida();
        }
    }

    static Lectura entrada(JsonNode v) {
        if (!v.isObject()) {
            return new Lectura.Fallida();
        }
        List<String> notas = new ArrayList<>();
        v.path("notas").forEach(n -> {
            if (n.isString()) {
                notas.add(n.asString());
            }
        });
        return new Lectura.Publicada(new EntradaDeManifiesto(
                texto(v, "version"),
                texto(v, "url"),
                texto(v, "sha256"),
                v.path("tamanoBytes").canConvertToLong() ? v.path("tamanoBytes").asLong() : null,
                texto(v, "publicadaEn"),
                notas));
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isString() ? valor.asString() : null;
    }
}
