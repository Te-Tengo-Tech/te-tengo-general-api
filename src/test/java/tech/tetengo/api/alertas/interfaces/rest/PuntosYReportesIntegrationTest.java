package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.alertas.RetencionDeClips;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.MovimientosApiDePrueba;

/** US-36: the body landmarks of a clip; US-40: «Hubo una caída y no avisó». */
class PuntosYReportesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RetencionDeClips retencion;

    String titular;
    UUID hogar;
    String camara;
    String agente;
    UUID eventoId;
    String alertaId;

    @BeforeEach
    void caidaConClip() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, sesion, "Sala");
        camara = campo(registro, "$.camaraId");
        agente = campo(registro, "$.token");
        eventoId = UUID.randomUUID();
        alertaId = campo(
                ApiDePrueba.enviarEvento(mvc, agente, eventoId, "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        mvc.perform(post("/api/agente/eventos/" + eventoId + "/clip").header("Authorization", bearer(agente)))
                .andExpect(status().isCreated());
        almacenamiento.completarSubidas();
    }

    private ResultActions pedirPuntos() throws Exception {
        return mvc.perform(
                post("/api/agente/eventos/" + eventoId + "/clip/puntos").header("Authorization", bearer(agente)));
    }

    private ResultActions pedirPuntosLigeros(String token) throws Exception {
        return mvc.perform(
                post("/api/agente/eventos/" + eventoId + "/clip/puntos/ligero").header("Authorization", bearer(token)));
    }

    private String clip() throws Exception {
        return mvc.perform(get("/api/alertas/" + alertaId + "/clip").header("Authorization", bearer(titular)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca36_2_sinConsentimientoDeMovimientosNoHayPuntosYElClipSeVeIgual() throws Exception {
        pedirPuntos()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
        String clip = clip();
        assertThat(campo(clip, "$.url")).isNotBlank();
        assertThat(campo(clip, "$.puntos")).isNull();
        assertThat(campo(clip, "$.puntosNoDisponibles")).isEqualTo("SIN_CONSENTIMIENTO");
    }

    @Test
    void ca36_1_conConsentimientoElClipTraeSusPuntosDelCuerpo() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular).andExpect(status().isCreated());
        assertThat(campo(clip(), "$.puntosNoDisponibles")).isEqualTo("SIN_ARCHIVO");

        String subida = pedirPuntos()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("application/vnd.apache.parquet"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(subida, "$.urlSubida")).contains("clips/" + alertaId + ".landmarks.parquet");
        almacenamiento.completarSubidas();

        String clip = clip();
        assertThat(campo(clip, "$.puntos.url")).contains("clips/" + alertaId + ".landmarks.parquet");
        assertThat(campo(clip, "$.puntos.expiraEn")).isEqualTo(campo(clip, "$.expiraEn"));
        assertThat(campo(clip, "$.puntosNoDisponibles")).isNull();
    }

    @Test
    void ca36_3_losPuntosSeBorranConSuClipYAlRevocarElConsentimiento() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular);
        pedirPuntos();
        almacenamiento.completarSubidas();

        retencion.eliminarAnterioresA(reloj.instant().plus(Duration.ofMinutes(1)));
        assertThat(almacenamiento.eliminadas()).contains("clips/" + alertaId + ".landmarks.parquet");
    }

    @Test
    void ca35_4_revocarElConsentimientoDeMovimientosBorraLosPuntosDeLosClips() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular);
        pedirPuntos();
        almacenamiento.completarSubidas();

        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> almacenamiento.eliminadas().contains("clips/" + alertaId + ".landmarks.parquet"));
        assertThat(campo(clip(), "$.puntosNoDisponibles")).isEqualTo("SIN_CONSENTIMIENTO");
    }

    @Test
    void adr0011_elClipTraeLosPuntosEnElFormatoLigeroCuandoLaPcLosSubio() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular).andExpect(status().isCreated());
        pedirPuntos().andExpect(status().isCreated());
        almacenamiento.completarSubidas();
        // An older PC: only the Parquet landmarks.
        assertThat(campo(clip(), "$.puntos.ligero")).isNull();

        String subida = pedirPuntosLigeros(agente)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("application/gzip"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(subida, "$.urlSubida")).contains("clips/" + alertaId + ".landmarks.ttmov");
        // Not in the storage yet.
        assertThat(campo(clip(), "$.puntos.ligero")).isNull();
        almacenamiento.completarSubidas();

        String clip = clip();
        assertThat(campo(clip, "$.puntos.url")).contains("clips/" + alertaId + ".landmarks.parquet");
        assertThat(campo(clip, "$.puntos.ligero.url")).contains("clips/" + alertaId + ".landmarks.ttmov");
        assertThat(campo(clip, "$.puntos.expiraEn")).isEqualTo(campo(clip, "$.expiraEn"));
    }

    @Test
    void adr0011_sinLosPuntosEnParquetNoHayPuntosAunqueEsteElArchivoLigero() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular).andExpect(status().isCreated());
        pedirPuntosLigeros(agente).andExpect(status().isCreated());
        almacenamiento.completarSubidas();

        String clip = clip();
        assertThat(campo(clip, "$.puntos")).isNull();
        assertThat(campo(clip, "$.puntosNoDisponibles")).isEqualTo("SIN_ARCHIVO");
    }

    @Test
    void adr0011_losPuntosLigerosPidenElConsentimientoYSonDeLaCamaraDelEvento() throws Exception {
        pedirPuntosLigeros(agente)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
        MovimientosApiDePrueba.otorgar(mvc, titular).andExpect(status().isCreated());

        String otra = ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis");
        String otroAgente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otra, "Sala"), "$.token");
        MovimientosApiDePrueba.otorgar(mvc, campo(otra, "$.tokenAcceso")).andExpect(status().isCreated());
        pedirPuntosLigeros(otroAgente)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NO_ENCONTRADO"));
        assertThat(almacenamiento.subidas()).noneMatch(c -> c.endsWith(".ttmov"));
    }

    @Test
    void adr0011_losPuntosLigerosSeBorranConSuClipYAlRevocarElConsentimiento() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular);
        pedirPuntos();
        pedirPuntosLigeros(agente);
        almacenamiento.completarSubidas();

        retencion.eliminarAnterioresA(reloj.instant().plus(Duration.ofMinutes(1)));
        assertThat(almacenamiento.eliminadas())
                .contains("clips/" + alertaId + ".landmarks.parquet", "clips/" + alertaId + ".landmarks.ttmov");
    }

    @Test
    void adr0011_revocarElConsentimientoDeMovimientosBorraLosPuntosLigeros() throws Exception {
        MovimientosApiDePrueba.otorgar(mvc, titular);
        pedirPuntosLigeros(agente);
        almacenamiento.completarSubidas();

        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> almacenamiento.eliminadas().contains("clips/" + alertaId + ".landmarks.ttmov"));
        assertThat(almacenamiento.subidas()).noneMatch(c -> c.contains(".landmarks."));
    }

    @Test
    void ca40_4_laFamiliaInformaUnaCaidaSinAlertaYSeRegistraParaLaEvaluacion() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(post("/api/reportes-de-caida")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"camaraId\":\"%s\",\"ocurridaAproximadamenteEn\":\"%s\",\"nota\":\"Se resbaló al salir de la cama\"}"
                                        .formatted(camara, reloj.instant().minus(Duration.ofHours(1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.recibidoEn").exists())
                .andExpect(jsonPath("$.movimientosDisponibles").value(false));
        assertThat(jdbc.queryForObject(
                        "select habitacion from reportes_de_caida_sin_alerta where hogar_id = ?", String.class, hogar))
                .isEqualTo("Sala");
        // «No sé» the room; no alert and no notice.
        mvc.perform(post("/api/reportes-de-caida")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ocurridaAproximadamenteEn\":\"%s\"}"
                                .formatted(reloj.instant().minusSeconds(60))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/alertas").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void ca40_4_laHoraDebeHaberPasadoYLaCamaraSerDeLaVivienda() throws Exception {
        mvc.perform(post("/api/reportes-de-caida")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ocurridaAproximadamenteEn\":\"%s\"}"
                                .formatted(reloj.instant().plus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ocurridaAproximadamenteEn").value("Elige una hora que ya pasó."));
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");
        mvc.perform(post("/api/reportes-de-caida")
                        .header("Authorization", bearer(otro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"camaraId\":\"%s\",\"ocurridaAproximadamenteEn\":\"%s\"}"
                                .formatted(camara, reloj.instant().minusSeconds(60))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
        mvc.perform(post("/api/reportes-de-caida")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ocurridaAproximadamenteEn\":\"%s\",\"nota\":\"%s\"}"
                                .formatted(reloj.instant(), "x".repeat(301))))
                .andExpect(status().isBadRequest());
    }
}
