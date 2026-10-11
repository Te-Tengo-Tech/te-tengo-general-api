package tech.tetengo.api.migraciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.tetengo.api.support.AbstractIntegrationTest;

/** Migration {@code V25}: every camera in use gets the room of its name; equal names share one. */
class MigracionDeAmbientesIntegrationTest extends AbstractIntegrationTest {

    private static final String ESQUEMA = "migracion_v25";

    @Autowired
    DataSource fuente;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void borrarEsquema() {
        jdbc.execute("drop schema if exists " + ESQUEMA + " cascade");
    }

    private void migrar(String version) {
        Flyway.configure()
                .dataSource(fuente)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    @Test
    void cadaCamaraQuedaEnElAmbienteDeSuNombre() {
        migrar("24");
        String e = ESQUEMA + ".";
        UUID hogar = UUID.randomUUID();
        UUID otroHogar = UUID.randomUUID();
        for (UUID h : List.of(hogar, otroHogar)) {
            jdbc.update(
                    "insert into " + e + "hogares (id, titular_id, adulto_mayor_nombre, adulto_mayor_direccion,"
                            + " adulto_mayor_convivencia, creado_en, actualizado_en) values (?, ?, 'Rosa', 'Lima', 'SOLO', now(), now())",
                    h,
                    UUID.randomUUID());
        }
        UUID sala = camara(e, hogar, "Sala");
        UUID salaOtra = camara(e, hogar, " sala ");
        UUID cocina = camara(e, hogar, "Cocina");
        UUID salaDeOtroHogar = camara(e, otroHogar, "Sala");

        migrar("25");

        assertThat(jdbc.queryForObject(
                        "select count(*) from " + e + "ambientes where hogar_id = ?", Integer.class, hogar))
                .isEqualTo(2);
        UUID ambienteSala = ambienteDe(e, sala);
        assertThat(ambienteDe(e, salaOtra)).isEqualTo(ambienteSala);
        assertThat(ambienteDe(e, cocina)).isNotEqualTo(ambienteSala);
        assertThat(ambienteDe(e, salaDeOtroHogar)).isNotEqualTo(ambienteSala);
        assertThat(jdbc.queryForObject(
                        "select nombre_normalizado from " + e + "ambientes where id = ?", String.class, ambienteSala))
                .isEqualTo("sala");
        // The camera's room name is untouched.
        assertThat(jdbc.queryForObject(
                        "select nombre_habitacion from " + e + "camaras where id = ?", String.class, salaOtra))
                .isEqualTo(" sala ");
    }

    private UUID camara(String e, UUID hogar, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into " + e
                        + "camaras (id, hogar_id, nombre_habitacion, estado_conexion, creado_en, actualizado_en)"
                        + " values (?, ?, ?, 'DESCONECTADA', now(), now())",
                id,
                hogar,
                nombre);
        return id;
    }

    private UUID ambienteDe(String e, UUID camara) {
        return jdbc.queryForObject("select ambiente_id from " + e + "camaras where id = ?", UUID.class, camara);
    }
}
