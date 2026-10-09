package tech.tetengo.api.alertas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispositivoTest {

    private final UUID ana = UUID.randomUUID();

    @Test
    void unDispositivoNuevoEstaActivoYSinDireccionDelProveedor() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.ANDROID);
        assertThat(d.isActivo()).isTrue();
        assertThat(d.getReferenciaPush()).isNull();
    }

    @Test
    void registrarloOtraVezLoReactivaYOlvidaLaDireccionDelProveedor() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.ANDROID);
        d.asignarReferenciaPush("arn:endpoint");
        d.desactivar();
        assertThat(d.isActivo()).isFalse();

        d.asignar(ana, Plataforma.ANDROID);

        assertThat(d.isActivo()).isTrue();
        assertThat(d.getReferenciaPush()).isNull();
    }

    @Test
    void registrarloActivoEnLaMismaPlataformaConservaLaDireccion() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.IOS);
        d.asignarReferenciaPush("arn:endpoint");
        UUID beto = UUID.randomUUID();

        d.asignar(beto, Plataforma.IOS);

        assertThat(d.getUsuarioId()).isEqualTo(beto);
        assertThat(d.getReferenciaPush()).isEqualTo("arn:endpoint");
    }

    @Test
    void cambiarDePlataformaOlvidaLaDireccion() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.IOS);
        d.asignarReferenciaPush("arn:endpoint");

        d.asignar(ana, Plataforma.ANDROID);

        assertThat(d.getPlataforma()).isEqualTo(Plataforma.ANDROID);
        assertThat(d.getReferenciaPush()).isNull();
    }
}
