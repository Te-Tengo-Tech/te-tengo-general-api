# Contrato con el agente de la vivienda (Te Tengo Captura)

> **Borrador.** El equipo decidió procesar el video en la PC de la vivienda (MediaPipe en CPU), así que el agente envía **eventos**, no video. La solicitud de cambio al charter está pendiente (ADR 0004). Los nombres de los eventos y sus campos vienen de la clasificación ya validada (`te-tengo-desktop-pywebview`, `docs/especificacion-clasificacion.md`).

## Autenticación
- **Instalación:** el equipo del proyecto instala el agente con una configuración fija (vivienda, webcam y habitación) y una credencial de instalación.
- **Registro:** al iniciar, el agente registra su cámara y recibe un **JWT por cámara** con los claims `hogar_id` y `camara_id`. El token solo vale para ese hogar (ver `MULTITENANCY.md`).

## Endpoints previstos (versión 1, cabecera `Api-Version: 1`)
| Método y ruta | Qué hace | Historias |
|---|---|---|
| `POST /api/agente/camaras/registro` | Registra la cámara al iniciar el agente y devuelve el token de la cámara | US-06 (CA-06.1) |
| `GET /api/agente/estado-captura` | Consentimiento vigente y pausas: el agente no procesa sin consentimiento ni con una pausa activa | US-05, US-22 |
| `POST /api/agente/senal` | Señal periódica de conexión (hora de la última señal) | US-07 |
| `POST /api/agente/eventos` | Evento detectado (cuerpo abajo) | US-11 a US-21 |
| `POST /api/agente/eventos/{id}/clip` | Pide un enlace firmado de S3 para subir el clip de 6 s + 6 s | US-18 |
| `GET /api/agente/configuracion` | Umbrales y versión vigentes de la clasificación, para actualizar el agente | — |

## Evento detectado
```json
{
  "eventoId": "0192f6e4-...",
  "tipo": "caida",
  "ocurridoEn": "2026-10-07T15:04:31.250Z",
  "parametros": { "angulo_grados": 22.1, "razon_ancho_alto": 1.8, "velocidad": 0.41 }
}
```
`tipo` puede ser:
- `caida`
- `caida_confirmada` (30 s en el suelo)
- `movimiento_inestable`
- `recuperacion`
- `deteccion_no_confiable` (5 min sin ver a la persona)

**Requisito de tiempo:** la alerta push debe llegar al familiar **en menos de 10 s** desde el evento (US-16).
