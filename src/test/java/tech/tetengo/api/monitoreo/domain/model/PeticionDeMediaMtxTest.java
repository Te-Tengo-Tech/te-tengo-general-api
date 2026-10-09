package tech.tetengo.api.monitoreo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx.Tipo;

class PeticionDeMediaMtxTest {

    private static final UUID CAMARA = UUID.fromString("0192f6e4-0000-7000-8000-000000000001");

    private static PeticionDeMediaMtx peticion(String accion, String usuario, String clave, String ruta, String query) {
        return new PeticionDeMediaMtx(accion, usuario, clave, ruta, query, "hls");
    }

    private static PeticionDeMediaMtx leerPor(String protocolo) {
        return new PeticionDeMediaMtx("read", null, null, null, null, protocolo);
    }

    @Test
    void soloPublicarYLeerPuedenPermitirse() {
        assertThat(peticion("publish", null, null, null, null).tipo()).isEqualTo(Tipo.PUBLICAR);
        assertThat(peticion("read", null, null, null, null).tipo()).isEqualTo(Tipo.LEER);
        assertThat(peticion("playback", null, null, null, null).tipo()).isEqualTo(Tipo.LEER);
        assertThat(peticion("api", null, null, null, null).tipo()).isEqualTo(Tipo.OTRA);
        assertThat(peticion("metrics", null, null, null, null).tipo()).isEqualTo(Tipo.OTRA);
        assertThat(peticion("pprof", null, null, null, null).tipo()).isEqualTo(Tipo.OTRA);
        assertThat(peticion(null, null, null, null, null).tipo()).isEqualTo(Tipo.OTRA);
    }

    @Test
    void soloSeLeePorHlsOWebRtc() {
        assertThat(leerPor("hls").tipo()).isEqualTo(Tipo.LEER);
        assertThat(leerPor("webrtc").tipo()).isEqualTo(Tipo.LEER);
        assertThat(leerPor("rtsp").tipo()).isEqualTo(Tipo.OTRA);
        assertThat(leerPor("rtmp").tipo()).isEqualTo(Tipo.OTRA);
        assertThat(leerPor("srt").tipo()).isEqualTo(Tipo.OTRA);
        assertThat(leerPor(null).tipo()).isEqualTo(Tipo.OTRA);
    }

    @Test
    void laRutaEsLaDeUnaCamara() {
        assertThat(peticion("read", null, null, "camaras/" + CAMARA, null).camara())
                .contains(CAMARA);
        assertThat(peticion("read", null, null, "camaras/" + CAMARA + "/x", null)
                        .camara())
                .isEmpty();
        assertThat(peticion("read", null, null, "otra/" + CAMARA, null).camara())
                .isEmpty();
        assertThat(peticion("read", null, null, "camaras/no-es-uuid", null).camara())
                .isEmpty();
        assertThat(peticion("read", null, null, null, null).camara()).isEmpty();
    }

    @Test
    void elAgentePublicaConSuUsuarioYSuClave() {
        assertThat(peticion("publish", "agente", "clave", null, null).claveDelAgente())
                .contains("clave");
        assertThat(peticion("publish", "otro", "clave", null, null).claveDelAgente())
                .isEmpty();
        assertThat(peticion("publish", "agente", " ", null, null).claveDelAgente())
                .isEmpty();
        assertThat(peticion("publish", "agente", null, null, null).claveDelAgente())
                .isEmpty();
    }

    @Test
    void elEspectadorLeeConElTokenDeLaQuery() {
        assertThat(peticion("read", null, null, null, "token=abc").tokenDeEspectador())
                .contains("abc");
        assertThat(peticion("read", null, null, null, "cookieCheck=1&token=a%2Bb")
                        .tokenDeEspectador())
                .contains("a+b");
        assertThat(peticion("read", null, null, null, "token=").tokenDeEspectador())
                .isEmpty();
        assertThat(peticion("read", null, null, null, "otro=abc").tokenDeEspectador())
                .isEmpty();
        assertThat(peticion("read", null, null, null, "").tokenDeEspectador()).isEmpty();
        assertThat(peticion("read", null, null, null, null).tokenDeEspectador()).isEmpty();
    }
}
