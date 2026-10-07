package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** Events of the household agent become alerts (US-11 to US-17, US-13, US-15). */
class EventosDelAgenteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String agente;
    UUID camara;
    Instant cuando;

    @BeforeEach
    void agente() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = UUID.fromString(campo(registro, "$.camaraId"));
        cuando = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private String evento(String tipo, Instant ocurridoEn) throws Exception {
        return enviarEvento(mvc, agente, UUID.randomUUID(), tipo, ocurridoEn)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<String, Object> alerta(String id) {
        return jdbc.queryForMap("select * from alertas where id = ?", UUID.fromString(id));
    }

    private int alertas() {
        return jdbc.queryForObject("select count(*) from alertas", Integer.class);
    }

    @Test
    void ca11_1_unaCaidaRegistraUnaAlertaConLaHabitacionYLaHora() throws Exception {
        String respuesta = evento("caida", cuando);
        String alertaId = campo(respuesta, "$.alertaId");
        assertThat(alertaId).isNotBlank();

        Map<String, Object> alerta = alerta(alertaId);
        assertThat(alerta.get("tipo")).isEqualTo("CAIDA");
        assertThat(alerta.get("severidad")).isEqualTo("ALTA");
        assertThat(alerta.get("estado")).isEqualTo("ACTIVA");
        assertThat(alerta.get("habitacion")).isEqualTo("Sala");
        assertThat(alerta.get("camara_id")).isEqualTo(camara);
        assertThat(((Timestamp) alerta.get("ocurrida_en")).toInstant()).isEqualTo(cuando);
        assertThat(alerta.get("hogar_id")).hasToString(campo(titular, "$.hogarId"));
    }

    @Test
    void elMismoEventoEnviadoDosVecesSoloCuentaUnaVez() throws Exception {
        UUID eventoId = UUID.randomUUID();
        String primera = enviarEvento(mvc, agente, eventoId, "caida", cuando)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        enviarEvento(mvc, agente, eventoId, "caida", cuando)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventoId").value(eventoId.toString()))
                .andExpect(jsonPath("$.alertaId").value(campo(primera, "$.alertaId")));
        assertThat(alertas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from eventos_de_agente", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ca17_1_unMovimientoInestableEsUnaAlertaDeSeveridadMedia() throws Exception {
        Map<String, Object> alerta = alerta(campo(evento("movimiento_inestable", cuando), "$.alertaId"));
        assertThat(alerta.get("tipo")).isEqualTo("MOVIMIENTO_INESTABLE");
        assertThat(alerta.get("severidad")).isEqualTo("MEDIA");
        assertThat(alerta.get("habitacion")).isEqualTo("Sala");
    }

    @Test
    void ca17_3_unMovimientoInestableQueTerminaEnElSueloSeActualizaACaida() throws Exception {
        String inestable = campo(evento("movimiento_inestable", cuando), "$.alertaId");
        String caida = campo(evento("caida", cuando.plusSeconds(3)), "$.alertaId");

        assertThat(caida).isEqualTo(inestable);
        Map<String, Object> alerta = alerta(caida);
        assertThat(alerta.get("tipo")).isEqualTo("CAIDA");
        assertThat(alerta.get("severidad")).isEqualTo("ALTA");
        assertThat(alerta.get("origen_inestable")).isEqualTo(true);
        assertThat(alertas()).isEqualTo(1);
    }

    @Test
    void ca13_1_tras30SegundosEnElSueloLaCaidaQuedaConfirmadaYActiva() throws Exception {
        String caida = campo(evento("caida", cuando), "$.alertaId");
        assertThat(campo(evento("caida_confirmada", cuando.plusSeconds(30)), "$.alertaId"))
                .isEqualTo(caida);

        Map<String, Object> alerta = alerta(caida);
        assertThat(alerta.get("confirmada")).isEqualTo(true);
        assertThat(alerta.get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    void ca13_2_registraQueLaPersonaSeLevanto() throws Exception {
        String caida = campo(evento("caida", cuando), "$.alertaId");
        assertThat(campo(evento("recuperacion", cuando.plusSeconds(12)), "$.alertaId"))
                .isEqualTo(caida);
        assertThat(((Timestamp) alerta(caida).get("recuperada_en")).toInstant()).isEqualTo(cuando.plusSeconds(12));
    }

    @Test
    void ca15_3_laDeteccionNoConfiableSeMuestraEnLaCamaraHastaQueVuelveAVerALaPersona() throws Exception {
        assertThat(campo(evento("deteccion_no_confiable", cuando), "$.alertaId"))
                .isNull();
        assertThat(alertas()).isZero();
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].deteccionConfiable").value(false))
                .andExpect(jsonPath("$[0].noConfiableDesde").value(cuando.toString()));

        // A repeated report keeps the time detection stopped being reliable.
        evento("deteccion_no_confiable", cuando.plusSeconds(300));
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].noConfiableDesde").value(cuando.toString()));

        evento("movimiento_inestable", cuando.plusSeconds(400));
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].deteccionConfiable").value(true))
                .andExpect(jsonPath("$[0].noConfiableDesde").isEmpty());
    }

    @Test
    void ca06_2_lasAlertasSiguientesUsanElNuevoNombreDeLaHabitacion() throws Exception {
        String antes = campo(evento("caida", cuando), "$.alertaId");
        mvc.perform(patch("/api/camaras/" + camara)
                        .header("Authorization", bearer(campo(titular, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Dormitorio\"}"))
                .andExpect(status().isOk());
        String despues = campo(evento("caida", cuando.plusSeconds(60)), "$.alertaId");

        assertThat(alerta(antes).get("habitacion")).isEqualTo("Sala");
        assertThat(alerta(despues).get("habitacion")).isEqualTo("Dormitorio");
    }

    @Test
    void ca22_1_conLaCamaraEnPausaNoSeGeneranAlertas() throws Exception {
        jdbc.update(
                "update camaras set pausada_hasta = ?",
                Timestamp.from(reloj.instant().plus(Duration.ofHours(1))));
        assertThat(campo(evento("caida", cuando), "$.alertaId")).isNull();
        assertThat(alertas()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from eventos_de_agente", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ca05_2_sinConsentimientoNoSeGeneranAlertas() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String agenteSinConsentimiento = campo(
                ApiDePrueba.registrarAgente(
                        mvc, DatosDePrueba.instalacion(jdbc, UUID.fromString(campo(otro, "$.hogarId"))), "Cocina"),
                "$.token");
        enviarEvento(mvc, agenteSinConsentimiento, UUID.randomUUID(), "caida", cuando)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.alertaId").isEmpty());
        assertThat(alertas()).isZero();
    }

    @Test
    void unTipoDesconocidoSeRechaza() throws Exception {
        enviarEvento(mvc, agente, UUID.randomUUID(), "tropiezo", cuando)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.tipo").isNotEmpty());
    }

    @Test
    void soloElAgentePuedeEnviarEventos() throws Exception {
        enviarEvento(mvc, campo(titular, "$.tokenAcceso"), UUID.randomUUID(), "caida", cuando)
                .andExpect(status().isForbidden());
    }

    @Test
    void cadaAgenteSoloCreaAlertasEnSuHogarAunqueRepitaElEventoId() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");
        UUID mismoEvento = UUID.randomUUID();

        String deA = campo(
                enviarEvento(mvc, agente, mismoEvento, "caida", cuando)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        String deB = campo(
                enviarEvento(mvc, agenteB, mismoEvento, "caida", cuando)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");

        assertThat(deB).isNotEqualTo(deA);
        assertThat(alerta(deA).get("hogar_id")).hasToString(campo(titular, "$.hogarId"));
        assertThat(alerta(deB).get("hogar_id")).hasToString(campo(otro, "$.hogarId"));
        assertThat(alerta(deB).get("habitacion")).isEqualTo("Cocina");
    }
}
