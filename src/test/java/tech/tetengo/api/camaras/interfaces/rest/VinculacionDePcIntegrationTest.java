package tech.tetengo.api.camaras.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.VinculacionDePrueba.consultar;
import static tech.tetengo.api.support.VinculacionDePrueba.resolver;
import static tech.tetengo.api.support.VinculacionDePrueba.solicitar;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.VinculacionDePrueba;

/**
 * US-28: the PC link with the OAuth 2.0 device grant (RFC 8628), with the security properties of the
 * change request: codes expire and work once, polling is rate limited, only the owner approves, wrong
 * codes are limited (CA-28.6).
 */
class VinculacionDePcIntegrationTest extends AbstractIntegrationTest {

    private static final String ALFABETO = "[BCDFGHJKLMNPQRSTVWXZ]";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtDecoder decodificador;

    String sesion;
    String titular;
    UUID hogar;

    @BeforeEach
    void titular() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
    }

    @Test
    void laPcRecibeUnCodigoDeDispositivoSecretoYUnCodigoDe8LetrasConSuQrDe10Minutos() throws Exception {
        String solicitud = solicitar(mvc, "Sala-Rosa");

        String codigo = campo(solicitud, "$.user_code");
        assertThat(codigo).matches(ALFABETO + "{4}-" + ALFABETO + "{4}");
        assertThat(campo(solicitud, "$.device_code")).hasSizeGreaterThanOrEqualTo(43);
        assertThat(campo(solicitud, "$.verification_uri")).isEqualTo("https://app.tetengo.reqsai.tech/vincular");
        assertThat(campo(solicitud, "$.verification_uri_complete"))
                .isEqualTo("https://app.tetengo.reqsai.tech/vincular?codigo=" + codigo);
        assertThat(campo(solicitud, "$.expires_in")).isEqualTo("600");
        assertThat(campo(solicitud, "$.interval")).isEqualTo("5");
        // Only hashes are stored.
        assertThat(jdbc.queryForObject(
                        "select count(*) from autorizaciones_de_dispositivo where codigo_dispositivo_hash = ?"
                                + " or codigo_usuario_hash = ?",
                        Integer.class,
                        campo(solicitud, "$.device_code"),
                        codigo.replace("-", "")))
                .isZero();
    }

    @Test
    void laSolicitudSigueElFormatoDeOAuth() throws Exception {
        mvc.perform(post("/api/agente/vinculacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "otro-cliente")
                        .param("nombre_pc", "Sala-Rosa"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mvc.perform(post("/api/agente/vinculacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", VinculacionDePrueba.CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/api/agente/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", VinculacionDePrueba.CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        consultar(mvc, "codigo-que-no-existe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void mientrasNadieApruebaLaPcEsperaYSiConsultaMuyRapidoSeLePideIrMasLento() throws Exception {
        String dispositivo = campo(solicitar(mvc, "Sala-Rosa"), "$.device_code");

        consultar(mvc, dispositivo)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("authorization_pending"));
        consultar(mvc, dispositivo).andExpect(jsonPath("$.error").value("slow_down"));
        // RFC 8628 §3.5: the interval grew by 5 s, to 10 s.
        reloj.avanzar(Duration.ofSeconds(6));
        consultar(mvc, dispositivo).andExpect(jsonPath("$.error").value("slow_down"));
        reloj.avanzar(Duration.ofSeconds(16));
        consultar(mvc, dispositivo).andExpect(jsonPath("$.error").value("authorization_pending"));
    }

    @Test
    void ca28_1_elTitularApruebaYLaPcRecibeSusTokensUnaSolaVez() throws Exception {
        String solicitud = solicitar(mvc, "Sala-Rosa");
        String dispositivo = campo(solicitud, "$.device_code");

        resolver(mvc, titular, "aprobacion", campo(solicitud, "$.user_code"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Sala-Rosa"))
                .andExpect(jsonPath("$.sistemaOperativo").value("Windows 11 Home"))
                .andExpect(jsonPath("$.versionAgente").value("0.5.0"))
                .andExpect(jsonPath("$.origen").value("VINCULACION"))
                .andExpect(jsonPath("$.vinculadaPor.nombre").value("Carmen"))
                .andExpect(jsonPath("$.camaras").isEmpty());

        reloj.avanzar(Duration.ofSeconds(5));
        String tokens = consultar(mvc, dispositivo)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.hogar_id").value(hogar.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Jwt acceso = decodificador.decode(campo(tokens, "$.access_token"));
        assertThat(acceso.getClaimAsString("hogar_id")).isEqualTo(hogar.toString());
        assertThat(acceso.getClaimAsString("pc_id")).isEqualTo(campo(tokens, "$.pc_id"));
        assertThat(acceso.getClaimAsString("rol")).isEqualTo("AGENTE");
        assertThat(acceso.getClaimAsString("camara_id")).isNull();

        // The device code works once.
        reloj.avanzar(Duration.ofSeconds(5));
        consultar(mvc, dispositivo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // D-28.4: the PC shows the household it joined and who approved it.
        mvc.perform(get("/api/agente/pc").header("Authorization", bearer(campo(tokens, "$.access_token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pcId").value(campo(tokens, "$.pc_id")))
                .andExpect(jsonPath("$.nombreAdultoMayor").value("Adulto de Carmen"))
                .andExpect(jsonPath("$.vinculadaPor").value("Carmen"))
                .andExpect(jsonPath("$.consentimientoVigente").value(false));
    }

    @Test
    void ca28_2_elCodigoEscritoSeAceptaEnMinusculasYSinGuion() throws Exception {
        String solicitud = solicitar(mvc, "Sala-Rosa");
        String escrito = campo(solicitud, "$.user_code").replace("-", "").toLowerCase(Locale.ROOT);

        resolver(mvc, titular, "consulta", escrito)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(campo(solicitud, "$.user_code")));
        resolver(
                        mvc,
                        titular,
                        "consulta",
                        "  " + campo(solicitud, "$.user_code").toLowerCase(Locale.ROOT) + " ")
                .andExpect(status().isOk());
    }

    @Test
    void ca28_2_soloSeAdmitenLasLetrasDelCodigo() throws Exception {
        resolver(mvc, titular, "consulta", "KQ7T-MX4D")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.codigo").exists());
        resolver(mvc, titular, "consulta", "KQAT-MXHD").andExpect(status().isBadRequest());
        resolver(mvc, titular, "consulta", "KQWT-MXH").andExpect(status().isBadRequest());
    }

    @Test
    void ca28_3_unCodigoUsadoOVencidoNoSirveYLaPcPideOtro() throws Exception {
        String usada = solicitar(mvc, "Sala-Rosa");
        resolver(mvc, titular, "aprobacion", campo(usada, "$.user_code")).andExpect(status().isCreated());
        resolver(mvc, titular, "aprobacion", campo(usada, "$.user_code"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CODIGO_VENCIDO"));

        String vencida = solicitar(mvc, "Cocina-Rosa");
        reloj.avanzar(Duration.ofMinutes(10));
        resolver(mvc, titular, "consulta", campo(vencida, "$.user_code"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CODIGO_VENCIDO"));
        consultar(mvc, campo(vencida, "$.device_code"))
                .andExpect(jsonPath("$.error").value("expired_token"));
        // The PC asks for a new code on its own.
        assertThat(campo(solicitar(mvc, "Cocina-Rosa"), "$.user_code")).isNotEqualTo(campo(vencida, "$.user_code"));
    }

    @Test
    void ca28_4_laAppVeLosDatosDeLaPcYSiNoLaReconoceLaPcNoSeVincula() throws Exception {
        String solicitud = solicitar(mvc, "Sala-Rosa");
        String codigo = campo(solicitud, "$.user_code");

        resolver(mvc, titular, "consulta", codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePc").value("Sala-Rosa"))
                .andExpect(jsonPath("$.sistemaOperativo").value("Windows 11 Home"))
                .andExpect(jsonPath("$.versionAgente").value("0.5.0"))
                .andExpect(jsonPath("$.expiraEn").exists());
        resolver(mvc, titular, "rechazo", codigo).andExpect(status().isNoContent());

        consultar(mvc, campo(solicitud, "$.device_code"))
                .andExpect(jsonPath("$.error").value("access_denied"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
        resolver(mvc, titular, "aprobacion", codigo).andExpect(status().isGone());
    }

    @Test
    void ca28_5_unFamiliarInvitadoNoPuedeVerNiAprobarElCodigo() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String tokenInvitado = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);
        String solicitud = solicitar(mvc, "Sala-Rosa");

        for (String accion : new String[] {"consulta", "aprobacion", "rechazo"}) {
            resolver(mvc, tokenInvitado, accion, campo(solicitud, "$.user_code"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
        }
        consultar(mvc, campo(solicitud, "$.device_code"))
                .andExpect(jsonPath("$.error").value("authorization_pending"));
    }

    @Test
    void ca28_6_cincoCodigosIncorrectosSeguidosBloqueanElIngresoPor15Minutos() throws Exception {
        String valida = campo(solicitar(mvc, "Sala-Rosa"), "$.user_code");
        for (int restantes = 4; restantes >= 1; restantes--) {
            resolver(mvc, titular, "consulta", "BBBB-BBBB")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("CODIGO_INCORRECTO"))
                    .andExpect(jsonPath("$.intentosRestantes").value(restantes));
        }
        String bloqueo = resolver(mvc, titular, "consulta", "BBBB-BBBB")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("VINCULACION_BLOQUEADA"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(java.time.Instant.parse(campo(bloqueo, "$.bloqueadaHasta")))
                .isEqualTo(reloj.instant().plus(Duration.ofMinutes(15)));

        // Even the right code is refused while blocked, and the PC is not linked.
        resolver(mvc, titular, "aprobacion", valida).andExpect(status().isLocked());

        reloj.avanzar(Duration.ofMinutes(15));
        String nueva = campo(solicitar(mvc, "Sala-Rosa"), "$.user_code");
        resolver(mvc, titular, "aprobacion", nueva).andExpect(status().isCreated());
    }

    @Test
    void ca28_6_unCodigoCorrectoReiniciaLaCuentaYLosIntentosSonPorCuenta() throws Exception {
        for (int i = 0; i < 4; i++) {
            resolver(mvc, titular, "consulta", "BBBB-BBBB").andExpect(status().isNotFound());
        }
        resolver(mvc, titular, "consulta", campo(solicitar(mvc, "Sala-Rosa"), "$.user_code"))
                .andExpect(status().isOk());
        resolver(mvc, titular, "consulta", "BBBB-BBBB")
                .andExpect(jsonPath("$.intentosRestantes").value(4));

        String otroTitular = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");
        resolver(mvc, otroTitular, "consulta", "BBBB-BBBB")
                .andExpect(jsonPath("$.intentosRestantes").value(4));
    }

    @Test
    void ca30_1_otraPcSeAgregaALaMismaViviendaSinCambiarLaPrimera() throws Exception {
        String primera = VinculacionDePrueba.vincular(mvc, titular, "Sala-Rosa");
        String segunda = VinculacionDePrueba.vincular(mvc, titular, "Cocina-Rosa");

        assertThat(campo(segunda, "$.hogar_id")).isEqualTo(hogar.toString());
        assertThat(campo(segunda, "$.pc_id")).isNotEqualTo(campo(primera, "$.pc_id"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Sala-Rosa"))
                .andExpect(jsonPath("$[1].nombre").value("Cocina-Rosa"));
    }

    @Test
    void elCodigoDeOtraViviendaSeApruebaParaLaViviendaDeQuienLoAprueba() throws Exception {
        String otra = ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis");
        String tokens = VinculacionDePrueba.vincular(mvc, campo(otra, "$.tokenAcceso"), "PC de Luis");

        assertThat(campo(tokens, "$.hogar_id")).isEqualTo(campo(otra, "$.hogarId"));
        mvc.perform(get("/api/pcs").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$").isEmpty());
    }
}
