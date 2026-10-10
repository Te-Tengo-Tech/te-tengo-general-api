package tech.tetengo.api.alertas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispositivoTest {

    private final UUID ana = UUID.randomUUID();
    private final Instant ahora = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void unDispositivoNuevoEstaActivoYSinDireccionDelProveedor() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.ANDROID, ahora);
        assertThat(d.isActivo()).isTrue();
        assertThat(d.getReferenciaPush()).isNull();
        assertThat(d.getVistoEn()).isEqualTo(ahora);
        assertThat(d.getDesactivadoEn()).isNull();
    }

    @Test
    void registrarloOtraVezLoReactivaYOlvidaLaDireccionDelProveedor() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.ANDROID, ahora);
        d.asignarReferenciaPush("arn:endpoint");
        d.desactivar(ahora.plusSeconds(60));
        assertThat(d.isActivo()).isFalse();
        assertThat(d.getDesactivadoEn()).isEqualTo(ahora.plusSeconds(60));

        d.asignar(ana, Plataforma.ANDROID, ahora.plusSeconds(120));

        assertThat(d.isActivo()).isTrue();
        assertThat(d.getReferenciaPush()).isNull();
        assertThat(d.getDesactivadoEn()).isNull();
    }

    @Test
    void registrarloSinCambiosActualizaCuandoSeVioPorUltimaVez() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.WEB, ahora);

        d.asignar(ana, Plataforma.WEB, ahora.plusSeconds(3600));

        assertThat(d.getVistoEn()).isEqualTo(ahora.plusSeconds(3600));
    }

    @Test
    void desactivarloDosVecesConservaLaPrimeraFecha() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.ANDROID, ahora);
        d.desactivar(ahora.plusSeconds(1));
        d.desactivar(ahora.plusSeconds(2));
        assertThat(d.getDesactivadoEn()).isEqualTo(ahora.plusSeconds(1));
    }

    @Test
    void registrarloActivoEnLaMismaPlataformaConservaLaDireccion() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.IOS, ahora);
        d.asignarReferenciaPush("arn:endpoint");
        UUID beto = UUID.randomUUID();

        d.asignar(beto, Plataforma.IOS, ahora);

        assertThat(d.getUsuarioId()).isEqualTo(beto);
        assertThat(d.getReferenciaPush()).isEqualTo("arn:endpoint");
    }

    @Test
    void cambiarDePlataformaOlvidaLaDireccion() {
        Dispositivo d = new Dispositivo("token", ana, Plataforma.IOS, ahora);
        d.asignarReferenciaPush("arn:endpoint");

        d.asignar(ana, Plataforma.ANDROID, ahora);

        assertThat(d.getPlataforma()).isEqualTo(Plataforma.ANDROID);
        assertThat(d.getReferenciaPush()).isNull();
    }
}
