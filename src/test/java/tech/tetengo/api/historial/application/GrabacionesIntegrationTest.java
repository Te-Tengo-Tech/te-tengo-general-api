package tech.tetengo.api.historial.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.AlmacenamientoDePrueba.Lectura;
import tech.tetengo.api.support.ApiDePrueba;

/** US-26: reviewing, downloading and retaining recordings. */
class GrabacionesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AplicarRetencionDeGrabaciones retencion;

    String titular;
    String token;
    String agente;

    @BeforeEach
    void hogar() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
    }

    private String alertaConClip(String tokenAgente, Instant cuando) throws Exception {
        UUID evento = UUID.randomUUID();
        String alerta = campo(
                enviarEvento(mvc, tokenAgente, evento, "caida", cuando)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        // An alert that happened long ago may already have been attended; a new fall should not join it.
        jdbc.update("update alertas set estado = 'ATENDIDA' where id = ?", UUID.fromString(alerta));
        mvc.perform(post("/api/agente/eventos/" + evento + "/clip").header("Authorization", bearer(tokenAgente)))
                .andExpect(status().isOk());
        almacenamiento.completarSubidas();
        return alerta;
    }

    private ResultActions clip(String token, String alerta, String consulta) throws Exception {
        return mvc.perform(get("/api/alertas/" + alerta + "/clip" + consulta).header("Authorization", bearer(token)));
    }

    @Test
    void ca26_1_laGrabacionDeUnaAlertaPasadaSeReproduce() throws Exception {
        String alerta = alertaConClip(agente, Instant.parse("2026-09-01T10:00:00Z"));
        clip(token, alerta, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isNotEmpty());
        assertThat(almacenamiento.lecturas()).extracting(Lectura::descarga).containsExactly(false);
    }

    @Test
    void ca26_2_alPedirLaDescargaSeGeneraUnArchivoDescargable() throws Exception {
        String alerta = alertaConClip(agente, Instant.parse("2026-09-01T10:00:00Z"));
        clip(token, alerta, "?descarga=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(containsString("descarga=")))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty());
        Lectura lectura = almacenamiento.lecturas().getFirst();
        assertThat(lectura.descarga()).isTrue();
        assertThat(lectura.nombreArchivo()).startsWith("te-tengo-caida-2026-09-01T10-00");
    }

    @Test
    void ca26_3_unaGrabacionEliminadaPorLaRetencionYaNoEstaDisponible() throws Exception {
        String antigua = alertaConClip(agente, reloj.instant().minus(Duration.ofDays(40)));
        String reciente = alertaConClip(agente, reloj.instant().minus(Duration.ofDays(2)));

        assertThat(retencion.aplicar(Duration.ofDays(30))).isEqualTo(1);

        clip(token, antigua, "")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIP_ELIMINADO"));
        clip(token, reciente, "").andExpect(status().isOk());
        assertThat(retencion.aplicar(Duration.ofDays(30))).isZero();
    }

    @Test
    void sinPeriodoDeRetencionConfiguradoNoSeEliminaNada() throws Exception {
        String antigua = alertaConClip(agente, reloj.instant().minus(Duration.ofDays(400)));
        retencion.ejecutar();
        clip(token, antigua, "").andExpect(status().isOk());
    }

    @Test
    void laRetencionTrabajaHogarPorHogarYNadieDescargaGrabacionesAjenas() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");
        String deA = alertaConClip(agente, reloj.instant().minus(Duration.ofDays(40)));
        String deB = alertaConClip(agenteB, reloj.instant().minus(Duration.ofDays(40)));
        String recienteDeB = alertaConClip(agenteB, reloj.instant().minus(Duration.ofDays(1)));

        clip(campo(otro, "$.tokenAcceso"), deA, "?descarga=true")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));

        assertThat(retencion.aplicar(Duration.ofDays(30))).isEqualTo(2);
        clip(token, deA, "").andExpect(status().isGone());
        clip(campo(otro, "$.tokenAcceso"), deB, "").andExpect(status().isGone());
        clip(campo(otro, "$.tokenAcceso"), recienteDeB, "").andExpect(status().isOk());
    }
}
