# Backend architecture

## Place in the system
This repository implements the **"Backend API del sistema"** container of the C4 model: the Services layer (Backend API and Application Services) and the Shared Persistence layer of the logical architecture (`docs/references/ARQUITECTURA_LOGICA_FISICA.md`, Spanish).

```
Household agent (PC: capture + detection) ──HTTPS: events, heartbeat, clips──┐
Mobile app ─────────────────────────────HTTPS/REST + WSS (live view)─────────┤
                                                                             ▼
                          te-tengo-general-api (Spring Boot, modular monolith)
                          ├─ PostgreSQL (Base de Datos Central)
                          ├─ S3 (Almacenamiento de clips, pre-signed URLs)
                          └─ Amazon SNS (Servicio de notificaciones push)
```

## Modules
| Module | Backlog epic | Stories | Status |
|---|---|---|---|
| `shared` | — | Base entities, multi-tenancy (with the membership check), security and token issuing, errors, versioning, OpenAPI, clock, ports for e-mail and push | Done |
| `cuentas` | EP01 Accounts and access | US-01 to US-03 | Done |
| `hogares` | EP02 Profile, consent and family | US-04, US-05, US-08 to US-10 | Done |
| `camaras` | EP02 Cameras | US-06, US-07; camera side of US-05, US-15, US-22 | Done (**reference slice**) |
| `alertas` | EP03 Detection and alerts | US-11 to US-21; alert list (US-25) and clips (US-18, US-26) | Done |
| `monitoreo` | EP04 Monitoring and privacy | US-22 to US-24 | Done (live view transport pending confirmation) |
| `historial` | EP05 History and summary | US-26 retention, US-27 | Done (retention period pending) |

Open decisions and missing credentials are in [BLOCKERS.md](BLOCKERS.md).

## How the modules talk
Modules only use each other's **base package** (their public API: interfaces, records and events); `ModularidadTest` rejects anything else and any cycle.

```
cuentas ◄── hogares ◄── camaras ◄── monitoreo
   ▲           ▲           ▲            ▲
   └───────────┴─────── alertas ────────┘ ◄── historial
```

| From | To | What for |
|---|---|---|
| `hogares` | `cuentas` | `ServicioDeSesiones` (sessions after creating or switching households, accepting invitations), `AltaDeCuentas`, `DirectorioDeUsuarios`; implements the `MembresiasDeUsuario` SPI that sign-in uses |
| `camaras` | `hogares` | Listens to `ConsentimientoOtorgado` / `ConsentimientoRevocado` to keep the capture state |
| `monitoreo` | `camaras`, `cuentas` | Pauses and live view through `CamarasDelHogar`; names for the access log |
| `alertas` | `camaras`, `hogares`, `monitoreo`, `cuentas` | Camera name, capture state and detection reliability; members and alert order; listens to camera, consent and pause events to send their pushes |
| `historial` | `alertas` | `ConteoDeAlertas` (weekly summary) and `RetencionDeClips` |

Cross-module listeners are `@Async @TransactionalEventListener`: they run after the publishing transaction commits, Spring Modulith keeps each publication in `event_publication`, and the listener binds the event's household with `EjecutorEnHogar` (see [MULTITENANCY.md](MULTITENANCY.md)).

## External services
Every external service sits behind a port with a fake adapter, so tests and local runs never call AWS:

| Port | Production | Today |
|---|---|---|
| `NotificadorCorreo` (`shared`) | Amazon SES | Logs the message |
| `NotificadorPush` (`shared`) | Amazon SNS (FCM, APNs) | Logs the notice |
| `AlmacenamientoDeClips` (`alertas`) | Amazon S3, pre-signed URLs | In memory, placeholder URLs |

Tests replace them with recording fakes (`CorreoDePrueba`, `PushDePrueba`, `AlmacenamientoDePrueba`) and move time with `RelojDePrueba`.

## Scheduled jobs
All are idempotent, find their candidates with a native query across households and then work one household at a time. Tests turn scheduling off (`tetengo.tareas.habilitadas=false`) and call them.

| Job | Module | Story |
|---|---|---|
| `DetectarCamarasDesconectadas` | `camaras` | US-07: 3 missed 30-second heartbeats |
| `ReintentarAvisos` | `alertas` | CA-16.4: retries pushes the service did not accept |
| `EscalarAlertas` | `alertas` | US-20 |
| `EliminarGrabaciones` | `alertas` | US-09: deletes recordings after revocation |
| `FinalizarPausasVencidas` | `monitoreo` | US-22 |
| `AplicarRetencionDeGrabaciones` | `historial` | US-26, only when the retention period is set |

## Quality gates
- `ModularidadTest` (Spring Modulith) and `ArquitecturaTest` (ArchUnit).
- JaCoCo: at least 80 % line coverage of `domain` and `application` (`./gradlew jacocoTestCoverageVerification`, part of `check` and CI).
- `DocumentacionOpenApiIntegrationTest`: every endpoint is documented in OpenAPI, and every endpoint of `API_CONTRACT.md` and `AGENT_CONTRACT.md` exists.

## Module layout
```
camaras/
├── package-info.java                  @ApplicationModule
├── domain/                            rules; no outside dependencies
│   ├── CamaraError.java               error catalog (CodigoError)
│   └── model/Camara.java              aggregate (extends EntidadDelHogar)
├── application/                       one use case per action
│   ├── ListarCamaras.java
│   ├── RenombrarCamara.java
│   └── port/CamaraRepository.java     port
├── infrastructure/persistence/        adapter + package-private JpaRepository
└── interfaces/rest/                   controller, record DTOs, static mapper
```

## Decisions
See [docs/adr/](adr/).
