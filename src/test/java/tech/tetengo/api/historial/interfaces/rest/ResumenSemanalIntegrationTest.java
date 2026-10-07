package tech.tetengo.api.historial.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** US-27: weekly summary. */
class ResumenSemanalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String token;
    String agente;

    @BeforeEach
    void hogar() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
    }

    /** Each alert is closed right away, so a later fall never updates an earlier unstable alert. */
    private void alerta(String tipo, String cuando, String estado) throws Exception {
        String alerta = campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), tipo, Instant.parse(cuando))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        jdbc.update("update alertas set estado = ? where id = ?", estado, UUID.fromString(alerta));
    }

    private ResultActions resumen(String consulta) throws Exception {
        return mvc.perform(get("/api/resumen-semanal" + consulta).header("Authorization", bearer(token)));
    }

    @Test
    void ca27_1_cuentaLasAlertasDeLaSemanaPorTipoSinContarFalsasAlarmasComoCaidas() throws Exception {
        // Week 2026-W41 runs from Monday 05-Oct 00:00 to Monday 12-Oct 00:00 in Lima (05:00Z).
        alerta("caida", "2026-10-05T05:00:00Z", "ATENDIDA");
        alerta("caida", "2026-10-08T20:00:00Z", "ACTIVA");
        alerta("caida", "2026-10-09T20:00:00Z", "FALSA_ALARMA");
        alerta("movimiento_inestable", "2026-10-10T15:00:00Z", "ATENDIDA");
        alerta("caida", "2026-10-12T04:59:00Z", "ATENDIDA");
        alerta("caida", "2026-10-12T05:00:00Z", "ATENDIDA");

        resumen("?semana=2026-W41")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semana").value("2026-W41"))
                .andExpect(jsonPath("$.conteos.caidas").value(3))
                .andExpect(jsonPath("$.conteos.movimientosInestables").value(1))
                .andExpect(jsonPath("$.conteos.falsasAlarmas").value(1));
    }

    @Test
    void ca27_2_unaSemanaSinEventosEsUnResumenEnCero() throws Exception {
        resumen("?semana=2026-W30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteos.caidas").value(0))
                .andExpect(jsonPath("$.conteos.movimientosInestables").value(0))
                .andExpect(jsonPath("$.conteos.falsasAlarmas").value(0))
                .andExpect(jsonPath("$.semanaAnterior.caidas").value(0))
                .andExpect(jsonPath("$.tendencia.caidas").value("IGUAL"));
    }

    @Test
    void ca27_3_indicaSiCadaTipoAumentoDisminuyoOSeMantuvo() throws Exception {
        alerta("caida", "2026-09-29T15:00:00Z", "ATENDIDA");
        alerta("movimiento_inestable", "2026-09-30T15:00:00Z", "ATENDIDA");
        alerta("movimiento_inestable", "2026-10-01T15:00:00Z", "ATENDIDA");
        alerta("caida", "2026-10-06T15:00:00Z", "ATENDIDA");
        alerta("caida", "2026-10-07T15:00:00Z", "ATENDIDA");
        alerta("movimiento_inestable", "2026-10-08T15:00:00Z", "FALSA_ALARMA");

        resumen("?semana=2026-W41")
                .andExpect(jsonPath("$.conteos.caidas").value(2))
                .andExpect(jsonPath("$.conteos.movimientosInestables").value(0))
                .andExpect(jsonPath("$.conteos.falsasAlarmas").value(1))
                .andExpect(jsonPath("$.semanaAnterior.caidas").value(1))
                .andExpect(jsonPath("$.semanaAnterior.movimientosInestables").value(2))
                .andExpect(jsonPath("$.semanaAnterior.falsasAlarmas").value(0))
                .andExpect(jsonPath("$.tendencia.caidas").value("AUMENTO"))
                .andExpect(jsonPath("$.tendencia.movimientosInestables").value("DISMINUCION"))
                .andExpect(jsonPath("$.tendencia.falsasAlarmas").value("AUMENTO"));
    }

    @Test
    void sinSemanaUsaLaSemanaActual() throws Exception {
        reloj.fijar(Instant.parse("2026-10-07T15:00:00Z"));
        alerta("caida", "2026-10-06T15:00:00Z", "ATENDIDA");
        resumen("")
                .andExpect(jsonPath("$.semana").value("2026-W41"))
                .andExpect(jsonPath("$.conteos.caidas").value(1));
    }

    @Test
    void unaSemanaMalEscritaEsUnErrorDeValidacion() throws Exception {
        resumen("?semana=2026-41")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.semana").isNotEmpty());
    }

    @Test
    void cadaHogarCuentaSoloSusAlertas() throws Exception {
        alerta("caida", "2026-10-06T15:00:00Z", "ATENDIDA");
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        mvc.perform(get("/api/resumen-semanal?semana=2026-W41")
                        .header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.conteos.caidas").value(0));
    }
}
