package tech.tetengo.api.shared.infrastructure.web.limites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FiltroDeLimitePorIpTest {

    @Test
    void unaDireccionIpv4EsSuPropiaCubeta() {
        assertThat(FiltroDeLimitePorIp.claveDeCliente("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(FiltroDeLimitePorIp.claveDeCliente("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void lasDireccionesIpv6DeLaMismaRed64CompartenCubeta() {
        String una = FiltroDeLimitePorIp.claveDeCliente("2001:db8:1:2:aaaa::1");
        String otra = FiltroDeLimitePorIp.claveDeCliente("2001:db8:1:2:bbbb::2");
        String deOtraRed = FiltroDeLimitePorIp.claveDeCliente("2001:db8:1:3::1");

        assertThat(una).isEqualTo(otra).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(deOtraRed).isNotEqualTo(una);
    }

    @Test
    void unaDireccionQueNoEsIpSeUsaTalCual() {
        assertThat(FiltroDeLimitePorIp.claveDeCliente("no-es-una-ip")).isEqualTo("no-es-una-ip");
        assertThat(FiltroDeLimitePorIp.claveDeCliente(null)).isEqualTo("desconocida");
    }
}
