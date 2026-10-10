package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import tech.tetengo.api.shared.application.port.NotificadorPush.Plataforma;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.push.ContenidoDelAviso;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.PushDePrueba.Envio;

/**
 * US-16 and US-17: push alerts to every member device, sent right after the agent's request commits,
 * with retries (CA-16.4), the alert's delivery state and the device status the app reads.
 */
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

    /** Sends the agent's event and waits for the first attempt of the notice it queued. */
    private String evento(String tipo, Instant ocurridoEn) throws Exception {
        String alertaId = campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), tipo, ocurridoEn)
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        esperarEventosPendientes();
        return alertaId;
    }

    private String estadoAviso(String alertaId) {
        return jdbc.queryForObject(
                "select estado_aviso from alertas where id = ?", String.class, UUID.fromString(alertaId));
    }

    private int pendientes() {
        return jdbc.queryForObject("select count(*) from avisos_pendientes", Integer.class);
    }

    private String tokenDeBeto() {
        return JwtDePrueba.token(invitado, UUID.fromString(campo(titular, "$.hogarId")), Rol.INVITADO);
    }

    private Instant notificadaEn(String alertaId) {
        Timestamp t = jdbc.queryForObject(
                "select notificada_en from alertas where id = ?", Timestamp.class, UUID.fromString(alertaId));
        return t == null ? null : t.toInstant();
    }

    @Test
    void ca16_1_laCaidaSeNotificaATodosLosFamiliaresAlGuardarseConHabitacionYHora() throws Exception {
        String alertaId = evento("caida", cuando);

        // Sent right after the agent's request committed (< 10 s, CA-11.3), on another thread: the
        // agent never waits for the push service.
        Envio envio = push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst();
        assertThat(envio.hilo()).isNotEqualTo(Thread.currentThread().getName());
        assertThat(envio.tokens()).containsExactlyInAnyOrder("telefono-ana", "telefono-beto");
        assertThat(envio.aviso())
                .isEqualTo(new Aviso(TipoAviso.ALERTA_CAIDA, UUID.fromString(alertaId), camara, "Sala", cuando, ROSA));
        // The prototype's notice (screen 44), with the household's older adult.
        assertThat(ContenidoDelAviso.de(envio.aviso()))
                .extracting(ContenidoDelAviso::titulo, ContenidoDelAviso::etiqueta)
                .containsExactly("Posible caída de Rosa en la Sala", "URGENTE · CAÍDA");
        assertThat(notificadaEn(alertaId)).isEqualTo(reloj.instant());
        assertThat(estadoAviso(alertaId)).isEqualTo("ENTREGADO");
        assertThat(pendientes()).isZero();
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
        assertThat(estadoAviso(alertaId)).isEqualTo("REINTENTANDO");
        assertThat(jdbc.queryForObject("select intentos from avisos_pendientes", Integer.class))
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
        assertThat(estadoAviso(alertaId)).isEqualTo("ENTREGADO");
        assertThat(pendientes()).isZero();

        reintentarAvisos.ejecutar();
        assertThat(push.enviados()).hasSize(1);
    }

    @Test
    void ca16_4_unAvisoQueNoSeEntregaSeAbandonaAlTerminarSuPlazo() throws Exception {
        push.simularCaida(true);
        String alertaId = evento("caida", cuando);

        for (int i = 0; i < 120; i++) {
            reloj.avanzar(Duration.ofSeconds(15));
            reintentarAvisos.ejecutar();
        }

        assertThat(jdbc.queryForList("select tipo || ' ' || intentos from avisos_pendientes", String.class))
                .isEmpty();
        assertThat(estadoAviso(alertaId)).isEqualTo("NO_ENTREGADO");
        assertThat(notificadaEn(alertaId)).isNull();
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
    void laPwaRegistraSuTokenWebYRecibeLasAlertasComoLosTelefonos() throws Exception {
        registrarDispositivo(campo(titular, "$.tokenAcceso"), "navegador-ana", "WEB")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                        "select plataforma from dispositivos where token_push = ?", String.class, "navegador-ana"))
                .isEqualTo("WEB");

        evento("caida", cuando);

        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA).getFirst().destinos())
                .extracting(d -> d.tokenPush(), d -> d.plataforma())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("telefono-ana", Plataforma.ANDROID),
                        org.assertj.core.groups.Tuple.tuple("telefono-beto", Plataforma.IOS),
                        org.assertj.core.groups.Tuple.tuple("navegador-ana", Plataforma.WEB));
    }

    @Test
    void laPlataformaDebeSerAndroidIosOWeb() throws Exception {
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
        assertThat(jdbc.queryForObject(
                        "select desactivado_en from dispositivos where token_push = ?",
                        Timestamp.class,
                        "telefono-beto"))
                .isNotNull();
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
    void siElServicioRechazaTodosLosTokensLaCaidaSeReintentaYLlegaAlTelefonoQueSeRegistraDeNuevo() throws Exception {
        push.rechazarToken("telefono-ana");
        push.rechazarToken("telefono-beto");

        String alertaId = evento("caida", cuando);

        assertThat(notificadaEn(alertaId)).isNull();
        assertThat(estadoAviso(alertaId)).isEqualTo("REINTENTANDO");
        assertThat(pendientes()).isEqualTo(1);

        // Nobody can receive it: it stays queued, without calling the push service again.
        push.limpiar();
        reloj.avanzar(Duration.ofSeconds(15));
        reintentarAvisos.ejecutar();
        assertThat(push.enviados()).isEmpty();
        assertThat(pendientes()).isEqualTo(1);

        // Ana's phone gets a new token and registers it: the fall goes out right away.
        reloj.avanzar(Duration.ofMinutes(3));
        registrarDispositivo(campo(titular, "$.tokenAcceso"), "telefono-ana-nuevo", "ANDROID")
                .andExpect(status().isCreated());

        assertThat(push.deTipo(TipoAviso.ALERTA_CAIDA))
                .singleElement()
                .satisfies(e -> assertThat(e.tokens()).containsExactly("telefono-ana-nuevo"));
        assertThat(notificadaEn(alertaId)).isEqualTo(reloj.instant());
        assertThat(estadoAviso(alertaId)).isEqualTo("ENTREGADO");
        assertThat(pendientes()).isZero();
    }

    @Test
    void sinNingunDispositivoLaCaidaSeReintentaTreintaMinutosYLuegoSeAbandona() throws Exception {
        jdbc.update("delete from dispositivos");
        String alertaId = evento("caida", cuando);
        assertThat(estadoAviso(alertaId)).isEqualTo("REINTENTANDO");

        reloj.avanzar(Duration.ofMinutes(29));
        reintentarAvisos.ejecutar();
        assertThat(pendientes()).isEqualTo(1);

        reloj.avanzar(Duration.ofMinutes(2));
        reintentarAvisos.ejecutar();
        assertThat(pendientes()).isZero();
        assertThat(estadoAviso(alertaId)).isEqualTo("NO_ENTREGADO");
    }

    @Test
    void unaAlertaAtendidaDejaDeReintentarse() throws Exception {
        jdbc.update("delete from dispositivos");
        String alertaId = evento("caida", cuando);

        mvc.perform(post("/api/alertas/" + alertaId + "/atencion")
                        .header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(status().isOk());
        esperarEventosPendientes();
        reloj.avanzar(Duration.ofSeconds(15));
        reintentarAvisos.ejecutar();

        assertThat(jdbc.queryForObject(
                        "select count(*) from avisos_pendientes where tipo = 'ALERTA_CAIDA'", Integer.class))
                .isZero();
        assertThat(estadoAviso(alertaId)).isEqualTo("NO_ENTREGADO");
    }

    @Test
    void unAvisoQueNoEsUrgenteSinDispositivosNoSeReintenta() throws Exception {
        jdbc.update("delete from dispositivos");
        evento("deteccion_no_confiable", cuando);
        assertThat(pendientes()).isZero();
    }

    @Test
    void registrarDevuelveElDispositivoYRegistrarloSinCambiosActualizaCuandoSeVio() throws Exception {
        String sesion = campo(titular, "$.tokenAcceso");
        String primero = registrarDispositivo(sesion, "telefono-ana", "ANDROID")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plataforma").value("ANDROID"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.desactivadoEn").isEmpty())
                .andExpect(jsonPath("$.tokenPush").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        reloj.avanzar(Duration.ofHours(2));
        String segundo = registrarDispositivo(sesion, "telefono-ana", "ANDROID")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(campo(segundo, "$.id")).isEqualTo(campo(primero, "$.id"));
        assertThat(Instant.parse(campo(segundo, "$.vistoEn"))).isEqualTo(reloj.instant());
        assertThat(jdbc.queryForObject(
                                "select visto_en from dispositivos where token_push = ?",
                                Timestamp.class,
                                "telefono-ana")
                        .toInstant())
                .isEqualTo(reloj.instant());
    }

    @Test
    void elTelefonoConsultaSiSigueRecibiendoAvisos() throws Exception {
        String sesion = campo(titular, "$.tokenAcceso");
        String id = campo(
                registrarDispositivo(sesion, "telefono-ana", "ANDROID")
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(get("/api/dispositivos/" + id).header("Authorization", bearer(sesion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.activo").value(true));

        // The push service says the token is gone: the app learns it and gets a new one.
        push.rechazarToken("telefono-ana");
        evento("caida", cuando);
        mvc.perform(get("/api/dispositivos/" + id).header("Authorization", bearer(sesion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.desactivadoEn").isNotEmpty());

        // Another account cannot read it.
        mvc.perform(get("/api/dispositivos/" + id).header("Authorization", bearer(tokenDeBeto())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DISPOSITIVO_NO_ENCONTRADO"));
        mvc.perform(get("/api/dispositivos/" + UUID.randomUUID()).header("Authorization", bearer(sesion)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elHogarDiceCuantosDispositivosPuedenRecibirAlertas() throws Exception {
        String sesion = campo(titular, "$.tokenAcceso");
        mvc.perform(get("/api/hogar").header("Authorization", bearer(sesion)))
                .andExpect(jsonPath("$.dispositivosActivos").value(2));

        push.rechazarToken("telefono-ana");
        push.rechazarToken("telefono-beto");
        evento("caida", cuando);

        // Nobody in the family can receive alerts: the app warns about it.
        mvc.perform(get("/api/hogar").header("Authorization", bearer(tokenDeBeto())))
                .andExpect(jsonPath("$.dispositivosActivos").value(0));
    }

    @Test
    void losDispositivosActivosSonLosDeLosFamiliaresDeCadaHogar() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(otro, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.dispositivosActivos").value(0));
        mvc.perform(get("/api/hogar").header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.dispositivosActivos").value(2));
    }

    @Test
    void lasAlertasMuestranElEstadoDelAviso() throws Exception {
        String alertaId = evento("caida", cuando);
        mvc.perform(get("/api/alertas/" + alertaId).header("Authorization", bearer(campo(titular, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.estadoAviso").value("ENTREGADO"))
                .andExpect(jsonPath("$.notificadaEn").isNotEmpty());
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
