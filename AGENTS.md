# AGENTS.md

## Propósito
Backend API del sistema **Te Tengo**, que detecta caídas de adultos mayores en su vivienda. Es el contenedor «Backend API del sistema» del modelo C4. Recibe:
- los **eventos** que envía el agente de la vivienda (Te Tengo Captura), que procesa el video en la PC;
- las peticiones de la **app móvil** del familiar/cuidador.

Guarda los datos en PostgreSQL y los clips en S3, y envía las alertas push con Amazon SNS.

Lee antes de cambiar algo:
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md): módulos y su relación con la arquitectura del sistema.
- [docs/MULTITENANCY.md](docs/MULTITENANCY.md): la regla más importante.
- [docs/GUIA_CASOS_DE_USO.md](docs/GUIA_CASOS_DE_USO.md): cómo agregar un caso de uso.
- [docs/CONTRATO_AGENTE.md](docs/CONTRATO_AGENTE.md): lo que envía el agente de la vivienda.
- [docs/referencias/PRODUCT_BACKLOG.md](docs/referencias/PRODUCT_BACKLOG.md): las 27 historias, sus criterios y sus sprints. **Todo valor de negocio sale de aquí** (10 s, 30 s, 5 min, 5 intentos / 15 min, enlace de 30 min, clip de 6 s + 6 s, espera de 3/5/10 min); no inventes otros.

## Dónde guiarte
- **Qué hacer y en qué orden:** [docs/PLAN_DE_TRABAJO.md](docs/PLAN_DE_TRABAJO.md), por sprint y módulo.
- **Cómo debe funcionar cada flujo:** , la secuencia completa entre el agente, el backend, la base de datos, el almacenamiento de clips, el push y la app.
- **Cómo debe comportarse:** los criterios de aceptación en .

## Visión general
- **Monolito modular:** Java 25 + Spring Boot 4.1 + Spring Modulith 2.1 + PostgreSQL 18 + Flyway. El paquete base es `tech.tetengo.api`.
- **Módulos** (uno por épica del backlog): `cuentas`, `hogares`, `camaras`, `alertas`, `monitoreo` e `historial`, más `shared`, que es el único del que los demás pueden depender.
- **`camaras` es el slice de referencia:** está completo, con dominio, caso de uso, adaptador, controlador y pruebas. Copia su estructura.
- **Capas hexagonales en cada módulo:** `domain` (modelo y reglas), `application` (casos de uso y puertos), `infrastructure` (adaptadores) e `interfaces/rest` (controladores, DTO y mappers).
- **Límites verificados:** las pruebas `ModularidadTest` (Spring Modulith) y `ArquitecturaTest` (ArchUnit) hacen cumplir los límites. Entre módulos se usan eventos de dominio o UUID, nunca claves foráneas ni imports de paquetes internos.

## Multi-tenancy (lo más importante)
- **El tenant es el hogar:** la vivienda, su adulto mayor y la familia vinculada.
- **Discriminador por columna:** toda tabla del hogar tiene `hogar_id`, y su entidad extiende `EntidadDelHogar`, que lo marca con `@TenantId` de Hibernate.
- **Hibernate filtra solo:** rellena y filtra `hogar_id` en cada operación. **Nunca escribas `WHERE hogar_id = ...` ni recibas el `hogarId` por parámetro en un caso de uso.**
- **De dónde sale el hogar:** del claim `hogar_id` del JWT, que lee `FiltroHogarActual` y guarda en `HogarActual` (ThreadLocal que se limpia en `finally`).
- **Falla cerrada:** sin hogar en el contexto, `ResolvedorDeHogar` devuelve un hogar inexistente y las consultas no devuelven nada.
- **Tablas globales,** sin `hogar_id`: `hogares`, cuentas de usuario y membresías hogar-usuario. Sus entidades extienden `AggregateRoot`, no `EntidadDelHogar`.
- **Toda funcionalidad nueva que lea o escriba datos del hogar necesita una prueba de integración con dos hogares** que demuestre que uno no ve ni modifica los datos del otro. El modelo es `CamarasMultitenancyIntegrationTest`.

## API, seguridad y errores
- **Rutas** bajo `/api/...` (`ApiVersioning.BASE`). La versión se elige con la cabecera `Api-Version: 1` (versionado nativo de Spring Framework 7); cada mapping declara `version = ApiVersioning.V1`.
- **JWT RS256:** la API es un *resource server*. La clave pública se configura en `spring.security.oauth2.resourceserver.jwt.public-key-location`; en local, `scripts/generar-claves.sh`. Emitir tokens (inicio de sesión, US-02) es tarea del módulo `cuentas`.
- **Errores en RFC 9457 `ProblemDetail`** con la propiedad `codigo`. Cada módulo tiene un `enum` que implementa `CodigoError`, y las reglas lanzan `ErrorDeNegocio`. Los errores internos nunca exponen su mensaje.
- **DTO** como `record` planos y mappers estáticos (`*Mapper`). Nada de MapStruct.

## Patrones de código
- **Identificadores:** UUID v7, generados en el constructor de `AuditableEntity` (nunca `@GeneratedValue`).
- **Las reglas viven en el dominio,** como `Camara.renombrar`. Los casos de uso orquestan y siguen siendo delgados.
- **Persistencia:** los puertos están en `application/port`; los adaptadores, en `infrastructure/persistence`, y los `JpaRepository` son internos (*package-private*). No inyectes `JpaRepository` en casos de uso ni en controladores.
- **Migraciones Flyway** en `src/main/resources/db/migration` como `V<n>__descripcion.sql`, en minúsculas y en español. Nunca edites una migración ya publicada.
- **Nombres en español,** igual que la arquitectura (por ejemplo, «Servicios de Aplicación», «Persistencia central»).

## Pruebas y flujo de trabajo
| Comando | Qué hace |
|---|---|
| `./gradlew bootRun` | Arranca la API y levanta `compose.yaml` (PostgreSQL) |
| `./gradlew test` | Todas las pruebas: unitarias, de integración (Testcontainers) y de arquitectura |
| `./gradlew unitTest` | Solo las rápidas, sin Docker |
| `./gradlew integrationTest` | Solo las de integración (`@Tag("integration")`, extienden `AbstractIntegrationTest`) |
| `./gradlew architectureTest` | Spring Modulith y ArchUnit |
| `./gradlew spotlessApply` | Formatea el código (Palantir Java Format) |

- **Pirámide:** pruebas de dominio primero, luego de caso de uso con puertos falsos, luego de integración HTTP con el hogar del token y, al final, las de arquitectura en verde.
- **Base de datos en las pruebas:** PostgreSQL real con Testcontainers, nunca H2.
- **JWT en las pruebas:** `JwtDePrueba.tokenDeFamiliar(hogarId)`.

## Acuerdos para agentes
- **Antes de un caso de uso nuevo,** abre el slice `camaras` y copia su ubicación de carpetas, la forma de los DTO, el mapper y las pruebas.
- **Un caso de uso por historia de usuario,** citando la historia y el criterio en el Javadoc (por ejemplo, `US-06 / CA-06.2`).
- **Commits en Conventional Commits y en español,** sin línea de coautor. Un commit por cambio coherente.
- **Si la documentación y el código discrepan,** confía en el código y corrige la documentación en el mismo cambio.
