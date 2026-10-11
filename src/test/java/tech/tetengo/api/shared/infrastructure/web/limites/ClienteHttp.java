package tech.tetengo.api.shared.infrastructure.web.limites;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real HTTP requests to the API on its random port, through Tomcat (MockMvc skips Tomcat, where the
 * forwarded headers are read), as the reverse proxy sends them: with an {@code X-Forwarded-For}.
 */
final class ClienteHttp {

    static final String VINCULACION = "/api/agente/vinculacion";
    static final String TOKEN = "/api/agente/token";
    static final String CLIENTE = "te-tengo-captura";
    static final String CONCESION_DISPOSITIVO = "urn:ietf:params:oauth:grant-type:device_code";

    private final HttpClient http = HttpClient.newHttpClient();
    private final int puerto;

    ClienteHttp(int puerto) {
        this.puerto = puerto;
    }

    HttpResponse<String> vinculacion(String reenviadoPara) throws IOException, InterruptedException {
        return formulario(VINCULACION, reenviadoPara, Map.of("client_id", CLIENTE, "nombre_pc", "Sala-Rosa"));
    }

    HttpResponse<String> formulario(String ruta, String reenviadoPara, Map<String, String> campos)
            throws IOException, InterruptedException {
        String cuerpo = campos.entrySet().stream()
                .map(c -> c.getKey() + "=" + URLEncoder.encode(c.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return enviar(ruta, reenviadoPara, "application/x-www-form-urlencoded", cuerpo);
    }

    HttpResponse<String> json(String ruta, String reenviadoPara, String cuerpo)
            throws IOException, InterruptedException {
        return enviar(ruta, reenviadoPara, "application/json", cuerpo);
    }

    private HttpResponse<String> enviar(String ruta, String reenviadoPara, String tipo, String cuerpo)
            throws IOException, InterruptedException {
        // 127.0.0.1, not localhost: the connection must come from the IPv4 loopback.
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .header("Content-Type", tipo)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (reenviadoPara != null) {
            peticion.header("X-Forwarded-For", reenviadoPara);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
