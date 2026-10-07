package tech.tetengo.api.camaras.interfaces.rest;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.DatosDePrueba;
import tech.tetengo.api.support.JwtDePrueba;

/** The key multi-tenancy test: a household never sees or changes another household's data. */
class CamarasMultitenancyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID titularA = UUID.randomUUID();
    UUID titularB = UUID.randomUUID();
    UUID hogarA;
    UUID hogarB;
    UUID camaraDeB = UUID.randomUUID();

    Instant instaladaEnA = Instant.parse("2026-08-03T14:00:00Z");
    Instant instaladaEnB = Instant.parse("2026-09-22T15:30:00Z");

    @BeforeEach
    void datos() {
        var enA = java.sql.Timestamp.from(instaladaEnA);
        hogarA = DatosDePrueba.hogar(jdbc, titularA, "Rosa");
        hogarB = DatosDePrueba.hogar(jdbc, titularB, "Jorge");
        insertarCamara(UUID.randomUUID(), hogarA, "Sala", enA);
        insertarCamara(UUID.randomUUID(), hogarA, "Dormitorio", enA);
        insertarCamara(camaraDeB, hogarB, "Cocina", java.sql.Timestamp.from(instaladaEnB));
        // Household B's detection is unreliable; household A must not see it.
        jdbc.update(
                "update camaras set deteccion_confiable = false, no_confiable_desde = ? where id = ?",
                java.sql.Timestamp.from(instaladaEnB.plusSeconds(3600)),
                camaraDeB);
    }

    private void insertarCamara(UUID id, UUID hogar, String nombre, java.sql.Timestamp ahora) {
        jdbc.update(
                "insert into camaras (id, hogar_id, nombre_habitacion, estado_conexion, creado_en, actualizado_en)"
                        + " values (?, ?, ?, 'DESCONECTADA', ?, ?)",
                id,
                hogar,
                nombre,
                ahora,
                ahora);
    }

    @Test
    void cadaHogarVeSoloSusCamaras() throws Exception {
        mvc.perform(get("/api/camaras")
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularA, hogarA, Rol.TITULAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombreHabitacion").value("Dormitorio"))
                .andExpect(jsonPath("$[0].estadoConexion").value("DESCONECTADA"))
                .andExpect(jsonPath("$[0].ultimaSenal").isEmpty())
                .andExpect(jsonPath("$[0].pausadaHasta").isEmpty())
                .andExpect(jsonPath("$[0].deteccionConfiable").value(true))
                .andExpect(jsonPath("$[*].instaladaEn", everyItem(is(instaladaEnA.toString()))))
                .andExpect(jsonPath("$[*].noConfiableDesde", everyItem(nullValue())));
        mvc.perform(get("/api/camaras")
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularB, hogarB, Rol.TITULAR)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].instaladaEn").value(instaladaEnB.toString()))
                .andExpect(jsonPath("$[0].deteccionConfiable").value(false))
                .andExpect(jsonPath("$[0].noConfiableDesde")
                        .value(instaladaEnB.plusSeconds(3600).toString()));
    }

    @Test
    void unHogarNoPuedeRenombrarLaCamaraDeOtro() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularA, hogarA, Rol.TITULAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Baño\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NO_ENCONTRADA"));
    }

    @Test
    void nombreVacioDevuelveProblemDetail() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularB, hogarB, Rol.TITULAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NOMBRE_VACIO"));
    }

    @Test
    void ca06_2_elTitularRenombraLaHabitacion() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularB, hogarB, Rol.TITULAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\" Baño \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(camaraDeB.toString()))
                .andExpect(jsonPath("$.nombreHabitacion").value("Baño"));
    }

    @Test
    void elInvitadoVeLasCamarasPeroNoLasRenombra() throws Exception {
        UUID invitado = UUID.randomUUID();
        DatosDePrueba.membresia(jdbc, hogarB, invitado, Rol.INVITADO);
        mvc.perform(get("/api/camaras")
                        .header("Authorization", "Bearer " + JwtDePrueba.token(invitado, hogarB, Rol.INVITADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.token(invitado, hogarB, Rol.INVITADO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"Baño\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_TITULAR"));
    }

    @Test
    void unNombreDeMasDeCuarentaCaracteresSeRechaza() throws Exception {
        mvc.perform(patch("/api/camaras/" + camaraDeB)
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularB, hogarB, Rol.TITULAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreHabitacion\":\"%s\"}".formatted("x".repeat(41))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CAMARA_NOMBRE_MUY_LARGO"));
    }

    @Test
    void conUnTokenDeUnHogarAlQueNoPerteneceNoVeNada() throws Exception {
        mvc.perform(get("/api/camaras")
                        .header("Authorization", "Bearer " + JwtDePrueba.token(titularA, hogarB, Rol.TITULAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sinTokenNoHayAcceso() throws Exception {
        mvc.perform(get("/api/camaras")).andExpect(status().isUnauthorized());
    }
}
