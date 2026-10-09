package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-18: clip of the event. */
class ClipsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String agente;
    UUID eventoId;
    String alertaId;

    @BeforeEach
    void caida() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
        eventoId = UUID.randomUUID();
        alertaId = campo(
                enviarEvento(mvc, agente, eventoId, "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
    }

    private ResultActions pedirSubida(String tokenAgente, UUID evento) throws Exception {
        return mvc.perform(
                post("/api/agente/eventos/" + evento + "/clip").header("Authorization", bearer(tokenAgente)));
    }

    private ResultActions pedirSubida(String tokenAgente, UUID evento, String cuerpo) throws Exception {
        return mvc.perform(post("/api/agente/eventos/" + evento + "/clip")
                .header("Authorization", bearer(tokenAgente))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions verClip(String token, String alerta) throws Exception {
        return mvc.perform(get("/api/alertas/" + alerta + "/clip").header("Authorization", bearer(token)));
    }

    @Test
    void ca18_1_elAgenteSubeElClipYLaFamiliaLoVeConUnEnlaceTemporal() throws Exception {
        pedirSubida(agente, eventoId, "{\"contentType\":\"video/mp4\",\"tamanoBytes\":734003}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urlSubida").value(org.hamcrest.Matchers.containsString("metodo=PUT")))
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("video/mp4"))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofMinutes(10)).toString()));
        almacenamiento.completarSubidas();

        verClip(campo(titular, "$.tokenAcceso"), alertaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("metodo=GET")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString(alertaId)))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofMinutes(5)).toString()));
        assertThat(jdbc.queryForObject(
                        "select clip_subido from alertas where id = ?", Boolean.class, UUID.fromString(alertaId)))
                .isTrue();
    }

    @Test
    void sinCuerpoElClipEsMp4() throws Exception {
        pedirSubida(agente, eventoId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("video/mp4"));
    }

    @Test
    void unTipoDeContenidoQueNoEsVideoSeRechaza() throws Exception {
        pedirSubida(agente, eventoId, "{\"contentType\":\"text/html\",\"tamanoBytes\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.contentType").isNotEmpty());
    }

    @Test
    void ca08_4_elInvitadoTambienVeElClip() throws Exception {
        pedirSubida(agente, eventoId).andExpect(status().isCreated());
        almacenamiento.completarSubidas();
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);

        verClip(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), alertaId).andExpect(status().isOk());
    }

    @Test
    void ca18_2_siElVideoNoSeAlmacenoLaAlertaSeMuestraSinClip() throws Exception {
        verClip(campo(titular, "$.tokenAcceso"), alertaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIP_NO_DISPONIBLE"));

        // The agent got a URL but the upload failed.
        pedirSubida(agente, eventoId).andExpect(status().isCreated());
        verClip(campo(titular, "$.tokenAcceso"), alertaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIP_NO_DISPONIBLE"));
    }

    @Test
    void ca26_3_unClipEliminadoYaNoEstaDisponible() throws Exception {
        pedirSubida(agente, eventoId).andExpect(status().isCreated());
        jdbc.update(
                "update alertas set clip_eliminado_en = ? where id = ?",
                Timestamp.from(reloj.instant()),
                UUID.fromString(alertaId));
        verClip(campo(titular, "$.tokenAcceso"), alertaId)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIP_ELIMINADO"));
    }

    @Test
    void soloHayClipParaEventosConAlerta() throws Exception {
        pedirSubida(agente, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NO_ENCONTRADO"));
        UUID sinAlerta = UUID.randomUUID();
        enviarEvento(mvc, agente, sinAlerta, "deteccion_no_confiable", reloj.instant());
        pedirSubida(agente, sinAlerta).andExpect(status().isNotFound());
        verClip(campo(titular, "$.tokenAcceso"), UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));
    }

    @Test
    void unHogarNoVeNiSubeClipsDeOtro() throws Exception {
        pedirSubida(agente, eventoId).andExpect(status().isCreated());
        almacenamiento.completarSubidas();
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");

        verClip(campo(otro, "$.tokenAcceso"), alertaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));
        pedirSubida(agenteB, eventoId).andExpect(status().isNotFound());
        assertThat(almacenamiento.subidas()).allMatch(clave -> clave.contains(campo(titular, "$.hogarId")));
    }
}
