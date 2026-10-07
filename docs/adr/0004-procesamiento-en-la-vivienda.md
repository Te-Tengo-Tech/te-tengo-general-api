# 0004. El agente de la vivienda procesa el video y envía eventos

**Estado:** propuesta. El charter actual dice que el procesamiento se ejecuta en la nube; se requiere una solicitud de cambio aprobada por el asesor.

## Contexto
Medición del equipo: enviar video 480p a 8 fps en JPEG exige unos 2,8 Mbit/s de subida constante, unos 31 GB al día por cámara. MediaPipe *lite* corre en CPU (6,5 ms por fotograma en una MacBook M5 Pro). En la revisión sistemática, la única comparación directa entre borde y nube (Mundody y Guddeti, 2026) reporta menor latencia bajo congestión y más privacidad en el borde.

## Decisión
El agente de la vivienda estima la pose, clasifica y envía solo **eventos y clips**. El backend:
- recibe los eventos;
- entrega enlaces firmados para subir los clips;
- publica la configuración vigente de la clasificación.

Ver [CONTRATO_AGENTE.md](../CONTRATO_AGENTE.md).

## Consecuencias
- El video no sale de la vivienda, salvo el clip del evento y la vista en vivo cuando el familiar la pide.
- La EC2 no ejecuta detección.
- El backend debe distribuir los umbrales y las versiones a cada agente.

Mundody, S., & Guddeti, R. M. R. (2026). Pose-based fall detection with robust feature analysis and privacy-aware edge-fog-cloud deployment. *IEEE Access, 14*, 114183–114208. https://doi.org/10.1109/ACCESS.2026.3716718
