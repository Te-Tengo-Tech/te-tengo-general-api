package tech.tetengo.api.movimientos.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.MovimientosApiDePrueba.confirmar;
import static tech.tetengo.api.support.MovimientosApiDePrueba.otorgar;
import static tech.tetengo.api.support.MovimientosApiDePrueba.pedirSubida;
import static tech.tetengo.api.support.MovimientosApiDePrueba.retencion;

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
import tech.tetengo.api.alertas.application.EliminarGrabaciones;
import tech.tetengo.api.movimientos.application.TareasDeMovimientos;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;
import tech.tetengo.api.support.MovimientosApiDePrueba;

/**
 * US-35 and US-37: the consent to store movements, the PC's uploads straight to object storage with an
 * index, the cloud retention and the deletions.
 */
class DatosDeMovimientoIntegrationTest extends AbstractIntegrationTest {

    private static final DateTimeFormatter RUTA =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm").withZone(ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TareasDeMovimientos tareas;

    @Autowired
    EliminarGrabaciones eliminarGrabaciones;

    String sesion;
    String titular;
    UUID hogar;
    String camara;
    String agente;

    @BeforeEach
    void hogarConCamara() throws Exception {
        sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(sesion, "$.usuario.id")), "token-de-carmen");
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, sesion, "Sala");
        camara = campo(registro, "$.camaraId");
        agente = campo(registro, "$.token");
    }

    private Instant hace(Duration duracion) {
        return reloj.instant().minus(duracion).truncatedTo(ChronoUnit.MINUTES);
    }

    /** Asks, uploads and confirms a 5-minute file that starts at {@code desde}; returns its id. */
    private String subir(Instant desde) throws Exception {
        String subida = pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        movimientos.subir(clave(desde), 4000);
        confirmar(mvc, agente, campo(subida, "$.archivoId")).andExpect(status().isOk());
        return campo(subida, "$.archivoId");
    }

    private String clave(Instant desde) {
        return "movimientos/%s/%s/%s.parquet".formatted(hogar, camara, RUTA.format(desde));
    }

    private String ver(String ruta) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", bearer(titular)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void ca35_1_sinConsentimientoDeMonitoreoNoSePuedeRegistrar() throws Exception {
        String otro = campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.tokenAcceso");
        otorgar(mvc, otro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO"));
        mvc.perform(get("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(otro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.versionTextoVigente").value("2"))
                .andExpect(jsonPath("$.constancia").doesNotExist());
    }

    @Test
    void ca35_2_quedaLaConstanciaConQuienLoOtorgoQuienLoRegistroCuandoYLaVersion() throws Exception {
        otorgar(mvc, titular)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.constancia.otorgadoPor").value("Rosa Huamán"))
                .andExpect(jsonPath("$.constancia.representante").value(false))
                .andExpect(jsonPath("$.constancia.registradoPor.nombre").value("Carmen"))
                .andExpect(jsonPath("$.constancia.versionTexto").value("2"))
                .andExpect(jsonPath("$.constancia.otorgadoEn").exists());
        mvc.perform(
                        post("/api/hogar/consentimiento-movimientos")
                                .header("Authorization", bearer(titular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"otorgadoPor\":\"Rosa\",\"representante\":false,\"aceptado\":false,\"versionTexto\":\"2\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_NO_ACEPTADO"));
        mvc.perform(
                        post("/api/hogar/consentimiento-movimientos")
                                .header("Authorization", bearer(titular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"otorgadoPor\":\"Rosa\",\"representante\":true,\"aceptado\":true,\"versionTexto\":\"0\"}"))
                .andExpect(jsonPath("$.codigo").value("VERSION_DE_TEXTO_INVALIDA"));
        // The previous text version is refused too: the app must show the text in force.
        mvc.perform(
                        post("/api/hogar/consentimiento-movimientos")
                                .header("Authorization", bearer(titular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"otorgadoPor\":\"Rosa\",\"representante\":false,\"aceptado\":true,\"versionTexto\":\"1\"}"))
                .andExpect(jsonPath("$.codigo").value("VERSION_DE_TEXTO_INVALIDA"));

        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        String tokenInvitado = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);
        otorgar(mvc, tokenInvitado).andExpect(status().isForbidden());
        mvc.perform(get("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(tokenInvitado)))
                .andExpect(jsonPath("$.vigente").value(true));
    }

    @Test
    void ca35_2_unConsentimientoDeUnaVersionAnteriorSigueVigenteConSuVersion() throws Exception {
        // Given with text version 1, before version 2 was published: nobody is asked to consent again.
        jdbc.update("""
                insert into consentimientos_de_movimientos
                    (id, hogar_id, otorgado_en, otorgado_por, representante, registrado_por, version_texto,
                     creado_en, actualizado_en)
                values (?, ?, now(), 'Rosa Huamán', false, ?, '1', now(), now())
                """, UUID.randomUUID(), hogar, UUID.fromString(campo(sesion, "$.usuario.id")));
        mvc.perform(get("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.versionTextoVigente").value("2"))
                .andExpect(jsonPath("$.constancia.versionTexto").value("1"));
        retencion(mvc, titular, 30).andExpect(status().isOk());
    }

    @Test
    void ca35_3_sinConsentimientoDeMovimientosNoSeGuardaNada() throws Exception {
        mvc.perform(get("/api/agente/movimientos/estado").header("Authorization", bearer(agente)))
                .andExpect(jsonPath("$.consentimientoVigente").value(false))
                .andExpect(jsonPath("$.guardarEnLaPc").value(false))
                .andExpect(jsonPath("$.guardarEnLaNube").value(false));
        pedirSubida(mvc, agente, hace(Duration.ofMinutes(10)), hace(Duration.ofMinutes(5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
        retencion(mvc, titular, 30).andExpect(status().isConflict());
    }

    @Test
    void ca37_1_laNubeVieneDesactivadaYSoloAceptaLosPlazosDeLaLista() throws Exception {
        otorgar(mvc, titular);
        mvc.perform(get("/api/hogar/movimientos-en-la-nube").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.retencionDias").doesNotExist())
                .andExpect(jsonPath("$.opciones.length()").value(4))
                .andExpect(jsonPath("$.maximoRecomendadoDias").value(365))
                .andExpect(jsonPath("$.mbPorCamaraPorDia.minimo").value(40))
                .andExpect(jsonPath("$.consentimientoVigente").value(true));
        pedirSubida(mvc, agente, hace(Duration.ofMinutes(10)), hace(Duration.ofMinutes(5)))
                .andExpect(jsonPath("$.codigo").value("MOVIMIENTOS_EN_LA_NUBE_DESACTIVADOS"));
        retencion(mvc, titular, 15)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RETENCION_INVALIDA"));
        retencion(mvc, titular, 30)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retencionDias").value(30));
        mvc.perform(get("/api/agente/movimientos/estado").header("Authorization", bearer(agente)))
                .andExpect(jsonPath("$.guardarEnLaNube").value(true))
                .andExpect(jsonPath("$.retencionDias").value(30));
    }

    @Test
    void ca37_2_laPcSubeDirectoAlAlmacenamientoYElBackendSoloGuardaElIndice() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        Instant desde = hace(Duration.ofMinutes(10));

        String subida = pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("application/vnd.apache.parquet"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(subida, "$.urlSubida")).isEqualTo("https://r2.prueba/" + clave(desde) + "?metodo=PUT");

        // Not uploaded yet.
        confirmar(mvc, agente, campo(subida, "$.archivoId"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_SUBIDO"));
        movimientos.subir(clave(desde), 3900);
        confirmar(mvc, agente, campo(subida, "$.archivoId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.tamanoBytes").value(3900));
        pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_YA_SUBIDO"))
                .andExpect(jsonPath("$.archivoId").value(campo(subida, "$.archivoId")));

        String nube = ver("/api/hogar/movimientos-en-la-nube");
        assertThat(campo(nube, "$.archivos")).isEqualTo("1");
        assertThat(campo(nube, "$.tamanoBytes")).isEqualTo("3900");
        assertThat(Instant.parse(campo(nube, "$.datosDesde"))).isEqualTo(desde);
        assertThat(jdbc.queryForObject(
                        "select archivos from resumenes_de_movimiento where camara_id = ?::uuid",
                        Integer.class,
                        camara))
                .isEqualTo(1);
    }

    @Test
    void ca37_3_unArchivoPendienteSePuedePedirDeNuevoYLosDatosSeValidan() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 7);
        Instant desde = hace(Duration.ofHours(30));
        String primera = campo(
                pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.archivoId");
        assertThat(campo(
                        pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.archivoId"))
                .isEqualTo(primera);

        pedirSubida(
                        mvc,
                        agente,
                        hace(Duration.ofDays(8)),
                        hace(Duration.ofDays(8)).plusSeconds(300))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOVIMIENTOS_FUERA_DEL_PLAZO"));
        pedirSubida(mvc, agente, desde, desde.minusSeconds(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.hasta").exists());
        pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(11))).andExpect(status().isBadRequest());
        pedirSubida(
                        mvc,
                        agente,
                        reloj.instant().plus(Duration.ofMinutes(5)),
                        reloj.instant().plus(Duration.ofMinutes(9)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.desde").exists());
    }

    @Test
    void ca37_4_laEliminacionDiariaBorraLoMasAntiguoQueElPlazo() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        Instant viejo = hace(Duration.ofDays(20));
        Instant nuevo = hace(Duration.ofDays(2));
        subir(viejo);
        subir(nuevo);

        tareas.aplicarRetencion();
        assertThat(movimientos.subidas()).containsExactlyInAnyOrder(clave(viejo), clave(nuevo));

        // The owner shortens it to 7 days: the older file goes at the next daily run.
        retencion(mvc, titular, 7).andExpect(status().isOk());
        tareas.aplicarRetencion();
        tareas.aplicarRetencion();
        assertThat(movimientos.subidas()).containsExactly(clave(nuevo));
        assertThat(campo(ver("/api/hogar/movimientos-en-la-nube"), "$.archivos"))
                .isEqualTo("1");

        // Turned off: nothing is kept.
        retencion(mvc, titular, null).andExpect(jsonPath("$.retencionDias").doesNotExist());
        tareas.aplicarRetencion();
        assertThat(movimientos.subidas()).isEmpty();
    }

    @Test
    void ca35_4_alRevocarloSeBorranLosMovimientosDeLaNubeYSeAvisaAlTerminar() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        Instant desde = hace(Duration.ofHours(1));
        subir(desde);

        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eliminacionProgramada").value(true))
                .andExpect(jsonPath("$.archivos").value(1));
        String revocado = ver("/api/hogar/consentimiento-movimientos");
        assertThat(campo(revocado, "$.vigente")).isEqualTo("false");
        assertThat(campo(revocado, "$.eliminacion.estado")).isEqualTo("PROGRAMADA");
        // Nothing more is stored, and the owner must choose a retention again after a new consent.
        pedirSubida(mvc, agente, hace(Duration.ofMinutes(10)), hace(Duration.ofMinutes(5)))
                .andExpect(status().isConflict());
        assertThat(campo(ver("/api/hogar/movimientos-en-la-nube"), "$.retencionDias"))
                .isNull();

        tareas.eliminarRevocados();
        tareas.eliminarRevocados();

        assertThat(movimientos.subidas()).isEmpty();
        assertThat(movimientos.eliminadas()).containsExactly(clave(desde));
        String terminado = ver("/api/hogar/consentimiento-movimientos");
        assertThat(campo(terminado, "$.eliminacion.estado")).isEqualTo("TERMINADA");
        assertThat(campo(terminado, "$.eliminacion.archivos")).isEqualTo("1");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> push.deTipo(TipoAviso.MOVIMIENTOS_ELIMINADOS).size() == 1);
        // The monitoring consent and the alerts stay.
        mvc.perform(get("/api/hogar/consentimiento").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.vigente").value(true));
        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
    }

    @Test
    void ca09_1_revocarElMonitoreoBorraLosMovimientosConLasGrabaciones() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        subir(hace(Duration.ofHours(1)));

        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> "false".equals(campo(ver("/api/hogar/consentimiento-movimientos"), "$.vigente")));
        assertThat(campo(ver("/api/hogar"), "$.eliminacion.movimientos")).isEqualTo("1");
        esperarEventosPendientes();

        eliminarGrabaciones.ejecutar();

        assertThat(movimientos.subidas()).isEmpty();
        String hogarJson = ver("/api/hogar");
        assertThat(campo(hogarJson, "$.eliminacion.estado")).isEqualTo("TERMINADA");
        assertThat(campo(hogarJson, "$.eliminacion.movimientos")).isEqualTo("1");
        assertThat(campo(ver("/api/hogar/movimientos-en-la-nube"), "$.archivos"))
                .isEqualTo("0");
    }

    @Test
    void multitenancy_otraViviendaNoVeNiConfirmaLosMovimientos() throws Exception {
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
        String archivo = subir(hace(Duration.ofHours(1)));
        String otra = ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis");
        String otroAgente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otra, "Sala"), "$.token");

        confirmar(mvc, otroAgente, archivo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_ENCONTRADO"));
        mvc.perform(get("/api/hogar/movimientos-en-la-nube")
                        .header("Authorization", bearer(campo(otra, "$.tokenAcceso"))))
                .andExpect(jsonPath("$.archivos").value(0))
                .andExpect(jsonPath("$.consentimientoVigente").value(false));
    }
}
