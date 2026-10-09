package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.monitoreo.application.FinalizarSesionesDeVistaEnVivo;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision.Lector;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-23: live view sessions, MediaMTX's authorization and the end of sessions nobody closed. */
class VistaEnVivoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FinalizarSesionesDeVistaEnVivo finalizarSesiones;

    String titular;
    String token;
    String agente;
    String camara;

    @BeforeEach
    void camaraEnLinea() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        token = campo(titular, "$.tokenAcceso");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala");
        agente = campo(registro, "$.token");
        camara = campo(registro, "$.camaraId");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(agente)))
                .andExpect(status().isOk());
    }

    private ResultActions abrir(String token, String camara, String cuerpo) throws Exception {
        return mvc.perform(post("/api/camaras/" + camara + "/vista-en-vivo")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String sesion(String token, String camara, String cuerpo) throws Exception {
        return abrir(token, camara, cuerpo)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions cambiarModo(String token, String sesionId, String cuerpo) throws Exception {
        return mvc.perform(patch("/api/vista-en-vivo/" + sesionId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions autorizar(
            String secreto, String accion, String usuario, String clave, String ruta, String query) throws Exception {
        return autorizar(secreto, accion, usuario, clave, ruta, query, "publish".equals(accion) ? "rtsp" : "hls");
    }

    private ResultActions autorizar(
            String secreto, String accion, String usuario, String clave, String ruta, String query, String protocolo)
            throws Exception {
        return mvc.perform(post("/api/interno/mediamtx/autorizar?secreto=" + secreto)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"user":"%s","password":"%s","token":"%s","ip":"172.17.0.1","action":"%s","path":"%s",\
                        "protocol":"%s","id":"5ee83fbf-23ab-414b-99d1-362e9917ed6e","query":"%s"}""".formatted(usuario, clave, clave, accion, ruta, protocolo, query)));
    }

    private ResultActions preparar(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/api/vista-en-vivo/preparar")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions leer(String sesion) throws Exception {
        return autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, "token=" + tokenDe(sesion));
    }

    private static String tokenDe(String sesion) {
        String query = URI.create(campo(sesion, "$.urlTransmision")).getQuery();
        return query.substring("token=".length());
    }

    private Map<String, Object> acceso(String sesionId) {
        return jdbc.queryForMap("select * from accesos_vista_en_vivo where id = ?", UUID.fromString(sesionId));
    }

    private Instant fin(String sesionId) {
        Timestamp fin = (Timestamp) acceso(sesionId).get("fin");
        return fin == null ? null : fin.toInstant();
    }

    private List<Map<String, Object>> transmisiones() {
        return jdbc.queryForList("select * from transmisiones_en_vivo");
    }

    private String alertaDe(String tokenAgente) throws Exception {
        return campo(
                ApiDePrueba.enviarEvento(mvc, tokenAgente, UUID.randomUUID(), "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
    }

    @Test
    void ca23_1_conLaCamaraEnLineaEntregaLaTransmisionHlsDeMediaMtx() throws Exception {
        String sesion = sesion(token, camara, "{\"alertaId\":null}");
        assertThat(campo(sesion, "$.urlTransmision"))
                .matches("http://localhost:8888/camaras/" + camara + "/index\\.m3u8\\?token=[A-Za-z0-9_-]{43}");
        assertThat(campo(sesion, "$.expiraEn"))
                .isEqualTo(reloj.instant().plus(Duration.ofMinutes(10)).toString());
        assertThat(campo(sesion, "$.modo")).isEqualTo("VIDEO");
        // WebRTC playback is off in this configuration (no tetengo.vista-en-vivo.url-webrtc): the field is
        // there, null, and the app plays HLS. MediaMtxIntegrationTest covers it on.
        assertThat(sesion).contains("\"urlWebrtc\":null");

        Map<String, Object> acceso = acceso(campo(sesion, "$.sesionId"));
        assertThat(acceso.get("usuario_id")).hasToString(campo(titular, "$.usuario.id"));
        assertThat(acceso.get("camara_id")).hasToString(camara);
        assertThat(acceso.get("alerta_id")).isNull();
        assertThat(acceso.get("token_hash")).isEqualTo(Secretos.huella(tokenDe(sesion)));
        assertThat(transmisiones()).singleElement().satisfies(t -> {
            assertThat(t.get("camara_id")).hasToString(camara);
            assertThat(t.get("modo")).isEqualTo("VIDEO");
        });
    }

    @Test
    void ca23_2_desdeUnaAlertaDeLaCamaraQuedaRegistradoElOrigen() throws Exception {
        String alerta = alertaDe(agente);
        String sesion = sesion(token, camara, "{\"alertaId\":\"%s\"}".formatted(alerta));
        assertThat(acceso(campo(sesion, "$.sesionId")).get("alerta_id")).hasToString(alerta);
    }

    @Test
    void ca23_2_unaAlertaDeOtraCamaraODesconocidaEsUnErrorDeValidacion() throws Exception {
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        String otraCamara = ApiDePrueba.registrarAgente(mvc, DatosDePrueba.instalacion(jdbc, hogar), "Cocina");
        String alertaDeOtraCamara = alertaDe(campo(otraCamara, "$.token"));

        abrir(token, camara, "{\"alertaId\":\"%s\"}".formatted(alertaDeOtraCamara))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.alertaId").isNotEmpty());
        abrir(token, camara, "{\"alertaId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.alertaId").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from accesos_vista_en_vivo", Integer.class))
                .isZero();
    }

    @Test
    void elModoSePideAlAbrirSeMantieneParaLaCamaraYSeCambiaDuranteLaSesion() throws Exception {
        String primera = sesion(token, camara, "{\"modo\":\"SOLO_POSTURA\"}");
        assertThat(campo(primera, "$.modo")).isEqualTo("SOLO_POSTURA");
        String segunda = sesion(token, camara, "{}");
        assertThat(campo(segunda, "$.modo")).isEqualTo("SOLO_POSTURA");

        cambiarModo(token, campo(segunda, "$.sesionId"), "{\"modo\":\"VIDEO_CON_POSTURA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionId").value(campo(segunda, "$.sesionId")))
                .andExpect(jsonPath("$.modo").value("VIDEO_CON_POSTURA"));
        assertThat(transmisiones())
                .singleElement()
                .satisfies(t -> assertThat(t.get("modo")).isEqualTo("VIDEO_CON_POSTURA"));
    }

    @Test
    void unModoDesconocidoEsUnErrorDeValidacion() throws Exception {
        abrir(token, camara, "{\"modo\":\"INFRARROJO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.modo").isNotEmpty());
        String sesion = campo(sesion(token, camara, "{}"), "$.sesionId");
        cambiarModo(token, sesion, "{\"modo\":\"video\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.modo").isNotEmpty());
        cambiarModo(token, sesion, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void soloElEspectadorCambiaElModoYMientrasLaSesionEstaAbierta() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String sesion = campo(sesion(token, camara, "{}"), "$.sesionId");

        cambiarModo(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), sesion, "{\"modo\":\"SOLO_POSTURA\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SESION_NO_ENCONTRADA"));
        mvc.perform(delete("/api/vista-en-vivo/" + sesion).header("Authorization", bearer(token)));
        cambiarModo(token, sesion, "{\"modo\":\"SOLO_POSTURA\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SESION_NO_ENCONTRADA"));
    }

    @Test
    void ca23_3_conLaCamaraDesconectadaNoEstaDisponible() throws Exception {
        jdbc.update("update camaras set estado_conexion = 'DESCONECTADA'");
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_DESCONECTADA"));
    }

    @Test
    void ca23_4_conLaCamaraEnPausaIndicaHastaCuando() throws Exception {
        String hasta = reloj.instant().plus(Duration.ofHours(1)).toString();
        jdbc.update(
                "update camaras set pausada_hasta = ?",
                Timestamp.from(reloj.instant().plus(Duration.ofHours(1))));
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_EN_PAUSA"))
                .andExpect(jsonPath("$.pausadaHasta").value(hasta));
    }

    @Test
    void sinConsentimientoNoHayVistaEnVivo() throws Exception {
        jdbc.update("update estados_de_captura set consentimiento_vigente = false");
        abrir(token, camara, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
    }

    @Test
    void ca24_1_alCerrarlaSeRegistraElFinSeExpulsaAlEspectadorYTerminaLaTransmision() throws Exception {
        String sesion = sesion(token, camara, "{}");
        String id = campo(sesion, "$.sesionId");
        reloj.avanzar(Duration.ofSeconds(95));
        mvc.perform(delete("/api/vista-en-vivo/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(fin(id)).isEqualTo(reloj.instant());
        assertThat(transmision.lectoresExpulsados()).containsExactly(Secretos.huella(tokenDe(sesion)));
        assertThat(transmisiones()).isEmpty();
    }

    @Test
    void laTransmisionSigueMientrasQuedeOtraSesionAbierta() throws Exception {
        String primera = campo(sesion(token, camara, "{}"), "$.sesionId");
        sesion(token, camara, "{}");
        mvc.perform(delete("/api/vista-en-vivo/" + primera).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(transmisiones()).hasSize(1);
    }

    @Test
    void ca08_4_elInvitadoTambienVeEnVivo() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        abrir(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), camara, "{}").andExpect(status().isCreated());
    }

    @Test
    void mediaMtxDejaLeerSoloConElTokenDeUnaSesionAbiertaDeEsaCamara() throws Exception {
        String sesion = sesion(token, camara, "{}");
        leer(sesion).andExpect(status().isOk());
        Map<String, Object> acceso = acceso(campo(sesion, "$.sesionId"));
        assertThat(((Timestamp) acceso.get("conectada_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(((Timestamp) acceso.get("ultima_actividad")).toInstant()).isEqualTo(reloj.instant());

        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, "").andExpect(status().isUnauthorized());
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, "token=otro")
                .andExpect(status().isUnauthorized());
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + UUID.randomUUID(), "token=" + tokenDe(sesion))
                .andExpect(status().isUnauthorized());
        autorizar("otro-secreto", "read", "", "", "camaras/" + camara, "token=" + tokenDe(sesion))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/interno/mediamtx/autorizar?secreto=secreto-de-prueba"))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId")).header("Authorization", bearer(token)));
        leer(sesion).andExpect(status().isUnauthorized());
    }

    @Test
    void mediaMtxDejaLeerPorWebRtcConElMismoTokenYNoPorOtrosProtocolos() throws Exception {
        String sesion = sesion(token, camara, "{}");
        String query = "token=" + tokenDe(sesion);
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, query, "webrtc")
                .andExpect(status().isOk());
        assertThat(((Timestamp) acceso(campo(sesion, "$.sesionId")).get("ultima_actividad")).toInstant())
                .isEqualTo(reloj.instant());
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, "", "webrtc")
                .andExpect(status().isUnauthorized());
        for (String protocolo : List.of("rtsp", "rtmp", "srt")) {
            autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, query, protocolo)
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId")).header("Authorization", bearer(token)));
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camara, query, "webrtc")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mediaMtxDejaPublicarSoloAlAgenteConLaClaveDeLaTransmision() throws Exception {
        sesion(token, camara, "{}");
        jdbc.update("update transmisiones_en_vivo set clave_hash = ?", Secretos.huella("clave-de-prueba"));
        autorizar("secreto-de-prueba", "publish", "agente", "clave-de-prueba", "camaras/" + camara, "")
                .andExpect(status().isOk());
        autorizar("secreto-de-prueba", "publish", "agente", "otra", "camaras/" + camara, "")
                .andExpect(status().isUnauthorized());
        autorizar("secreto-de-prueba", "publish", "otro", "clave-de-prueba", "camaras/" + camara, "")
                .andExpect(status().isUnauthorized());
        for (String accion : List.of("api", "metrics", "pprof")) {
            autorizar("secreto-de-prueba", accion, "agente", "clave-de-prueba", "camaras/" + camara, "")
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unaSesionQueNadieCerroTerminaCuandoSuEspectadorDejaDeLeer() throws Exception {
        String sesion = sesion(token, camara, "{}");
        String id = campo(sesion, "$.sesionId");
        String huella = Secretos.huella(tokenDe(sesion));
        reloj.avanzar(Duration.ofSeconds(2));
        leer(sesion).andExpect(status().isOk());

        // MediaMTX authorizes an HLS reader once; then its session keeps receiving bytes.
        for (int i = 1; i <= 6; i++) {
            reloj.avanzar(Duration.ofSeconds(10));
            transmision.leyendo("hls-1", UUID.fromString(camara), huella, 1000L * i);
            finalizarSesiones.ejecutar();
        }
        Instant ultimaLectura = reloj.instant();
        assertThat(fin(id)).isNull();

        reloj.avanzar(Duration.ofSeconds(20));
        finalizarSesiones.ejecutar();
        assertThat(fin(id)).isNull();
        reloj.avanzar(Duration.ofSeconds(10));
        finalizarSesiones.ejecutar();

        assertThat(fin(id)).isEqualTo(ultimaLectura);
        assertThat(transmisiones()).isEmpty();
        assertThat(transmision.lectoresExpulsados()).contains(huella);
        leer(sesion).andExpect(status().isUnauthorized());
    }

    @Test
    void unEspectadorWebRtcQueSigueRecibiendoBytesMantieneLaSesionYAlTerminarSeLeExpulsa() throws Exception {
        String sesion = sesion(token, camara, "{}");
        String id = campo(sesion, "$.sesionId");
        String huella = Secretos.huella(tokenDe(sesion));
        for (int i = 1; i <= 6; i++) {
            reloj.avanzar(Duration.ofSeconds(10));
            transmision.leyendo("webrtc-1", Lector.WEBRTC, UUID.fromString(camara), huella, 5000L * i);
            finalizarSesiones.ejecutar();
        }
        assertThat(fin(id)).isNull();

        mvc.perform(delete("/api/vista-en-vivo/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(transmision.lectoresExpulsados()).containsExactly(huella);
    }

    @Test
    void unaSesionQueNuncaSeReprodujoTerminaALos30SegundosSinDuracion() throws Exception {
        String id = campo(sesion(token, camara, "{}"), "$.sesionId");
        Instant inicio = reloj.instant();
        reloj.avanzar(Duration.ofSeconds(30));
        finalizarSesiones.ejecutar();
        assertThat(fin(id)).isEqualTo(inicio);
    }

    @Test
    void unaSesionTerminaAlLlegarASuDuracionMaxima() throws Exception {
        String sesion = sesion(token, camara, "{}");
        String id = campo(sesion, "$.sesionId");
        Instant maximo = reloj.instant().plus(Duration.ofMinutes(10));
        String huella = Secretos.huella(tokenDe(sesion));
        for (int i = 1; i <= 61; i++) {
            reloj.avanzar(Duration.ofSeconds(10));
            transmision.leyendo("hls-1", UUID.fromString(camara), huella, 1000L * i);
            finalizarSesiones.ejecutar();
        }
        assertThat(fin(id)).isEqualTo(maximo);
        assertThat(transmision.lectoresExpulsados()).contains(huella);
    }

    @Test
    void ca22_1_alPausarLaCamaraTerminanSusSesionesYMediaMtxExpulsaATodos() throws Exception {
        String id = campo(sesion(token, camara, "{}"), "$.sesionId");
        reloj.avanzar(Duration.ofSeconds(40));
        mvc.perform(post("/api/camaras/" + camara + "/pausa")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion\":\"MIN_30\"}"))
                .andExpect(status().isOk());
        assertThat(fin(id)).isEqualTo(reloj.instant());
        assertThat(transmisiones()).isEmpty();
        assertThat(transmision.camarasExpulsadas()).containsExactly(UUID.fromString(camara));
    }

    @Test
    void ca09_1_alRevocarseElConsentimientoTerminanLasSesionesYMediaMtxExpulsaATodos() throws Exception {
        String id = campo(sesion(token, camara, "{}"), "$.sesionId");
        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(token)))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(5)).until(() -> fin(id) != null);
        assertThat(transmisiones()).isEmpty();
        await().atMost(Duration.ofSeconds(5))
                .until(() -> transmision.camarasExpulsadas().contains(UUID.fromString(camara)));
    }

    @Test
    void unHogarNoVeLaCamaraDeOtroNiTocaSusSesiones() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String tokenOtro = campo(otro, "$.tokenAcceso");
        abrir(tokenOtro, camara, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));

        String sesion = campo(sesion(token, camara, "{}"), "$.sesionId");
        mvc.perform(delete("/api/vista-en-vivo/" + sesion).header("Authorization", bearer(tokenOtro)))
                .andExpect(status().isNoContent());
        cambiarModo(tokenOtro, sesion, "{\"modo\":\"SOLO_POSTURA\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SESION_NO_ENCONTRADA"));
        assertThat(acceso(sesion).get("fin")).isNull();
        assertThat(transmisiones())
                .singleElement()
                .satisfies(t -> assertThat(t.get("modo")).isEqualTo("VIDEO"));
    }

    @Test
    void elTokenDeUnHogarNoAbreLaCamaraDeOtro() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String camaraDeBeto = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Dormitorio"), "$.camaraId");
        String deAna = sesion(token, camara, "{}");
        autorizar("secreto-de-prueba", "read", "", "", "camaras/" + camaraDeBeto, "token=" + tokenDe(deAna))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTrabajoTerminaLasSesionesDeCadaHogarEnSuHogar() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String registroBeto = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otro, "Dormitorio");
        mvc.perform(post("/api/agente/senal").header("Authorization", bearer(campo(registroBeto, "$.token"))));
        String deAna = sesion(token, camara, "{}");
        String deBeto = sesion(campo(otro, "$.tokenAcceso"), campo(registroBeto, "$.camaraId"), "{}");

        reloj.avanzar(Duration.ofSeconds(10));
        transmision.leyendo(
                "hls-beto", UUID.fromString(campo(registroBeto, "$.camaraId")), Secretos.huella(tokenDe(deBeto)), 10);
        finalizarSesiones.ejecutar();
        reloj.avanzar(Duration.ofSeconds(20));
        finalizarSesiones.ejecutar();

        assertThat(fin(campo(deAna, "$.sesionId"))).isNotNull();
        assertThat(fin(campo(deBeto, "$.sesionId"))).isNull();
        assertThat(jdbc.queryForObject("select count(*) from transmisiones_en_vivo", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void prepararNoAbreNiRegistraUnaSesion() throws Exception {
        preparar(token, "{\"camaraId\":\"%s\"}".formatted(camara)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from accesos_vista_en_vivo", Integer.class))
                .isZero();
        assertThat(transmisiones()).isEmpty();
    }

    @Test
    void prepararSigueLasReglasDeAbrir() throws Exception {
        preparar(token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.camaraId").isNotEmpty());
        preparar(token, "{\"camaraId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));

        String cuerpo = "{\"camaraId\":\"%s\"}".formatted(camara);
        jdbc.update("update estados_de_captura set consentimiento_vigente = false");
        preparar(token, cuerpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
        jdbc.update("update estados_de_captura set consentimiento_vigente = true");

        jdbc.update(
                "update camaras set pausada_hasta = ?",
                Timestamp.from(reloj.instant().plus(Duration.ofHours(1))));
        preparar(token, cuerpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_EN_PAUSA"))
                .andExpect(jsonPath("$.pausadaHasta")
                        .value(reloj.instant().plus(Duration.ofHours(1)).toString()));
        jdbc.update("update camaras set pausada_hasta = null");

        jdbc.update("update camaras set estado_conexion = 'DESCONECTADA'");
        preparar(token, cuerpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMARA_DESCONECTADA"));
    }

    @Test
    void elInvitadoTambienPreparaYOtroHogarNoVeLaCamara() throws Exception {
        UUID invitado = UUID.randomUUID();
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String cuerpo = "{\"camaraId\":\"%s\"}".formatted(camara);
        preparar(JwtDePrueba.token(invitado, hogar, Rol.INVITADO), cuerpo).andExpect(status().isNoContent());

        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        preparar(campo(otro, "$.tokenAcceso"), cuerpo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
    }
}
