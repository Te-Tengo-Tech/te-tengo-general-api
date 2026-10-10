package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** US-21: the "se levantó" follow-up notice. */
class AvisoDeRecuperacionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String agente;
    Instant cuando;

    @BeforeEach
    void agente() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(titular, "$.usuario.id")), "telefono-ana");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
        cuando = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private String evento(String tipo, Instant ocurridoEn) throws Exception {
        return campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), tipo, ocurridoEn)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
    }

    @Test
    void ca21_1_cuandoSeLevantaSeEnviaElAvisoDeSeguimiento() throws Exception {
        String alerta = evento("caida", cuando);
        evento("recuperacion", cuando.plusSeconds(15));

        var avisos = push.deTipo(TipoAviso.SE_LEVANTO);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.getFirst().tokens()).containsExactly("telefono-ana");
        assertThat(avisos.getFirst().aviso().alertaId()).hasToString(alerta);
        assertThat(avisos.getFirst().aviso().habitacion()).isEqualTo("Sala");
        assertThat(avisos.getFirst().aviso().ocurridaEn()).isEqualTo(cuando.plusSeconds(15));
        // «Adulto se levantó»: the first name of «Adulto de Ana», the household's older adult.
        assertThat(avisos.getFirst().aviso().detalle().adultoMayor()).isEqualTo("Adulto");
        mvc.perform(get("/api/alertas/" + alerta).header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(
                        jsonPath("$.recuperadaEn").value(cuando.plusSeconds(15).toString()));
    }

    @Test
    void ca21_2_siLaCaidaConfirmadaSeLevantaSeAvisaYLaAlertaSigueActivaComoConfirmada() throws Exception {
        // Product decision of 2026-10-10 (docs/BLOCKERS.md): the family must know the person got up,
        // also after a confirmed fall; the alert stays active until somebody attends it.
        String alerta = evento("caida", cuando);
        evento("caida_confirmada", cuando.plusSeconds(30));
        evento("recuperacion", cuando.plusSeconds(90));

        assertThat(push.deTipo(TipoAviso.SE_LEVANTO)).singleElement().satisfies(e -> {
            assertThat(e.aviso().alertaId()).hasToString(alerta);
            assertThat(e.aviso().ocurridaEn()).isEqualTo(cuando.plusSeconds(90));
        });
        mvc.perform(get("/api/alertas/" + alerta).header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.confirmada").value(true))
                .andExpect(jsonPath("$.recuperadaEn").isNotEmpty());
    }

    @Test
    void ca21_2_siLaCaidaSigueConfirmadaSinLevantarseNoHayAvisoDeSeguimiento() throws Exception {
        evento("caida", cuando);
        evento("caida_confirmada", cuando.plusSeconds(30));
        assertThat(push.deTipo(TipoAviso.SE_LEVANTO)).isEmpty();
    }

    @Test
    void sinCaidaPreviaNoHayAvisoDeRecuperacion() throws Exception {
        evento("recuperacion", cuando);
        assertThat(push.deTipo(TipoAviso.SE_LEVANTO)).isEmpty();
    }

    @Test
    void elAvisoSoloLlegaAlHogarDeLaCaida() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(otro, "$.usuario.id")), "telefono-beto");
        ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina");

        evento("caida", cuando);
        evento("recuperacion", cuando.plusSeconds(10));
        assertThat(push.deTipo(TipoAviso.SE_LEVANTO).getFirst().tokens()).containsExactly("telefono-ana");
    }
}
