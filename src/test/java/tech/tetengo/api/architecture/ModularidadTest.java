package tech.tetengo.api.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import tech.tetengo.api.TeTengoGeneralApiApplication;

/** Los módulos solo se comunican por su API pública, eventos o UUID; nada de dependencias cíclicas. */
@Tag("architecture")
class ModularidadTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(TeTengoGeneralApiApplication.class);

    @Test
    void respetaLosLimitesDeLosModulos() {
        MODULOS.verify();
    }

    @Test
    void documentaLosModulos() {
        new Documenter(MODULOS).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
