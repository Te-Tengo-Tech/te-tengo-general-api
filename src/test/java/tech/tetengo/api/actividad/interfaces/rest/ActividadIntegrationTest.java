package tech.tetengo.api.actividad.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.MovimientosApiDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/** US-33: who changed what and when, from the app or a PC, append-only. */
class ActividadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    UUID hogar;
    String tokenPc;
    String pcId;

    @BeforeEach
    void pcVinculada() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        ApiDePrueba.otorgarConsentimiento(mvc, titular);
        String tokens = VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa");
        tokenPc = campo(tokens, "$.access_token");
        pcId = campo(tokens, "$.pc_id");
    }

    private String actividad(String token, String consulta) throws Exception {
        esperarEventosPendientes();
        return mvc.perform(get("/api/actividad" + consulta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private List<String> tipos(String token) throws Exception {
        return JsonPath.read(actividad(token, ""), "$.elementos[*].tipo");
    }

    @Test
    void ca33_1_quedaQuienCambioQueAntesDespuesYCuandoDesdeLaAppYDesdeLaPc() throws Exception {
        String camara = campo(
                VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Logitech C920", "Dormitorio")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.camaraId");
        mvc.perform(post("/api/camaras/" + camara + "/pausa")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion\":\"HORA_1\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/camaras/" + camara + "/pausa").header("Authorization", bearer(titular)));
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 7);
        MovimientosApiDePrueba.retencion(mvc, titular, 30);

        await().atMost(Duration.ofSeconds(5)).until(() -> tipos(titular).size() >= 8);
        String todo = actividad(titular, "");
        assertThat((List<String>) JsonPath.read(todo, "$.elementos[*].tipo"))
                .contains(
                        "PC_VINCULADA",
                        "CAMARA_ASIGNADA",
                        "CAMARA_PAUSADA",
                        "CAMARA_REANUDADA",
                        "CONSENTIMIENTO_OTORGADO",
                        "CONSENTIMIENTO_MOVIMIENTOS_OTORGADO",
                        "RETENCION_MOVIMIENTOS_CAMBIADA");

        // «Carmen vinculó la PC Sala-Rosa».
        String vinculada = actividad(titular, "?tipo=PC_VINCULADA");
        assertThat(campo(vinculada, "$.elementos[0].actor.tipo")).isEqualTo("FAMILIAR");
        assertThat(campo(vinculada, "$.elementos[0].actor.usuario.nombre")).isEqualTo("Carmen");
        assertThat(campo(vinculada, "$.elementos[0].pc.nombre")).isEqualTo("Sala-Rosa");
        // «Desde la PC Sala-Rosa: la cámara Logitech C920 pasó a Dormitorio».
        String asignada = actividad(titular, "?tipo=CAMARA_ASIGNADA");
        assertThat(campo(asignada, "$.elementos[0].actor.tipo")).isEqualTo("PC");
        assertThat(campo(asignada, "$.elementos[0].actor.usuario")).isNull();
        assertThat(campo(asignada, "$.elementos[0].pc.id")).isEqualTo(pcId);
        assertThat(campo(asignada, "$.elementos[0].objeto")).isEqualTo("Logitech C920");
        assertThat(campo(asignada, "$.elementos[0].valorNuevo")).isEqualTo("Dormitorio");
        // «Carmen cambió los movimientos en la nube de 7 a 30 días».
        String retencion = actividad(titular, "?tipo=RETENCION_MOVIMIENTOS_CAMBIADA");
        assertThat(campo(retencion, "$.elementos[0].valorAnterior")).isEqualTo("7 días");
        assertThat(campo(retencion, "$.elementos[0].valorNuevo")).isEqualTo("30 días");
        String pausa = actividad(titular, "?tipo=CAMARA_PAUSADA");
        assertThat(campo(pausa, "$.elementos[0].objeto")).isEqualTo("Dormitorio");
        assertThat(campo(pausa, "$.elementos[0].pc.nombre")).isEqualTo("Sala-Rosa");
        assertThat(campo(pausa, "$.elementos[0].valorNuevo")).isNotBlank();
    }

    @Test
    void ca33_2_elInvitadoVeLaActividadDeLaMasRecienteALaMasAntiguaYFiltradaPorPc() throws Exception {
        String otraPc = campo(VinculacionDePrueba.vincular(mvc, titular, "Cocina-Rosa"), "$.pc_id");
        reloj.avanzar(Duration.ofMinutes(1));
        mvc.perform(patch("/api/pcs/" + otraPc)
                .header("Authorization", bearer(titular))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Cocina\"}"));
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String token = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);

        await().atMost(Duration.ofSeconds(5)).until(() -> tipos(token).size() >= 4);
        String todo = actividad(token, "");
        assertThat(campo(todo, "$.elementos[0].tipo")).isEqualTo("PC_RENOMBRADA");
        assertThat(campo(todo, "$.total")).isEqualTo("4");
        String deLaCocina = actividad(token, "?pcId=" + otraPc);
        assertThat((List<String>) JsonPath.read(deLaCocina, "$.elementos[*].tipo"))
                .containsExactly("PC_RENOMBRADA", "PC_VINCULADA");
        assertThat(campo(deLaCocina, "$.elementos[0].valorAnterior")).isEqualTo("Cocina-Rosa");
    }

    @Test
    void ca33_3_laPcEnviaLosCambiosQueHizoSinInternetYReenviarlosNoLosDuplica() throws Exception {
        UUID cambio = UUID.randomUUID();
        String cuerpo = """
                {"cambios":[{"id":"%s","tipo":"MODO_AVANZADO_ACTIVADO","ocurridoEn":"%s"},
                 {"id":"%s","tipo":"GUARDADO_LOCAL_CAMBIADO","ocurridoEn":"%s","objeto":"Video continuo","valorAnterior":"No","valorNuevo":"Sí, 7 días"}]}
                """.formatted(
                        cambio,
                        reloj.instant().minus(Duration.ofHours(2)),
                        UUID.randomUUID(),
                        reloj.instant().minus(Duration.ofHours(1)));
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrados").value(2));
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(jsonPath("$.registrados").value(0))
                .andExpect(jsonPath("$.repetidos").value(2));

        String avanzado = actividad(titular, "?tipo=MODO_AVANZADO_ACTIVADO");
        assertThat(campo(avanzado, "$.elementos[0].actor.tipo")).isEqualTo("PC");
        assertThat(campo(avanzado, "$.elementos[0].pc.nombre")).isEqualTo("Sala-Rosa");
        assertThat(campo(actividad(titular, "?tipo=GUARDADO_LOCAL_CAMBIADO"), "$.elementos[0].valorNuevo"))
                .isEqualTo("Sí, 7 días");
    }

    @Test
    void ca39_3_unaRepeticionVistaEnLaPcQuedaConLaCamaraElMomentoYCuantoSeVio() throws Exception {
        String camara = campo(
                VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Logitech C920", "Sala")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.camaraId");
        var desde = reloj.instant().minus(Duration.ofHours(3));
        var hasta = desde.plus(Duration.ofMinutes(10));
        String cuerpo =
                """
                {"cambios":[{"id":"%s","tipo":"REPETICION_VISTA","ocurridoEn":"%s","camaraId":"%s",
                 "momentoDesde":"%s","momentoHasta":"%s","duracionSegundos":180}]}
                """.formatted(UUID.randomUUID(), reloj.instant().minus(Duration.ofMinutes(5)), camara, desde, hasta);
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrados").value(1));

        // Any member sees it, from the PC, with the camera, the room, the moment and the seconds.
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String vista = actividad(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), "?tipo=REPETICION_VISTA");
        assertThat(campo(vista, "$.total")).isEqualTo("1");
        assertThat(campo(vista, "$.elementos[0].actor.tipo")).isEqualTo("PC");
        assertThat(campo(vista, "$.elementos[0].pc.nombre")).isEqualTo("Sala-Rosa");
        assertThat(campo(vista, "$.elementos[0].camaraId")).isEqualTo(camara);
        assertThat(campo(vista, "$.elementos[0].objeto")).isEqualTo("Sala");
        assertThat(campo(vista, "$.elementos[0].valorAnterior")).isEqualTo(desde + "/" + hasta);
        assertThat(campo(vista, "$.elementos[0].valorNuevo")).isEqualTo("180");

        // Another household does not see it.
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana"), "$.tokenAcceso");
        assertThat(campo(actividad(otro, "?tipo=REPETICION_VISTA"), "$.total")).isEqualTo("0");
    }

    @Test
    void ca39_3_laRepeticionDeLaPcNecesitaUnaCamaraSuyaYUnMomentoPasado() throws Exception {
        var desde = reloj.instant().minus(Duration.ofHours(1));
        String sinCamara = """
                {"cambios":[{"id":"%s","tipo":"REPETICION_VISTA","ocurridoEn":"%s","camaraId":"%s",
                 "momentoDesde":"%s","momentoHasta":"%s","duracionSegundos":60}]}
                """.formatted(
                UUID.randomUUID(), reloj.instant(), UUID.randomUUID(), desde, desde.plus(Duration.ofMinutes(10)));
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinCamara))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['cambios[0].camaraId']").exists());

        String camara = campo(
                VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Logitech C920", "Sala")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.camaraId");
        String alReves = """
                {"cambios":[{"id":"%s","tipo":"REPETICION_VISTA","ocurridoEn":"%s","camaraId":"%s",
                 "momentoDesde":"%s","momentoHasta":"%s","duracionSegundos":60}]}
                """.formatted(UUID.randomUUID(), reloj.instant(), camara, desde, desde.minusSeconds(60));
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alReves))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['cambios[0].momentoDesde']").exists());
        String sinDuracion =
                """
                {"cambios":[{"id":"%s","tipo":"REPETICION_VISTA","ocurridoEn":"%s","camaraId":"%s",
                 "momentoDesde":"%s","momentoHasta":"%s"}]}
                """.formatted(UUID.randomUUID(), reloj.instant(), camara, desde, desde.plus(Duration.ofMinutes(1)));
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinDuracion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['cambios[0].duracionSegundos']").exists());
    }

    @Test
    void laPcSoloInformaSusPropiosCambiosConFechasPasadas() throws Exception {
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cambios\":[{\"id\":\"%s\",\"tipo\":\"PC_VINCULADA\",\"ocurridoEn\":\"%s\"}]}"
                                .formatted(UUID.randomUUID(), reloj.instant())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['cambios[0].tipo']").exists());
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(tokenPc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"cambios\":[{\"id\":\"%s\",\"tipo\":\"MODO_AVANZADO_ACTIVADO\",\"ocurridoEn\":\"%s\"}]}"
                                        .formatted(
                                                UUID.randomUUID(),
                                                reloj.instant().plus(Duration.ofHours(1)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/agente/pc/actividad")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cambios\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ca33_1_nadiePuedeEditarNiBorrarElRegistro() throws Exception {
        await().atMost(Duration.ofSeconds(5)).until(() -> !tipos(titular).isEmpty());
        assertThatThrownBy(() -> jdbc.update("update actividad set valor_nuevo = 'x'"))
                .hasMessageContaining("no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.update("delete from actividad"))
                .hasMessageContaining("no se puede modificar ni borrar");
    }

    @Test
    void ca33_4_sinCambiosLaListaEstaVaciaYOtraViviendaNoVeLaActividad() throws Exception {
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");
        String vacia = actividad(otro, "");
        assertThat(campo(vacia, "$.elementos.length()")).isEqualTo("0");
        assertThat(campo(vacia, "$.total")).isEqualTo("0");
        mvc.perform(get("/api/actividad?tamano=101").header("Authorization", bearer(otro)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desvincularDesdeLaPcQuedaComoHechoPorLaPcConLosAmbientesQuePierdenDeteccion() throws Exception {
        VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Webcam", "Sala");
        mvc.perform(delete("/api/agente/pc").header("Authorization", bearer(tokenPc)))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).until(() -> tipos(titular).contains("PC_DESVINCULADA"));
        String desvinculada = actividad(titular, "?tipo=PC_DESVINCULADA");
        assertThat(campo(desvinculada, "$.elementos[0].actor.tipo")).isEqualTo("PC");
        assertThat(campo(desvinculada, "$.elementos[0].valorAnterior")).isEqualTo("Sala");
        assertThat(campo(desvinculada, "$.elementos[0].valorNuevo")).isEqualTo("DESDE_LA_PC");
    }

    @Test
    void ca31_6_desvincularDesdeLaPcBorrandoSusDatosQuedaEnElRegistro() throws Exception {
        mvc.perform(delete("/api/agente/pc?datosLocalesBorrados=true").header("Authorization", bearer(tokenPc)))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).until(() -> tipos(titular).contains("DATOS_LOCALES_ELIMINADOS"));
        String borrados = actividad(titular, "?tipo=DATOS_LOCALES_ELIMINADOS");
        assertThat(campo(borrados, "$.elementos[0].actor.tipo")).isEqualTo("PC");
        assertThat(campo(borrados, "$.elementos[0].pc.nombre")).isEqualTo("Sala-Rosa");
    }

    @Test
    void ca29_8_cuandoLaAppYLaPcCambianUnaCamaraGanaElUltimoYSeRegistraDeDondeVinoCadaUno() throws Exception {
        String camara = campo(
                VinculacionDePrueba.reportarWebcams(mvc, tokenPc, "usb#1:Webcam USB HD")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].camaraId");
        VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Webcam USB HD", "Sala");
        reloj.avanzar(Duration.ofSeconds(1));
        VinculacionDePrueba.asignarDesdeLaApp(mvc, titular, camara, "Cocina");
        reloj.avanzar(Duration.ofSeconds(1));
        mvc.perform(delete("/api/camaras/" + camara + "/ambiente").header("Authorization", bearer(titular)));

        await().atMost(Duration.ofSeconds(5)).until(() -> tipos(titular).contains("CAMARA_RETIRADA"));
        String asignaciones = actividad(titular, "?tipo=CAMARA_ASIGNADA");
        assertThat((List<String>) JsonPath.read(asignaciones, "$.elementos[*].actor.tipo"))
                .containsExactly("FAMILIAR", "PC");
        assertThat((List<String>) JsonPath.read(asignaciones, "$.elementos[*].valorNuevo"))
                .containsExactly("Cocina", "Sala");
        String retirada = actividad(titular, "?tipo=CAMARA_RETIRADA");
        assertThat(campo(retirada, "$.elementos[0].actor.tipo")).isEqualTo("FAMILIAR");
        assertThat(campo(retirada, "$.elementos[0].actor.usuario.nombre")).isEqualTo("Carmen");
        assertThat(campo(retirada, "$.elementos[0].valorAnterior")).isEqualTo("Cocina");
    }
}
