package tech.tetengo.api.hogares.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.hogares.application.LimpiarSubidasDeFotos;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** API contract §13: photos of the members and of the older adult, with two households. */
class FotosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LimpiarSubidasDeFotos limpiarSubidas;

    String titular;
    UUID titularId;
    UUID hogar;
    UUID invitadoId;
    String invitado;

    @BeforeEach
    void hogarConInvitado() throws Exception {
        String sesion = ApiDePrueba.titularConHogar(mvc, "carmen@correo.pe", "Carmen");
        titular = campo(sesion, "$.tokenAcceso");
        titularId = UUID.fromString(campo(sesion, "$.usuario.id"));
        hogar = UUID.fromString(campo(sesion, "$.hogarId"));
        invitadoId = ApiDePrueba.registrarCuenta(mvc, "luis@correo.pe", "secreta123", "Luis");
        DatosDePrueba.membresia(jdbc, hogar, invitadoId, Rol.INVITADO);
        invitado = JwtDePrueba.token(invitadoId, hogar, Rol.INVITADO);
    }

    private ResultActions pedir(String token, String de, String tipo, long tamano) throws Exception {
        return mvc.perform(post("/api/fotos/subidas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"de\":\"%s\",\"tipo\":\"%s\",\"tamanoBytes\":%d}".formatted(de, tipo, tamano)));
    }

    private ResultActions confirmar(String token, String subidaId) throws Exception {
        return mvc.perform(
                post("/api/fotos/subidas/" + subidaId + "/confirmacion").header("Authorization", bearer(token)));
    }

    /** Asks for an upload, "uploads" the image and returns the upload's id. */
    private String subir(String token, String de, byte[] imagen) throws Exception {
        String subidaId = campo(
                pedir(token, de, "image/jpeg", imagen.length)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.subidaId");
        fotos.subir("fotos/subidas/" + subidaId, imagen);
        return subidaId;
    }

    private String cambiarFoto(String token, String de) throws Exception {
        String subidaId = subir(token, de, jpeg(800, 600));
        confirmar(token, subidaId).andExpect(status().isOk());
        return subidaId;
    }

    private String json(String token, String ruta) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String claveDelUsuario(UUID usuario) {
        return jdbc.queryForObject("select foto_clave from cuentas where id = ?", String.class, usuario);
    }

    private String claveDelAdultoMayor(UUID hogarId) {
        return jdbc.queryForObject("select adulto_mayor_foto_clave from hogares where id = ?", String.class, hogarId);
    }

    @Test
    void laSubidaEsUnPutFirmadoDeDiezMinutosParaEseTipoYTamano() throws Exception {
        pedir(titular, "YO", "image/png", 1234)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subidaId").isNotEmpty())
                .andExpect(
                        jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://r2.prueba/fotos/subidas/")))
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("image/png"))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofMinutes(10)).toString()));

        assertThat(fotos.pedidas()).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo("image/png");
            assertThat(p.tamanoBytes()).isEqualTo(1234);
        });
    }

    @Test
    void unTipoOUnTamanoNoAdmitidosSeRechazan() throws Exception {
        pedir(titular, "YO", "image/gif", 100)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("FOTO_FORMATO_NO_ADMITIDO"));
        pedir(titular, "YO", "image/jpeg", 5L * 1024 * 1024 + 1)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.codigo").value("FOTO_MUY_GRANDE"));
        pedir(titular, "OTRO", "image/jpeg", 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.de").exists());
        pedir(titular, "YO", "image/jpeg", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tamanoBytes").exists());
    }

    @Test
    void laFotoPropiaSeGuardaCuadradaYReemplazaYBorraLaAnterior() throws Exception {
        String primera = subir(titular, "YO", jpeg(800, 600));
        confirmar(titular, primera)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url")
                        .value(org.hamcrest.Matchers.startsWith(
                                "https://r2.prueba/fotos/usuarios/%s/%s.jpg".formatted(titularId, primera))))
                .andExpect(jsonPath("$.expiraEn")
                        .value(reloj.instant().plus(Duration.ofHours(1)).toString()));

        String claveUno = claveDelUsuario(titularId);
        assertThat(claveUno).isEqualTo("fotos/usuarios/%s/%s.jpg".formatted(titularId, primera));
        BufferedImage guardada =
                ImageIO.read(new ByteArrayInputStream(fotos.objeto(claveUno).orElseThrow()));
        assertThat(guardada.getWidth()).isEqualTo(512);
        assertThat(guardada.getHeight()).isEqualTo(512);
        assertThat(fotos.objeto("fotos/subidas/" + primera))
                .as("the upload is deleted")
                .isEmpty();

        cambiarFoto(titular, "YO");
        assertThat(fotos.objeto(claveUno)).as("the previous photo is deleted").isEmpty();
        assertThat(claveDelUsuario(titularId)).isNotEqualTo(claveUno);

        mvc.perform(get("/api/cuenta").header("Authorization", bearer(titular)))
                .andExpect(
                        jsonPath("$.foto.url").value(org.hamcrest.Matchers.containsString(claveDelUsuario(titularId))))
                .andExpect(jsonPath("$.foto.expiraEn").isNotEmpty());
        // A member's own photo is not logged.
        esperarEventosPendientes();
        assertThat(JsonPath.<List<String>>read(json(titular, "/api/actividad"), "$.elementos[*].tipo"))
                .doesNotContain("FOTO_ADULTO_MAYOR_CAMBIADA");
    }

    @Test
    void unUsuarioSinHogarCambiaSuFoto() throws Exception {
        ApiDePrueba.registrarCuenta(mvc, "eva@correo.pe", "secreta123", "Eva");
        String eva = campo(ApiDePrueba.iniciarSesion(mvc, "eva@correo.pe", "secreta123"), "$.tokenAcceso");

        cambiarFoto(eva, "YO");

        mvc.perform(get("/api/cuenta").header("Authorization", bearer(eva)))
                .andExpect(jsonPath("$.foto.url").isNotEmpty());
    }

    @Test
    void laFotoDelAdultoMayorLaCambiaSoloElTitularYLaVeTodaLaFamilia() throws Exception {
        pedir(invitado, "ADULTO_MAYOR", "image/jpeg", 100)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));

        String subidaId = cambiarFoto(titular, "ADULTO_MAYOR");

        String clave = "fotos/hogares/%s/adulto-mayor/%s.jpg".formatted(hogar, subidaId);
        assertThat(claveDelAdultoMayor(hogar)).isEqualTo(clave);
        mvc.perform(get("/api/hogar").header("Authorization", bearer(invitado)))
                .andExpect(jsonPath("$.adultoMayor.foto.url").value(org.hamcrest.Matchers.containsString(clave)));
        mvc.perform(
                        put("/api/hogar/adulto-mayor")
                                .header("Authorization", bearer(titular))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"nombre\":\"Rosa Huamán\",\"edad\":78,\"direccion\":\"Lima\",\"convivencia\":\"SOLO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto.url").value(org.hamcrest.Matchers.containsString(clave)));

        mvc.perform(delete("/api/fotos/adulto-mayor").header("Authorization", bearer(invitado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
        mvc.perform(delete("/api/fotos/adulto-mayor").header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());
        assertThat(fotos.objeto(clave)).isEmpty();
        mvc.perform(get("/api/hogar").header("Authorization", bearer(invitado)))
                .andExpect(jsonPath("$.adultoMayor.foto").isEmpty());
        // Removing a missing photo also succeeds.
        mvc.perform(delete("/api/fotos/adulto-mayor").header("Authorization", bearer(titular)))
                .andExpect(status().isNoContent());

        esperarEventosPendientes();
        String actividad = json(invitado, "/api/actividad");
        assertThat(JsonPath.<List<String>>read(actividad, "$.elementos[*].tipo"))
                .containsExactly("FOTO_ADULTO_MAYOR_QUITADA", "FOTO_ADULTO_MAYOR_CAMBIADA");
        assertThat(JsonPath.<List<String>>read(actividad, "$.elementos[*].actor.tipo"))
                .containsOnly("FAMILIAR");
        assertThat(JsonPath.<List<String>>read(actividad, "$.elementos[*].actor.usuario.id"))
                .containsOnly(titularId.toString());
    }

    @Test
    void losFamiliaresMuestranTelefonoParentescoYFoto() throws Exception {
        mvc.perform(put("/api/cuenta")
                        .header("Authorization", bearer(invitado))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis\",\"telefono\":\"987 654 321\",\"parentesco\":\"Sobrino\"}"))
                .andExpect(status().isOk());
        cambiarFoto(invitado, "YO");

        String familiares = json(titular, "/api/familiares");
        assertThat(JsonPath.<List<String>>read(familiares, "$[?(@.usuarioId == '%s')].telefono".formatted(invitadoId)))
                .containsExactly("987 654 321");
        assertThat(JsonPath.<List<String>>read(
                        familiares, "$[?(@.usuarioId == '%s')].parentesco".formatted(invitadoId)))
                .containsExactly("Sobrino");
        assertThat(JsonPath.<List<String>>read(familiares, "$[?(@.usuarioId == '%s')].foto.url".formatted(invitadoId)))
                .singleElement()
                .asString()
                .contains(claveDelUsuario(invitadoId));
        assertThat(JsonPath.<List<Object>>read(familiares, "$[?(@.usuarioId == '%s')].foto".formatted(titularId)))
                .containsExactly((Object) null);
    }

    @Test
    void laConfirmacionValidaQuienCuandoYQueSeSubio() throws Exception {
        confirmar(titular, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SUBIDA_NO_ENCONTRADA"));

        // Someone else's upload.
        String ajena = subir(invitado, "YO", jpeg(10, 10));
        confirmar(titular, ajena).andExpect(status().isNotFound());

        // The file never arrived.
        String sinArchivo = campo(
                pedir(titular, "YO", "image/jpeg", 100)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.subidaId");
        confirmar(titular, sinArchivo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SUBIDA_NO_ENCONTRADA"));

        // Not an image, whatever its declared type.
        String falsa = subir(titular, "YO", "<html>no soy una foto</html>".getBytes());
        confirmar(titular, falsa)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("FOTO_FORMATO_NO_ADMITIDO"));

        // Bigger than 5 MB once uploaded.
        String grande = subir(titular, "YO", jpeg(10, 10));
        fotos.subir("fotos/subidas/" + grande, new byte[5 * 1024 * 1024 + 1]);
        confirmar(titular, grande)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.codigo").value("FOTO_MUY_GRANDE"));

        // More than an hour later.
        String tardia = subir(titular, "YO", jpeg(10, 10));
        reloj.avanzar(Duration.ofMinutes(61));
        confirmar(titular, tardia)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("SUBIDA_VENCIDA"));
        assertThat(claveDelUsuario(titularId)).isNull();
    }

    @Test
    void unaSubidaDelAdultoMayorNoSeConfirmaDesdeOtroHogarNiSeVeAlli() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "beto@correo.pe", "Beto");
        String tokenB = campo(otro, "$.tokenAcceso");
        UUID hogarB = UUID.fromString(campo(otro, "$.hogarId"));
        // Carmen also belongs to Beto's household, as its owner's guest.
        DatosDePrueba.membresia(jdbc, hogarB, titularId, Rol.INVITADO);

        String subidaId = subir(titular, "ADULTO_MAYOR", jpeg(100, 100));
        confirmar(JwtDePrueba.token(titularId, hogarB, Rol.INVITADO), subidaId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SUBIDA_NO_ENCONTRADA"));

        confirmar(titular, subidaId).andExpect(status().isOk());
        assertThat(claveDelAdultoMayor(hogarB)).isNull();
        mvc.perform(get("/api/hogar").header("Authorization", bearer(tokenB)))
                .andExpect(jsonPath("$.adultoMayor.foto").isEmpty());
        esperarEventosPendientes();
        assertThat(JsonPath.<List<String>>read(json(tokenB, "/api/actividad"), "$.elementos[*].tipo"))
                .doesNotContain("FOTO_ADULTO_MAYOR_CAMBIADA");
    }

    @Test
    void lasSubidasSinConfirmarSeBorranALas24Horas() throws Exception {
        String vieja = subir(titular, "YO", jpeg(10, 10));
        reloj.avanzar(Duration.ofHours(23));
        String reciente = subir(titular, "YO", jpeg(10, 10));
        reloj.avanzar(Duration.ofHours(1).plusSeconds(1));

        assertThat(limpiarSubidas.ejecutar()).isEqualTo(1);
        assertThat(limpiarSubidas.ejecutar()).as("idempotent").isZero();

        assertThat(fotos.objeto("fotos/subidas/" + vieja)).isEmpty();
        assertThat(fotos.objeto("fotos/subidas/" + reciente)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from subidas_de_fotos", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void quitarLaFotoPropiaLaBorra() throws Exception {
        cambiarFoto(invitado, "YO");
        String clave = claveDelUsuario(invitadoId);

        mvc.perform(delete("/api/fotos/yo").header("Authorization", bearer(invitado)))
                .andExpect(status().isNoContent());

        assertThat(claveDelUsuario(invitadoId)).isNull();
        assertThat(fotos.objeto(clave)).isEmpty();
        mvc.perform(delete("/api/fotos/otra").header("Authorization", bearer(invitado)))
                .andExpect(status().isNotFound());
    }

    /** A JPEG of that size: red, blue in the middle. */
    private static byte[] jpeg(int ancho, int alto) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.BLUE);
        g.fillRect(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        var salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpeg", salida);
        return salida.toByteArray();
    }
}
