package tech.tetengo.api.cuentas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** US-03: password recovery. */
class RecuperacionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void cuenta() throws Exception {
        ApiDePrueba.registrarCuenta(mvc, "ana@correo.pe", "olvidada1", "Ana");
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mvc.perform(post("/api/recuperaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\"}".formatted(correo)));
    }

    private ResultActions confirmar(String token, String nueva) throws Exception {
        return mvc.perform(post("/api/recuperaciones/confirmacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"nuevaContrasena\":\"%s\"}".formatted(token, nueva)));
    }

    @Test
    void ca03_1_enviaElEnlaceAlCorreoRegistradoYPermiteCambiarLaContrasena() throws Exception {
        solicitar("Ana@Correo.pe").andExpect(status().isAccepted());

        assertThat(correos.enviadosA("ana@correo.pe")).hasSize(1);
        String token = correos.ultimoToken("ana@correo.pe");
        assertThat(correos.enviadosA("ana@correo.pe").getFirst().cuerpo())
                .contains("tetengo://app/nueva-contrasena?token=" + token);

        confirmar(token, "nueva-clave").andExpect(status().isNoContent());

        String sesion = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "nueva-clave");
        assertThat(campo(sesion, "$.tokenAcceso")).isNotBlank();
        mvc.perform(post("/api/sesiones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@correo.pe\",\"contrasena\":\"olvidada1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ca03_2_paraUnCorreoNoRegistradoRespondeIgualYNoEnviaNada() throws Exception {
        MvcResult existente = solicitar("ana@correo.pe").andReturn();
        MvcResult inexistente = solicitar("nadie@correo.pe")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""))
                .andReturn();

        assertThat(inexistente.getResponse().getStatus())
                .isEqualTo(existente.getResponse().getStatus());
        assertThat(inexistente.getResponse().getContentAsString())
                .isEqualTo(existente.getResponse().getContentAsString());
        assertThat(correos.enviadosA("nadie@correo.pe")).isEmpty();
    }

    @Test
    void ca03_3_unEnlaceDeMasDeTreintaMinutosSeRechazaYSePuedePedirOtro() throws Exception {
        solicitar("ana@correo.pe");
        String vencido = correos.ultimoToken("ana@correo.pe");

        reloj.avanzar(Duration.ofMinutes(30));
        confirmar(vencido, "nueva-clave")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_VENCIDO"));

        solicitar("ana@correo.pe").andExpect(status().isAccepted());
        confirmar(correos.ultimoToken("ana@correo.pe"), "nueva-clave").andExpect(status().isNoContent());
    }

    @Test
    void unEnlaceUsadoOInventadoNoSirve() throws Exception {
        solicitar("ana@correo.pe");
        String token = correos.ultimoToken("ana@correo.pe");
        confirmar(token, "nueva-clave").andExpect(status().isNoContent());

        confirmar(token, "otra-clave")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ENLACE_VENCIDO"));
        confirmar("inventado", "otra-clave").andExpect(status().isGone());
    }

    @Test
    void elCambioDeContrasenaCierraLasSesionesAbiertas() throws Exception {
        String sesion = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "olvidada1");
        solicitar("ana@correo.pe");
        confirmar(correos.ultimoToken("ana@correo.pe"), "nueva-clave").andExpect(status().isNoContent());

        mvc.perform(post("/api/sesiones/refresco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenRefresco\":\"%s\"}".formatted(campo(sesion, "$.tokenRefresco"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laNuevaContrasenaEsObligatoria() throws Exception {
        confirmar("algo", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nuevaContrasena").isNotEmpty());
    }
}
