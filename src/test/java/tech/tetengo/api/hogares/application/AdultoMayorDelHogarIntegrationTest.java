package tech.tetengo.api.hogares.application;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tech.tetengo.api.hogares.AdultoMayorDelHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.support.AbstractIntegrationTest;
import tech.tetengo.api.support.ApiDePrueba;

/** The older adult's first name for push notices: always the household in context, never another. */
class AdultoMayorDelHogarIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AdultoMayorDelHogar adultoMayor;

    @Autowired
    EjecutorEnHogar enHogar;

    UUID hogarDeAna;
    UUID hogarDeLuis;

    @BeforeEach
    void dosHogares() throws Exception {
        hogarDeAna = UUID.fromString(campo(ApiDePrueba.titularConHogar(mvc, "ana@correo.pe", "Ana"), "$.hogarId"));
        hogarDeLuis = UUID.fromString(campo(ApiDePrueba.titularConHogar(mvc, "luis@correo.pe", "Luis"), "$.hogarId"));
        jdbc.update("update hogares set adulto_mayor_nombre = ? where id = ?", "Rosa Huamán Torres", hogarDeAna);
        jdbc.update("update hogares set adulto_mayor_nombre = ? where id = ?", "  Juana   Quispe ", hogarDeLuis);
    }

    @Test
    void cadaHogarLeeSoloElNombreDePilaDeSuAdultoMayor() {
        assertThat(enHogar.obtener(hogarDeAna, adultoMayor::nombreDePila)).contains("Rosa");
        assertThat(enHogar.obtener(hogarDeLuis, adultoMayor::nombreDePila)).contains("Juana");
    }

    @Test
    void sinHogarEnContextoNoHayNombre() {
        assertThat(adultoMayor.nombreDePila()).isEmpty();
        assertThat(enHogar.obtener(UUID.randomUUID(), adultoMayor::nombreDePila))
                .isEmpty();
    }
}
