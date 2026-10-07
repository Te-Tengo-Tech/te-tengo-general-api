package tech.tetengo.api.cuentas.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tech.tetengo.api.support.AbstractIntegrationTest;

/** US-01: account registration. */
class RegistroDeCuentaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions registrar(String cuerpo) throws Exception {
        return mvc.perform(
                post("/api/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    void ca01_1_creaLaCuentaYGuardaLaContrasenaCifrada() throws Exception {
        registrar("{\"correo\":\"Ana@Correo.pe\",\"contrasena\":\"secreta123\",\"nombre\":\"Ana\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.correo").value("ana@correo.pe"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());

        String cifrada = jdbc.queryForObject(
                "select contrasena_cifrada from cuentas where correo = 'ana@correo.pe'", String.class);
        assertThat(cifrada).isNotEqualTo("secreta123").startsWith("$2");
    }

    @Test
    void ca01_2_rechazaUnCorreoYaRegistradoSinImportarMayusculas() throws Exception {
        registrar("{\"correo\":\"ana@correo.pe\",\"contrasena\":\"secreta123\",\"nombre\":\"Ana\"}")
                .andExpect(status().isCreated());

        registrar("{\"correo\":\"ANA@Correo.PE\",\"contrasena\":\"otra\",\"nombre\":\"Otra Ana\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_EN_USO"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void ca01_3_resaltaCadaCampoObligatorioVacio() throws Exception {
        registrar("{\"correo\":\"\",\"contrasena\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos", aMapWithSize(3)))
                .andExpect(jsonPath("$.campos.correo").isNotEmpty())
                .andExpect(jsonPath("$.campos.contrasena").isNotEmpty())
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from cuentas", Integer.class))
                .isZero();
    }

    @Test
    void rechazaUnCorreoMalFormado() throws Exception {
        registrar("{\"correo\":\"no-es-correo\",\"contrasena\":\"secreta123\",\"nombre\":\"Ana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").isNotEmpty());
    }

    @Test
    void unCuerpoIlegibleTambienEsValidacion() throws Exception {
        registrar("{no es json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
