package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.hamcrest.Matchers.contains;
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
import org.awaitility.Awaitility;
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

/** US-41: «Ver todas» is one entry of the access log with the rooms seen (CA-41.6). */
class MosaicosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String tokenAna;
    String sala;
    String cocina;
    String pasillo;
    String tokenBeto;

    @BeforeEach
    void tresCamarasEnLinea() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        tokenAna = campo(titular, "$.tokenAcceso");
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        sala = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(registro, "$.token"))));
        cocina = otraCamara(hogar, "Cocina");
        pasillo = otraCamara(hogar, "Pasillo");
        ApiDePrueba.registrarCuenta(mvc, "beto@correo.pe", "secreta123", "Beto");
        UUID beto =
                UUID.fromString(campo(ApiDePrueba.iniciarSesion(mvc, "beto@correo.pe", "secreta123"), "$.usuario.id"));
        DatosDePrueba.membresia(jdbc, hogar, beto, Rol.INVITADO);
        tokenBeto = JwtDePrueba.token(beto, hogar, Rol.INVITADO);
    }

    private String otraCamara(UUID hogar, String habitacion) throws Exception {
        String registro = ApiDePrueba.registrarAgente(mvc, DatosDePrueba.instalacion(jdbc, hogar), habitacion);
        String token = campo(registro, "$.token");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(token)));
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> campo(
                                mvc.perform(get("/api/agente/estado-captura").header("Authorization", bearer(token)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(),
                                "$.capturaPermitida")
                        .equals("true"));
        return campo(registro, "$.camaraId");
    }

    private String abrirMosaico(String token) throws Exception {
        return campo(
                mvc.perform(post("/api/vista-en-vivo/mosaicos").header("Authorization", bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.mosaicoId");
    }

    private ResultActions verEnVivo(String token, String camara, String cuerpo) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String sesion(String token, String camara, String mosaico) throws Exception {
        return campo(
                verEnVivo(token, camara, "{\"mosaicoId\":\"%s\"}".formatted(mosaico))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.sesionId");
    }

    private void cerrar(String token, String sesion) throws Exception {
        mvc.perform(delete("/api/vista-en-vivo/" + sesion).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void ca41_6_unSoloAccesoConLasHabitacionesVistasYCuantoDuro() throws Exception {
        Instant inicio = reloj.instant();
        String mosaico = abrirMosaico(tokenAna);
        String enSala = sesion(tokenAna, sala, mosaico);
        String enCocina = sesion(tokenAna, cocina, mosaico);
        reloj.avanzar(Duration.ofSeconds(30));
        // Another page: the Sala stops and the Pasillo starts, in the same access.
        cerrar(tokenAna, enSala);
        String enPasillo = sesion(tokenAna, pasillo, mosaico);
        reloj.avanzar(Duration.ofSeconds(60));
        cerrar(tokenAna, enCocina);
        cerrar(tokenAna, enPasillo);

        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenBeto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mosaico").value(true))
                .andExpect(jsonPath("$[0].usuario.nombre").value("Ana"))
                .andExpect(jsonPath("$[0].camaraIds", contains(sala, cocina, pasillo)))
                .andExpect(jsonPath("$[0].camaraId").value(sala))
                .andExpect(jsonPath("$[0].inicio").value(inicio.toString()))
                .andExpect(jsonPath("$[0].duracionSegundos").value(90))
                .andExpect(jsonPath("$[0].vistaPrevia").value(false));
    }

    @Test
    void ca41_6_lasCamarasNoDisponiblesNoSeCuentanYLaPantallaCompletaEsElMismoAcceso() throws Exception {
        mvc.perform(post("/api/camaras/" + pasillo + "/pausa")
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion\":\"MIN_30\"}"))
                .andExpect(status().isOk());
        String mosaico = abrirMosaico(tokenAna);
        String enSala = sesion(tokenAna, sala, mosaico);
        verEnVivo(tokenAna, pasillo, "{\"mosaicoId\":\"%s\"}".formatted(mosaico))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_EN_PAUSA"));
        reloj.avanzar(Duration.ofSeconds(20));
        cerrar(tokenAna, enSala);
        // The Sala full screen: only it streams, still within the same access.
        String completa = sesion(tokenAna, sala, mosaico);
        reloj.avanzar(Duration.ofSeconds(40));
        cerrar(tokenAna, completa);

        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenAna)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].camaraIds", contains(sala)))
                .andExpect(jsonPath("$[0].mosaico").value(true))
                .andExpect(jsonPath("$[0].duracionSegundos").value(60));
    }

    @Test
    void unaVistaSueltaSigueSiendoSuPropioAccesoDelMasRecienteAlMasAntiguo() throws Exception {
        String mosaico = abrirMosaico(tokenAna);
        cerrar(tokenAna, sesion(tokenAna, sala, mosaico));
        reloj.avanzar(Duration.ofMinutes(5));
        String suelta = campo(
                verEnVivo(tokenBeto, cocina, "{}")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.sesionId");
        reloj.avanzar(Duration.ofSeconds(15));
        cerrar(tokenBeto, suelta);

        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenAna)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].usuario.nombre").value("Beto"))
                .andExpect(jsonPath("$[0].mosaico").value(false))
                .andExpect(jsonPath("$[0].camaraIds", contains(cocina)))
                .andExpect(jsonPath("$[0].duracionSegundos").value(15))
                .andExpect(jsonPath("$[1].mosaico").value(true));
    }

    @Test
    void unMosaicoDeOtroMiembroODesconocidoNoSeAcepta() throws Exception {
        String deAna = abrirMosaico(tokenAna);
        verEnVivo(tokenBeto, sala, "{\"mosaicoId\":\"%s\"}".formatted(deAna))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.mosaicoId").exists());
        verEnVivo(tokenAna, sala, "{\"mosaicoId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.mosaicoId").exists());
    }

    @Test
    void cadaHogarVeSoloSusMosaicos() throws Exception {
        String mosaico = abrirMosaico(tokenAna);
        cerrar(tokenAna, sesion(tokenAna, sala, mosaico));
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        String tokenCarla = campo(otro, "$.tokenAcceso");
        String camaraDeCarla = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Sala"), "$.camaraId");

        mvc.perform(get("/api/accesos-vista-en-vivo").header("Authorization", bearer(tokenCarla)))
                .andExpect(jsonPath("$", hasSize(0)));
        // Carla cannot add her camera to Ana's mosaic: it is not in her household.
        verEnVivo(tokenCarla, camaraDeCarla, "{\"mosaicoId\":\"%s\"}".formatted(mosaico))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.mosaicoId").exists());
    }
}
