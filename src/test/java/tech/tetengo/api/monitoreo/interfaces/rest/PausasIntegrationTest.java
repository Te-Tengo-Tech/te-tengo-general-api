package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.monitoreo.application.FinalizarPausasVencidas;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-22: temporary camera pauses with automatic resume. */
class PausasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FinalizarPausasVencidas finalizarPausas;

    String titular;
    String token;
    String agente;
    String camara;

    @BeforeEach
    void camara() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(titular, "$.usuario.id")), "telefono-ana");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = campo(registro, "$.camaraId");
    }

    private ResultActions pausar(String token, String camara, String duracion) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/pausa")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracion\":\"%s\"}".formatted(duracion)));
    }

    private String estadoDeCaptura() throws Exception {
        return mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(agente)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca22_1_alPausarSeDetieneLaCapturaYLaDeteccion() throws Exception {
        String hasta = reloj.instant().plus(Duration.ofMinutes(30)).toString();
        pausar(token, camara, "MIN_30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(camara))
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala"))
                .andExpect(jsonPath("$.pausadaHasta").value(hasta));

        String estado = estadoDeCaptura();
        assertThat(campo(estado, "$.capturaPermitida")).isEqualTo("false");
        assertThat(campo(estado, "$.motivo")).isEqualTo("EN_PAUSA");
        assertThat(campo(estado, "$.pausadaHasta")).isEqualTo(hasta);
        enviarEvento(mvc, agente, UUID.randomUUID(), "caida", reloj.instant())
                .andExpect(jsonPath("$.alertaId").isEmpty());
    }

    @Test
    void ca22_2_laAplicacionMuestraHastaCuandoEstaEnPausa() throws Exception {
        pausar(token, camara, "HORAS_2").andExpect(status().isOk());
        mvc.perform(get("/api/camaras").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].pausadaHasta")
                        .value(reloj.instant().plus(Duration.ofHours(2)).toString()));
    }

    @Test
    void ca22_3_alCumplirseLaDuracionSeRetomaLaCapturaYSeAvisa() throws Exception {
        pausar(token, camara, "HORA_1").andExpect(status().isOk());

        reloj.avanzar(Duration.ofMinutes(59));
        finalizarPausas.ejecutar();
        assertThat(campo(estadoDeCaptura(), "$.capturaPermitida")).isEqualTo("false");

        reloj.avanzar(Duration.ofMinutes(1));
        finalizarPausas.ejecutar();

        String estado = estadoDeCaptura();
        assertThat(campo(estado, "$.capturaPermitida")).isEqualTo("true");
        assertThat(campo(estado, "$.motivo")).isNull();
        assertThat(campo(estado, "$.pausadaHasta")).isNull();
        mvc.perform(get("/api/camaras").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].pausadaHasta").isEmpty());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !push.deTipo(TipoAviso.PAUSA_FINALIZADA).isEmpty());
        var aviso = push.deTipo(TipoAviso.PAUSA_FINALIZADA).getFirst();
        assertThat(aviso.tokens()).containsExactly("telefono-ana");
        assertThat(aviso.aviso().camaraId()).hasToString(camara);
        assertThat(aviso.aviso().habitacion()).isEqualTo("Sala");

        finalizarPausas.ejecutar();
        Thread.sleep(300);
        assertThat(push.deTipo(TipoAviso.PAUSA_FINALIZADA)).hasSize(1);
    }

    @Test
    void reanudarAMano() throws Exception {
        pausar(token, camara, "HASTA_MANANA").andExpect(status().isOk());
        mvc.perform(delete("/api/camaras/" + camara + "/pausa").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pausadaHasta").isEmpty());
        assertThat(campo(estadoDeCaptura(), "$.capturaPermitida")).isEqualTo("true");
        Thread.sleep(300);
        assertThat(push.deTipo(TipoAviso.PAUSA_FINALIZADA)).isEmpty();
    }

    @Test
    void unaDuracionDesconocidaSeRechaza() throws Exception {
        pausar(token, camara, "HORAS_3")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
        mvc.perform(post("/api/camaras/" + camara + "/pausa").header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DURACION_INVALIDA"));
    }

    @Test
    void elInvitadoTambienPuedePausar() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        pausar(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), camara, "MIN_30")
                .andExpect(status().isOk());
    }

    @Test
    void unHogarNoPausaLasCamarasDeOtroYSusPausasSonIndependientes() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(otro, "$.usuario.id")), "telefono-beto");
        String camaraB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.camaraId");

        pausar(campo(otro, "$.tokenAcceso"), camara, "MIN_30")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));

        pausar(token, camara, "MIN_30").andExpect(status().isOk());
        pausar(campo(otro, "$.tokenAcceso"), camaraB, "HORA_1").andExpect(status().isOk());
        reloj.avanzar(Duration.ofMinutes(30));
        finalizarPausas.ejecutar();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> !push.deTipo(TipoAviso.PAUSA_FINALIZADA).isEmpty());
        Thread.sleep(300);
        assertThat(push.deTipo(TipoAviso.PAUSA_FINALIZADA)).hasSize(1);
        assertThat(push.deTipo(TipoAviso.PAUSA_FINALIZADA).getFirst().tokens()).containsExactly("telefono-ana");
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].pausadaHasta").isNotEmpty());
    }
}
