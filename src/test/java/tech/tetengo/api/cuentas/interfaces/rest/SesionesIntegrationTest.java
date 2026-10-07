package tech.tetengo.api.cuentas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** US-02: secure sign-in, lockout, refresh and sign-out. */
class SesionesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder decodificador;

    UUID usuario;

    @BeforeEach
    void cuenta() throws Exception {
        usuario = ApiDePrueba.registrarCuenta(mvc, "ana@correo.pe", "secreta123", "Ana");
    }

    private ResultActions iniciar(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/sesiones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)));
    }

    private ResultActions refrescar(String token) throws Exception {
        return mvc.perform(post("/api/sesiones/refresco")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenRefresco\":\"%s\"}".formatted(token)));
    }

    @Test
    void ca02_1_conCredencialesCorrectasEntregaUnaSesion() throws Exception {
        String sesion = iniciar("ANA@correo.pe", "secreta123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenAcceso").isNotEmpty())
                .andExpect(jsonPath("$.tokenRefresco").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(usuario.toString()))
                .andExpect(jsonPath("$.usuario.nombre").value("Ana"))
                .andExpect(jsonPath("$.usuario.correo").value("ana@correo.pe"))
                .andExpect(jsonPath("$.hogarId").isEmpty())
                .andExpect(jsonPath("$.rol").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Jwt acceso = decodificador.decode(campo(sesion, "$.tokenAcceso"));
        assertThat(acceso.getSubject()).isEqualTo(usuario.toString());
        assertThat(acceso.getClaimAsString("sid")).isNotBlank();
        assertThat(acceso.getHeaders()).containsEntry("alg", "RS256");
        // Access token lifetime: 60 minutes (API contract, implementation choice).
        assertThat(Duration.between(acceso.getIssuedAt(), acceso.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(60));
        assertThat(Instant.parse(campo(sesion, "$.expiraEn"))).isEqualTo(acceso.getExpiresAt());

        // The token gives access to the application.
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesion, "$.tokenAcceso"))))
                .andExpect(status().isOk());
    }

    @Test
    void ca02_2_conCredencialesIncorrectasNiegaElAcceso() throws Exception {
        iniciar("ana@correo.pe", "equivocada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        iniciar("nadie@correo.pe", "secreta123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void ca02_3_cincoFallosSeguidosBloqueanLaCuentaQuinceMinutos() throws Exception {
        for (int i = 0; i < 4; i++) {
            iniciar("ana@correo.pe", "equivocada").andExpect(status().isUnauthorized());
        }
        Instant bloqueadaHasta = reloj.instant().plus(Duration.ofMinutes(15));
        iniciar("ana@correo.pe", "equivocada")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"))
                .andExpect(jsonPath("$.bloqueadaHasta").value(bloqueadaHasta.toString()));

        reloj.avanzar(Duration.ofMinutes(14));
        iniciar("ana@correo.pe", "secreta123")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.bloqueadaHasta").value(bloqueadaHasta.toString()));

        reloj.avanzar(Duration.ofMinutes(1));
        iniciar("ana@correo.pe", "secreta123").andExpect(status().isOk());
    }

    @Test
    void ca02_4_cerrarSesionExigeAutenticarseDeNuevo() throws Exception {
        String sesion = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123");

        mvc.perform(delete("/api/sesiones/actual").header("Authorization", bearer(campo(sesion, "$.tokenAcceso"))))
                .andExpect(status().isNoContent());

        refrescar(campo(sesion, "$.tokenRefresco"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void cerrarSesionRequiereUnToken() throws Exception {
        mvc.perform(delete("/api/sesiones/actual")).andExpect(status().isUnauthorized());
    }

    @Test
    void elRefrescoEntregaTokensNuevosYRotaElDeRefresco() throws Exception {
        String sesion = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123");
        String anterior = campo(sesion, "$.tokenRefresco");

        String nueva = refrescar(anterior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(usuario.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(nueva, "$.tokenRefresco")).isNotEqualTo(anterior);
        assertThat(decodificador.decode(campo(nueva, "$.tokenAcceso")).getClaimAsString("sid"))
                .isEqualTo(decodificador.decode(campo(sesion, "$.tokenAcceso")).getClaimAsString("sid"));

        refrescar(anterior)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
        refrescar(campo(nueva, "$.tokenRefresco")).andExpect(status().isOk());
    }

    @Test
    void elTokenDeRefrescoVenceALosTreintaDias() throws Exception {
        String sesion = ApiDePrueba.iniciarSesion(mvc, "ana@correo.pe", "secreta123");
        reloj.avanzar(Duration.ofDays(30));
        refrescar(campo(sesion, "$.tokenRefresco"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void unTokenDeRefrescoDesconocidoNoSirve() throws Exception {
        refrescar("inventado")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }
}
