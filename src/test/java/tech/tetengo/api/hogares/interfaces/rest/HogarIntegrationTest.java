package tech.tetengo.api.hogares.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-04: the household and its older adult; household switching. */
class HogarIntegrationTest extends AbstractIntegrationTest {

    private static final String ADULTO_MAYOR =
            "{\"adultoMayor\":{\"nombre\":\"Rosa Quispe\",\"edad\":78,\"direccion\":\"Jr. Puno 123, Lima\","
                    + "\"convivencia\":\"SOLO\",\"telefono\":\"987 654 321\"}}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    UUID ana;
    String tokenSinHogar;

    @BeforeEach
    void cuenta() throws Exception {
        ana = ApiDePrueba.registrarCuenta(mvc, "ana@correo.pe", "secreta123", "Ana");
        tokenSinHogar = campo(ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123"), "$.tokenAcceso");
    }

    private ResultActions registrarHogar(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/api/hogar")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String crearHogar() throws Exception {
        return registrarHogar(tokenSinHogar, ADULTO_MAYOR)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca04_1_creaElPerfilYLoAsociaALaCuentaComoTitular() throws Exception {
        String sesion = crearHogar();
        String hogarId = campo(sesion, "$.hogarId");
        assertThat(hogarId).isNotBlank();
        assertThat(campo(sesion, "$.rol")).isEqualTo("TITULAR");
        assertThat(campo(sesion, "$.usuario.id")).isEqualTo(ana.toString());

        var acceso = decodificador.decode(campo(sesion, "$.tokenAcceso"));
        assertThat(acceso.getClaimAsString("hogar_id")).isEqualTo(hogarId);
        assertThat(acceso.getClaimAsString("rol")).isEqualTo("TITULAR");

        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(sesion, "$.tokenAcceso"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hogarId").value(hogarId))
                .andExpect(jsonPath("$.adultoMayor.nombre").value("Rosa Quispe"))
                .andExpect(jsonPath("$.adultoMayor.edad").value(78))
                .andExpect(jsonPath("$.adultoMayor.direccion").value("Jr. Puno 123, Lima"))
                .andExpect(jsonPath("$.adultoMayor.convivencia").value("SOLO"))
                .andExpect(jsonPath("$.adultoMayor.telefono").value("987 654 321"))
                .andExpect(jsonPath("$.rol").value("TITULAR"))
                .andExpect(jsonPath("$.consentimiento").isEmpty());

        // Later sign-ins land in the household.
        String nueva = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123");
        assertThat(campo(nueva, "$.hogarId")).isEqualTo(hogarId);
        assertThat(campo(nueva, "$.rol")).isEqualTo("TITULAR");
    }

    @Test
    void ca04_2_unaCuentaGestionaUnSoloAdultoMayor() throws Exception {
        String sesion = crearHogar();
        registrarHogar(campo(sesion, "$.tokenAcceso"), ADULTO_MAYOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HOGAR_YA_REGISTRADO"));
        registrarHogar(tokenSinHogar, ADULTO_MAYOR).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from hogares", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ca04_3_resaltaLosCamposObligatoriosVacios() throws Exception {
        registrarHogar(tokenSinHogar, "{\"adultoMayor\":{\"nombre\":\" \",\"direccion\":\"\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos['adultoMayor.nombre']").isNotEmpty())
                .andExpect(jsonPath("$.campos['adultoMayor.edad']").value("Escribe su edad."))
                .andExpect(jsonPath("$.campos['adultoMayor.direccion']").isNotEmpty())
                .andExpect(jsonPath("$.campos['adultoMayor.convivencia']").isNotEmpty());
        registrarHogar(tokenSinHogar, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.adultoMayor").isNotEmpty());
        registrarHogar(
                        tokenSinHogar,
                        "{\"adultoMayor\":{\"nombre\":\"Rosa\",\"edad\":78,\"direccion\":\"Lima\",\"convivencia\":\"OTRA\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['adultoMayor.convivencia']").isNotEmpty());
        for (int edad : new int[] {49, 121}) {
            registrarHogar(
                            tokenSinHogar,
                            "{\"adultoMayor\":{\"nombre\":\"Rosa\",\"edad\":%d,\"direccion\":\"Lima\",\"convivencia\":\"SOLO\"}}"
                                    .formatted(edad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos['adultoMayor.edad']").value("Escribe una edad válida, en años."));
        }
        registrarHogar(
                        tokenSinHogar,
                        "{\"adultoMayor\":{\"nombre\":\"Rosa\",\"edad\":78,\"direccion\":\"Lima\","
                                + "\"convivencia\":\"SOLO\",\"telefono\":\"llamar\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['adultoMayor.telefono']").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from hogares", Integer.class))
                .isZero();
    }

    @Test
    void elTitularEditaElPerfilYElInvitadoNo() throws Exception {
        String sesion = crearHogar();
        UUID hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        String cambio =
                "{\"nombre\":\"Rosa Quispe de Ríos\",\"edad\":79,\"direccion\":\"Jr. Puno 456\",\"convivencia\":\"CON_CUIDADOR\"}";

        mvc.perform(put("/api/hogar/adulto-mayor")
                        .header("Authorization", bearer(campo(sesion, "$.tokenAcceso")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa Quispe de Ríos"))
                .andExpect(jsonPath("$.edad").value(79))
                .andExpect(jsonPath("$.convivencia").value("CON_CUIDADOR"))
                // The profile is replaced as a whole: without a phone, the old one is cleared.
                .andExpect(jsonPath("$.telefono").isEmpty());

        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(put("/api/hogar/adulto-mayor")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));

        mvc.perform(get("/api/hogar").header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("INVITADO"))
                .andExpect(jsonPath("$.adultoMayor.nombre").value("Rosa Quispe de Ríos"))
                .andExpect(jsonPath("$.adultoMayor.edad").value(79));
    }

    @Test
    void listaLosHogaresDelUsuarioYPermiteCambiarDeHogar() throws Exception {
        String sesion = crearHogar();
        UUID propio = UUID.fromString(campo(sesion, "$.hogarId"));
        UUID ajeno = DatosDePrueba.hogar(jdbc, UUID.randomUUID(), "Jorge");
        DatosDePrueba.membresia(jdbc, ajeno, ana, Rol.INVITADO);
        String token = campo(sesion, "$.tokenAcceso");

        mvc.perform(get("/api/hogares").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].hogarId").value(propio.toString()))
                .andExpect(jsonPath("$[0].nombreAdultoMayor").value("Rosa Quispe"))
                .andExpect(jsonPath("$[0].rol").value("TITULAR"))
                .andExpect(jsonPath("$[1].hogarId").value(ajeno.toString()))
                .andExpect(jsonPath("$[1].nombreAdultoMayor").value("Jorge"))
                .andExpect(jsonPath("$[1].rol").value("INVITADO"));

        String otra = mvc.perform(post("/api/sesiones/hogar")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hogarId\":\"%s\"}".formatted(ajeno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hogarId").value(ajeno.toString()))
                .andExpect(jsonPath("$.rol").value("INVITADO"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(otra, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.adultoMayor.nombre").value("Jorge"));

        // The session it replaced can no longer be refreshed.
        mvc.perform(post("/api/sesiones/refresco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenRefresco\":\"%s\"}".formatted(campo(sesion, "$.tokenRefresco"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noSePuedeCambiarAUnHogarAjeno() throws Exception {
        UUID ajeno = DatosDePrueba.hogar(jdbc);
        mvc.perform(post("/api/sesiones/hogar")
                        .header("Authorization", bearer(tokenSinHogar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hogarId\":\"%s\"}".formatted(ajeno)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_MEMBRESIA"));
    }

    @Test
    void cadaHogarVeYCambiaSoloSuPropioPerfil() throws Exception {
        UUID titularA = UUID.randomUUID();
        UUID titularB = UUID.randomUUID();
        UUID hogarA = DatosDePrueba.hogar(jdbc, titularA, "Rosa");
        UUID hogarB = DatosDePrueba.hogar(jdbc, titularB, "Jorge");

        mvc.perform(put("/api/hogar/adulto-mayor")
                        .header("Authorization", bearer(JwtDePrueba.token(titularA, hogarA, Rol.TITULAR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Rosa Cambiada\",\"edad\":80,\"direccion\":\"Lima\","
                                + "\"convivencia\":\"SOLO\",\"telefono\":\"+51 987 654 321\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/hogar").header("Authorization", bearer(JwtDePrueba.token(titularA, hogarA, Rol.TITULAR))))
                .andExpect(jsonPath("$.adultoMayor.edad").value(80))
                .andExpect(jsonPath("$.adultoMayor.telefono").value("+51 987 654 321"));
        // Household B keeps its own profile: no age or phone of A leaks into it.
        mvc.perform(get("/api/hogar").header("Authorization", bearer(JwtDePrueba.token(titularB, hogarB, Rol.TITULAR))))
                .andExpect(jsonPath("$.hogarId").value(hogarB.toString()))
                .andExpect(jsonPath("$.adultoMayor.nombre").value("Jorge"))
                .andExpect(jsonPath("$.adultoMayor.edad").isEmpty())
                .andExpect(jsonPath("$.adultoMayor.telefono").isEmpty());

        // A token for a household the user does not belong to gives no access.
        mvc.perform(get("/api/hogar").header("Authorization", bearer(JwtDePrueba.token(titularA, hogarB, Rol.TITULAR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_MEMBRESIA"));
    }

    @Test
    void sinHogarNoHayPerfilQueVer() throws Exception {
        mvc.perform(get("/api/hogar").header("Authorization", bearer(tokenSinHogar)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_MEMBRESIA"));
        mvc.perform(get("/api/hogares").header("Authorization", bearer(tokenSinHogar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
