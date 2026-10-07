package tech.tetengo.api.alertas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.tetengo.api.support.ApiDePrueba.bearer;
import static tech.tetengo.api.support.ApiDePrueba.campo;
import static tech.tetengo.api.support.ApiDePrueba.enviarEvento;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** US-19: marking an alert as attended or as a false alarm. */
class EstadoDeAlertaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String titular;
    String tokenTitular;
    UUID invitado;
    String tokenInvitado;
    String agente;
    String alerta;

    @BeforeEach
    void alertaActiva() throws Exception {
        titular = ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana");
        tokenTitular = campo(titular, "$.tokenAcceso");
        UUID hogar = UUID.fromString(campo(titular, "$.hogarId"));
        ApiDePrueba.registrarCuenta(mvc, "beto@correo.pe", "secreta123", "Beto");
        invitado =
                UUID.fromString(campo(ApiDePrueba.iniciarSesion(mvc, "beto@correo.pe", "secreta123"), "$.usuario.id"));
        DatosDePrueba.membresia(jdbc, hogar, invitado, Rol.INVITADO);
        tokenInvitado = JwtDePrueba.token(invitado, hogar, Rol.INVITADO);
        DatosDePrueba.dispositivo(jdbc, UUID.fromString(campo(titular, "$.usuario.id")), "telefono-ana");
        DatosDePrueba.dispositivo(jdbc, invitado, "telefono-beto");
        agente = campo(ApiDePrueba.agenteConConsentimiento(mvc, jdbc, titular, "Sala"), "$.token");
        alerta = campo(
                enviarEvento(mvc, agente, UUID.randomUUID(), "caida", reloj.instant())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.alertaId");
        push.limpiar();
    }

    private ResultActions marcar(String token, String accion) throws Exception {
        return mvc.perform(post("/api/alertas/" + alerta + "/" + accion).header("Authorization", bearer(token)));
    }

    @Test
    void ca19_1_alMarcarlaAtendidaGuardaElEstadoYLaHora() throws Exception {
        marcar(tokenInvitado, "atencion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alerta))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.atendidaPor.id").value(invitado.toString()))
                .andExpect(jsonPath("$.atendidaPor.nombre").value("Beto"))
                .andExpect(jsonPath("$.atendidaEn").value(reloj.instant().toString()));
    }

    @Test
    void ca19_3_losDemasFamiliaresVenQuienLaAtendioYCuando() throws Exception {
        marcar(tokenInvitado, "atencion").andExpect(status().isOk());

        var avisos = push.deTipo(TipoAviso.ALERTA_ATENDIDA);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.getFirst().tokens()).containsExactly("telefono-ana");
        assertThat(avisos.getFirst().aviso().alertaId()).hasToString(alerta);
        assertThat(avisos.getFirst().aviso().habitacion()).isEqualTo("Sala");
        // «Beto atendió la alerta» / «… · Caída en la Sala.»
        assertThat(avisos.getFirst().aviso().detalle())
                .extracting(Detalle::quien, Detalle::tipoDeAlerta)
                .containsExactly("Beto", TipoDeAlerta.CAIDA);

        mvc.perform(get("/api/alertas/" + alerta).header("Authorization", bearer(tokenTitular)))
                .andExpect(jsonPath("$.atendidaPor.nombre").value("Beto"))
                .andExpect(jsonPath("$.atendidaEn").value(reloj.instant().toString()));
    }

    @Test
    void ca19_2_unaFalsaAlarmaQuedaMarcadaComoTal() throws Exception {
        marcar(tokenTitular, "falsa-alarma")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALSA_ALARMA"))
                .andExpect(jsonPath("$.atendidaPor.nombre").value("Ana"));
        mvc.perform(get("/api/alertas?estado=FALSA_ALARMA").header("Authorization", bearer(tokenTitular)))
                .andExpect(jsonPath("$.total").value(1));
        assertThat(push.deTipo(TipoAviso.ALERTA_ATENDIDA)).isEmpty();
    }

    @Test
    void unaAlertaCerradaNoCambiaDeEstado() throws Exception {
        marcar(tokenTitular, "atencion").andExpect(status().isOk());
        marcar(tokenInvitado, "atencion")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ALERTA_CERRADA"));
        marcar(tokenInvitado, "falsa-alarma")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ALERTA_CERRADA"));
    }

    @Test
    void unHogarNoCambiaLasAlertasDeOtro() throws Exception {
        String otro = ApiDePrueba.titularConHogar(mvc, "carla@correo.pe", "Carla");
        marcar(campo(otro, "$.tokenAcceso"), "atencion")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));
        mvc.perform(get("/api/alertas/" + alerta).header("Authorization", bearer(tokenTitular)))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }
}
