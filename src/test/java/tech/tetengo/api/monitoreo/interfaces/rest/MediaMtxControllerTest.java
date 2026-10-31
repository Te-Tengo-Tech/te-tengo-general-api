package tech.tetengo.api.monitoreo.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** How the hook reads MediaMTX's credentials and where it accepts them from. */
class MediaMtxControllerTest {

    private static String basica(String usuarioYClave) {
        return "Basic " + Base64.getEncoder().encodeToString(usuarioYClave.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void elSecretoEsLaClaveDeLasCredencialesBasicas() {
        assertThat(MediaMtxController.claveBasica(basica("mediamtx:s3cr3t-_x"))).contains("s3cr3t-_x");
        assertThat(MediaMtxController.claveBasica("basic " + basica("m:a:b").substring(6)))
                .contains("a:b");
        assertThat(MediaMtxController.claveBasica(basica(":solo-clave"))).contains("solo-clave");
        assertThat(MediaMtxController.claveBasica(basica("sin-separador"))).isEmpty();
        assertThat(MediaMtxController.claveBasica("Basic %%%")).isEmpty();
        assertThat(MediaMtxController.claveBasica("Bearer abc")).isEmpty();
        assertThat(MediaMtxController.claveBasica(null)).isEmpty();
    }

    @Test
    void soloLaRedInternaLlamaAlGancho() {
        for (String interna : new String[] {
            "127.0.0.1", "::1", "10.1.2.3", "172.18.0.4", "192.168.65.1", "169.254.1.1", "fd00::1", "fe80::1"
        }) {
            assertThat(RedInterna.contiene(interna)).as(interna).isTrue();
        }
        for (String externa :
                new String[] {"203.0.113.7", "104.28.195.72", "172.32.0.1", "2001:db8::1", "", "api", "no es una ip"}) {
            assertThat(RedInterna.contiene(externa)).as(externa).isFalse();
        }
        assertThat(RedInterna.contiene(null)).isFalse();
    }
}
