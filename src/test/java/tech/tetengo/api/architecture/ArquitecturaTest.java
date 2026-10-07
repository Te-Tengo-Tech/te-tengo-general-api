package tech.tetengo.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;

/** Hexagonal layers inside each module. */
@Tag("architecture")
@AnalyzeClasses(packages = "tech.tetengo.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeInfraestructuraNiInterfaces = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..interfaces..", "..application..");

    @ArchTest
    static final ArchRule aplicacionNoUsaSpringDataDirectamente = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(org.springframework.data.repository.Repository.class);

    @ArchTest
    static final ArchRule controladoresNoUsanRepositorios = noClasses()
            .that()
            .resideInAPackage("..interfaces..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.persistence..");
}
