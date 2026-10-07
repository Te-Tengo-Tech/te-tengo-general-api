package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-24: live view access log. */
class AccesosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String tokenAna;
    String camara;
    UUID beto;
    String tokenBeto;

    @BeforeEach
    void camaraEnLinea() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        tokenAna = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        camara = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(registro, "$.token"))));
        ApiDePrueba.registrarCuenta(mvc, "beto@correo.pe", "secreta123", "Beto");
        beto = UUID.fromString(campo(ApiDePrueba.iniciarSesion(mvc, "beto@correo.pe", "secreta123"), "$.usuario.id"));
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, beto, Rol.INVITADO);
        tokenBeto = JwtDePrueba.token(beto, hogar, Rol.INVITADO);
    }

    private String verEnVivo(String token, String cuerpo, Duration duracion) throws Exception {
        String sesion = campo(
                mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.sesionId");
        reloj.avanzar(duracion);
        mvc.perform(delete("/api/vista-en-vivo/" + sesion).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        return sesion;
    }

    @Test
    void ca24_3_sinAccesosLaListaEstaVacia() throws Exception {
        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ca24_1_y_ca24_2_registraQuienCuandoYCuantoDelMasRecienteAlMasAntiguo() throws Exception {
        Instant primero = reloj.instant();
        verEnVivo(tokenAna, "{}", Duration.ofSeconds(90));
        reloj.avanzar(Duration.ofMinutes(10));
        Instant segundo = reloj.instant();
        verEnVivo(tokenBeto, "{\"alertaId\":\"%s\"}".formatted(UUID.randomUUID()), Duration.ofSeconds(40));

        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenBeto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].usuario.id").value(beto.toString()))
                .andExpect(jsonPath("$[0].usuario.nombre").value("Beto"))
                .andExpect(jsonPath("$[0].inicio").value(segundo.toString()))
                .andExpect(jsonPath("$[0].duracionSegundos").value(40))
                .andExpect(jsonPath("$[0].desdeAlerta").value(true))
                .andExpect(jsonPath("$[1].usuario.nombre").value("Ana"))
                .andExpect(jsonPath("$[1].inicio").value(primero.toString()))
                .andExpect(jsonPath("$[1].duracionSegundos").value(90))
                .andExpect(jsonPath("$[1].desdeAlerta").value(false));
    }

    @Test
    void cadaHogarVeSoloSusAccesos() throws Exception {
        verEnVivo(tokenAna, "{}", Duration.ofSeconds(30));
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
