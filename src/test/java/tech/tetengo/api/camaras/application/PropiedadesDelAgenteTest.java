package tech.tetengo.api.camaras.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** GET /api/agente/configuracion reads its thresholds from {@code tetengo.agente.umbrales}. */
class PropiedadesDelAgenteTest {

    private static PropiedadesDelAgente enlazar(Map<String, String> valores) {
        return new Binder(new MapConfigurationPropertySource(valores))
                .bind("tetengo.agente", PropiedadesDelAgente.class)
                .get();
    }

    @Test
    void sinUmbralesConfiguradosNoSePublicaNinguno() {
        PropiedadesDelAgente propiedades = enlazar(Map.of(
                "tetengo.agente.vigencia-token", "30d",
                "tetengo.agente.intervalo-senal", "30s",
                "tetengo.agente.senales-perdidas", "3"));

        assertThat(propiedades.umbrales()).isEmpty();
        assertThat(propiedades.versionPublicada()).isEmpty();
    }

    @Test
    void losUmbralesConservanLosNombresDelAgenteYSonNumeros() {
        PropiedadesDelAgente propiedades = enlazar(Map.of(
                "tetengo.agente.vigencia-token", "30d",
                "tetengo.agente.intervalo-senal", "30s",
                "tetengo.agente.senales-perdidas", "3",
                "tetengo.agente.version-publicada", "0.2.0",
                "tetengo.agente.umbrales[velocidad_descenso_min]", "0.5",
                "tetengo.agente.umbrales[angulo_linea_central_max_grados]", "45"));

        assertThat(propiedades.versionPublicada()).isEqualTo("0.2.0");
        assertThat(propiedades.umbrales())
                .containsExactly(
                        Map.entry("angulo_linea_central_max_grados", new BigDecimal("45")),
                        Map.entry("velocidad_descenso_min", new BigDecimal("0.5")));
    }
}
