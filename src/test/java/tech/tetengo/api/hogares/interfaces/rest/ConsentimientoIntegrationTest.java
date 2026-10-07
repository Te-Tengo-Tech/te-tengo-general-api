package tech.tetengo.api.hogares.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.time.Duration;
import java.util.List;
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

/** US-05: consent of the older adult. */
class ConsentimientoIntegrationTest extends AbstractIntegrationTest {

    private static final String ACEPTADO =
            "{\"otorgadoPor\":\"Rosa Quispe\",\"aceptadoPorAdultoMayor\":true,\"vistaEnVivoAceptada\":true}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String sesion;
    String token;
    UUID hogar;

    @BeforeEach
    void hogar() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
    }

    private ResultActions otorgar(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/api/hogar/consentimiento")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private List<Boolean> capturaDe(UUID hogar) {
        return jdbc.queryForList(
                "select consentimiento_vigente from estados_de_captura where hogar_id = ?", Boolean.class, hogar);
    }

    @Test
    void ca05_1_y_ca05_3_registraElConsentimientoConSuFechaYHabilitaLaCaptura() throws Exception {
        otorgar(token, ACEPTADO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otorgadoEn").value(reloj.instant().toString()))
                .andExpect(jsonPath("$.otorgadoPor").value("Rosa Quispe"))
                .andExpect(jsonPath("$.registradoPor.id").value(campo(sesion, "$.usuario.id")))
                .andExpect(jsonPath("$.registradoPor.nombre").value("Ana"))
                .andExpect(jsonPath("$.vistaEnVivoAceptada").value(true))
                .andExpect(jsonPath("$.vigente").value(true));

        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otorgadoEn").value(reloj.instant().toString()))
                .andExpect(jsonPath("$.vigente").value(true));
        mvc.perform(get("/api/hogar").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.consentimiento.otorgadoPor").value("Rosa Quispe"));

        // camaras hears the event and enables capture for this household.
        await().atMost(Duration.ofSeconds(5)).until(() -> capturaDe(hogar).equals(List.of(true)));
    }

    @Test
    void ca05_2_sinConsentimientoLaAppLoSolicitaYNoHayCaptura() throws Exception {
        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
        mvc.perform(get("/api/hogar").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.consentimiento").isEmpty());
        assertThat(capturaDe(hogar)).isEmpty();
    }

    @Test
    void ca05_4_soloSeRegistraSiElAdultoMayorLoAcepta() throws Exception {
        otorgar(token, "{\"otorgadoPor\":\"Rosa\",\"aceptadoPorAdultoMayor\":false,\"vistaEnVivoAceptada\":true}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_NO_ACEPTADO"));
        otorgar(token, "{\"otorgadoPor\":\"Rosa\",\"aceptadoPorAdultoMayor\":true,\"vistaEnVivoAceptada\":false}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_NO_ACEPTADO"));
        otorgar(token, "{\"aceptadoPorAdultoMayor\":true,\"vistaEnVivoAceptada\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.otorgadoPor").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from consentimientos", Integer.class))
                .isZero();
    }

    @Test
    void soloElTitularLoRegistraPeroTodosLosMiembrosLoVen() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String tokenInvitado = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);

        otorgar(tokenInvitado, ACEPTADO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));

        otorgar(token, ACEPTADO).andExpect(status().isCreated());
        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(tokenInvitado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otorgadoPor").value("Rosa Quispe"));
    }

    @Test
    void elConsentimientoDeUnHogarNoSeVeNiActivaLaCapturaEnOtro() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        UUID hogarB = UUID.fromString(campo(otro, "$.hogarId"));

        otorgar(token, ACEPTADO).andExpect(status().isCreated());

        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(status().isNotFound());
        await().atMost(Duration.ofSeconds(5)).until(() -> capturaDe(hogar).equals(List.of(true)));
        assertThat(capturaDe(hogarB)).isEmpty();
        assertThat(jdbc.queryForObject("select hogar_id from consentimientos", UUID.class))
                .isEqualTo(hogar);
    }
}
