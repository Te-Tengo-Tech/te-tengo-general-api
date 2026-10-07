package tech.tetengo.api.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;

/**
 * The OpenAPI description documents every endpoint, and every endpoint of the two contracts
 * ({@code docs/API_CONTRACT.md}, {@code docs/AGENT_CONTRACT.md}) exists in the code.
 */
class DocumentacionOpenApiIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern ENDPOINT = Pattern.compile("`(GET|POST|PUT|PATCH|DELETE) (/api/[^ `?]+)");

    /** Contract endpoints that are knowingly missing, each recorded in docs/BLOCKERS.md. */
    private static final Set<String> PENDIENTES = Set.of("GET /api/agente/configuracion");

    private static final Set<String> PUBLICOS = Set.of(
            "POST /api/cuentas",
            "POST /api/sesiones",
            "POST /api/sesiones/refresco",
            "POST /api/recuperaciones",
            "POST /api/recuperaciones/confirmacion",
            "POST /api/invitaciones/{}/aceptacion",
            "POST /api/agente/camaras/registro");

    @Autowired
    MockMvc mvc;

    String documento;

    @BeforeEach
    void documento() throws Exception {
        documento = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Set<String> operacionesDocumentadas() {
        Map<String, Map<String, Object>> rutas = JsonPath.read(documento, "$.paths");
        Set<String> operaciones = new TreeSet<>();
        rutas.forEach((ruta, metodos) -> metodos.keySet()
                .forEach(metodo -> operaciones.add(metodo.toUpperCase(Locale.ROOT) + " " + normalizar(ruta))));
        return operaciones;
    }

    private static String normalizar(String ruta) {
        return ruta.replaceAll("\\{[^}]+}", "{}");
    }

    private static Set<String> endpointsDelContrato(String archivo) throws Exception {
        Set<String> endpoints = new TreeSet<>();
        Matcher m = ENDPOINT.matcher(Files.readString(Path.of(archivo)));
        while (m.find()) {
            endpoints.add(m.group(1) + " " + normalizar(m.group(2)));
        }
        return endpoints;
    }

    @Test
    void describeLaApiConSeguridadJwt() {
        assertThat((String) JsonPath.read(documento, "$.info.title")).isEqualTo("Te Tengo API");
        assertThat((String) JsonPath.read(documento, "$.components.securitySchemes.jwt.scheme"))
                .isEqualTo("bearer");
    }

    @Test
    void cadaEndpointTieneResumenYEtiqueta() {
        Map<String, Map<String, Map<String, Object>>> rutas = JsonPath.read(documento, "$.paths");
        List<String> sinDocumentar = new ArrayList<>();
        rutas.forEach((ruta, metodos) -> metodos.forEach((metodo, operacion) -> {
            Object resumen = operacion.get("summary");
            Object etiquetas = operacion.get("tags");
            if (resumen == null || resumen.toString().isBlank() || etiquetas == null) {
                sinDocumentar.add(metodo + " " + ruta);
            }
        }));
        assertThat(sinDocumentar).isEmpty();
    }

    @Test
    void cadaEndpointDelContratoConLaAppExiste() throws Exception {
        Set<String> contrato = endpointsDelContrato("docs/API_CONTRACT.md");
        assertThat(contrato).hasSizeGreaterThan(30);
        assertThat(operacionesDocumentadas()).containsAll(contrato);
    }

    @Test
    void cadaEndpointDelContratoConElAgenteExisteSalvoLosPendientes() throws Exception {
        Set<String> contrato = endpointsDelContrato("docs/AGENT_CONTRACT.md");
        contrato.removeAll(PENDIENTES);
        assertThat(contrato).isNotEmpty();
        assertThat(operacionesDocumentadas()).containsAll(contrato);
    }

    @Test
    void losEndpointsPublicosNoPidenToken() {
        Map<String, Map<String, Map<String, Object>>> rutas = JsonPath.read(documento, "$.paths");
        rutas.forEach((ruta, metodos) -> metodos.forEach((metodo, operacion) -> {
            String clave = metodo.toUpperCase(Locale.ROOT) + " " + normalizar(ruta);
            boolean sinSeguridad = operacion.containsKey("security") && ((List<?>) operacion.get("security")).isEmpty();
            assertThat(sinSeguridad).as(clave).isEqualTo(PUBLICOS.contains(clave));
        }));
    }
}
