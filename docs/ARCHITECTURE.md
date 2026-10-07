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
| `shared` | — | Base entities, multi-tenancy, security, errors, versioning | Done |
| `cuentas` | EP01 Accounts and access | US-01 to US-03 | To do |
| `hogares` | EP02 Profile, consent and family | US-04, US-05, US-08 to US-10 | To do |
| `camaras` | EP02 Cameras | US-06, US-07 | **Reference slice** (list and rename) |
| `alertas` | EP03 Detection and alerts | US-11 to US-21 (agent events) | To do |
| `monitoreo` | EP04 Monitoring and privacy | US-22 to US-24 | To do |
| `historial` | EP05 History and summary | US-25 to US-27 | To do |

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
