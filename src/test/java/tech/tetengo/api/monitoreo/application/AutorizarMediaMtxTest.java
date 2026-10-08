package tech.tetengo.api.monitoreo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository.SesionDeToken;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/** The authorization rules MediaMTX asks the API about (API contract §4). */
class AutorizarMediaMtxTest {

    private static final String SECRETO = "secreto";
    private static final Instant AHORA = Instant.parse("2026-10-07T15:00:00Z");

    private final UUID hogar = UUID.randomUUID();
    private final UUID camara = UUID.randomUUID();
    private final String ruta = "camaras/" + camara;

    private final AccesoVistaEnVivoRepository accesos = mock(AccesoVistaEnVivoRepository.class);
    private final TransmisionEnVivoRepository transmisiones = mock(TransmisionEnVivoRepository.class);
    private final CamarasDelHogar camaras = mock(CamarasDelHogar.class);
    private final EjecutorEnHogar enHogar = mock(EjecutorEnHogar.class);

    private AutorizarMediaMtx autorizar;
    private AccesoVistaEnVivo sesion;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        when(enHogar.obtener(any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(1)).get());
        when(camaras.buscar(camara)).thenReturn(Optional.of(new CamaraDelHogar(camara, "Sala", true)));
        when(transmisiones.hogarDeClave(camara, Secretos.huella("clave-publicacion")))
                .thenReturn(Optional.of(hogar));
        sesion = new AccesoVistaEnVivo(
                camara, UUID.randomUUID(), null, AHORA.minusSeconds(60), Duration.ofMinutes(10), "h");
        when(accesos.porToken(Secretos.huella("token-espectador")))
                .thenReturn(Optional.of(new SesionDeToken(sesion.getId(), hogar)));
        when(accesos.buscar(sesion.getId())).thenReturn(Optional.of(sesion));
        autorizar = autorizar(SECRETO);
    }

    private AutorizarMediaMtx autorizar(String secreto) {
        var propiedades = new PropiedadesDeVistaEnVivo(null, null, null, secreto, null, null);
        return new AutorizarMediaMtx(
                accesos, transmisiones, camaras, enHogar, propiedades, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PeticionDeMediaMtx publicar(String usuario, String clave, String ruta) {
        return new PeticionDeMediaMtx("publish", usuario, clave, ruta, "");
    }

    private static PeticionDeMediaMtx leer(String accion, String ruta, String query) {
        return new PeticionDeMediaMtx(accion, "", "", ruta, query);
    }

    @Test
    void sinElSecretoCorrectoTodoSeNiega() {
        assertThat(autorizar.ejecutar(null, publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        assertThat(autorizar.ejecutar("otro", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        assertThat(autorizar(" ").ejecutar(" ", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        assertThat(autorizar(null).ejecutar("x", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
    }

    @Test
    void elAgentePublicaConLaClaveDeLaTransmisionActual() {
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", ruta)))
                .isTrue();
    }

    @Test
    void seNiegaPublicarConOtraClaveOtroUsuarioOtraRuta() {
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "otra", ruta)))
                .isFalse();
        assertThat(autorizar.ejecutar(SECRETO, publicar("admin", "clave-publicacion", ruta)))
                .isFalse();
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", "camaras/" + UUID.randomUUID())))
                .isFalse();
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", "otra")))
                .isFalse();
    }

    @Test
    void sinCapturaPermitidaNoSePublicaNiSeLee() {
        when(camaras.buscar(camara)).thenReturn(Optional.of(new CamaraDelHogar(camara, "Sala", false)));
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isFalse();
    }

    @Test
    void elEspectadorLeeConElTokenDeUnaSesionAbiertaDeEsaCamaraYCuentaComoActividad() {
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isTrue();
        assertThat(autorizar.ejecutar(SECRETO, leer("playback", ruta, "token=token-espectador")))
                .isTrue();
        assertThat(sesion.getConectadaEn()).isEqualTo(AHORA);
        assertThat(sesion.getUltimaActividad()).isEqualTo(AHORA);
        verify(accesos, org.mockito.Mockito.times(2)).guardar(sesion);
    }

    @Test
    void seNiegaLeerSinTokenConOtroTokenOEnOtraCamara() {
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, ""))).isFalse();
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=otro")))
                .isFalse();
        assertThat(autorizar.ejecutar(SECRETO, leer("read", "camaras/" + UUID.randomUUID(), "token=token-espectador")))
                .isFalse();
        verify(accesos, never()).guardar(any());
    }

    @Test
    void seNiegaLeerConUnaSesionTerminadaOVencida() {
        sesion.finalizar(AHORA.minusSeconds(5));
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isFalse();

        AccesoVistaEnVivo vencida = new AccesoVistaEnVivo(
                camara, UUID.randomUUID(), null, AHORA.minus(Duration.ofMinutes(11)), Duration.ofMinutes(10), "v");
        when(accesos.porToken(Secretos.huella("vencido")))
                .thenReturn(Optional.of(new SesionDeToken(vencida.getId(), hogar)));
        when(accesos.buscar(vencida.getId())).thenReturn(Optional.of(vencida));
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=vencido")))
                .isFalse();
    }

    @Test
    void lasDemasAccionesSeNiegan() {
        for (String accion : new String[] {"api", "metrics", "pprof", "otra"}) {
            assertThat(autorizar.ejecutar(
                            SECRETO,
                            new PeticionDeMediaMtx(
                                    accion, "agente", "clave-publicacion", ruta, "token=token-espectador")))
                    .as(accion)
                    .isFalse();
        }
    }
}
