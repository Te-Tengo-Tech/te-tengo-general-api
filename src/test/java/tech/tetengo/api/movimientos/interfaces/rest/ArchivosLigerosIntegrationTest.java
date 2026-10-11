package tech.tetengo.api.movimientos.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.MovimientosApiDePrueba.confirmar;
import static tech.tetengo.api.support.MovimientosApiDePrueba.confirmarLigero;
import static tech.tetengo.api.support.MovimientosApiDePrueba.pedirLigero;
import static tech.tetengo.api.support.MovimientosApiDePrueba.pedirSubida;
import static tech.tetengo.api.support.MovimientosApiDePrueba.retencion;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.alertas.application.EliminarGrabaciones;
import tech.tetengo.api.movimientos.application.TareasDeMovimientos;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.MovimientosApiDePrueba;

/**
 * ADR 0011 (US-37, US-39): the light replay file the PC uploads next to each movement file, its index
 * columns, and its deletion with the movement file.
 */
class ArchivosLigerosIntegrationTest extends AbstractIntegrationTest {

    private static final DateTimeFormatter RUTA =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm").withZone(ZoneOffset.UTC);
    private static final String SHA_LIGERO = "B".repeat(64);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TareasDeMovimientos tareas;

    @Autowired
    EliminarGrabaciones eliminarGrabaciones;

    String titular;
    UUID hogar;
    String camara;
    String agente;

    @BeforeEach
    void hogarConCamaraYNube() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        String registro = ApiDePrueba.agenteConConsentimiento(mvc, jdbc, sesion, "Sala");
        camara = campo(registro, "$.camaraId");
        agente = campo(registro, "$.token");
        MovimientosApiDePrueba.guardarEnLaNube(mvc, titular, 30);
    }

    private Instant hace(Duration duracion) {
        return reloj.instant().minus(duracion).truncatedTo(ChronoUnit.MINUTES);
    }

    private String clave(Instant desde) {
        return "movimientos/%s/%s/%s.parquet".formatted(hogar, camara, RUTA.format(desde));
    }

    private String claveLigera(Instant desde) {
        return "movimientos/%s/%s/%s.ttmov".formatted(hogar, camara, RUTA.format(desde));
    }

    /** Asks, uploads and confirms the movement file that starts at {@code desde}; returns its id. */
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

    /** The movement file and its light file, both confirmed; returns the movement file's id. */
    private String subirConLigero(Instant desde) throws Exception {
        String archivo = subir(desde);
        pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO).andExpect(status().isCreated());
        movimientos.subir(claveLigera(desde), 2400);
        confirmarLigero(mvc, agente, archivo).andExpect(status().isOk());
        return archivo;
    }

    private Map<String, Object> fila(String archivoId) {
        return jdbc.queryForMap(
                "select ligero_tamano_bytes, ligero_sha256, ligero_subido_en from archivos_de_movimiento where id = ?::uuid",
                archivoId);
    }

    @Test
    void laPcSubeElArchivoLigeroJuntoAlDeMovimientosYLoConfirma() throws Exception {
        Instant desde = hace(Duration.ofMinutes(10));
        String archivo = subir(desde);

        String subida = pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("application/gzip"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(campo(subida, "$.urlSubida")).isEqualTo("https://r2.prueba/" + claveLigera(desde) + "?metodo=PUT");
        assertThat(fila(archivo).get("ligero_sha256")).isEqualTo("b".repeat(64));
        assertThat(fila(archivo).get("ligero_subido_en")).isNull();

        // Not uploaded yet.
        confirmarLigero(mvc, agente, archivo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_SUBIDO"));
        // Asking again before the confirmation replaces the size and the checksum.
        pedirLigero(mvc, agente, archivo, 2600, "c".repeat(64)).andExpect(status().isCreated());
        assertThat(fila(archivo).get("ligero_tamano_bytes")).isEqualTo(2600L);
        assertThat(fila(archivo).get("ligero_sha256")).isEqualTo("c".repeat(64));

        movimientos.subir(claveLigera(desde), 2590);
        confirmarLigero(mvc, agente, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivoId").value(archivo))
                .andExpect(jsonPath("$.tamanoBytes").value(2590));
        // Idempotent.
        confirmarLigero(mvc, agente, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanoBytes").value(2590));
        pedirLigero(mvc, agente, archivo, 2600, SHA_LIGERO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_YA_SUBIDO"))
                .andExpect(jsonPath("$.archivoId").value(archivo));

        // The cloud's size counts both files.
        mvc.perform(get("/api/hogar/movimientos-en-la-nube").header("Authorization", bearer(titular)))
                .andExpect(jsonPath("$.archivos").value(1))
                .andExpect(jsonPath("$.tamanoBytes").value(4000 + 2590));
    }

    @Test
    void losDatosDelArchivoLigeroSeValidan() throws Exception {
        String archivo = subir(hace(Duration.ofMinutes(10)));

        pedirLigero(mvc, agente, archivo, 0, SHA_LIGERO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.tamanoBytes").exists());
        pedirLigero(mvc, agente, archivo, 50L * 1024 * 1024 + 1, SHA_LIGERO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tamanoBytes").exists());
        pedirLigero(mvc, agente, archivo, 2500, "xyz")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.sha256").exists());
        pedirLigero(mvc, agente, UUID.randomUUID().toString(), 2500, SHA_LIGERO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_ENCONTRADO"));
        confirmarLigero(mvc, agente, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_ENCONTRADO"));
    }

    @Test
    void sinNubeOSinConsentimientoNoSeSubeYUnaSubidaEnCursoSeBorra() throws Exception {
        Instant desde = hace(Duration.ofMinutes(10));
        String archivo = subir(desde);
        pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO).andExpect(status().isCreated());
        movimientos.subir(claveLigera(desde), 2500);

        // Revoked between the PUT and the confirmation: the object is deleted and the row forgets it.
        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());
        confirmarLigero(mvc, agente, archivo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
        assertThat(movimientos.eliminadas()).contains(claveLigera(desde));
        assertThat(movimientos.subidas()).doesNotContain(claveLigera(desde));
        assertThat(fila(archivo).get("ligero_sha256")).isNull();
        pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_CONSENTIMIENTO_DE_MOVIMIENTOS"));
    }

    @Test
    void sinRetencionEnLaNubeNoSePideElArchivoLigero() throws Exception {
        String archivo = subir(hace(Duration.ofMinutes(10)));
        retencion(mvc, titular, null).andExpect(status().isOk());

        pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MOVIMIENTOS_EN_LA_NUBE_DESACTIVADOS"));
    }

    @Test
    void sinConsentimientoAlConfirmarElArchivoDeMovimientosSeBorranAmbos() throws Exception {
        Instant desde = hace(Duration.ofMinutes(10));
        String subida = pedirSubida(mvc, agente, desde, desde.plus(Duration.ofMinutes(5)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String archivo = campo(subida, "$.archivoId");
        pedirLigero(mvc, agente, archivo, 2500, SHA_LIGERO).andExpect(status().isCreated());
        movimientos.subir(clave(desde), 4000);
        movimientos.subir(claveLigera(desde), 2500);

        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)));
        confirmar(mvc, agente, archivo).andExpect(status().isConflict());

        assertThat(movimientos.eliminadas()).contains(clave(desde), claveLigera(desde));
        assertThat(movimientos.subidas()).isEmpty();
    }

    @Test
    void laRetencionYLasRevocacionesBorranTambienLosArchivosLigeros() throws Exception {
        Instant viejo = hace(Duration.ofDays(20));
        Instant nuevo = hace(Duration.ofHours(1));
        subirConLigero(viejo);
        subirConLigero(nuevo);

        // CA-37.4: the shorter retention deletes the older file and its light file.
        retencion(mvc, titular, 7).andExpect(status().isOk());
        tareas.aplicarRetencion();
        assertThat(movimientos.subidas()).containsExactlyInAnyOrder(clave(nuevo), claveLigera(nuevo));
        assertThat(movimientos.eliminadas()).contains(clave(viejo), claveLigera(viejo));

        // CA-35.4: revoking the consent deletes the rest.
        mvc.perform(delete("/api/hogar/consentimiento-movimientos").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());
        tareas.eliminarRevocados();
        assertThat(movimientos.subidas()).isEmpty();
        assertThat(movimientos.eliminadas()).contains(clave(nuevo), claveLigera(nuevo));
    }

    @Test
    void revocarElMonitoreoBorraLosArchivosLigeros() throws Exception {
        Instant desde = hace(Duration.ofHours(1));
        subirConLigero(desde);

        mvc.perform(delete("/api/hogar/consentimiento").header("Authorization", bearer(titular)))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> "false"
                        .equals(campo(
                                mvc.perform(get("/api/hogar/consentimiento-movimientos")
                                                .header("Authorization", bearer(titular)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString(),
                                "$.vigente")));
        esperarEventosPendientes();

        eliminarGrabaciones.ejecutar();

        assertThat(movimientos.subidas()).isEmpty();
        assertThat(movimientos.eliminadas()).contains(clave(desde), claveLigera(desde));
    }

    @Test
    void multitenancy_otraViviendaNoSubeNiConfirmaElArchivoLigeroAjeno() throws Exception {
        Instant desde = hace(Duration.ofHours(1));
        String archivo = subir(desde);
        String otra = ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis");
        String otroAgente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, otra, "Sala"), "$.token");
        MovimientosApiDePrueba.guardarEnLaNube(mvc, campo(otra, "$.tokenAcceso"), 30);

        pedirLigero(mvc, otroAgente, archivo, 2500, SHA_LIGERO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_ENCONTRADO"));
        movimientos.subir(claveLigera(desde), 2500);
        confirmarLigero(mvc, otroAgente, archivo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_ENCONTRADO"));
        assertThat(fila(archivo).get("ligero_sha256")).isNull();
        assertThat(fila(archivo).get("ligero_subido_en")).isNull();
    }
}
