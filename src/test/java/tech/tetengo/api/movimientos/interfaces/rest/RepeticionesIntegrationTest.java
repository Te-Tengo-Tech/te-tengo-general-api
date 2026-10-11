package tech.tetengo.api.movimientos.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.MovimientosApiDePrueba.confirmar;
import static tech.tetengo.api.support.MovimientosApiDePrueba.pedirSubida;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
import tech.tetengo.api.support.MovimientosApiDePrueba;

/** US-39: replaying a camera's movements, only within retention and with consent, and its access log. */
class RepeticionesIntegrationTest extends AbstractIntegrationTest {

    private static final DateTimeFormatter RUTA =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm").withZone(ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    UUID hogar;
    String camara;
    String agente;
    Instant inicio;

    @BeforeEach
    void movimientosGuardados() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, sesion, "Sala");
        camara = campo(registro, "$.camaraId");
        agente = campo(registro, "$.token");
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        inicio = reloj.instant().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 3; i++) {
            subir(inicio.plus(Duration.ofMinutes(5L * i)));
        }
    }

    private void subir(Instant desde) throws Exception {
        String subida = pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        movimientos.subir("movimientos/%s/%s/%s.parquet".formatted(hogar, camara, RUTA.format(desde)), 4000);
        confirmar(mvc, agente, campo(subida, "$.archivoId")).andExpect(status().isOk());
    }

    private ResultActions abrir(String token, Instant desde, Instant hasta) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/repeticiones")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desde\":\"%s\",\"hasta\":\"%s\"}".formatted(desde, hasta)));
    }

    @Test
    void ca39_1_laRepeticionDaLosArchivosDelMomentoConUrlsDeCortaDuracion() throws Exception {
        String repeticion = abrir(titular, inicio.plus(Duration.ofMinutes(3)), inicio.plus(Duration.ofMinutes(8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.habitacion").value("Sala"))
                .andExpect(jsonPath("$.guardadoEnLaNube").value(true))
                .andExpect(jsonPath("$.archivos.length()").value(2))
                .andExpect(jsonPath("$.archivos[0].fotogramas").value(2400))
                .andExpect(jsonPath("$.urlsExpiranEn").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(repeticion, "$.archivos[0].url")).startsWith("https://r2.prueba/movimientos/" + hogar);
        assertThat(Instant.parse(campo(repeticion, "$.disponiblesDesde"))).isEqualTo(inicio);

        // Moving along the timeline brings the next files in the same replay.
        mvc.perform(get("/api/repeticiones/" + campo(repeticion, "$.id") + "/archivos")
                        .header("Authorization", bearer(titular))
                        .param("desde", inicio.plus(Duration.ofMinutes(10)).toString())
                        .param("hasta", inicio.plus(Duration.ofMinutes(20)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivos.length()").value(1));
    }

    @Test
    void ca39_3_cadaRepeticionQuedaEnElRegistroDeAccesosConSuDuracion() throws Exception {
        String repeticion = abrir(titular, inicio, inicio.plus(Duration.ofMinutes(10)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        reloj.avanzar(Duration.ofSeconds(130));
        mvc.perform(delete("/api/repeticiones/" + campo(repeticion, "$.id")).header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(get("/api/accesos-repeticiones")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuario.nombre").value("Carmen"))
                .andExpect(jsonPath("$[0].camaraId").value(camara))
                .andExpect(jsonPath("$[0].habitacion").value("Sala"))
                .andExpect(jsonPath("$[0].momentoDesde").exists())
                .andExpect(jsonPath("$[0].duracionSegundos").value(130));
        // Closed: it serves no more files.
        mvc.perform(get("/api/repeticiones/" + campo(repeticion, "$.id") + "/archivos")
                        .header("Authorization", bearer(titular))
                        .param("desde", inicio.toString())
                        .param("hasta", inicio.plus(Duration.ofMinutes(5)).toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("REPETICION_NO_ENCONTRADA"));
    }

    @Test
    void ca39_4_fueraDelPlazoNoHayArchivosYSeDiceDesdeCuandoHayDatos() throws Exception {
        abrir(
                        titular,
                        inicio.minus(Duration.ofDays(3)),
                        inicio.minus(Duration.ofDays(3)).plus(Duration.ofMinutes(30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archivos").isEmpty())
                .andExpect(jsonPath("$.disponiblesDesde").exists());
    }

    @Test
    void ca39_4_sinConsentimientoNoSePuedeRepetir() throws Exception {
        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)));
        abrir(titular, inicio, inicio.plus(Duration.ofMinutes(10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
    }

    @Test
    void elRangoSeValidaYOtroMiembroNoUsaLaRepeticionAjena() throws Exception {
        abrir(titular, inicio, inicio.plus(Duration.ofMinutes(61)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.hasta").exists());
        String repeticion = abrir(titular, inicio, inicio.plus(Duration.ofMinutes(10)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        mvc.perform(get("/api/repeticiones/" + campo(repeticion, "$.id") + "/archivos")
                        .header("Authorization", bearer(JwtDePrueba.token(invitado, hogar, Rol.INVITADO)))
                        .param("desde", inicio.toString())
                        .param("hasta", inicio.plus(Duration.ofMinutes(5)).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void multitenancy_otraViviendaNoRepiteNiVeLosAccesos() throws Exception {
        abrir(titular, inicio, inicio.plus(Duration.ofMinutes(10)));
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");

        abrir(otro, inicio, inicio.plus(Duration.ofMinutes(10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
        mvc.perform(get("/api/accesos-repeticiones").header("Authorization", bearer(otro)))
                .andExpect(jsonPath("$").isEmpty());
    }
}
