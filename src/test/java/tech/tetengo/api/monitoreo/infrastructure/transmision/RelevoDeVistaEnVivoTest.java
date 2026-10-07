package tech.tetengo.api.monitoreo.infrastructure.transmision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

class RelevoDeVistaEnVivoTest {

    private final RelevoDeVistaEnVivo relevo = new RelevoDeVistaEnVivo();
    private final UUID camara = UUID.randomUUID();

    private static WebSocketSession sesion() {
        WebSocketSession sesion = mock(WebSocketSession.class);
        when(sesion.isOpen()).thenReturn(true);
        when(sesion.getId()).thenReturn(UUID.randomUUID().toString());
        return sesion;
    }

    @Test
    void elAgenteTransmiteMientrasHayEspectadores() throws Exception {
        WebSocketSession agente = sesion();
        WebSocketSession espectador = sesion();
        relevo.conectarAgente(camara, agente);
        verify(agente, never()).sendMessage(any());

        relevo.conectarEspectador(camara, espectador);
        verify(agente).sendMessage(RelevoDeVistaEnVivo.TRANSMITIR);

        relevo.desconectarEspectador(camara, espectador);
        verify(agente).sendMessage(RelevoDeVistaEnVivo.DETENER);
        assertThat(relevo.espectadores(camara)).isZero();
    }

    @Test
    void cadaCuadroLlegaALosEspectadoresDeEsaCamara() throws Exception {
        WebSocketSession uno = sesion();
        WebSocketSession dos = sesion();
        WebSocketSession deOtraCamara = sesion();
        relevo.conectarEspectador(camara, uno);
        relevo.conectarEspectador(camara, dos);
        relevo.conectarEspectador(UUID.randomUUID(), deOtraCamara);

        BinaryMessage cuadro = new BinaryMessage(new byte[] {0, 0, 1, (byte) 0xFF, (byte) 0xD8});
        relevo.reenviar(camara, cuadro);

        verify(uno).sendMessage(cuadro);
        verify(dos).sendMessage(cuadro);
        verify(deOtraCamara, never()).sendMessage(cuadro);
    }

    @Test
    void unAgenteQueLlegaConEspectadoresEsperandoEmpiezaATransmitir() throws Exception {
        relevo.conectarEspectador(camara, sesion());
        WebSocketSession agente = sesion();
        relevo.conectarAgente(camara, agente);
        verify(agente).sendMessage(RelevoDeVistaEnVivo.TRANSMITIR);
    }
}
