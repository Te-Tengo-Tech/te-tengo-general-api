package tech.tetengo.api.hogares.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-10: contact order and wait time. */
class OrdenDeAvisoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID hogar;
    UUID ana;
    String tokenAna;
    UUID beto = UUID.randomUUID();
    String tokenBeto;

    @BeforeEach
    void hogar() throws Exception {
        String titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        hogar = UUID.fromString(campo(titular, "$.hogarId"));
        ana = UUID.fromString(campo(titular, "$.usuario.id"));
        tokenAna = campo(titular, "$.tokenAcceso");
        tokenBeto = JwtDePrueba.token(beto, hogar, Rol.INVITADO);
    }

    private ResultActions consultar(String token) throws Exception {
        return mvc.perform(get("/api/hogar/aviso").header("Authorization", bearer(token)));
    }

    private ResultActions configurar(String token, Object principal, Object secundario, Object espera)
            throws Exception {
        String cuerpo = "{\"principalId\":%s,\"secundarioId\":%s,\"esperaMinutos\":%s}"
                .formatted(texto(principal), texto(secundario), espera);
        return mvc.perform(put("/api/hogar/aviso")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private static String texto(Object valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    @Test
    void ca10_3_y_ca10_4_conUnSoloFamiliarEsElPrincipalSinSecundarioYSeEsperanCincoMinutos() throws Exception {
        consultar(tokenAna)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(ana.toString()))
                .andExpect(jsonPath("$.secundarioId").isEmpty())
                .andExpect(jsonPath("$.esperaMinutos").value(5));
    }

    @Test
    void ca10_1_y_ca10_2_elTitularDefineElOrdenYElTiempo() throws Exception {
        DatosDePrueba.membresia(jdbc, hogar, beto, Rol.INVITADO);

        configurar(tokenAna, beto, ana, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(beto.toString()))
                .andExpect(jsonPath("$.secundarioId").value(ana.toString()))
                .andExpect(jsonPath("$.esperaMinutos").value(10));

        consultar(tokenBeto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(beto.toString()))
                .andExpect(jsonPath("$.secundarioId").value(ana.toString()))
                .andExpect(jsonPath("$.esperaMinutos").value(10));
    }

    @Test
    void ca10_2_otroTiempoDeEsperaSeRechaza() throws Exception {
        configurar(tokenAna, ana, null, 7)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESPERA_INVALIDA"));
        configurar(tokenAna, ana, null, 3).andExpect(status().isOk());
        configurar(tokenAna, ana, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.esperaMinutos").isNotEmpty());
    }

    @Test
    void losContactosDebenSerFamiliaresDelHogar() throws Exception {
        configurar(tokenAna, ana, UUID.randomUUID(), 5)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTACTO_NO_ES_FAMILIAR"));

        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        configurar(tokenAna, UUID.fromString(campo(otro, "$.usuario.id")), null, 5)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTACTO_NO_ES_FAMILIAR"));
    }

    @Test
    void ca08_4_elInvitadoNoCambiaElOrden() throws Exception {
        DatosDePrueba.membresia(jdbc, hogar, beto, Rol.INVITADO);
        configurar(tokenBeto, beto, ana, 5)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
    }

    @Test
    void siSeRetiraAlSecundarioYaNoHayAQuienEscalar() throws Exception {
        DatosDePrueba.membresia(jdbc, hogar, beto, Rol.INVITADO);
        configurar(tokenAna, ana, beto, 5).andExpect(status().isOk());

        mvc.perform(delete("/api/familiares/" + beto).header("Authorization", bearer(tokenAna)))
                .andExpect(status().isNoContent());

        consultar(tokenAna)
                .andExpect(jsonPath("$.principalId").value(ana.toString()))
                .andExpect(jsonPath("$.secundarioId").isEmpty());
    }

    @Test
    void cadaHogarTieneSuPropiaConfiguracion() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        configurar(tokenAna, ana, null, 10).andExpect(status().isOk());

        consultar(campo(otro, "$.tokenAcceso"))
                .andExpect(jsonPath("$.principalId").value(campo(otro, "$.usuario.id")))
                .andExpect(jsonPath("$.esperaMinutos").value(5));
    }
}
