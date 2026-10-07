package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.alertas.application.ReintentarAvisos;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.push.ContenidoDelAviso;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.PushDePrueba.Envio;

/** US-16 and US-17: push alerts to every member device, with retries (CA-16.4). */
class PushDeAlertasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReintentarAvisos reintentarAvisos;

    String titular;
    String agente;
    UUID camara;
    UUID invitado;
    Instant cuando;

    @BeforeEach
    void hogarConDosFamiliares() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = UUID.fromString(campo(registro, "$.camaraId"));
        invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, UUID.fromString(campo(titular, "$.hogarId")), invitado, Rol.INVITADO);
        registrarDispositivo(campo(titular, "$.tokenAcceso"), "telefono-ana", "ANDROID")
                .andExpect(status().isCreated());
        registrarDispositivo(
                        JwtDePrueba.token(invitado, UUID.fromString(campo(titular, "$.hogarId")), Rol.INVITADO),
                        "telefono-beto",
                        "IOS")
                .andExpect(status().isCreated());
        cuando = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        nombrarAdultoMayor(titular, "Rosa Huamán");
    }

    private void nombrarAdultoMayor(String sesion, String nombre) {
        jdbc.update(
                "update hogares set adulto_mayor_nombre = ? where id = ?",
                nombre,
                UUID.fromString(campo(sesion, "$.hogarId")));
    }

    private static final Detalle ROSA = new Detalle("Rosa", null, null, null, null);

    private ResultActions registrarDispositivo(String token, String tokenPush, String plataforma) throws Exception {
        return mvc.perform(post("/api/dispositivos")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenPush\":\"%s\",\"plataforma\":\"%s\"}".formatted(tokenPush, plataforma)));
    }

    private String evento(String tipo, Instant ocurridoEn) throws Exception {
        return campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), tipo, ocurridoEn)
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
    }

    private Instant notificadaEn(String alertaId) {
        Timestamp t = jdbc.queryForObject(
                "select notificada_en from alertas where id = ?", Timestamp.class, UUID.fromString(alertaId));
        return t == null ? null : t.toInstant();
    }

    @Test
    void ca16_1_laCaidaSeNotificaATodosLosFamiliaresDuranteLaMismaPeticionConHabitacionYHora() throws Exception {
        String alertaId = evento("caida", cuando);

        // No waiting: the push was requested before the agent got its answer (< 10 s, CA-11.3).
        Envio envio = push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst();
        assertThat(envio.tokens()).containsExactlyInAnyOrder("telefono-ana", "telefono-beto");
        assertThat(envio.aviso())
                .isEqualTo(new Aviso(TipoAviso.ALERTA_CAIDA, UUID.fromString(alertaId), camara, "Sala", cuando, ROSA));
        // The prototype's notice (screen 44), with the household's older adult.
        assertThat(ContenidoDelAviso.de(envio.aviso()))
                .extracting(ContenidoDelAviso::titulo, ContenidoDelAviso::etiqueta)
                .containsExactly("Posible caída de Rosa en la Sala", "URGENTE · CAÍDA");
        assertThat(notificadaEn(alertaId)).isEqualTo(reloj.instant());
    }

    @Test
    void ca17_1_elMovimientoInestableSeNotificaConSuPropioTipo() throws Exception {
        String alertaId = evento("movimiento_inestable", cuando);
        Envio envio = push.deTipo(TipoAviso.ALERTA_MOVIMIENTO_INESTABLE).getFirst();
        assertThat(envio.aviso().alertaId()).hasToString(alertaId);
        assertThat(envio.aviso().habitacion()).isEqualTo("Sala");
    }

    @Test
    void ca17_3_laEvolucionACaidaSeNotificaComoCaida() throws Exception {
        String alertaId = evento("movimiento_inestable", cuando);
        evento("caida", cuando.plusSeconds(2));
        Envio envio = push.deTipo(TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA).getFirst();
        assertThat(envio.aviso().alertaId()).hasToString(alertaId);
        assertThat(envio.aviso().ocurridaEn()).isEqualTo(cuando.plusSeconds(2));
        // «Empezó como movimiento inestable a las …» (screen 51): the time the alert began.
        assertThat(envio.aviso().detalle()).isEqualTo(new Detalle("Rosa", TipoDeAlerta.CAIDA, cuando, null, null));
    }

    @Test
    void ca13_1_laConfirmacionSeNotifica() throws Exception {
        String alertaId = evento("caida", cuando);
        evento("caida_confirmada", cuando.plusSeconds(30));
        assertThat(push.deTipo(TipoAviso.CAIDA_CONFIRMADA).getFirst().aviso().alertaId())
                .hasToString(alertaId);
    }

    @Test
    void ca15_3_laDeteccionNoConfiableSeAvisaUnaSolaVezMientrasDure() throws Exception {
        evento("deteccion_no_confiable", cuando);
        evento("deteccion_no_confiable", cuando.plusSeconds(300));
        Envio envio = push.deTipo(TipoAviso.DETECCION_NO_CONFIABLE).getFirst();
        assertThat(push.deTipo(TipoAviso.DETECCION_NO_CONFIABLE)).hasSize(1);
        assertThat(envio.aviso().camaraId()).isEqualTo(camara);
        assertThat(envio.aviso().habitacion()).isEqualTo("Sala");
        assertThat(envio.aviso().alertaId()).isNull();
    }

    @Test
    void ca16_4_siElServicioDePushFallaLaAlertaSeGuardaYSeReintenta() throws Exception {
        push.simularCaida(true);
        String alertaId = evento("caida", cuando);
        assertThat(alertaId).isNotBlank();
        assertThat(notificadaEn(alertaId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from avisos_pendientes", Integer.class))
                .isEqualTo(1);

        // Not before the retry interval.
        reintentarAvisos.ejecutar();
        assertThat(push.enviados()).isEmpty();

        reloj.avanzar(Duration.ofSeconds(15));
        reintentarAvisos.ejecutar();
        assertThat(jdbc.queryForObject("select intentos from avisos_pendientes", Integer.class))
                .isEqualTo(2);

        push.simularCaida(false);
        reloj.avanzar(Duration.ofSeconds(15));
        reintentarAvisos.ejecutar();

        Envio envio = push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst();
        assertThat(envio.tokens()).containsExactlyInAnyOrder("telefono-ana", "telefono-beto");
        assertThat(envio.aviso().alertaId()).hasToString(alertaId);
        assertThat(envio.aviso().detalle()).isEqualTo(ROSA);
        assertThat(notificadaEn(alertaId)).isEqualTo(reloj.instant());
        assertThat(jdbc.queryForObject("select count(*) from avisos_pendientes", Integer.class))
                .isZero();

        reintentarAvisos.ejecutar();
        assertThat(push.enviados()).hasSize(1);
    }

    @Test
    void unDispositivoEliminadoDejaDeRecibirAlertas() throws Exception {
        mvc.perform(delete("/api/dispositivos/telefono-ana")
                        .header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(status().isNoContent());
        evento("caida", cuando);
        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst().tokens()).containsExactly("telefono-beto");
    }

    @Test
    void unTelefonoRegistradoPorOtraCuentaPasaAEsaCuenta() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        registrarDispositivo(campo(otro, "$.tokenAcceso"), "telefono-ana", "ANDROID")
                .andExpect(status().isCreated());
        evento("caida", cuando);
        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst().tokens()).containsExactly("telefono-beto");
    }

    @Test
    void laPlataformaDebeSerAndroidOIos() throws Exception {
        registrarDispositivo(campo(titular, "$.tokenAcceso"), "x", "WINDOWS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.plataforma").isNotEmpty());
    }

    @Test
    void lasAlertasDeUnHogarSoloLleganASusFamiliares() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        nombrarAdultoMayor(otro, "Juana Quispe");
        registrarDispositivo(campo(otro, "$.tokenAcceso"), "telefono-carla", "ANDROID");
        String agenteB = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Cocina"), "$.token");

        evento("caida", cuando);
        enviarEvento(mvc, agenteB, UUID.randomUUID(), "caida", cuando).andExpect(status().isAccepted());

        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA))
                .extracting(Envio::tokens)
                .containsExactlyInAnyOrder(
                        java.util.List.of("telefono-ana", "telefono-beto"), java.util.List.of("telefono-carla"));
        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA))
                .filteredOn(e -> e.tokens().contains("telefono-carla"))
                .extracting(e -> e.aviso().habitacion())
                .containsExactly("Cocina");
        // Each household's notice names its own older adult.
        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA))
                .extracting(
                        e -> e.tokens().contains("telefono-carla"),
                        e -> e.aviso().detalle().adultoMayor())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(false, "Rosa"),
                        org.assertj.core.groups.Tuple.tuple(true, "Juana"));
    }

    private Boolean activo(String tokenPush) {
        return jdbc.queryForObject("select activo from dispositivos where token_push = ?", Boolean.class, tokenPush);
    }

    private String referencia(String tokenPush) {
        return jdbc.queryForObject(
                "select referencia_push from dispositivos where token_push = ?", String.class, tokenPush);
    }

    @Test
    void unTokenRechazadoPorElServicioDesactivaSuDispositivoHastaQueSeRegistreOtraVez() throws Exception {
        push.rechazarToken("telefono-beto");
        String alertaId = evento("caida", cuando);

        assertThat(activo("telefono-beto")).isFalse();
        assertThat(activo("telefono-ana")).isTrue();
        assertThat(notificadaEn(alertaId)).isEqualTo(reloj.instant());

        evento("caida_confirmada", cuando.plusSeconds(30));
        assertThat(push.deTipo(TipoAviso.CAIDA_CONFIRMADA).getFirst().tokens()).containsExactly("telefono-ana");

        registrarDispositivo(
                        JwtDePrueba.token(invitado, UUID.fromString(campo(titular, "$.hogarId")), Rol.INVITADO),
                        "telefono-beto",
                        "IOS")
                .andExpect(status().isCreated());
        assertThat(activo("telefono-beto")).isTrue();
    }

    @Test
    void siElServicioRechazaTodosLosTokensNoHayAQuienAvisarYNoSeReintenta() throws Exception {
        push.rechazarToken("telefono-ana");
        push.rechazarToken("telefono-beto");

        String alertaId = evento("caida", cuando);

        assertThat(notificadaEn(alertaId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from avisos_pendientes", Integer.class))
                .isZero();
        evento("caida_confirmada", cuando.plusSeconds(30));
        assertThat(push.deTipo(TipoAviso.CAIDA_CONFIRMADA)).isEmpty();
    }

    @Test
    void laDireccionDelProveedorSeGuardaConElDispositivoYSeReusa() throws Exception {
        evento("caida", cuando);
        assertThat(referencia("telefono-ana")).isEqualTo("referencia-telefono-ana");
        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst().destinos())
                .allSatisfy(d -> assertThat(d.referencia()).isNull());

        evento("caida_confirmada", cuando.plusSeconds(30));
        assertThat(push.deTipo(TipoAviso.CAIDA_CONFIRMADA).getFirst().destinos())
                .extracting(d -> d.referencia())
                .containsExactlyInAnyOrder("referencia-telefono-ana", "referencia-telefono-beto");
    }
}
