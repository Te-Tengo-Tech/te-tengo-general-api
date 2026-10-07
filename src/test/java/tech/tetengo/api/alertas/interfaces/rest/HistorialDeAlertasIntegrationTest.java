package tech.tetengo.api.alertas.interfaces.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** US-25 and CA-16.4: alert history and detail. */
class HistorialDeAlertasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String token;
    String agente;
    UUID camara;
    Instant lunes = Instant.parse("2026-10-05T14:00:00Z");

    @BeforeEach
    void hogar() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = UUID.fromString(campo(registro, "$.camaraId"));
    }

    private String alerta(String tipo, Instant cuando) throws Exception {
        return campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), tipo, cuando)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
    }

    private ResultActions listar(String consulta) throws Exception {
        return mvc.perform(get("/api/alertas" + consulta).header("Authorization", bearer(token)));
    }

    @Test
    void ca25_3_sinAlertasLaListaEstaVacia() throws Exception {
        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void ca25_1_listaLasAlertasConFechaHoraHabitacionTipoYEstadoDeLaMasRecienteALaMasAntigua() throws Exception {
        String primera = alerta("caida", lunes);
        String segunda = alerta("movimiento_inestable", lunes.plus(Duration.ofHours(5)));

        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elementos", hasSize(2)))
                .andExpect(jsonPath("$.elementos[0].id").value(segunda))
                .andExpect(jsonPath("$.elementos[0].tipo").value("MOVIMIENTO_INESTABLE"))
                .andExpect(jsonPath("$.elementos[0].severidad").value("MEDIA"))
                .andExpect(jsonPath("$.elementos[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$.elementos[0].habitacion").value("Sala"))
                .andExpect(jsonPath("$.elementos[0].camaraId").value(camara.toString()))
                .andExpect(jsonPath("$.elementos[0].ocurridaEn").value("2026-10-05T19:00:00Z"))
                .andExpect(jsonPath("$.elementos[0].confirmada").value(false))
                .andExpect(jsonPath("$.elementos[0].origenInestable").value(false))
                .andExpect(jsonPath("$.elementos[0].clip").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.elementos[0].atendidaPor").isEmpty())
                .andExpect(jsonPath("$.elementos[1].id").value(primera))
                .andExpect(jsonPath("$.elementos[1].tipo").value("CAIDA"))
                .andExpect(jsonPath("$.elementos[1].severidad").value("ALTA"));
    }

    @Test
    void ca25_2_filtraPorTipoEstadoYFechas() throws Exception {
        alerta("caida", lunes);
        String atendida = alerta("caida", lunes.plus(Duration.ofDays(1)));
        jdbc.update(
                "update alertas set estado = 'ATENDIDA', atendida_por = ?, atendida_en = ? where id = ?",
                UUID.fromString(campo(titular, "$.usuario.id")),
                Timestamp.from(lunes.plus(Duration.ofDays(1))),
                UUID.fromString(atendida));
        String inestable = alerta("movimiento_inestable", lunes.plus(Duration.ofDays(2)));

        listar("?tipo=MOVIMIENTO_INESTABLE")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(inestable));
        listar("?estado=ATENDIDA")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(atendida))
                .andExpect(jsonPath("$.elementos[0].atendidaPor.nombre").value("Ana"));
        listar("?tipo=CAIDA&estado=ACTIVA").andExpect(jsonPath("$.total").value(1));
        listar("?desde=2026-10-06T00:00:00Z&hasta=2026-10-06T23:59:59Z")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(atendida));
    }

    @Test
    void paginaLosResultados() throws Exception {
        for (int i = 0; i < 5; i++) {
            alerta("caida", lunes.plus(Duration.ofMinutes(i)));
        }
        listar("?pagina=1&tamano=2")
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.elementos", hasSize(2)))
                .andExpect(jsonPath("$.elementos[0].ocurridaEn")
                        .value(lunes.plus(Duration.ofMinutes(2)).toString()));
        listar("?pagina=2&tamano=2").andExpect(jsonPath("$.elementos", hasSize(1)));
    }

    @Test
    void filtrosInvalidosSonErroresDeValidacion() throws Exception {
        listar("?tipo=TROPIEZO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.tipo").isNotEmpty());
        listar("?tamano=500")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tamano").isNotEmpty());
        listar("?desde=ayer")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.desde").isNotEmpty());
    }

    @Test
    void ca16_4_unaAlertaCuyoPushFalloSeVeAlAbrirLaAplicacion() throws Exception {
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(titular, "$.usuario.id")), "telefono-ana");
        push.simularCaida(true);
        String alertaId = alerta("caida", reloj.instant().truncatedTo(ChronoUnit.MILLIS));

        listar("?estado=ACTIVA").andExpect(jsonPath("$.elementos[0].id").value(alertaId));
        mvc.perform(get("/api/alertas/" + alertaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.notificadaEn").isEmpty());
    }

    @Test
    void elDetalleIncluyeElEstadoDelClip() throws Exception {
        UUID evento = UUID.randomUUID();
        String alertaId = campo(
                enviarEvento(mvc, agente, evento, "caida", lunes)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/agente/eventos/" + evento + "/clip")
                        .header("Authorization", bearer(agente)))
                .andExpect(status().isOk());
        almacenamiento.completarSubidas();

        mvc.perform(get("/api/alertas/" + alertaId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.clip").value("DISPONIBLE"));
    }

    @Test
    void cadaHogarSoloVeSusAlertas() throws Exception {
        String deA = alerta("caida", lunes);
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");
        enviarEvento(mvc, agenteB, UUID.randomUUID(), "caida", lunes);

        listar("")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(deA));
        mvc.perform(get("/api/alertas").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elementos[0].habitacion").value("Cocina"));
        mvc.perform(get("/api/alertas/" + deA).header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));
    }
}
