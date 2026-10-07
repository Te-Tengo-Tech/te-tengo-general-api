# Arquitectura del backend

## Lugar en el sistema
Este repositorio implementa el contenedor **Backend API del sistema** del modelo C4: la Capa de Servicios (Backend API y Servicios de Aplicación) y la Capa de Persistencia Compartida de la arquitectura lógica (`docs/referencias/ARQUITECTURA_LOGICA_FISICA.md`).

```
Agente de la vivienda (PC: captura + detección) ──HTTPS: eventos, señal, clip──┐
App móvil del familiar ──────────────HTTPS/REST + WebSocket (vista en vivo)────┤
                                                                               ▼
                           te-tengo-general-api (Spring Boot, monolito modular)
                           ├─ PostgreSQL (Base de Datos Central)
                           ├─ S3 (Almacenamiento de clips, enlaces firmados)
                           └─ Amazon SNS (Servicio de notificaciones push)
```

## Módulos

Hay un módulo por épica del backlog; los paquetes están en `tech.tetengo.api`.

| Módulo | Épica | Historias | Estado |
|---|---|---|---|
| `shared` | — | Base común: `AuditableEntity`, `AggregateRoot`, `EntidadDelHogar`, multi-tenancy, seguridad, errores y versionado | Hecho |
| `cuentas` | EP01 Cuenta y acceso | US-01 a US-03 | Pendiente |
| `hogares` | EP02 Perfil, consentimiento y familia | US-04, US-05, US-08, US-09 y US-10 | Pendiente |
| `camaras` | EP02 Cámaras | US-06 y US-07 | **Slice de referencia**: listar y renombrar |
| `alertas` | EP03 Detección y alertas | US-11 a US-21 (recibe los eventos del agente) | Pendiente |
| `monitoreo` | EP04 Monitoreo y privacidad | US-22 a US-24 | Pendiente |
| `historial` | EP05 Historial y resumen | US-25 a US-27 | Pendiente |

El orden de implementación sigue los sprints del backlog:
- **Sprint 3:** cuentas, perfil, consentimiento, cámaras, familia y alertas con clip.
- **Sprint 4:** revocación, escalamiento, pausas, vista en vivo e historial.

## Estructura de un módulo
```
camaras/
├── package-info.java                  @ApplicationModule
├── domain/                            reglas: no depende de nada externo
│   ├── CamaraError.java               catálogo de errores (CodigoError)
│   └── model/Camara.java              agregado (extiende EntidadDelHogar)
├── application/                       casos de uso, uno por historia
│   ├── ListarCamaras.java
│   ├── RenombrarCamara.java
│   └── port/CamaraRepository.java     puerto
├── infrastructure/persistence/        adaptador + JpaRepository (package-private)
└── interfaces/rest/                   controlador, DTO (record) y mapper estático
```

## Decisiones
Ver [docs/adr/](adr/).
