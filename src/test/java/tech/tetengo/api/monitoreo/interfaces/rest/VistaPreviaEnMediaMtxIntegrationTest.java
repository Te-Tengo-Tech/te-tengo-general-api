package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-29 / CA-29.7: MediaMTX's authorization of the preview of a webcam «sin usar», which its PC publishes
 * with the publish token it received on its own channel, and the hook's shared secret and network.
 * Production, 2026-10-24: every preview publish logged «publish (rtsp) denegado» although it went on to
 * publish; that line was the RTSP client's first, credential-less request (it sends its credentials after
 * MediaMTX's 401), and it is no longer logged as a denial.
 */
@ExtendWith(OutputCaptureExtension.class)
class VistaPreviaEnMediaMtxIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRETO = "secreto-de-prueba";
    private static final String CLAVE = "clave-de-publicacion-de-la-previa";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String webcam;
    String otraWebcam;

    @BeforeEach
    void pcConWebcamsSinUsar() throws Exception {
        titular = campo(ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen"), "$.tokenAcceso");
        ApiDePrueba.otorgarConsentimiento(mvc, titular);
        String tokenPc = campo(VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa"), "$.access_token");
        String inventario = VinculacionDePrueba.reportarWebcams(
                        mvc, tokenPc, "usb#1:MacBook Pro Camera", "usb#2:Webcam USB HD")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        webcam = campo(inventario, "$[0].camaraId");
        otraWebcam = campo(inventario, "$[1].camaraId");
        esperarEventosPendientes();
    }

    /** Opens the preview; the publish token the PC got on its channel is replaced by a known one. */
    private String abrirVistaPrevia(String camara) throws Exception {
        String sesion = mvc.perform(
                        post("/api/camaras/" + camara + "/vista-previa").header("Authorization", bearer(titular)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        jdbc.update(
                "update transmisiones_en_vivo set clave_hash = ? where camara_id = ?::uuid",
                Secretos.huella(CLAVE),
                camara);
        return sesion;
    }

    private static String tokenDe(String sesion) {
        return URI.create(campo(sesion, "$.urlTransmision")).getQuery().substring("token=".length());
    }

    private static String cuerpo(
            String accion, String usuario, String clave, String camara, String query, String protocolo) {
        return """
                {"user":"%s","password":"%s","token":"","ip":"104.28.195.72","action":"%s","path":"camaras/%s",\
                "protocol":"%s","id":null,"query":"%s"}""".formatted(usuario, clave, accion, camara, protocolo, query);
    }

    /** What MediaMTX sends: the secret as the password of its Basic credentials, from the Docker network. */
    private ResultActions autorizar(String cuerpo) throws Exception {
        return mvc.perform(conSecreto(post("/api/interno/mediamtx/autorizar"), SECRETO)
                .with(peticion -> {
                    peticion.setRemoteAddr("172.18.0.4");
                    return peticion;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private static MockHttpServletRequestBuilder conSecreto(MockHttpServletRequestBuilder peticion, String secreto) {
        String basica = Base64.getEncoder().encodeToString(("mediamtx:" + secreto).getBytes(StandardCharsets.UTF_8));
        return peticion.header("Authorization", "Basic " + basica);
    }

    private ResultActions publicar(String usuario, String clave, String camara) throws Exception {
        return autorizar(cuerpo("publish", usuario, clave, camara, "", "rtsp"));
    }

    private ResultActions leer(String camara, String token) throws Exception {
        return autorizar(cuerpo("read", "", "", camara, "token=" + token, "webrtc"));
    }

    @Test
    void ca29_7_laPcPublicaLaWebcamSinUsarTrasElDesafioRtspYLaAppLaVeConElTokenDeLaPrevia(CapturedOutput salida)
            throws Exception {
        String sesion = abrirVistaPrevia(webcam);

        // The RTSP client's first ANNOUNCE has no credentials: MediaMTX gets a 401 and asks for them.
        publicar("", "", webcam).andExpect(status().isUnauthorized());
        assertThat(salida.getOut()).doesNotContain("publish (rtsp) denegado");
        // Then it sends them: the PC publishes the path of its own webcam.
        publicar("agente", CLAVE, webcam).andExpect(status().isOk());
        leer(webcam, tokenDe(sesion)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select conectada_en is not null from accesos_vista_en_vivo where id = ?::uuid",
                        Boolean.class,
                        campo(sesion, "$.sesionId")))
                .isTrue();

        // A wrong token is a real denial, and is logged as one.
        publicar("agente", "clave-inventada", webcam).andExpect(status().isUnauthorized());
        assertThat(salida.getOut()).contains("publish (rtsp) denegado en camaras/" + webcam);
    }

    @Test
    void ca29_7_laClaveDeLaPreviaSoloPublicaSuWebcamYSoloMientrasLaPreviaEstaAbierta() throws Exception {
        String sesion = abrirVistaPrevia(webcam);
        publicar("agente", CLAVE, otraWebcam).andExpect(status().isUnauthorized());

        // The preview lasts 2 minutes: once over, the PC may not publish, even before any job ends it.
        reloj.avanzar(Duration.ofMinutes(2).plusSeconds(1));
        publicar("agente", CLAVE, webcam).andExpect(status().isUnauthorized());
        leer(webcam, tokenDe(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void ca29_7_cerrarLaPreviaQuitaElPermisoDePublicarYDeLeer() throws Exception {
        String sesion = abrirVistaPrevia(webcam);
        mvc.perform(delete("/api/vista-en-vivo/" + campo(sesion, "$.sesionId"))
                        .header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        publicar("agente", CLAVE, webcam).andExpect(status().isUnauthorized());
        leer(webcam, tokenDe(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void ca29_7_unaWebcamSinUsarSoloSeLeeConElTokenDeSuPrevia() throws Exception {
        abrirVistaPrevia(webcam);
        String otra = abrirVistaPrevia(otraWebcam);

        leer(webcam, tokenDe(otra)).andExpect(status().isUnauthorized());
        leer(webcam, "token-inventado").andExpect(status().isUnauthorized());
    }

    @Test
    void elGanchoAceptaElSecretoComoClaveBasicaOEnLaQueryYSoloDesdeLaRedInterna() throws Exception {
        String sesion = abrirVistaPrevia(webcam);
        String lectura = cuerpo("read", "", "", webcam, "token=" + tokenDe(sesion), "webrtc");

        // Earlier deployments of MediaMTX send it in the query.
        mvc.perform(post("/api/interno/mediamtx/autorizar?secreto=" + SECRETO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectura))
                .andExpect(status().isOk());
        mvc.perform(conSecreto(post("/api/interno/mediamtx/autorizar"), "otro-secreto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectura))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/interno/mediamtx/autorizar")
                        .header("Authorization", "Basic no-es-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectura))
                .andExpect(status().isUnauthorized());
        // From the Internet (what Tomcat sees for a request the reverse proxy forwarded), never.
        mvc.perform(conSecreto(post("/api/interno/mediamtx/autorizar"), SECRETO)
                        .with(peticion -> {
                            peticion.setRemoteAddr("203.0.113.7");
                            return peticion;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lectura))
                .andExpect(status().isUnauthorized());
    }
}
