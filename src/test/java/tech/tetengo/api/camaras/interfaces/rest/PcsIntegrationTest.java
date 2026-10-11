package tech.tetengo.api.camaras.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.VinculacionDePrueba.renovar;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.camaras.application.VencerPcsSinUso;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-28 to US-31 after the link: the PCs in the app, token rotation with reuse detection (RFC 9700),
 * unlinking from the app or the PC, expiry after 90 days (CA-28.7), and isolation between households.
 */
class PcsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    VencerPcsSinUso vencerPcs;

    String titular;
    UUID hogar;
    String tokens;
    String pcId;

    @BeforeEach
    void pcVinculada() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(sesion, "$.usuario.id")), "token-de-carmen");
        tokens = VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa");
        pcId = campo(tokens, "$.pc_id");
        push.limpiar();
    }

    private String acceso() {
        return campo(tokens, "$.access_token");
    }

    private void verPcComoAgente(String token, int estado) throws Exception {
        mvc.perform(get("/api/agente/pc").header("Authorization", bearer(token)))
                .andExpect(status().is(estado));
    }

    @Test
    void ca30_2_cadaMiembroVeLaPcConSuEstadoYSuVersion() throws Exception {
        mvc.perform(post("/api/agente/pc/senal")
                        .header("Authorization", bearer(acceso()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionAgente\":\"0.5.1\"}"))
                .andExpect(status().isOk());
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);

        mvc.perform(get("/api/pcs").header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pcId))
                .andExpect(jsonPath("$[0].estadoConexion").value("EN_LINEA"))
                .andExpect(jsonPath("$[0].versionAgente").value("0.5.1"))
                .andExpect(jsonPath("$[0].ultimaSenal").exists());
    }

    @Test
    void ca30_3_unaPcQueDejaDeConectarseApareceSinConexionDesdeSuUltimaSenal() throws Exception {
        mvc.perform(post("/api/agente/pc/senal").header("Authorization", bearer(acceso())))
                .andExpect(status().isOk());
        String ultima = campo(
                mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$[0].ultimaSenal");

        reloj.avanzar(Duration.ofSeconds(91));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$[0].estadoConexion").value("DESCONECTADA"))
                .andExpect(jsonPath("$[0].ultimaSenal").value(ultima));
    }

    @Test
    void elTitularRenombraLaPcYElInvitadoNo() throws Exception {
        mvc.perform(patch("/api/pcs/" + pcId)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"PC de la sala\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("PC de la sala"));
        mvc.perform(patch("/api/pcs/" + pcId)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PC_NOMBRE_VACIO"));
        mvc.perform(patch("/api/pcs/" + pcId)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"%s\"}".formatted("x".repeat(61))))
                .andExpect(jsonPath("$.codigo").value("PC_NOMBRE_MUY_LARGO"));

        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(patch("/api/pcs/" + pcId)
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otra\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elTokenDeRenovacionRotaEnCadaUso() throws Exception {
        reloj.avanzar(Duration.ofMinutes(50));
        String renovados = renovar(mvc, campo(tokens, "$.refresh_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pc_id").value(pcId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(campo(renovados, "$.refresh_token")).isNotEqualTo(campo(tokens, "$.refresh_token"));
        verPcComoAgente(campo(renovados, "$.access_token"), 200);
        renovar(mvc, campo(renovados, "$.refresh_token")).andExpect(status().isOk());
    }

    @Test
    void rfc9700_reusarUnTokenDeRenovacionYaRotadoRevocaTodoYDesvinculaLaPc() throws Exception {
        String renovados = renovar(mvc, campo(tokens, "$.refresh_token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Someone replays the old token: both the attacker and the PC lose access.
        renovar(mvc, campo(tokens, "$.refresh_token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        renovar(mvc, campo(renovados, "$.refresh_token"))
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        mvc.perform(get("/api/agente/pc").header("Authorization", bearer(campo(renovados, "$.access_token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PC_DESVINCULADA"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !push.deTipo(TipoAviso.PC_DESVINCULADA).isEmpty());
    }

    @Test
    void ca31_1_alQuitarlaDesdeLaAppSusTokensDejanDeServirAlInstante() throws Exception {
        mvc.perform(delete("/api/pcs/" + pcId).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        verPcComoAgente(acceso(), 401);
        renovar(mvc, campo(tokens, "$.refresh_token"))
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete("/api/pcs/" + pcId).header("Authorization", bearer(titular)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PC_NO_ENCONTRADA"));
        // Removed from the app by the owner: no notice to the family.
        assertThat(push.deTipo(TipoAviso.PC_DESVINCULADA)).isEmpty();
    }

    @Test
    void ca31_3_alDesvincularseDesdeLaPcLaFamiliaRecibeUnAviso() throws Exception {
        mvc.perform(delete("/api/agente/pc").header("Authorization", bearer(acceso())))
                .andExpect(status().isNoContent());

        verPcComoAgente(acceso(), 401);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !push.deTipo(TipoAviso.PC_DESVINCULADA).isEmpty());
    }

    @Test
    void rfc7009_revocarElTokenDeRenovacionDesvinculaLaPcYSiempreResponde200() throws Exception {
        mvc.perform(post("/api/agente/token/revocacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", campo(tokens, "$.refresh_token"))
                        .param("token_type_hint", "refresh_token")
                        .param("client_id", VinculacionDePrueba.CLIENTE))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agente/token/revocacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "desconocido")
                        .param("client_id", VinculacionDePrueba.CLIENTE))
                .andExpect(status().isOk());

        verPcComoAgente(acceso(), 401);
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !push.deTipo(TipoAviso.PC_DESVINCULADA).isEmpty());
    }

    @Test
    void ca31_5_elInvitadoNoPuedeQuitarLaPc() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(delete("/api/pcs/" + pcId)
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
        verPcComoAgente(acceso(), 200);
    }

    @Test
    void ca28_7_menosDe90DiasSinConectarseLaPcSigueVinculada() throws Exception {
        reloj.avanzar(Duration.ofDays(89));
        vencerPcs.ejecutar();
        renovar(mvc, campo(tokens, "$.refresh_token")).andExpect(status().isOk());
    }

    @Test
    void ca28_7_tras90DiasSinConectarseHayQueVincularlaDeNuevo() throws Exception {
        reloj.avanzar(Duration.ofDays(90));
        renovar(mvc, campo(tokens, "$.refresh_token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void ca28_7_laTareaDiariaQuitaLasPcsCon90DiasSinUso() throws Exception {
        String otra = VinculacionDePrueba.vincular(mvc, titular, "Cocina-Rosa");
        reloj.avanzar(Duration.ofDays(45));
        renovar(mvc, campo(otra, "$.refresh_token")).andExpect(status().isOk());
        reloj.avanzar(Duration.ofDays(45));

        vencerPcs.ejecutar();
        vencerPcs.ejecutar();

        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Cocina-Rosa"));
    }

    @Test
    void elTokenDeLaPcNoSirveParaLosEndpointsDeUnaCamaraNiDeLaApp() throws Exception {
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(acceso())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TOKEN_DE_CAMARA_REQUERIDO"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(acceso()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/agente/pc").header("Authorization", bearer(titular)))
                .andExpect(status().isForbidden());
    }

    @Test
    void multitenancy_otraViviendaNoVeNiCambiaLaPc() throws Exception {
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");

        mvc.perform(get("/api/pcs").header("Authorization", bearer(otro)))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(patch("/api/pcs/" + pcId)
                        .header("Authorization", bearer(otro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mía\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/pcs/" + pcId).header("Authorization", bearer(otro)))
                .andExpect(status().isNotFound());
        // A PC token forged for another household is rejected.
        verPcComoAgente(JwtDePrueba.tokenDePc(UUID.randomUUID(), UUID.fromString(pcId)), 401);
        verPcComoAgente(acceso(), 200);
    }

    @Test
    void unaInstalacionDelEquipoApareceComoUnaPcYAlQuitarlaSuCredencialDejaDeServir() throws Exception {
        String credencial = DatosDePrueba.instalacion(jdbc, hogar);
        String registro = ApiDePrueba.registrarAgente(mvc, credencial, "Dormitorio");
        String camara = campo(registro, "$.camaraId");

        String pcs = mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].nombre").value("PC de la casa"))
                .andExpect(jsonPath("$[1].origen").value("INSTALACION"))
                .andExpect(jsonPath("$[1].vinculadaPor").doesNotExist())
                .andExpect(jsonPath("$[1].camaras[0].id").value(camara))
                .andExpect(jsonPath("$[1].camaras[0].nombreHabitacion").value("Dormitorio"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String pcDeInstalacion = campo(pcs, "$[1].id");
        mvc.perform(get("/api/camaras").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$[0].pcId").value(pcDeInstalacion));

        mvc.perform(delete("/api/pcs/" + pcDeInstalacion).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(registro, "$.token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PC_DESVINCULADA"));
        mvc.perform(post("/api/agente/camaras/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencialInstalacion\":\"%s\",\"nombreHabitacion\":\"Sala\"}"
                                .formatted(credencial)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_INVALIDA"));
        mvc.perform(get("/api/camaras").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
        // Its live view, pause and rename are gone with it.
        mvc.perform(patch("/api/camaras/" + camara)
                        .header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Sala\"}"))
                .andExpect(status().isNotFound());
    }
}
