package tech.tetengo.api.camaras.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.VinculacionDePrueba.usarCamara;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-29 / US-30: the webcams of linked PCs, each in a room of the household, known by the operating
 * system's device id; and the rooms in the app.
 */
class CamarasDeLaPcIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBCAM = "\\\\?\\usb#vid_046d&pid_0825#6&2b4ad0c3&0&2#{65e8773d}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    String sesion;
    String titular;
    UUID hogar;
    String salaRosa;
    String cocinaRosa;

    @BeforeEach
    void dosPcs() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        ApiDePrueba.otorgarConsentimiento(mvc, titular);
        salaRosa = campo(VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa"), "$.access_token");
        cocinaRosa = campo(VinculacionDePrueba.vincular(mvc, titular, "Cocina-Rosa"), "$.access_token");
    }

    private String usar(String tokenPc, String dispositivo, String ambiente) throws Exception {
        return usarCamara(mvc, tokenPc, dispositivo, "Webcam USB HD", ambiente)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String get(String ruta, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ruta)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca29_2_laCamaraElegidaDetectaEnSuAmbienteYApareceEnLaApp() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");

        assertThat(campo(camara, "$.dispositivoId")).isEqualTo(WEBCAM);
        assertThat(campo(camara, "$.nombreHabitacion")).isEqualTo("Sala");
        var token = decodificador.decode(campo(camara, "$.token"));
        assertThat(token.getClaimAsString("camara_id")).isEqualTo(campo(camara, "$.camaraId"));
        assertThat(token.getClaimAsString("pc_id")).isNotNull();
        assertThat(token.getClaimAsString("rol")).isEqualTo("AGENTE");

        // The camera's token works as an installation's: heartbeat, capture state and events.
        String agente = campo(camara, "$.token");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capturaPermitida").value(true))
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala"));
        String evento = ApiDePrueba.enviarEvento(mvc, agente, UUID.randomUUID(), "caida", Instant.now())
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/alertas/" + campo(evento, "$.alertaId"))
                        .header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.habitacion").value("Sala"));

        String camaras = get("/api/camaras", titular);
        assertThat(campo(camaras, "$[0].id")).isEqualTo(campo(camara, "$.camaraId"));
        assertThat(campo(camaras, "$[0].nombreDispositivo")).isEqualTo("Webcam USB HD");
        assertThat(campo(camaras, "$[0].ambienteId")).isEqualTo(campo(camara, "$.ambienteId"));
        assertThat(campo(get("/api/pcs", titular), "$[0].camaras[0].nombreHabitacion"))
                .isEqualTo("Sala");
    }

    @Test
    void ca29_2_elNombreDelAmbienteEsObligatorio() throws Exception {
        usarCamara(mvc, salaRosa, WEBCAM, "Webcam USB HD", "  ")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_NOMBRE_VACIO"));
        mvc.perform(put("/api/agente/pc/camaras")
                        .header("Authorization", bearer(salaRosa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispositivoId\":\"cam-1\",\"nombreDispositivo\":\"Webcam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ambienteElegido").exists());
    }

    @Test
    void ca29_3_unAmbienteConCamaraNoAceptaOtraNiDeOtraPc() throws Exception {
        usar(salaRosa, WEBCAM, "Sala");

        usarCamara(mvc, cocinaRosa, "otra-webcam", "HD Pro Webcam", "sala")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_OCUPADO"))
                .andExpect(jsonPath("$.ambiente").value("Sala"))
                .andExpect(jsonPath("$.camara.pcNombre").value("Sala-Rosa"))
                .andExpect(jsonPath("$.camara.nombreDispositivo").value("Webcam USB HD"));
        usarCamara(mvc, salaRosa, "segunda-webcam", "Logitech C920", "Sala").andExpect(status().isConflict());
        // The household's rooms tell the PC which ones already have a camera.
        String ambientes = get("/api/agente/ambientes", cocinaRosa);
        assertThat(campo(ambientes, "$[0].nombre")).isEqualTo("Sala");
        assertThat(campo(ambientes, "$[0].camaras[0].pcNombre")).isEqualTo("Sala-Rosa");
    }

    @Test
    void ca29_4_laMismaWebcamSiempreEsLaMismaCamara() throws Exception {
        String primera = campo(usar(salaRosa, WEBCAM, "Sala"), "$.camaraId");

        // After a restart the PC sends the same device again.
        assertThat(campo(usar(salaRosa, WEBCAM, "Sala"), "$.camaraId")).isEqualTo(primera);
        // Plugging another webcam does not move the room.
        usar(salaRosa, "otra-webcam", "Dormitorio");
        String camaras = get("/api/camaras", titular);
        assertThat(campo(camaras, "$.length()")).isEqualTo("2");
        // Moved to another room, then «Dejar de usar» and used again: still the same camera.
        assertThat(campo(usar(salaRosa, WEBCAM, "Pasillo"), "$.camaraId")).isEqualTo(primera);
        mvc.perform(delete("/api/agente/pc/camaras/" + primera).header("Authorization", bearer(salaRosa)))
                .andExpect(status().isNoContent());
        assertThat(campo(usar(salaRosa, WEBCAM, "Sala"), "$.camaraId")).isEqualTo(primera);
    }

    @Test
    void ca29_5_unaCamaraDesconectadaSeQuedaEnSuAmbiente() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");
        String agente = campo(camara, "$.token");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)));
        mvc.perform(post("/api/agente/senal")
                .header("Authorization", bearer(agente))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"webcamConectada\":false}"));

        String camaras = get("/api/camaras", titular);
        assertThat(campo(camaras, "$[0].estadoConexion")).isEqualTo("DESCONECTADA");
        assertThat(campo(camaras, "$[0].nombreHabitacion")).isEqualTo("Sala");
        usarCamara(mvc, cocinaRosa, "otra", "Otra", "Sala").andExpect(status().isConflict());
    }

    @Test
    void dejarDeUsarUnaCamaraLaQuitaDeLaAppYAnulaSuToken() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");

        mvc.perform(delete("/api/agente/pc/camaras/" + campo(camara, "$.camaraId"))
                        .header("Authorization", bearer(salaRosa)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(camara, "$.token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PC_DESVINCULADA"));
        assertThat(campo(get("/api/camaras", titular), "$.length()")).isEqualTo("0");
        usar(cocinaRosa, "otra", "Sala");
    }

    @Test
    void laPcRenuevaLosTokensDeSusCamarasYVeSoloLasSuyas() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");
        usar(cocinaRosa, "otra", "Cocina");

        String renovados = mvc.perform(post("/api/agente/pc/camaras/tokens").header("Authorization", bearer(salaRosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(renovados, "$[0].camaraId")).isEqualTo(campo(camara, "$.camaraId"));
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(renovados, "$[0].token"))))
                .andExpect(status().isOk());
        String suyas = get("/api/agente/pc/camaras", salaRosa);
        assertThat(campo(suyas, "$.length()")).isEqualTo("1");
        assertThat(campo(suyas, "$[0].token")).isNull();
        // A PC cannot stop another PC's camera, and a camera token is not a PC token.
        String deLaCocina = campo(get("/api/agente/pc/camaras", cocinaRosa), "$[0].camaraId");
        mvc.perform(delete("/api/agente/pc/camaras/" + deLaCocina).header("Authorization", bearer(salaRosa)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/agente/pc/camaras/tokens").header("Authorization", bearer(campo(camara, "$.token"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TOKEN_DE_PC_REQUERIDO"));
    }

    @Test
    void ca31_1_alQuitarLaPcSusCamarasDejanSusAmbientes() throws Exception {
        usar(salaRosa, WEBCAM, "Sala");
        String pc = campo(get("/api/pcs", titular), "$[0].id");

        mvc.perform(delete("/api/pcs/" + pc).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        String ambientes = get("/api/ambientes", titular);
        assertThat(campo(ambientes, "$[0].camaras.length()")).isEqualTo("0");
        usar(cocinaRosa, "otra", "Sala");
    }

    @Test
    void losAmbientesSeGestionanDesdeLaAppYSuNombreLlegaALasAlertas() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");
        String sala = campo(camara, "$.ambienteId");

        mvc.perform(post("/api/ambientes")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Dormitorio\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.camaras").isEmpty());
        mvc.perform(post("/api/ambientes")
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"dormitorio\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_YA_EXISTE"));
        mvc.perform(patch("/api/ambientes/" + sala)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sala comedor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camaras[0].id").value(campo(camara, "$.camaraId")));

        // The camera and its next alerts use the new name.
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(camara, "$.token"))))
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala comedor"));
        mvc.perform(delete("/api/ambientes/" + sala).header("Authorization", bearer(titular)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_CON_CAMARA"));
        String dormitorio = campo(get("/api/ambientes", titular), "$[0].id");
        mvc.perform(delete("/api/ambientes/" + dormitorio).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());
        assertThat(campo(get("/api/ambientes", titular), "$.length()")).isEqualTo("1");
    }

    @Test
    void elTitularCambiaElAmbienteDeUnaCamaraDesdeLaApp() throws Exception {
        String camara = campo(usar(salaRosa, WEBCAM, "Sala"), "$.camaraId");
        String otra = campo(usar(cocinaRosa, "otra", "Cocina"), "$.camaraId");

        // By name, as before rooms existed: the only camera of the room renames it.
        mvc.perform(patch("/api/camaras/" + camara)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Sala de estar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreHabitacion").value("Sala de estar"));
        // To a room with another camera: CA-29.3.
        mvc.perform(patch("/api/camaras/" + camara)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"cocina\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_OCUPADO"));
        // By id, to a free room.
        String pasillo = campo(
                mvc.perform(post("/api/ambientes")
                                .header("Authorization", bearer(titular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\":\"Pasillo\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(patch("/api/camaras/" + otra)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ambienteId\":\"%s\"}".formatted(pasillo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreHabitacion").value("Pasillo"))
                .andExpect(jsonPath("$.ambienteId").value(pasillo));
        mvc.perform(patch("/api/camaras/" + otra)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elInvitadoVeLosAmbientesPeroNoLosCambia() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String token = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);
        usar(salaRosa, WEBCAM, "Sala");

        assertThat(campo(get("/api/ambientes", token), "$[0].nombre")).isEqualTo("Sala");
        mvc.perform(post("/api/ambientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cocina\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
    }

    @Test
    void multitenancy_otraViviendaNoVeNiCambiaLosAmbientesNiLasCamaras() throws Exception {
        String camara = usar(salaRosa, WEBCAM, "Sala");
        String otra = ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis");
        String otroTitular = campo(otra, "$.tokenAcceso");
        String pcDeLuis = campo(VinculacionDePrueba.vincular(mvc, otroTitular, "PC de Luis"), "$.access_token");

        assertThat(campo(get("/api/ambientes", otroTitular), "$.length()")).isEqualTo("0");
        mvc.perform(patch("/api/ambientes/" + campo(camara, "$.ambienteId"))
                        .header("Authorization", bearer(otroTitular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mía\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_NO_ENCONTRADO"));
        mvc.perform(delete("/api/agente/pc/camaras/" + campo(camara, "$.camaraId"))
                        .header("Authorization", bearer(pcDeLuis)))
                .andExpect(status().isNotFound());
        // The same room name is free in another household.
        usar(pcDeLuis, WEBCAM, "Sala");
        assertThat(campo(get("/api/agente/ambientes", pcDeLuis), "$[0].camaras[0].pcNombre"))
                .isEqualTo("PC de Luis");
    }
}
