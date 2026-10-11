package tech.tetengo.api.migraciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.tetengo.api.support.AbstractIntegrationTest;

/**
 * Migration {@code V24} on data shaped like production's, in a schema of its own: each installation
 * with a camera becomes a PC of origin {@code INSTALACION} holding that camera, without losing any row.
 */
class MigracionDePcsIntegrationTest extends AbstractIntegrationTest {

    private static final String ESQUEMA = "migracion_v24";

    @Autowired
    DataSource fuente;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void borrarEsquema() {
        jdbc.execute("drop schema if exists " + ESQUEMA + " cascade");
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(fuente)
                .schemas(ESQUEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load();
    }

    @Test
    void cadaInstalacionConCamaraPasaASerUnaPcConEsaCamara() {
        flyway("23").migrate();
        UUID hogar = UUID.randomUUID();
        UUID camara = UUID.randomUUID();
        UUID otraCamara = UUID.randomUUID();
        UUID instalacion = UUID.randomUUID();
        UUID sinCamara = UUID.randomUUID();
        String e = ESQUEMA + ".";
        jdbc.update(
                "insert into " + e + "hogares (id, titular_id, adulto_mayor_nombre, adulto_mayor_direccion,"
                        + " adulto_mayor_convivencia, creado_en, actualizado_en) values (?, ?, 'Rosa', 'Lima', 'SOLO', now(), now())",
                hogar,
                UUID.randomUUID());
        for (UUID id : List.of(camara, otraCamara)) {
            jdbc.update(
                    "insert into " + e + "camaras (id, hogar_id, nombre_habitacion, estado_conexion, ultima_senal,"
                            + " creado_en, actualizado_en) values (?, ?, 'Sala', 'EN_LINEA', now(), now() - interval '30 days', now())",
                    id,
                    hogar);
        }
        jdbc.update(
                "insert into " + e
                        + "instalaciones (id, hogar_id, credencial_hash, camara_id, creado_en, actualizado_en)"
                        + " values (?, ?, 'a', ?, now(), now())",
                instalacion,
                hogar,
                camara);
        jdbc.update(
                "insert into " + e + "instalaciones (id, hogar_id, credencial_hash, creado_en, actualizado_en)"
                        + " values (?, ?, 'b', now(), now())",
                sinCamara,
                hogar);

        flyway("24").migrate();

        List<Map<String, Object>> pcs = jdbc.queryForList("select * from " + e + "pcs order by vinculada_en");
        assertThat(pcs).hasSize(2).allSatisfy(pc -> {
            assertThat(pc.get("hogar_id")).isEqualTo(hogar);
            assertThat(pc.get("origen")).isEqualTo("INSTALACION");
            assertThat(pc.get("estado")).isEqualTo("VINCULADA");
            assertThat(pc.get("nombre")).isEqualTo("PC de la casa");
        });
        UUID pcDeLaInstalacion =
                jdbc.queryForObject("select pc_id from " + e + "instalaciones where id = ?", UUID.class, instalacion);
        assertThat(jdbc.queryForObject("select pc_id from " + e + "camaras where id = ?", UUID.class, camara))
                .isEqualTo(pcDeLaInstalacion);
        assertThat(jdbc.queryForObject("select pc_id from " + e + "camaras where id = ?", UUID.class, otraCamara))
                .isNotNull()
                .isNotEqualTo(pcDeLaInstalacion);
        assertThat(jdbc.queryForObject("select pc_id from " + e + "instalaciones where id = ?", UUID.class, sinCamara))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + e + "camaras where retirada_en is null", Integer.class))
                .isEqualTo(2);
    }
}
