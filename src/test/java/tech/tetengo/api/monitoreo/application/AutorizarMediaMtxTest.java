package tech.tetengo.api.monitoreo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
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
import org.mockito.ArgumentCaptor;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDetectada;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository.SesionDeToken;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.MotivoDeDenegacion;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/** The authorization rules MediaMTX asks the API about (API contract §4), and why each denial happened. */
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
    private final RegistroDeAccesosDenegados denegados = mock(RegistroDeAccesosDenegados.class);

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
        var propiedades = new PropiedadesDeVistaEnVivo(null, null, null, null, secreto, null, null, null);
        return new AutorizarMediaMtx(
                accesos, transmisiones, camaras, enHogar, propiedades, denegados, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PeticionDeMediaMtx publicar(String usuario, String clave, String ruta) {
        return new PeticionDeMediaMtx("publish", usuario, clave, ruta, "", "rtsp");
    }

    private static PeticionDeMediaMtx leer(String accion, String ruta, String query) {
        return leer(accion, ruta, query, "hls");
    }

    private static PeticionDeMediaMtx leer(String accion, String ruta, String query, String protocolo) {
        return new PeticionDeMediaMtx(accion, "", "", ruta, query, protocolo);
    }

    /** The denial was recorded with this reason, and nothing else since the last check. */
    private void registrado(MotivoDeDenegacion motivo) {
        ArgumentCaptor<MotivoDeDenegacion> motivos = ArgumentCaptor.forClass(MotivoDeDenegacion.class);
        verify(denegados, atLeast(0)).enMediaMtx(motivos.capture(), any(), any(), any());
        assertThat(motivos.getAllValues()).containsExactly(motivo);
        clearInvocations(denegados);
    }

    @Test
    void sinElSecretoCorrectoTodoSeNiega() {
        assertThat(autorizar.ejecutar(null, publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.SECRETO_INVALIDO);
        assertThat(autorizar.ejecutar("otro", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.SECRETO_INVALIDO);
        assertThat(autorizar(" ").ejecutar(" ", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.SECRETO_INVALIDO);
        assertThat(autorizar(null).ejecutar("x", publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.SECRETO_INVALIDO);
    }

    @Test
    void desdeFueraDeLaRedInternaTodoSeNiegaAunConElSecreto() {
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador"), false))
                .isFalse();
        registrado(MotivoDeDenegacion.ORIGEN_EXTERNO);
        assertThat(sesion.getConectadaEn()).isNull();
    }

    @Test
    void elAgentePublicaConLaClaveDeLaTransmisionActual() {
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", ruta)))
                .isTrue();
        verify(denegados, never()).enMediaMtx(any(), any(), any(), any());
    }

    @Test
    void seNiegaPublicarConOtraClaveOtroUsuarioOtraRuta() {
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "otra", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.CLAVE_DE_PUBLICACION_INVALIDA);
        assertThat(autorizar.ejecutar(SECRETO, publicar("admin", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.CLAVE_DE_PUBLICACION_INVALIDA);
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", "camaras/" + UUID.randomUUID())))
                .isFalse();
        registrado(MotivoDeDenegacion.CLAVE_DE_PUBLICACION_INVALIDA);
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", "otra")))
                .isFalse();
        registrado(MotivoDeDenegacion.RUTA_INVALIDA);
    }

    @Test
    void laPrimeraPeticionRtspSinCredencialesNoEsUnaDenegacion() {
        assertThat(autorizar.ejecutar(SECRETO, publicar("", "", ruta))).isFalse();
        verify(denegados, never()).enMediaMtx(any(), any(), any(), any());
    }

    @Test
    void sinCapturaPermitidaNoSePublicaNiSeLee() {
        when(camaras.buscar(camara)).thenReturn(Optional.of(new CamaraDelHogar(camara, "Sala", false)));
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.CAPTURA_NO_PERMITIDA);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isFalse();
        registrado(MotivoDeDenegacion.CAPTURA_NO_PERMITIDA);
    }

    @Test
    void unaWebcamSinUsarSoloPublicaYSeLeeConSuVistaPrevia() {
        when(camaras.buscarDetectada(camara))
                .thenReturn(Optional.of(
                        new CamaraDetectada(camara, UUID.randomUUID(), "d", "Cam", false, true, true, null)));
        assertThat(autorizar.ejecutar(SECRETO, publicar("agente", "clave-publicacion", ruta)))
                .isFalse();
        registrado(MotivoDeDenegacion.PUBLICACION_SIN_VISTA_PREVIA);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isFalse();
        registrado(MotivoDeDenegacion.LECTURA_SIN_VISTA_PREVIA);
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
        verify(denegados, never()).enMediaMtx(any(), any(), any(), any());
    }

    @Test
    void seNiegaLeerSinTokenConOtroTokenOEnOtraCamara() {
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, ""))).isFalse();
        registrado(MotivoDeDenegacion.SIN_TOKEN);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=otro")))
                .isFalse();
        registrado(MotivoDeDenegacion.TOKEN_INVALIDO);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", "camaras/" + UUID.randomUUID(), "token=token-espectador")))
                .isFalse();
        // The viewer of that token is known: who it was and from which household is recorded.
        verify(denegados)
                .enMediaMtx(eq(MotivoDeDenegacion.TOKEN_DE_OTRA_CAMARA), any(), eq(sesion.getUsuarioId()), eq(hogar));
        verify(accesos, never()).guardar(any());
    }

    @Test
    void sinTokenNoSeSabeQuienPidio() {
        autorizar.ejecutar(SECRETO, leer("read", ruta, ""));
        verify(denegados).enMediaMtx(eq(MotivoDeDenegacion.SIN_TOKEN), any(), isNull(), isNull());
    }

    @Test
    void seNiegaLeerConUnaSesionTerminadaOVencida() {
        sesion.finalizar(AHORA.minusSeconds(5));
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador")))
                .isFalse();
        registrado(MotivoDeDenegacion.SESION_TERMINADA);

        AccesoVistaEnVivo vencida = new AccesoVistaEnVivo(
                camara, UUID.randomUUID(), null, AHORA.minus(Duration.ofMinutes(11)), Duration.ofMinutes(10), "v");
        when(accesos.porToken(Secretos.huella("vencido")))
                .thenReturn(Optional.of(new SesionDeToken(vencida.getId(), hogar)));
        when(accesos.buscar(vencida.getId())).thenReturn(Optional.of(vencida));
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=vencido")))
                .isFalse();
        registrado(MotivoDeDenegacion.SESION_TERMINADA);
    }

    @Test
    void lasDemasAccionesSeNiegan() {
        for (String accion : new String[] {"api", "metrics", "pprof", "otra"}) {
            assertThat(autorizar.ejecutar(
                            SECRETO,
                            new PeticionDeMediaMtx(
                                    accion, "agente", "clave-publicacion", ruta, "token=token-espectador", "hls")))
                    .as(accion)
                    .isFalse();
            registrado(MotivoDeDenegacion.ACCION_NO_PERMITIDA);
        }
    }

    @Test
    void elEspectadorLeePorWebRtcConElMismoTokenEnLaQueryDelWhep() {
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador", "webrtc")))
                .isTrue();
        assertThat(sesion.getUltimaActividad()).isEqualTo(AHORA);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "", "webrtc")))
                .isFalse();
        registrado(MotivoDeDenegacion.SIN_TOKEN);
        assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=otro", "webrtc")))
                .isFalse();
        registrado(MotivoDeDenegacion.TOKEN_INVALIDO);
    }

    @Test
    void elTokenSoloSeAceptaEnLaQueryNoComoCredencialDeWhep() {
        // Authorization: Bearer <token> reaches the hook as "token"/"password", never in the query: the API
        // could not find that WebRTC session to end it, so such a read is denied.
        assertThat(autorizar.ejecutar(
                        SECRETO, new PeticionDeMediaMtx("read", "", "token-espectador", ruta, "", "webrtc")))
                .isFalse();
        registrado(MotivoDeDenegacion.SIN_TOKEN);
    }

    @Test
    void seNiegaLeerPorOtrosProtocolosAunqueElTokenSeaValido() {
        for (String protocolo : new String[] {"rtsp", "rtmp", "srt", "moq", "", null}) {
            assertThat(autorizar.ejecutar(SECRETO, leer("read", ruta, "token=token-espectador", protocolo)))
                    .as(String.valueOf(protocolo))
                    .isFalse();
            registrado(MotivoDeDenegacion.PROTOCOLO_NO_PERMITIDO);
        }
        verify(accesos, never()).guardar(any());
    }
}
