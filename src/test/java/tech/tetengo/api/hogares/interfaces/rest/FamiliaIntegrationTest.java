package tech.tetengo.api.hogares.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;

/** US-08: inviting, accepting and removing family members. */
class FamiliaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String tokenTitular;
    UUID hogar;

    @BeforeEach
    void hogar() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        tokenTitular = campo(titular, "$.tokenAcceso");
        hogar = UUID.fromString(campo(titular, "$.hogarId"));
    }

    private ResultActions invitar(String token, String correo) throws Exception {
        return mvc.perform(post("/api/invitaciones")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(correo)));
    }

    private String tokenDeInvitacion(String correo) throws Exception {
        invitar(tokenTitular, correo).andExpect(status().isCreated());
        return correos.ultimoToken(correo);
    }

    private ResultActions aceptarConCuentaNueva(String token, String nombre) throws Exception {
        return mvc.perform(post("/api/invitaciones/" + token + "/aceptacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"%s\",\"contrasena\":\"secreta123\"}".formatted(nombre)));
    }

    private String invitadoConCuentaNueva(String correo, String nombre) throws Exception {
        return aceptarConCuentaNueva(tokenDeInvitacion(correo), nombre)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca08_1_elTitularInvitaYSeEnviaUnEnlaceAlCorreo() throws Exception {
        invitar(tokenTitular, "Beto@Correo.pe")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.correo").value("beto@correo.pe"))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofDays(7)).toString()));

        assertThat(correos.enviadosA("beto@correo.pe")).hasSize(1);
        assertThat(correos.ultimoToken("beto@correo.pe")).isNotBlank();
        assertThat(correos.enviadosA("beto@correo.pe").getFirst().cuerpo())
                .contains("Ana")
                .contains("tetengo://app/invitacion/" + correos.ultimoToken("beto@correo.pe"));
    }

    @Test
    void ca08_2_alAceptarConCuentaNuevaQuedaVinculadoComoInvitado() throws Exception {
        String sesion = invitadoConCuentaNueva("beto@correo.pe", "Beto");
        assertThat(campo(sesion, "$.hogarId")).isEqualTo(hogar.toString());
        assertThat(campo(sesion, "$.rol")).isEqualTo("INVITADO");
        assertThat(campo(sesion, "$.usuario.correo")).isEqualTo("beto@correo.pe");
        assertThat(campo(sesion, "$.usuario.nombre")).isEqualTo("Beto");

        mvc.perform(get("/api/familiares").header("Authorization", bearer(campo(sesion, "$.tokenAcceso"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Ana"))
                .andExpect(jsonPath("$[0].correo").value("ana@correo.pe"))
                .andExpect(jsonPath("$[0].rol").value("TITULAR"))
                .andExpect(jsonPath("$[1].usuarioId").value(campo(sesion, "$.usuario.id")))
                .andExpect(jsonPath("$[1].nombre").value("Beto"))
                .andExpect(jsonPath("$[1].rol").value("INVITADO"));

        // The new account can sign in again with its password.
        assertThat(campo(ApiDePrueba.iniciarSesion(mvc, "beto@correo.pe", "secreta123"), "$.hogarId"))
                .isEqualTo(hogar.toString());
    }

    @Test
    void ca08_2_unaCuentaExistenteAceptaConSuToken() throws Exception {
        ApiDePrueba.registrarCuenta(mvc, "beto@correo.pe", "secreta123", "Beto");
        String sesionBeto = ApiDePrueba.iniciarSesion(mvc, "beto@correo.pe", "secreta123");
        String token = tokenDeInvitacion("beto@correo.pe");

        String sesion = mvc.perform(post("/api/invitaciones/" + token + "/aceptacion")
                        .header("Authorization", bearer(campo(sesionBeto, "$.tokenAcceso"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hogarId").value(hogar.toString()))
                .andExpect(jsonPath("$.rol").value("INVITADO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(sesion, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.rol").value("INVITADO"));
    }

    @Test
    void unaInvitacionVencidaOUsadaNoSirve() throws Exception {
        String token = tokenDeInvitacion("beto@correo.pe");
        reloj.avanzar(Duration.ofDays(7));
        aceptarConCuentaNueva(token, "Beto")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("INVITACION_VENCIDA"));

        reloj.reiniciar();
        String otro = tokenDeInvitacion("carla@correo.pe");
        aceptarConCuentaNueva(otro, "Carla").andExpect(status().isCreated());
        aceptarConCuentaNueva(otro, "Carla").andExpect(status().isGone());
        aceptarConCuentaNueva("inventado", "Carla").andExpect(status().isGone());
    }

    @Test
    void sinTokenLaCuentaNuevaNecesitaNombreYContrasena() throws Exception {
        String token = tokenDeInvitacion("beto@correo.pe");
        mvc.perform(post("/api/invitaciones/" + token + "/aceptacion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty())
                .andExpect(jsonPath("$.campos.contrasena").isNotEmpty());
    }

    @Test
    void noSeInvitaAQuienYaEsFamiliar() throws Exception {
        invitadoConCuentaNueva("beto@correo.pe", "Beto");
        invitar(tokenTitular, "beto@correo.pe")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_ES_FAMILIAR"));
        invitar(tokenTitular, "ana@correo.pe").andExpect(status().isConflict());
    }

    @Test
    void ca08_4_elInvitadoNoInvitaNiRetiraFamiliares() throws Exception {
        String sesion = invitadoConCuentaNueva("beto@correo.pe", "Beto");
        String tokenInvitado = campo(sesion, "$.tokenAcceso");

        invitar(tokenInvitado, "carla@correo.pe")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
        mvc.perform(delete("/api/familiares/" + campo(titular, "$.usuario.id"))
                        .header("Authorization", bearer(tokenInvitado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
    }

    @Test
    void ca08_3_alRetirarloPierdeElAccesoDeInmediato() throws Exception {
        String sesion = invitadoConCuentaNueva("beto@correo.pe", "Beto");
        String tokenInvitado = campo(sesion, "$.tokenAcceso");
        UUID beto = UUID.fromString(campo(sesion, "$.usuario.id"));
        DatosDePrueba.dispositivo(jdbc, beto, "telefono-beto");

        mvc.perform(delete("/api/familiares/" + beto).header("Authorization", bearer(tokenTitular)))
                .andExpect(status().isNoContent());

        // The token has not expired, but it no longer opens the household.
        mvc.perform(get("/api/hogar").header("Authorization", bearer(tokenInvitado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_MEMBRESIA"));
        mvc.perform(get("/api/familiares").header("Authorization", bearer(tokenTitular)))
                .andExpect(jsonPath("$", hasSize(1)));

        // Refreshing gives a session without that household.
        mvc.perform(post("/api/sesiones/refresco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenRefresco\":\"%s\"}".formatted(campo(sesion, "$.tokenRefresco"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hogarId").isEmpty());
    }

    @Test
    void elTitularNoSePuedeRetirar() throws Exception {
        mvc.perform(delete("/api/familiares/" + campo(titular, "$.usuario.id"))
                        .header("Authorization", bearer(tokenTitular)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_SE_PUEDE_RETIRAR_TITULAR"));
    }

    @Test
    void cadaHogarVeYRetiraSoloASusFamiliares() throws Exception {
        String sesionBeto = invitadoConCuentaNueva("beto@correo.pe", "Beto");
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");

        mvc.perform(get("/api/familiares").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Carla"));

        // Another household's owner cannot remove Beto from Ana's household.
        mvc.perform(delete("/api/familiares/" + campo(sesionBeto, "$.usuario.id"))
                        .header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/familiares").header("Authorization", bearer(tokenTitular)))
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
