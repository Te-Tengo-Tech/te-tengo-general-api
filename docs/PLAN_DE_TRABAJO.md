# Plan de trabajo del backend

Orden según los sprints del product backlog (`docs/referencias/PRODUCT_BACKLOG.md`). Cada fila es una tarea para una sesión: implementar los casos de uso de la historia con sus criterios como pruebas, siguiendo [GUIA_CASOS_DE_USO.md](GUIA_CASOS_DE_USO.md).

**Cómo guiarse en cada tarea:**
1. **Qué debe hacer:** la historia y sus criterios Dado/Cuando/Entonces, en `PRODUCT_BACKLOG.md`.
2. **Con qué componentes y en qué orden:** el diagrama de integración, en `docs/referencias/diagramas/integracion.puml`. Es una secuencia por flujo: registro de la cámara, consentimiento y pausas, alerta, atención, escalamiento, vista en vivo e historial.
3. **Las responsabilidades de cada componente:** `docs/referencias/diagramas/arquitectura_logica_v2.puml`, `c4.dsl` y `docs/referencias/ARQUITECTURA_LOGICA_FISICA.md`.
4. **Lo que envía el agente de la vivienda:** [CONTRATO_AGENTE.md](CONTRATO_AGENTE.md).
5. **El patrón de código:** el slice `camaras`. Y siempre una prueba con dos hogares (multi-tenancy).

## Sprint 3
| # | Módulo | Historia | Notas |
|---|---|---|---|
| 1 | `cuentas` | US-01 Registro | Al registrarse se crea el **hogar** y la membresía del titular; contraseñas con BCrypt |
| 2 | `cuentas` | US-02 Inicio de sesión | Emite un JWT RS256 con el claim `hogar_id`; bloqueo tras 5 intentos durante 15 min; cierre de sesión |
| 3 | `cuentas` | US-03 Recuperar contraseña | Enlace de 30 min; respuesta genérica; correo detrás de un puerto (Amazon SES en producción, falso en pruebas) |
| 4 | `hogares` | US-04 Adulto mayor | Uno por hogar (CA-04.2) |
| 5 | `hogares` | US-05 Consentimiento | Fecha y hora (Ley N.° 29733); sin consentimiento, el agente no envía (CA-05.2) |
| 6 | `camaras` | Registro de la cámara por el agente | `POST /api/agente/camaras/registro`; token por cámara (CONTRATO_AGENTE.md) |
| 7 | `camaras` | US-07 Estado de conexión | Señal periódica; desconexión → aviso push |
| 8 | `hogares` | US-08 Invitar familiares | Membresía hogar–usuario (tabla global) |
| 9 | `alertas` | Recibir eventos del agente | `POST /api/agente/eventos`; idempotente por `eventoId` |
| 10 | `alertas` | US-16 / US-17 Alertas | Push a todos los familiares en **menos de 10 s**, detrás de un puerto (Amazon SNS en producción) |
| 11 | `alertas` | US-18 Clip | Enlace firmado de S3 para subir y para ver |

## Sprint 4
| # | Módulo | Historia |
|---|---|---|
| 12 | `hogares` | US-09 Revocación y eliminación de grabaciones |
| 13 | `alertas` | US-19 Estado de la alerta (atendida o falsa alarma; quién y cuándo) |
| 14 | `historial` | US-25 Historial |
| 15 | `hogares` | US-10 Orden de contacto y tiempo de espera |
| 16 | `alertas` | US-20 Escalamiento (tarea programada) |
| 17 | `monitoreo` | US-22 Pausa con reactivación automática |
| 18 | `monitoreo` | US-23 Vista en vivo (sesión temporal autorizada; protocolo por decidir) |
| 19 | `monitoreo` | US-24 Registro de accesos |
| 20 | `alertas` | US-21 Aviso de recuperación |
| 21 | `historial` | US-26 Grabaciones · US-27 Resumen semanal |
