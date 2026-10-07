package tech.tetengo.api.alertas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** US-20: escalation to the secondary contact. */
class EscalamientoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EscalarAlertas escalarAlertas;

    String titular;
    String tokenAna;
    UUID ana;
    UUID beto = UUID.randomUUID();
    String agente;

    @BeforeEach
    void hogar() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        tokenAna = campo(titular, "$.tokenAcceso");
        ana = UUID.fromString(campo(titular, "$.usuario.id"));
        DatosDePrueba.membresia(jdbc, UUID.fromString(campo(titular, "$.hogarId")), beto, Rol.INVITADO);
        DatosDePrueba.dispositivo(jdbc, ana, "telefono-ana");
        DatosDePrueba.dispositivo(jdbc, beto, "telefono-beto");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
    }

    private void ordenDeAviso(UUID principal, UUID secundario, int espera) throws Exception {
        mvc.perform(put("/api/hogar/aviso")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"%s\",\"secundarioId\":%s,\"esperaMinutos\":%d}"
                                .formatted(principal, secundario == null ? "null" : "\"" + secundario + "\"", espera)))
                .andExpect(status().isOk());
    }

    private String alerta(String tokenAgente) throws Exception {
        String alerta = campo(
                enviarEvento(mvc, tokenAgente, UUID.randomUUID(), "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        push.limpiar();
        return alerta;
    }

    @Test
    void ca20_1_sinAtencionEnElTiempoDeEsperaSeAvisaAlContactoSecundario() throws Exception {
        ordenDeAviso(ana, beto, 3);
        String alerta = alerta(agente);

        reloj.avanzar(Duration.ofMinutes(3).minusSeconds(1));
        escalarAlertas.ejecutar();
        assertThat(push.enviados()).isEmpty();

        reloj.avanzar(Duration.ofSeconds(1));
        escalarAlertas.ejecutar();

        var escaladas = push.deTipo(TipoAviso.ALERTA_ESCALADA);
        assertThat(escaladas).hasSize(1);
        assertThat(escaladas.getFirst().tokens()).containsExactly("telefono-beto");
        assertThat(escaladas.getFirst().aviso().alertaId()).hasToString(alerta);
        assertThat(escaladas.getFirst().aviso().habitacion()).isEqualTo("Sala");
        mvc.perform(get("/api/alertas/" + alerta).header("Authorization", bearer(tokenAna)))
                .andExpect(jsonPath("$.escaladaEn").value(reloj.instant().toString()))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        // Idempotent.
        reloj.avanzar(Duration.ofMinutes(10));
        escalarAlertas.ejecutar();
        assertThat(push.enviados()).hasSize(1);
    }

    @Test
    void ca20_2_siSeAtiendeATiempoNoSeEscala() throws Exception {
        ordenDeAviso(ana, beto, 3);
        String alerta = alerta(agente);
        reloj.avanzar(Duration.ofMinutes(2));
        mvc.perform(post("/api/alertas/" + alerta + "/atencion").header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk());
        push.limpiar();

        reloj.avanzar(Duration.ofMinutes(5));
        escalarAlertas.ejecutar();
        assertThat(push.enviados()).isEmpty();
    }

    @Test
    void ca20_3_sinContactoSecundarioSeInformaAlPrincipal() throws Exception {
        ordenDeAviso(ana, null, 3);
        alerta(agente);
        reloj.avanzar(Duration.ofMinutes(3));
        escalarAlertas.ejecutar();

        var avisos = push.deTipo(TipoAviso.SIN_CONTACTO_SECUNDARIO);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.getFirst().tokens()).containsExactly("telefono-ana");
        assertThat(push.deTipo(TipoAviso.ALERTA_ESCALADA)).isEmpty();
    }

    @Test
    void ca10_3_sinConfigurarSeEsperanCincoMinutos() throws Exception {
        alerta(agente);
        reloj.avanzar(Duration.ofMinutes(4));
        escalarAlertas.ejecutar();
        assertThat(push.enviados()).isEmpty();
        reloj.avanzar(Duration.ofMinutes(1));
        escalarAlertas.ejecutar();
        assertThat(push.deTipo(TipoAviso.SIN_CONTACTO_SECUNDARIO).getFirst().tokens())
                .containsExactly("telefono-ana");
    }

    @Test
    void cadaHogarEscalaConSuPropiaConfiguracionYASusFamiliares() throws Exception {
        ordenDeAviso(ana, beto, 10);
        alerta(agente);
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(otro, "$.usuario.id")), "telefono-carla");
        alerta(campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token"));

        reloj.avanzar(Duration.ofMinutes(5));
        escalarAlertas.ejecutar();

        assertThat(push.enviados()).hasSize(1);
        var aviso = push.enviados().getFirst();
        assertThat(aviso.aviso().tipo()).isEqualTo(TipoAviso.SIN_CONTACTO_SECUNDARIO);
        assertThat(aviso.tokens()).containsExactly("telefono-carla");
        assertThat(aviso.aviso().habitacion()).isEqualTo("Cocina");
    }
}
