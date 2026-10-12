package tech.tetengo.api.shared.infrastructure.web.limites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.infrastructure.web.limites.LimitadorDeSolicitudes.Limite;
import tech.tetengo.api.support.RelojDePrueba;

class LimitadorDeSolicitudesTest {

    private static final List<Limite> DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA =
            List.of(new Limite(10, Duration.ofMinutes(1)), new Limite(60, Duration.ofHours(1)));

    private final RelojDePrueba reloj = new RelojDePrueba();
    private final LimitadorDeSolicitudes limitador = new LimitadorDeSolicitudes(reloj, 1000);

    @Test
    void dejaPasarHastaElLimiteYLuegoDiceCuantoEsperar() {
        for (int i = 0; i < 10; i++) {
            assertThat(limitador
                            .consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA)
                            .permitido())
                    .isTrue();
        }

        var rechazo = limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);

        assertThat(rechazo.permitido()).isFalse();
        // One token every 6 s.
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void quienEsperaLoIndicadoPasaYUnRechazoNoCuenta() {
        for (int i = 0; i < 10; i++) {
            limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);
        }
        for (int i = 0; i < 5; i++) {
            assertThat(limitador
                            .consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA)
                            .permitido())
                    .isFalse();
        }

        reloj.avanzar(Duration.ofSeconds(6));

        assertThat(limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).permitido())
                .isTrue();
        assertThat(limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).permitido())
                .isFalse();
    }

    @Test
    void elLimitePorHoraSeImponeAunqueCadaMinutoQuedeBajoSuLimite() {
        int permitidas = 0;
        for (int minuto = 0; minuto < 10; minuto++) {
            for (int i = 0; i < 10; i++) {
                if (limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).permitido()) {
                    permitidas++;
                }
            }
            reloj.avanzar(Duration.ofMinutes(1));
        }

        // 60 at once, plus what the hour bucket refilled in 9 minutes (1 per minute).
        assertThat(permitidas).isEqualTo(69);
        var rechazo = limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);
        // 10 minutes have passed: the hour bucket has refilled 10 tokens, 9 of them already taken.
        assertThat(rechazo.permitido()).isTrue();
        assertThat(limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).reintentarEn())
                .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void cadaClaveTieneSusPropiasCubetas() {
        for (int i = 0; i < 10; i++) {
            limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);
        }

        assertThat(limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).permitido())
                .isFalse();
        assertThat(limitador.consumir("b", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA).permitido())
                .isTrue();
    }

    @Test
    void olvidaLasClavesQueYaSeRellenaron() {
        for (int i = 0; i < 5; i++) {
            limitador.consumir("a", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);
        }
        limitador.consumir("b", List.of(new Limite(1, Duration.ofMinutes(1))));
        assertThat(limitador.claves()).isEqualTo(2);

        reloj.avanzar(Duration.ofMinutes(2));
        limitador.consumir("c", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);

        // "b" refilled in a minute; "a" still misses part of its hour.
        assertThat(limitador.claves()).isEqualTo(2);
        reloj.avanzar(Duration.ofHours(1));
        limitador.consumir("d", DIEZ_POR_MINUTO_Y_SESENTA_POR_HORA);
        assertThat(limitador.claves()).isEqualTo(1);
    }

    @Test
    void conDemasiadasDireccionesLasNuevasPasanSinContarse() {
        var pequeno = new LimitadorDeSolicitudes(reloj, 2);
        var unoPorMinuto = List.of(new Limite(1, Duration.ofMinutes(1)));
        pequeno.consumir("a", unoPorMinuto);
        pequeno.consumir("b", unoPorMinuto);

        assertThat(pequeno.consumir("c", unoPorMinuto).permitido()).isTrue();
        assertThat(pequeno.consumir("c", unoPorMinuto).permitido()).isTrue();
        assertThat(pequeno.claves()).isEqualTo(2);
        // The tracked ones are still limited.
        assertThat(pequeno.consumir("a", unoPorMinuto).permitido()).isFalse();
    }

    @Test
    void unRelojQueRetrocedeNoRellenaNada() {
        var unoPorMinuto = List.of(new Limite(1, Duration.ofMinutes(1)));
        reloj.avanzar(Duration.ofMinutes(5));
        limitador.consumir("a", unoPorMinuto);

        reloj.avanzar(Duration.ofMinutes(-5));

        assertThat(limitador.consumir("a", unoPorMinuto).permitido()).isFalse();
    }

    @Test
    void sinLimitesTodoPasaYUnLimiteInvalidoSeRechaza() {
        assertThat(limitador.consumir("a", List.of()).permitido()).isTrue();
        assertThatThrownBy(() -> new Limite(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limite(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
