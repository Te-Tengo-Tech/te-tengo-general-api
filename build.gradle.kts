plugins {
  java
  id("org.springframework.boot") version "4.1.1"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "8.10.3"
  id("org.owasp.dependencycheck") version "13.0.0"
  jacoco
}

group = "tech.tetengo"

version = "0.1.0"

description = "Backend API del sistema Te Tengo"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

val mockitoAgent by configurations.creating

configurations { compileOnly { extendsFrom(configurations.annotationProcessor.get()) } }

repositories { mavenCentral() }

extra["springModulithVersion"] = "2.1.1"

extra["awsSdkVersion"] = "2.55.12"

dependencies {
  // Web, WebSocket (vista en vivo) y documentación OpenAPI
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-websocket")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

  // Seguridad: JWT (resource server)
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

  // Validación
  implementation("org.springframework.boot:spring-boot-starter-validation")

  // Datos: JPA + PostgreSQL + Flyway
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // Observabilidad
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // Monolito modular
  implementation("org.springframework.modulith:spring-modulith-starter-core")
  implementation("org.springframework.modulith:spring-modulith-starter-jpa")
  runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
  runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

  // Clips in Amazon S3 (or an S3-compatible store): pre-signed URLs with the AWS SDK v2
  implementation("software.amazon.awssdk:s3")

  // Utilidades: UUID v7
  implementation("com.github.f4b6a3:uuid-creator:6.1.1")

  // Procesadores de anotaciones
  annotationProcessor("org.projectlombok:lombok")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.projectlombok:lombok")
  testCompileOnly("org.projectlombok:lombok")

  // Desarrollo local: levanta compose.yaml al arrancar
  developmentOnly("org.springframework.boot:spring-boot-docker-compose")

  // Pruebas
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation(
      "org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test"
  )
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.modulith:spring-modulith-starter-test")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

dependencyManagement {
  imports {
    mavenBom(
        "org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}"
    )
    mavenBom("software.amazon.awssdk:bom:${property("awsSdkVersion")}")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

// `./gradlew bootRun` uses the `local` profile unless another one is active:
// clips go to the SeaweedFS service of compose.yaml (application-local.yml).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
  systemProperty("spring.profiles.default", "local")
}

// Carriles de pruebas (como en reqsai-api): unitarias rápidas, integración (Docker) y arquitectura.
val unitTest by
    tasks.registering(Test::class) {
      description = "Pruebas rápidas (sin Docker ni contexto de Spring)"
      group = "verification"
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      useJUnitPlatform { excludeTags("integration", "architecture") }
    }

val integrationTest by
    tasks.registering(Test::class) {
      description = "Pruebas de integración con Testcontainers (requiere Docker)"
      group = "verification"
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      useJUnitPlatform { includeTags("integration") }
    }

val architectureTest by
    tasks.registering(Test::class) {
      description = "Reglas de arquitectura (ArchUnit y Spring Modulith)"
      group = "verification"
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      useJUnitPlatform { includeTags("architecture") }
    }

spotless {
  java {
    target("src/**/*.java")
    palantirJavaFormat()
    removeUnusedImports()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
  }
}

jacoco { toolVersion = "0.8.15" }

tasks.jacocoTestReport {
  executionData(fileTree(layout.buildDirectory.get().asFile).include("jacoco/*.exec"))
  mustRunAfter(tasks.withType<Test>())
  reports {
    xml.required = true
    html.required = true
  }
}

// Line coverage of the rules (domain) and use cases (application) must stay at 80 % or more.
tasks.jacocoTestCoverageVerification {
  executionData(fileTree(layout.buildDirectory.get().asFile).include("jacoco/*.exec"))
  mustRunAfter(tasks.withType<Test>())
  classDirectories.setFrom(
      sourceSets.main.get().output.classesDirs.map {
        fileTree(it) { include("**/domain/**", "**/application/**") }
      }
  )
  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.80".toBigDecimal()
      }
    }
  }
}

tasks.named("check") { dependsOn("jacocoTestReport", "jacocoTestCoverageVerification") }

// OWASP Dependency-Check of the runtime dependencies: `./gradlew dependencyCheckAnalyze`.
// CI runs it weekly (.github/workflows/owasp.yml) and fails on CVSS 7.0 or higher.
dependencyCheck {
  nvd { apiKey = System.getenv("NVD_API_KEY") }
  scanConfigurations = listOf("runtimeClasspath")
  formats = listOf("HTML", "JSON")
  failBuildOnCVSS = 7.0f
  analyzers {
    assemblyEnabled = false
    nodeEnabled = false
    nodeAudit { enabled = false }
    ossIndex { enabled = false }
  }
}
