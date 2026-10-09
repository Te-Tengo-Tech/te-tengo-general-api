package tech.tetengo.api.monitoreo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The URLs a live view session hands to the app (API contract §4). */
class PropiedadesDeVistaEnVivoTest {

    private static final UUID CAMARA = UUID.fromString("0192f6e4-0000-7000-8000-000000000001");

    private static PropiedadesDeVistaEnVivo conWebrtc(String urlWebrtc) {
        return new PropiedadesDeVistaEnVivo(
                null, "https://api.tetengo.reqsai.tech/vivo/", urlWebrtc, null, null, null, null);
    }

    @Test
    void urlWebrtcEsElWhepDeLaCamaraConElTokenDelEspectadorEnLaQuery() {
        var propiedades = conWebrtc("https://api.tetengo.reqsai.tech/vivo-webrtc/camaras/{camaraId}/whep");
        assertThat(propiedades.urlWebrtcDe(CAMARA, "a+b/c"))
                .hasValueSatisfying(url -> assertThat(url)
                        .hasToString("https://api.tetengo.reqsai.tech/vivo-webrtc/camaras/" + CAMARA
                                + "/whep?token=a%2Bb%2Fc"));
    }

    @Test
    void sinPlantillaNoHayWebrtc() {
        assertThat(conWebrtc(null).urlWebrtcDe(CAMARA, "t")).isEmpty();
        assertThat(conWebrtc("").urlWebrtcDe(CAMARA, "t")).isEmpty();
        assertThat(conWebrtc("  ").urlWebrtcDe(CAMARA, "t")).isEmpty();
    }

    @Test
    void urlTransmisionSigueSiendoElPlaylistHls() {
        assertThat(conWebrtc(null).urlTransmisionDe(CAMARA, "t"))
                .hasToString("https://api.tetengo.reqsai.tech/vivo/camaras/" + CAMARA + "/index.m3u8?token=t");
    }
}
