package tech.tetengo.api.camaras.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.VinculacionDePrueba.asignarDesdeLaApp;
import static tech.tetengo.api.support.VinculacionDePrueba.reportarWebcams;

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
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-29 (product decision of 2026-10-10): the PC reports the webcams it detects, the owner assigns them
 * to rooms from the app («Elige las cámaras»), previews them first, and stops using them.
 */
class CamarasDesdeLaAppIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String sesion;
    String titular;
    UUID hogar;
    String tokenPc;
    String pcId;

    @BeforeEach
    void pcConDosWebcams() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        String tokens = VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa");
        tokenPc = campo(tokens, "$.access_token");
        pcId = campo(tokens, "$.pc_id");
    }

    private String webcams() throws Exception {
        return reportarWebcams(mvc, tokenPc, "usb#1:Webcam USB HD", "usb#2:Logitech C920")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String camaraDe(String inventario, String dispositivo) {
        return campo(inventario, "$[?(@.dispositivoId == '%s')].camaraId".formatted(dispositivo))
                .replaceAll("[\\[\\]\"]", "");
    }

    private String ver(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca29_1_laAppListaLasWebcamsQueReportoLaPcSinUsarHastaQueElTitularLasElija() throws Exception {
        String inventario = webcams();
        assertThat(campo(inventario, "$.length()")).isEqualTo("2");
        assertThat(campo(inventario, "$[0].enUso")).isEqualTo("false");
        assertThat(campo(inventario, "$[0].token")).isNull();
        assertThat(campo(inventario, "$[0].nombreHabitacion")).isNull();

        String detectadas = ver("/api/pcs/" + pcId + "/camaras", titular);
        assertThat(campo(detectadas, "$.length()")).isEqualTo("2");
        assertThat(campo(detectadas, "$[0].nombreDispositivo")).isEqualTo("Logitech C920");
        assertThat(campo(detectadas, "$[0].enUso")).isEqualTo("false");
        assertThat(campo(detectadas, "$[0].conectada")).isEqualTo("true");
        assertThat(campo(ver("/api/pcs", titular), "$[0].camarasSinUsar")).isEqualTo("2");
        assertThat(campo(ver("/api/camaras", titular), "$.length()")).isEqualTo("0");
        // Reporting again changes nothing.
        assertThat(camaraDe(webcams(), "usb#1")).isEqualTo(camaraDe(inventario, "usb#1"));
    }

    @Test
    void ca29_2_elTitularAsignaUnaWebcamDesdeElCelularYEmpiezaACapturarEnEseAmbiente() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");

        asignarDesdeLaApp(mvc, titular, camara, "Sala")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(camara))
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala"))
                .andExpect(jsonPath("$.pcId").value(pcId));

        assertThat(campo(ver("/api/camaras", titular), "$[0].id")).isEqualTo(camara);
        assertThat(campo(ver("/api/pcs", titular), "$[0].camarasSinUsar")).isEqualTo("1");
        // On reconnection the PC gets its assignment and the camera's token.
        String dePc = ver("/api/agente/pc/camaras", tokenPc);
        String token =
                campo(dePc, "$[?(@.camaraId == '%s')].token".formatted(camara)).replaceAll("[\\[\\]\"]", "");
        assertThat(campo(dePc, "$[?(@.camaraId == '%s')].nombreHabitacion".formatted(camara)))
                .contains("Sala");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala"));
        // The other webcam is not in use: it has no token.
        String otra = camaraDe(dePc, "usb#2");
        assertThat(campo(dePc, "$[?(@.camaraId == '%s')].token".formatted(otra)))
                .isEqualTo("[null]");
    }

    @Test
    void ca29_3_unaCamaraPorAmbienteYNingunaEnElBano() throws Exception {
        String inventario = webcams();
        asignarDesdeLaApp(mvc, titular, camaraDe(inventario, "usb#1"), "Sala").andExpect(status().isOk());

        asignarDesdeLaApp(mvc, titular, camaraDe(inventario, "usb#2"), "sala")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_OCUPADO"))
                .andExpect(jsonPath("$.camara.pcNombre").value("Sala-Rosa"));
        for (String bano : new String[] {"Baño", "baño de visitas", "SS.HH.", "Servicios higiénicos", "WC"}) {
            asignarDesdeLaApp(mvc, titular, camaraDe(inventario, "usb#2"), bano)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("AMBIENTE_NO_PERMITIDO"));
        }
        mvc.perform(post("/api/ambientes")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Baño\"}"))
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_NO_PERMITIDO"));
        // «Bañera» is not a bathroom word; a room called «Pasillo del baño» is.
        asignarDesdeLaApp(mvc, titular, camaraDe(inventario, "usb#2"), "Pasillo del baño")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void ca29_9_elInvitadoVeLasCamarasPeroSoloElTitularAsignaYDejaDeUsar() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String tokenInvitado = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);

        assertThat(campo(ver("/api/pcs/" + pcId + "/camaras", tokenInvitado), "$.length()"))
                .isEqualTo("2");
        asignarDesdeLaApp(mvc, tokenInvitado, camara, "Sala")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
        mvc.perform(delete("/api/camaras/" + camara + "/ambiente").header("Authorization", bearer(tokenInvitado)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ca29_2_dejarDeUsarDesdeElCelularDetieneLaCamaraYLaMismaWebcamVuelveConSuIdentificador() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");
        asignarDesdeLaApp(mvc, titular, camara, "Sala");
        String token = campo(
                mvc.perform(post("/api/agente/pc/camaras/tokens").header("Authorization", bearer(tokenPc)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].token");

        mvc.perform(delete("/api/camaras/" + camara + "/ambiente").header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PC_DESVINCULADA"));
        assertThat(campo(ver("/api/camaras", titular), "$.length()")).isEqualTo("0");
        assertThat(campo(ver("/api/pcs/" + pcId + "/camaras", titular), "$[?(@.id == '%s')].enUso".formatted(camara)))
                .isEqualTo("[false]");
        // CA-29.4: the same webcam is the same camera when it is used again.
        asignarDesdeLaApp(mvc, titular, camara, "Dormitorio")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(camara));
    }

    @Test
    void ca29_5_unaWebcamQueYaNoApareceQuedaDesconectadaYConservaSuAmbiente() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");
        asignarDesdeLaApp(mvc, titular, camara, "Sala");

        reportarWebcams(mvc, tokenPc, "usb#2:Logitech C920").andExpect(status().isOk());

        String detectadas = ver("/api/pcs/" + pcId + "/camaras", titular);
        assertThat(campo(detectadas, "$[?(@.id == '%s')].nombreHabitacion".formatted(camara)))
                .contains("Sala");
        String otra = camaraDe(ver("/api/agente/pc/camaras", tokenPc), "usb#2");
        asignarDesdeLaApp(mvc, titular, otra, "Sala").andExpect(status().isConflict());
    }

    @Test
    void ca29_7_laVistaPreviaDeUnaWebcamSinUsarSigueLasReglasDeLaVistaEnVivoYQuedaEnElRegistro() throws Exception {
        String inventario = webcams();
        String camara = camaraDe(inventario, "usb#1");
        mvc.perform(post("/api/camaras/" + camara + "/vista-previa").header("Authorization", bearer(titular)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
        ApiDePrueba.otorgarConsentimiento(mvc, titular);
        esperarEventosPendientes();

        String sesion = mvc.perform(
                        post("/api/camaras/" + camara + "/vista-previa").header("Authorization", bearer(titular)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(sesion, "$.urlTransmision")).contains("/camaras/" + camara + "/index.m3u8?token=");
        // A regular live view needs a camera in use.
        mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId"))
                        .header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());
        String accesos = ver("/api/accesos-vista-en-vivo", titular);
        assertThat(campo(accesos, "$[0].vistaPrevia")).isEqualTo("true");
        assertThat(campo(accesos, "$[0].camaraId")).isEqualTo(camara);

        // An unplugged webcam cannot be previewed.
        reportarWebcams(mvc, tokenPc, "usb#1:Webcam USB HD");
        String desenchufada = camaraDe(ver("/api/agente/pc/camaras", tokenPc), "usb#2");
        mvc.perform(post("/api/camaras/" + desenchufada + "/vista-previa").header("Authorization", bearer(titular)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_DESCONECTADA"));
    }

    @Test
    void multitenancy_otraViviendaNoVeNiAsignaNiPrevisualizaLasWebcams() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");

        mvc.perform(get("/api/pcs/" + pcId + "/camaras").header("Authorization", bearer(otro)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PC_NO_ENCONTRADA"));
        asignarDesdeLaApp(mvc, otro, camara, "Sala")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
        mvc.perform(post("/api/camaras/" + camara + "/vista-previa").header("Authorization", bearer(otro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ca29_6_elTitularPideALaPcBuscarDeNuevoSusWebcams() throws Exception {
        mvc.perform(post("/api/pcs/" + pcId + "/busqueda-de-camaras").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(post("/api/pcs/" + pcId + "/busqueda-de-camaras")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isForbidden());
        // The PC found none: the app says so (an empty list).
        reportarWebcams(mvc, tokenPc).andExpect(status().isOk());
        assertThat(campo(ver("/api/pcs/" + pcId + "/camaras", titular), "$.length()"))
                .isEqualTo("0");
    }

    @Test
    void ca29_8_laAsignacionDesdeLaPcApareceEnLaAppYGanaElUltimoCambio() throws Exception {
        String camara = camaraDe(webcams(), "usb#1");
        // «Elegir aquí» on the PC, then the owner moves it from the phone: the last change wins.
        VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#1", "Webcam USB HD", "Sala")
                .andExpect(status().isOk());
        assertThat(campo(ver("/api/camaras", titular), "$[0].nombreHabitacion")).isEqualTo("Sala");
        asignarDesdeLaApp(mvc, titular, camara, "Cocina").andExpect(status().isOk());
        assertThat(campo(
                        ver("/api/agente/pc/camaras", tokenPc),
                        "$[?(@.camaraId == '%s')].nombreHabitacion".formatted(camara)))
                .contains("Cocina");
        // The PC follows the same rules: no bathrooms.
        VinculacionDePrueba.usarCamara(mvc, tokenPc, "usb#2", "Logitech C920", "Baño")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_NO_PERMITIDO"));
    }
}
