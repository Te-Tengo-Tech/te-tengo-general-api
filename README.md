# te-tengo-general-api

**Backend API del sistema Te Tengo**, el sistema basado en estimación de pose para la detección de caídas en adultos mayores en su vivienda.

Recibe los eventos que detecta el agente de la vivienda y atiende a la app móvil del familiar/cuidador. Gestiona cuentas, hogares, cámaras, consentimiento, alertas, escalamiento, vista en vivo e historial.

| Stack | Versión |
|---|---|
| Java | 25 (Temurin) |
| Spring Boot | 4.1 · Spring Modulith 2.1 |
| PostgreSQL | 18 · Flyway |
| Gradle | 9.7 (wrapper incluido) |

## Puesta en marcha
```bash
./scripts/generar-claves.sh   # claves RS256 locales para los JWT (.claves/, no se versiona)
./gradlew bootRun             # arranca la API y levanta PostgreSQL con compose.yaml
```
- Swagger UI: http://localhost:8080/swagger-ui.html
- Salud: http://localhost:8080/actuator/health

## Pruebas
```bash
./gradlew test               # unitarias + integración (Testcontainers, requiere Docker) + arquitectura
./gradlew unitTest           # solo las rápidas
./gradlew integrationTest    # solo integración
./gradlew architectureTest   # Spring Modulith y ArchUnit
./gradlew spotlessApply      # formatear
```

## Documentación
| Documento | Contenido |
|---|---|
| [AGENTS.md](AGENTS.md) | Reglas del repositorio, para personas y agentes de IA |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Módulos, slice de referencia y relación con la arquitectura del sistema |
| [docs/MULTITENANCY.md](docs/MULTITENANCY.md) | Aislamiento por hogar con `@TenantId` |
| [docs/GUIA_CASOS_DE_USO.md](docs/GUIA_CASOS_DE_USO.md) | Cómo agregar un caso de uso |
| [docs/CONTRATO_AGENTE.md](docs/CONTRATO_AGENTE.md) | API para el agente de la vivienda |
| [docs/adr/](docs/adr/) | Decisiones de arquitectura |
| [docs/referencias/](docs/referencias/) | Copia del product backlog y de la arquitectura del sistema |

## Trabajar con Claude Code en la nube
El repositorio está preparado:
- `CLAUDE.md` importa `AGENTS.md`.
- El hook `SessionStart` (`scripts/cloud/preparar-entorno.sh`) instala el JDK 25, porque la imagen trae Java 21, y arranca Docker para Testcontainers.
- El backlog está copiado en `docs/referencias/`.

---

Proyecto de tesis, Ingeniería de Software, UPC. Autores: Jhosepmyr Gutierrez Soto y Elmer Riva Rodriguez.
