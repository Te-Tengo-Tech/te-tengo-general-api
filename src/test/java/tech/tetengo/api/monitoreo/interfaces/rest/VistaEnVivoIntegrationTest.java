package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
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

/** US-23: live view sessions. */
class VistaEnVivoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String token;
    String agente;
    String camara;

    @BeforeEach
    void camaraEnLinea() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)))
                .andExpect(status().isOk());
    }

    private ResultActions abrir(String token, String camara, String cuerpo) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private Map<String, Object> acceso(String sesionId) {
        return jdbc.queryForMap("select * from accesos_vista_en_vivo where id = ?", UUID.fromString(sesionId));
    }

    @Test
    void ca23_1_conLaCamaraEnLineaEntregaLaTransmision() throws Exception {
        String sesion = abrir(token, camara, "{\"alertaId\":null}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesionId").isNotEmpty())
                .andExpect(jsonPath("$.urlTransmision").value(containsString("/api/vista-en-vivo/")))
                .andExpect(jsonPath("$.urlTransmision").value(containsString("/transmision?token=")))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofMinutes(5)).toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> acceso = acceso(campo(sesion, "$.sesionId"));
        assertThat(acceso.get("usuario_id")).hasToString(campo(titular, "$.usuario.id"));
        assertThat(acceso.get("camara_id")).hasToString(camara);
        assertThat(acceso.get("alerta_id")).isNull();
        assertThat(campo(sesion, "$.urlTransmision")).contains(campo(sesion, "$.sesionId"));
    }

    @Test
    void ca23_2_desdeUnaAlertaQuedaRegistradoElOrigen() throws Exception {
        UUID alerta = UUID.randomUUID();
        String sesion = abrir(token, camara, "{\"alertaId\":\"%s\"}".formatted(alerta))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(acceso(campo(sesion, "$.sesionId")).get("alerta_id")).isEqualTo(alerta);
    }

    @Test
    void ca23_3_conLaCamaraDesconectadaNoEstaDisponible() throws Exception {
        jdbc.update("update camaras set estado_conexion = 'DESCONECTADA'");
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_DESCONECTADA"));
    }

    @Test
    void ca23_4_conLaCamaraEnPausaIndicaHastaCuando() throws Exception {
        String hasta = reloj.instant().plus(Duration.ofHours(1)).toString();
        jdbc.update(
                "update camaras set pausada_hasta = ?",
                Timestamp.from(reloj.instant().plus(Duration.ofHours(1))));
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_EN_PAUSA"))
                .andExpect(jsonPath("$.pausadaHasta").value(hasta));
    }

    @Test
    void sinConsentimientoNoHayVistaEnVivo() throws Exception {
        jdbc.update("update estados_de_captura set consentimiento_vigente = false");
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
    }

    @Test
    void ca24_1_alCerrarlaSeRegistraElFin() throws Exception {
        String sesion =
                campo(abrir(token, camara, "{}").andReturn().getResponse().getContentAsString(), "$.sesionId");
        reloj.avanzar(Duration.ofSeconds(95));
        mvc.perform(delete("/api/vista-en-vivo/" + sesion).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(((Timestamp) acceso(sesion).get("fin")).toInstant()).isEqualTo(reloj.instant());
    }

    @Test
    void ca08_4_elInvitadoTambienVeEnVivo() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        abrir(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), camara, "{}").andExpect(status().isCreated());
    }

    @Test
    void unHogarNoVeLaCamaraDeOtroNiCierraSusSesiones() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        abrir(campo(otro, "$.tokenAcceso"), camara, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));

        String sesion =
                campo(abrir(token, camara, "{}").andReturn().getResponse().getContentAsString(), "$.sesionId");
        mvc.perform(delete("/api/vista-en-vivo/" + sesion)
                        .header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(status().isNoContent());
        assertThat(acceso(sesion).get("fin")).isNull();
    }
}
