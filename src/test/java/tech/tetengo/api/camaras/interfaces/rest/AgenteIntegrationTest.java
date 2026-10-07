package tech.tetengo.api.camaras.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** Household agent: camera registration (CA-06.1) and capture state (CA-05.2). */
class AgenteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    String sesionA;
    UUID hogarA;
    String credencialA;

    @BeforeEach
    void hogar() throws Exception {
        sesionA = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        hogarA = UUID.fromString(campo(sesionA, "$.hogarId"));
        credencialA = DatosDePrueba.instalacion(jdbc, hogarA);
    }

    private String estadoDeCaptura(String tokenAgente) throws Exception {
        return mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(tokenAgente)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca06_1_laCamaraRegistradaApareceEnLaViviendaConElNombreDeLaInstalacion() throws Exception {
        String registro = ApiDePrueba.registrarAgente(mvc, credencialA, "Sala");
        String camaraId = campo(registro, "$.camaraId");
        assertThat(campo(registro, "$.expiraEn")).isNotBlank();

        Jwt token = decodificador.decode(campo(registro, "$.token"));
        assertThat(token.getClaimAsString("hogar_id")).isEqualTo(hogarA.toString());
        assertThat(token.getClaimAsString("camara_id")).isEqualTo(camaraId);
        assertThat(token.getClaimAsString("rol")).isEqualTo("AGENTE");

        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(camaraId))
                .andExpect(jsonPath("$[0].nombreHabitacion").value("Sala"));
    }

    @Test
    void registrarseDeNuevoDevuelveLaMismaCamaraYConservaElNombreQuePusoLaFamilia() throws Exception {
        String camaraId = campo(ApiDePrueba.registrarAgente(mvc, credencialA, "Sala"), "$.camaraId");
        mvc.perform(patch("/api/camaras/" + camaraId)
                        .header("Authorization", bearer(campo(sesionA, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Dormitorio de Rosa\"}"))
                .andExpect(status().isOk());

        assertThat(campo(ApiDePrueba.registrarAgente(mvc, credencialA, "Sala"), "$.camaraId"))
                .isEqualTo(camaraId);
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreHabitacion").value("Dormitorio de Rosa"));
    }

    @Test
    void unaCredencialDesconocidaSeRechaza() throws Exception {
        mvc.perform(post("/api/agente/camaras/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"inventada\",\"nombreHabitacion\":\"Sala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_INVALIDA"));
        assertThat(jdbc.queryForObject("select count(*) from camaras", Integer.class))
                .isZero();
    }

    @Test
    void ca05_2_sinConsentimientoLaCamaraNoCapturaYConConsentimientoSi() throws Exception {
        String token = campo(ApiDePrueba.registrarAgente(mvc, credencialA, "Sala"), "$.token");

        String sinConsentimiento = estadoDeCaptura(token);
        assertThat(campo(sinConsentimiento, "$.capturaPermitida")).isEqualTo("false");
        assertThat(campo(sinConsentimiento, "$.consentimientoVigente")).isEqualTo("false");
        assertThat(campo(sinConsentimiento, "$.pausadaHasta")).isNull();

        ApiDePrueba.otorgarConsentimiento(mvc, campo(sesionA, "$.tokenAcceso"));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> campo(estadoDeCaptura(token), "$.capturaPermitida").equals("true"));
        assertThat(campo(estadoDeCaptura(token), "$.consentimientoVigente")).isEqualTo("true");
    }

    @Test
    void elTokenDelAgenteSoloSirveParaLosEndpointsDelAgenteYViceversa() throws Exception {
        String token = campo(ApiDePrueba.registrarAgente(mvc, credencialA, "Sala"), "$.token");

        mvc.perform(get("/api/camaras").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/hogar").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/agente/estado-captura")).andExpect(status().isUnauthorized());
    }

    @Test
    void cadaAgenteRegistraSuCamaraYVeElEstadoSoloDeSuHogar() throws Exception {
        String sesionB = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        UUID hogarB = UUID.fromString(campo(sesionB, "$.hogarId"));
        String credencialB = DatosDePrueba.instalacion(jdbc, hogarB);

        String tokenA = campo(ApiDePrueba.registrarAgente(mvc, credencialA, "Sala"), "$.token");
        String tokenB = campo(ApiDePrueba.registrarAgente(mvc, credencialB, "Cocina"), "$.token");

        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionB, "$.tokenAcceso"))))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreHabitacion").value("Cocina"));

        ApiDePrueba.otorgarConsentimiento(mvc, campo(sesionA, "$.tokenAcceso"));
        await().atMost(Duration.ofSeconds(5))
                .until(() ->
                        campo(estadoDeCaptura(tokenA), "$.capturaPermitida").equals("true"));
        assertThat(campo(estadoDeCaptura(tokenB), "$.capturaPermitida")).isEqualTo("false");
    }
}
