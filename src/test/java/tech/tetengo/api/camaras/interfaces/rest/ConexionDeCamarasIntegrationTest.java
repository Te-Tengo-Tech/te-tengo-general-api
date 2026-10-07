package tech.tetengo.api.camaras.interfaces.rest;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.camaras.application.DetectarCamarasDesconectadas;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.PushDePrueba.Envio;

/** US-07: connection status of the cameras and its push notices. */
class ConexionDeCamarasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DetectarCamarasDesconectadas detectarDesconectadas;

    String sesionA;
    String agenteA;
    String camaraA;

    @BeforeEach
    void camara() throws Exception {
        sesionA = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(sesionA, "$.usuario.id")), "telefono-ana");
        String registro = ApiDePrueba.registrarAgente(
                mvc, DatosDePrueba.instalacion(jdbc, UUID.fromString(campo(sesionA, "$.hogarId"))), "Sala");
        agenteA = campo(registro, "$.token");
        camaraA = campo(registro, "$.camaraId");
    }

    private void senal(String tokenAgente) throws Exception {
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(tokenAgente)))
                .andExpect(status().isNoContent());
    }

    private List<Envio> esperarPush(TipoAviso tipo, int cantidad) {
        await().atMost(Duration.ofSeconds(5)).until(() -> push.deTipo(tipo).size() >= cantidad);
        return push.deTipo(tipo);
    }

    @Test
    void ca07_1_laCamaraQueTransmiteApareceEnLineaConLaHoraDeSuUltimaSenal() throws Exception {
        senal(agenteA);

        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].estadoConexion").value("EN_LINEA"))
                .andExpect(jsonPath("$[0].ultimaSenal").value(reloj.instant().toString()));
        // A brand-new camera coming online is not a "monitoring restored" notice.
        assertThat(push.deTipo(TipoAviso.CAMARA_RECONECTADA)).isEmpty();
    }

    @Test
    void ca07_2_trasTresSenalesPerdidasSeDesconectaYAvisaAlFamiliar() throws Exception {
        senal(agenteA);

        reloj.avanzar(Duration.ofSeconds(90));
        detectarDesconectadas.ejecutar();
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].estadoConexion").value("EN_LINEA"));

        reloj.avanzar(Duration.ofSeconds(1));
        detectarDesconectadas.ejecutar();
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].estadoConexion").value("DESCONECTADA"));

        Envio aviso = esperarPush(TipoAviso.CAMARA_DESCONECTADA, 1).getFirst();
        assertThat(aviso.tokens()).containsExactly("telefono-ana");
        assertThat(aviso.aviso().camaraId()).hasToString(camaraA);
        assertThat(aviso.aviso().habitacion()).isEqualTo("Sala");
        assertThat(aviso.aviso().ocurridaEn()).isEqualTo(reloj.instant());

        // Idempotent: running the job again sends nothing new.
        detectarDesconectadas.ejecutar();
        Thread.sleep(300);
        assertThat(push.deTipo(TipoAviso.CAMARA_DESCONECTADA)).hasSize(1);
    }

    @Test
    void ca07_3_alVolverATransmitirQuedaEnLineaYAvisaQueSeRestablecio() throws Exception {
        senal(agenteA);
        reloj.avanzar(Duration.ofMinutes(5));
        detectarDesconectadas.ejecutar();
        esperarPush(TipoAviso.CAMARA_DESCONECTADA, 1);

        senal(agenteA);

        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionA, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].estadoConexion").value("EN_LINEA"));
        Envio aviso = esperarPush(TipoAviso.CAMARA_RECONECTADA, 1).getFirst();
        assertThat(aviso.tokens()).containsExactly("telefono-ana");
        assertThat(aviso.aviso().habitacion()).isEqualTo("Sala");
    }

    @Test
    void laDesconexionDeUnHogarSoloSeAvisaASusMiembros() throws Exception {
        String sesionB = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(sesionB, "$.usuario.id")), "telefono-beto");
        String agenteB = campo(
                ApiDePrueba.registrarAgente(
                        mvc, DatosDePrueba.instalacion(jdbc, UUID.fromString(campo(sesionB, "$.hogarId"))), "Cocina"),
                "$.token");

        senal(agenteA);
        reloj.avanzar(Duration.ofSeconds(60));
        senal(agenteB);
        reloj.avanzar(Duration.ofSeconds(40));
        detectarDesconectadas.ejecutar();

        Envio aviso = esperarPush(TipoAviso.CAMARA_DESCONECTADA, 1).getFirst();
        assertThat(aviso.tokens()).containsExactly("telefono-ana");
        mvc.perform(get("/api/camaras").header("Authorization", bearer(campo(sesionB, "$.tokenAcceso"))))
                .andExpect(jsonPath("$[0].estadoConexion").value("EN_LINEA"));

        Thread.sleep(300);
        assertThat(push.enviados()).hasSize(1);
    }
}
