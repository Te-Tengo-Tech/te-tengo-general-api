package tech.tetengo.api.hogares.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import tech.tetengo.api.alertas.application.EliminarGrabaciones;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-09: revoking the consent stops capture and deletes the recordings. */
class RevocacionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EliminarGrabaciones eliminarGrabaciones;

    String titular;
    String token;
    String agente;
    String alertaConClip;

    @BeforeEach
    void hogarConUnClip() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(titular, "$.usuario.id")), "telefono-ana");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
        alertaConClip = alertaConClip(agente);
        push.limpiar();
    }

    private String alertaConClip(String tokenAgente) throws Exception {
        UUID evento = UUID.randomUUID();
        String alerta = campo(
                enviarEvento(mvc, tokenAgente, evento, "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        mvc.perform(post("/api/agente/eventos/" + evento + "/clip").header("Authorization", bearer(tokenAgente)))
                .andExpect(status().isCreated());
        almacenamiento.completarSubidas();
        return alerta;
    }

    private String capturaPermitida(String tokenAgente) throws Exception {
        return campo(
                mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(tokenAgente)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.capturaPermitida");
    }

    private void revocar(String token) throws Exception {
        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eliminacionProgramada").value(true));
    }

    @Test
    void ca09_1_laRevocacionDiceCuantasGrabacionesElimina() throws Exception {
        alertaConClip(agente);
        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eliminacionProgramada").value(true))
                .andExpect(jsonPath("$.clips").value(2));
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));
        eliminarGrabaciones.ejecutar();

        assertThat(almacenamiento.eliminadas()).hasSize(2);
        assertThat(campo(hogar(token), "$.eliminacion.clips")).isEqualTo("2");
    }

    private void esperarEliminacionProgramada(String hogar) {
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from eliminaciones_de_grabaciones where hogar_id = ?",
                                Integer.class,
                                UUID.fromString(hogar))
                        == 1);
    }

    @Test
    void ca09_1_laRevocacionDetieneLaCapturaYProgramaLaEliminacion() throws Exception {
        revocar(token);

        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(false));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> capturaPermitida(agente).equals("false"));
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));

        // Events that still arrive create no alert.
        enviarEvento(mvc, agente, UUID.randomUUID(), "caida", reloj.instant())
                .andExpect(jsonPath("$.alertaId").isEmpty());
    }

    @Test
    void ca09_2_sinConfirmarNadaCambia() throws Exception {
        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.vigente").value(true));
        assertThat(capturaPermitida(agente)).isEqualTo("true");
        assertThat(jdbc.queryForObject("select count(*) from eliminaciones_de_grabaciones", Integer.class))
                .isZero();
    }

    @Test
    void ca09_3_alTerminarLaEliminacionSeAvisaALaFamilia() throws Exception {
        revocar(token);
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));

        eliminarGrabaciones.ejecutar();

        assertThat(almacenamiento.subidas()).isEmpty();
        assertThat(almacenamiento.eliminadas()).hasSize(1);
        mvc.perform(get("/api/alertas/" + alertaConClip + "/clip").header("Authorization", bearer(token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIP_ELIMINADO"));
        mvc.perform(get("/api/alertas/" + alertaConClip).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.clip").value("ELIMINADO"));
        assertThat(push.deTipo(TipoAviso.DATOS_ELIMINADOS)).hasSize(1);
        assertThat(push.deTipo(TipoAviso.DATOS_ELIMINADOS).getFirst().tokens()).containsExactly("telefono-ana");

        // Idempotent.
        eliminarGrabaciones.ejecutar();
        assertThat(push.deTipo(TipoAviso.DATOS_ELIMINADOS)).hasSize(1);
    }

    private String hogar(String token) throws Exception {
        return mvc.perform(get("/api/hogar").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void sinRevocacionNoHayEliminacion() throws Exception {
        mvc.perform(get("/api/hogar").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasKey("eliminacion")))
                .andExpect(jsonPath("$.eliminacion").value(nullValue()));
    }

    /** The app polls the deletion, because the push DATOS_ELIMINADOS may never reach its screen. */
    @Test
    void ca09_3_elHogarDiceSiLaEliminacionTermino() throws Exception {
        Instant revocadoEn = reloj.instant();
        revocar(token);

        // Right away, even before alertas records the deletion on its own thread.
        String programada = hogar(token);
        assertThat(campo(programada, "$.eliminacion.estado")).isEqualTo("PROGRAMADA");
        assertThat(campo(programada, "$.eliminacion.clips")).isEqualTo("1");
        assertThat(Instant.parse(campo(programada, "$.eliminacion.programadaEn")))
                .isEqualTo(revocadoEn);
        mvc.perform(get("/api/hogar").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.eliminacion.terminadaEn").value(nullValue()));
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));
        assertThat(campo(hogar(token), "$.eliminacion.estado")).isEqualTo("PROGRAMADA");

        reloj.avanzar(Duration.ofMinutes(1));
        Instant terminadaEn = reloj.instant();
        eliminarGrabaciones.ejecutar();

        // Any member reads it: the invited member also gets DATOS_ELIMINADOS.
        UUID invitado = UUID.randomUUID();
        UUID hogarId = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogarId, invitado, Rol.INVITADO);
        for (String quien : new String[] {token, JwtDePrueba.token(invitado, hogarId, Rol.INVITADO)}) {
            String terminada = hogar(quien);
            assertThat(campo(terminada, "$.eliminacion.estado")).isEqualTo("TERMINADA");
            assertThat(campo(terminada, "$.eliminacion.clips")).isEqualTo("1");
            assertThat(Instant.parse(campo(terminada, "$.eliminacion.programadaEn")))
                    .isEqualTo(revocadoEn);
            assertThat(Instant.parse(campo(terminada, "$.eliminacion.terminadaEn")))
                    .isEqualTo(terminadaEn);
        }
    }

    @Test
    void unaNuevaRevocacionNoMuestraLaEliminacionAnteriorComoTerminada() throws Exception {
        revocar(token);
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));
        eliminarGrabaciones.ejecutar();
        assertThat(campo(hogar(token), "$.eliminacion.estado")).isEqualTo("TERMINADA");

        reloj.avanzar(Duration.ofHours(1));
        ApiDePrueba.otorgarConsentimiento(mvc, token);
        // With a current consent, the earlier revocation's deletion stays visible as done.
        assertThat(campo(hogar(token), "$.eliminacion.estado")).isEqualTo("TERMINADA");

        reloj.avanzar(Duration.ofHours(1));
        Instant otraRevocacion = reloj.instant();
        revocar(token);
        String programada = hogar(token);
        assertThat(campo(programada, "$.eliminacion.estado")).isEqualTo("PROGRAMADA");
        assertThat(campo(programada, "$.eliminacion.clips")).isEqualTo("0");
        assertThat(Instant.parse(campo(programada, "$.eliminacion.programadaEn")))
                .isEqualTo(otraRevocacion);

        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from eliminaciones_de_grabaciones where completada_en is null",
                                Integer.class)
                        == 1);
        eliminarGrabaciones.ejecutar();
        String terminada = hogar(token);
        assertThat(campo(terminada, "$.eliminacion.estado")).isEqualTo("TERMINADA");
        assertThat(campo(terminada, "$.eliminacion.clips")).isEqualTo("0");
        assertThat(Instant.parse(campo(terminada, "$.eliminacion.programadaEn")))
                .isEqualTo(otraRevocacion);
    }

    @Test
    void soloElTitularRevocaYSoloSiHayConsentimiento() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(delete("/api/hogar/consentimiento")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));

        revocar(token);
        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
    }

    @Test
    void conUnConsentimientoNuevoLaCapturaSeReanuda() throws Exception {
        revocar(token);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> capturaPermitida(agente).equals("false"));
        ApiDePrueba.otorgarConsentimiento(mvc, token);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> capturaPermitida(agente).equals("true"));
    }

    @Test
    void laRevocacionDeUnHogarNoTocaAOtro() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(otro, "$.usuario.id")), "telefono-beto");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");
        String alertaDeB = alertaConClip(agenteB);
        push.limpiar();

        revocar(token);
        esperarEliminacionProgramada(campo(titular, "$.hogarId"));
        eliminarGrabaciones.ejecutar();

        assertThat(capturaPermitida(agenteB)).isEqualTo("true");
        mvc.perform(get("/api/alertas/" + alertaDeB + "/clip")
                        .header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(status().isOk());
        assertThat(push.deTipo(TipoAviso.DATOS_ELIMINADOS).getFirst().tokens()).containsExactly("telefono-ana");
        assertThat(campo(hogar(token), "$.eliminacion.estado")).isEqualTo("TERMINADA");
        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.eliminacion").value(nullValue()));
    }
}
