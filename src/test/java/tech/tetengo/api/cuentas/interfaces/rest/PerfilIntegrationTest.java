package tech.tetengo.api.cuentas.interfaces.rest;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** API contract §13: the caller's own profile and password. */
class PerfilIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    UUID usuario;
    String token;

    @BeforeEach
    void cuenta() throws Exception {
        usuario = ApiDePrueba.registrarCuenta(mvc, "ana@correo.pe", "secreta123", "Ana");
        token = campo(ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123"), "$.tokenAcceso");
    }

    private ResultActions actualizar(String cuerpo) throws Exception {
        return mvc.perform(put("/api/cuenta")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions cambiarContrasena(String actual, String nueva) throws Exception {
        return mvc.perform(put("/api/cuenta/contrasena")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"%s\",\"nueva\":\"%s\"}".formatted(actual, nueva)));
    }

    @Test
    void sinHogarSeLeeElPropioPerfilSinFoto() throws Exception {
        mvc.perform(get("/api/cuenta").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.correo").value("ana@correo.pe"))
                .andExpect(jsonPath("$.telefono").isEmpty())
                .andExpect(jsonPath("$.parentesco").isEmpty())
                .andExpect(jsonPath("$.foto").isEmpty());
    }

    @Test
    void seEditaNombreTelefonoYParentescoYUnoOmitidoSeBorra() throws Exception {
        actualizar("{\"nombre\":\"Ana Pérez\",\"telefono\":\"+51 987 654 321\",\"parentesco\":\"Hija\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.telefono").value("+51 987 654 321"))
                .andExpect(jsonPath("$.parentesco").value("Hija"))
                .andExpect(jsonPath("$.correo").value("ana@correo.pe"));

        actualizar("{\"nombre\":\"Ana Pérez\",\"telefono\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").isEmpty())
                .andExpect(jsonPath("$.parentesco").isEmpty());

        mvc.perform(get("/api/cuenta").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"));
    }

    @Test
    void unPerfilInvalidoSeRechazaCampoPorCampo() throws Exception {
        actualizar("{\"nombre\":\"\",\"telefono\":\"abc\",\"parentesco\":\"%s\"}".formatted("x".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.telefono").exists())
                .andExpect(jsonPath("$.campos.parentesco").exists());
        actualizar("{\"nombre\":\"%s\"}".formatted("x".repeat(81)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
    }

    @Test
    void laContrasenaSeCambiaConLaActualYLaSesionSigueAbierta() throws Exception {
        String refresco = campo(ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123"), "$.tokenRefresco");

        cambiarContrasena("secreta123", "nueva-clave-9").andExpect(status().isNoContent());

        ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "nueva-clave-9");
        mvc.perform(post("/api/sesiones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@correo.pe\",\"contrasena\":\"secreta123\"}"))
                .andExpect(status().isUnauthorized());
        // Other sessions stay open.
        mvc.perform(post("/api/sesiones/refresco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenRefresco\":\"%s\"}".formatted(refresco)))
                .andExpect(status().isOk());
    }

    @Test
    void unaContrasenaActualIncorrectaOUnaNuevaDebilSeRechazan() throws Exception {
        cambiarContrasena("equivocada1", "nueva-clave-9")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));
        cambiarContrasena("secreta123", "sinnumero")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nueva").exists());
        cambiarContrasena("secreta123", "corta1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nueva").exists());
    }
}
