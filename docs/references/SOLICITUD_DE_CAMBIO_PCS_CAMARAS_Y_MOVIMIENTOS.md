# Solicitud de cambio: PCs, cámaras y datos de movimiento

**Proyecto:** Sistema basado en estimación de pose para la detección de caídas en adultos mayores en su vivienda (producto Te Tengo)
**Documento afectado:** Project Charter, versión 1.3 (05/10/2026), `ACP_Gutierrez_Jhosepmyr_Riva_Elmer.docx`, y sus fuentes `.md` de esta carpeta
**Solicitantes:** Gutierrez Soto, Jhosepmyr Orlando; Riva Rodriguez, Elmer Augusto
**Fecha de la solicitud:** 10/10/2026
**Estado:** pendiente de aprobación del equipo y del asesor

> Este documento solicita el cambio y **no modifica el Project Charter**. Si se aprueba, el equipo aplicará al Charter los textos propuestos en la sección 2 como una nueva versión. Las historias US-28 a US-40 ya figuran en el Product Backlog como «Sin sprint asignado», a la espera de esta aprobación.

## 1. Descripción del cambio

Se propone ampliar el alcance en cuatro frentes:

1. **Varias cámaras y ambientes.** Una vivienda puede tener una o varias PCs, y cada PC una o varias webcams, cada una asignada a un ambiente (Sala, Dormitorio…).
2. **Instalación sin archivo de configuración.** El familiar titular instala Te Tengo Captura y vincula la PC desde la app móvil, escaneando un código QR o escribiendo un código. Hoy el equipo del proyecto deja un archivo de configuración fijo en la PC.
3. **Diagnóstico.** Un modo avanzado en la PC muestra en lenguaje sencillo qué detecta y decide el sistema. Un registro de actividad anota quién cambió qué y cuándo.
4. **Historial de movimientos.** Con un consentimiento aparte y opcional, se guardan los puntos del cuerpo (sin imagen) para repetir lo que pasó como un esqueleto animado y para evaluar los falsos negativos y falsos positivos de la detección.

| Aspecto | Según el Charter 1.3 | Propuesto |
|---|---|---|
| Cámaras por vivienda | Una webcam (Recursos requeridos, «1 cámara RGB estándar»). Las fuentes `.md` excluyen el «uso de varias cámaras por vivienda» como trabajo futuro (`ALCANCE.md`). | Varias PCs por vivienda y varias webcams por PC, con una cámara por ambiente |
| Instalación | El equipo del proyecto instala la webcam y configura el agente (`SUPOSICIONES_RESTRICCIONES.md`, «Instalación de la cámara») | El familiar titular vincula la PC desde la app (QR o código), sin archivos ni contraseñas en la PC |
| Diagnóstico | No previsto | Modo avanzado en la PC, registro de actividad y exportación de un diagnóstico sin datos privados |
| Datos guardados | Clips de 6 s antes y 6 s después de cada evento | Además, si el adulto mayor lo acepta aparte: puntos del cuerpo en la nube con el plazo que elija el titular, y en la PC según su configuración; video continuo solo en la PC, desactivado por defecto |
| Evaluación | Validación en conjuntos de datos públicos y medición de falsas alarmas en el piloto (OE4) | Igual, con intervalos de confianza, resultados por condición y nueva ejecución de las reglas sobre los movimientos guardados, sin entrenar modelos |

Historias del Product Backlog: US-28 a US-31 (EP06 - Instalación y Cámaras), US-32 a US-34 (EP07 - Diagnóstico y Actividad), US-35 a US-39 (EP08 - Datos de Movimiento) y US-40 (EP03 - Detección y Alertas), con 51 SP en total (`03-product-backlog/PRODUCT_BACKLOG.md`).

## 2. Decisiones propuestas

### 2.1. Vinculación de la PC (US-28, US-31)

Se usa la concesión de autorización de dispositivo de OAuth 2.0 (*Device Authorization Grant*, RFC 8628), pensada para equipos en los que es incómodo escribir credenciales.

- **Códigos.** Al instalarse, la PC pide al backend un `device_code` (secreto, nunca se muestra) y un `user_code`. El `user_code` tiene 8 caracteres del alfabeto `BCDFGHJKLMNPQRSTVWXZ` (20 consonantes, sin vocales ni dígitos, como recomienda la RFC 8628, §6.1), se muestra como `XXXX-XXXX` por legibilidad, sirve una sola vez y vence a los 10 minutos.
- **QR y código escrito.** La PC muestra la URI de verificación y un QR con el código incluido (`verification_uri_complete`, §3.3.1). Si el celular no puede escanearlo, el familiar escribe el código en la app.
- **Consulta de la PC.** La PC consulta cada 5 s, el intervalo por defecto de la RFC (§3.2 y §3.5), y atiende las respuestas `authorization_pending` (seguir esperando), `slow_down` (sumar 5 s al intervalo), `access_denied` (el familiar rechazó) y `expired_token` (pedir un código nuevo y mostrarlo).
- **Aprobación.** Antes de aprobar, la app muestra el nombre de la PC, su sistema operativo, la versión de Te Tengo Captura y lo que podrá hacer, para evitar que alguien haga aprobar a distancia una PC ajena (*remote phishing*, §5.4). Solo el familiar titular aprueba.
- **Fuerza bruta.** El backend limita los intentos con códigos incorrectos (§5.1). Se propone el mismo criterio del inicio de sesión (CA-02.3): 5 intentos fallidos bloquean el ingreso de códigos durante 15 minutos (CA-28.6).
- **Vinculación permanente.** La PC recibe un token de acceso de vida corta, que renueva sin intervención con un token de renovación que rota en cada uso, como exige la RFC 9700 para clientes públicos (§2.2.2 y §4.14). Ambos se guardan en el almacén de credenciales del sistema operativo. Solo se pide vincular de nuevo si la PC fue desvinculada o pasó 90 días o más sin conectarse (CA-28.7).
- **Desvinculación.** Desde la app («Quitar esta PC») o desde la PC. El backend revoca los tokens y avisa a la PC por el canal de control; si está apagada, se entera al conectarse. La PC deja de capturar, borra sus credenciales y vuelve a la pantalla del QR (US-31).

### 2.2. Cámaras y ambientes (US-29, US-30)

- **Ahora (opción A): una cámara por ambiente.** La PC lista sus webcams por nombre, con una vista previa que no sale de la PC, y el familiar asigna cada una a un ambiente.
- **Modelo de datos preparado para después.** La relación ambiente → cámaras se guarda como de uno a muchos, para admitir más adelante varias cámaras por ambiente con detección independiente (opción B) o con fusión de vistas (opción C). Ninguna de las dos entra en este cambio.
- **Identificación de la cámara.** Cada cámara se reconoce por el identificador único del dispositivo que da el sistema operativo, no por su índice. Así, conectar o desconectar otra webcam no cambia la cámara de un ambiente (CA-29.4).
- **Varias PCs.** Cada PC se vincula por separado y conserva sus propias cámaras; la app muestra cada PC con su estado, la última señal y su versión (US-30).
- **CPU.** Cada cámara agrega carga a la CPU de su PC. Cuántas cámaras conectar a cada PC es decisión del usuario; si la PC no da abasto, se agrega otra PC.

### 2.3. Diagnóstico y actividad (US-32, US-33, US-34)

- **Modo avanzado** (desactivado por defecto): registro en vivo por cámara en lenguaje sencillo (con filtros, búsqueda, pausa y «Ver línea técnica») y medidores de fps, persona detectada, visibilidad de los puntos, velocidad de descenso, ángulo del tronco y razón ancho/alto frente a sus umbrales, condiciones M1 a M3, fase con la ventana de reacción de 1,48 s y la confirmación a los 30 s, y el esqueleto. Los parámetros y umbrales son los de la especificación del clasificador (`te-tengo-desktop-pywebview/docs/classification-spec.md`). Nada de esto sale de la PC.
- **Registro de actividad:** quién, qué y cuándo (PC vinculada o desvinculada, cámara ↔ ambiente, pausas, cambios de retención, modo avanzado, consentimientos), guardado en la PC y en el backend, visible en la PC y en la app, sin edición ni borrado.
- **Diagnóstico exportable:** un `.zip` con los registros, la versión y los umbrales, sin credenciales, imágenes, video ni puntos del cuerpo.

### 2.4. Movimientos en la nube (US-37)

- **Qué se guarda.** Por cada fotograma con persona: los 33 puntos de MediaPipe cuantizados (`x` e `y` en `uint16`, `z` en `int16` y la visibilidad en `uint8`), la marca de tiempo y los parámetros θ (ángulo), P (razón ancho/alto), v (velocidad) y la fase del clasificador. No se guarda ninguna imagen.
- **Formato.** Parquet comprimido con zstd, un archivo por cámara cada 5 minutos.
- **Envío.** La PC sube cada archivo directamente al almacenamiento de objetos (Cloudflare R2) con una URL prefirmada que emite el backend; los datos no pasan por la máquina virtual del backend. En la cola del agente van en un carril de menor prioridad que los eventos urgentes, los demás eventos y los clips (ver la decisión 5 del [registro de decisiones](../04-arquitectura/registro-de-decisiones.md)). Sin internet, se acumulan en la PC y se suben al volver la conexión.
- **Índice.** PostgreSQL guarda solo un índice (una fila por archivo: cámara, inicio, fin, tamaño y clave del objeto) y resúmenes; no guarda los puntos.
- **Retención.** La elige el titular: 7, 30, 90 o 365 días; viene desactivada hasta que la elija. Una tarea diaria borra los archivos vencidos y sus filas del índice.
- **Por qué como máximo 1 año (proporcionalidad).** Las dos finalidades declaradas, revisar qué pasó y evaluar la detección, se cubren dentro de un año: la repetición sirve sobre todo en los días siguientes a un evento, y un año alcanza para comparar temporadas y cubrir el piloto y su análisis. Guardar más tiempo no agrega valor a esas finalidades y aumenta el riesgo, contra el principio de proporcionalidad de la Ley N.° 29733. En la PC, en cambio, el familiar puede elegir «Nunca borrar», porque los datos no salen de la vivienda.
- **Volumen (estimado, no medido).** Entre 40 y 80 MB por cámara al día con persona visible todo el día: unos 250 bytes por fotograma sin comprimir × 8 fps × 86 400 s ≈ 170 MB, con una compresión de 2 a 4 veces que debe medirse.

### 2.5. Guardado en la PC (US-38)

- Carpeta elegible (por defecto, la carpeta de datos de la aplicación del sistema operativo), con un interruptor y un plazo por tipo: movimientos, video continuo y clips. En la PC se admite «Nunca borrar».
- Límite de espacio: al alcanzarlo se borra primero el video más antiguo y luego los movimientos más antiguos; la detección y las alertas nunca se detienen.
- Cifrado en reposo con una clave guardada en el almacén de credenciales del sistema operativo. Índice local en SQLite.
- Video continuo **desactivado por defecto** y **nunca subido a la nube**: 480p, 8 fps, H.264, en tramos de 10 minutos.

### 2.6. Consentimiento para guardar movimientos (US-35, US-36, US-39)

- **Separado y opcional.** No es condición para usar el servicio: las alertas funcionan igual si no se acepta.
- **Información previa** (derecho de información, art. 18 de la Ley N.° 29733): finalidad, qué se guarda, dónde (en la PC y en Cloudflare R2, fuera del Perú), por cuánto tiempo, quién accede (los familiares vinculados) y cómo ejercer los derechos de acceso (art. 19), de actualización, inclusión, rectificación y supresión (art. 20) y de oposición (art. 22).
- **Dato sensible.** Los movimientos de una persona mayor asociados a sus caídas son información relacionada con la salud, que la ley trata como dato sensible (art. 2, numeral 5). Por eso el consentimiento debe ser expreso y por escrito (art. 13, numerales 13.5 y 13.6): se registra con fecha, hora, quién lo otorgó, quién lo registró y la versión del texto (CA-35.2).
- **Flujo transfronterizo.** R2 no ofrece ubicaciones en Sudamérica: sus sugerencias de ubicación y jurisdicciones son Norteamérica, Europa, Asia-Pacífico, Oceanía, la Unión Europea, FedRAMP y Estados Unidos (documentación de Cloudflare, consultada el 10/10/2026). Guardar ahí los movimientos es un flujo transfronterizo de datos personales (art. 2, numeral 10, y art. 15 de la ley). El consentimiento previo, informado, expreso e inequívoco del titular es uno de los supuestos que admite el art. 15. Las obligaciones de detalle están en el Reglamento aprobado por el Decreto Supremo N.° 016-2024-JUS (publicado el 30/11/2024); **el equipo debe revisar sus artículos sobre flujo transfronterizo y datos sensibles antes de implementar**, porque esta solicitud no los cita.
- **Revocación.** Detiene el guardado, elimina los movimientos de la nube y, en la PC, pregunta si también se borran los guardados localmente. El consentimiento de monitoreo y las alertas no cambian.
- **Repetición del movimiento (US-39).** En la app, solo el esqueleto, nunca video. En la PC, también sobre el video local si se guardó. Cada repetición queda en el registro de accesos, como la vista en vivo (US-24); solo la ven los familiares vinculados y solo dentro del plazo de retención y con el consentimiento vigente.

### 2.7. Evaluación sin entrenamiento de modelos (US-40)

- Sensibilidad, especificidad y exactitud con intervalos de confianza del 95 %, sobre conjuntos de datos públicos etiquetados y grabaciones propias hechas con el protocolo de la prueba piloto (`08-asesorias/protocolo-prueba-piloto.md`, en la rama `feature/piloto-y-decisiones`, aún no publicada en `main`).
- Resultados por condición: dirección de la caída, distancia, altura de la cámara, luz, oclusión, caídas lentas y caídas desde la cama.
- Nueva ejecución de las reglas, fuera de línea, sobre los movimientos guardados, para comparar umbrales.
- **No se entrena ningún modelo.** El clasificador es por reglas con umbrales calibrados y MediaPipe se usa preentrenado. Se mantiene la restricción del Charter «Sin aprendizaje profundo» (fuentes `.md`) y la exclusión de modelos entrenados (`ALCANCE.md`).
- La app permite informar «Hubo una caída y no avisó» (CA-40.4), que entra a la evaluación como posible falso negativo.

## 3. Textos del Charter que cambiarían

| Sección | Dónde está | Texto actual | Texto propuesto |
|---|---|---|---|
| Alcance (entregables) | `ALCANCE.md` | «Agente de captura para Windows (Te Tengo Captura), con una cámara instalada y configurada por el equipo del proyecto.» | «Agente de captura para Windows (Te Tengo Captura), que el familiar vincula desde la aplicación móvil, con una o varias cámaras, una por ambiente.» |
| Alcance (exclusiones) | `ALCANCE.md` | «Vinculación de cámaras por parte del usuario y uso de varias cámaras por vivienda (en la prueba piloto la cámara es instalada y configurada por el equipo del proyecto; queda como trabajo futuro).» | «Uso de varias cámaras en un mismo ambiente y fusión de sus vistas, incluida la reconstrucción 3D (trabajo futuro).» |
| Recursos requeridos (Hardware) | `.docx` y `RECURSOS_REQUERIDOS.md` | `.docx`: «1 cámara RGB estándar (webcam) para la captura de video durante el desarrollo y las pruebas.» | «Cámaras RGB estándar (webcam USB), una por ambiente monitoreado; de 1 a 2 para el desarrollo y las pruebas.» |
| Suposiciones (Instalación de la cámara) | `SUPOSICIONES_RESTRICCIONES.md` | «El equipo del proyecto instala una webcam USB y configura el agente de captura en una PC con Windows de la vivienda, que permanece encendida durante la prueba piloto.» | «El familiar instala Te Tengo Captura en una PC con Windows de la vivienda, que permanece encendida, y la vincula desde la aplicación móvil; el equipo del proyecto lo acompaña durante la prueba piloto.» |
| Suposiciones (Consentimiento informado) | `.docx` | «El adulto mayor y/o su familiar autorizan el uso de la cámara y el procesamiento de su imagen, conforme a la Ley N.° 29733 de Protección de Datos Personales.» | Agregar: «El almacenamiento de sus movimientos (puntos del cuerpo, sin imagen) requiere un consentimiento aparte, opcional, expreso y por escrito.» |
| Riesgos (riesgo 8 del `.docx`; 10 en `RIESGOS_Y_MITIGACION.md`) | `.docx` y `.md` | Mitigación: «… y definir una política clara de retención y eliminación del video, conforme a la Ley N.° 29733.» | Agregar: «… consentimiento aparte para guardar movimientos, plazo de retención elegido por el titular (como máximo 1 año en la nube) y tratamiento del almacenamiento fuera del Perú como flujo transfronterizo.» |
| Glosario | `.docx` y `GLOSARIO.md` | — | Agregar «Ambiente: espacio de la vivienda vigilado por una cámara (por ejemplo, la sala)» y «Vinculación de la PC: aprobación, desde la aplicación móvil, de la PC de la vivienda para que capture y envíe eventos». |

No cambian los objetivos (OE1 a OE4), los indicadores de éxito, la restricción «Sin aprendizaje profundo», la exclusión de modelos entrenados ni la gestión de un único adulto mayor por cuenta.

## 4. Justificación

1. **Cobertura.** Una sola cámara cubre un ambiente; las caídas pueden ocurrir en el dormitorio, la cocina o el pasillo. El riesgo 10 del Charter (`.docx`), la oclusión por el mobiliario, se reduce con más de un punto de vista en la vivienda.
2. **Instalación sin el equipo.** El archivo de configuración fijo obliga a que el equipo vaya a cada vivienda y conozca las credenciales. La vinculación con QR o código no deja secretos en archivos de la PC y es el mecanismo estándar para equipos sin teclado cómodo (RFC 8628).
3. **Diagnóstico.** Durante el piloto, el equipo y la familia necesitan entender por qué el sistema avisó o no avisó, sin acceso remoto a la PC y sin enviar video.
4. **Evaluación y repetición.** Los movimientos guardados permiten medir falsos negativos y falsos positivos en condiciones reales, recalibrar umbrales sin volver a grabar (OE4-I1 y OE4-I2) y que la familia vea qué pasó antes y después de un evento sin ver video.

## 5. Impacto

### 5.1. Alcance

- Se agregan 13 historias y 51 SP al Product Backlog (de 27 historias y 86 SP a 40 historias y 137 SP).
- Criterios vigentes que habría que ajustar si se aprueba (hoy no se modifican):
  - **CA-06.1** dice que «el equipo del proyecto instaló y configuró la cámara en la PC de la vivienda»; con US-28 y US-29 la vincula el familiar.
  - **CA-11.4** dice «sin guardar video salvo el clip del evento»; US-38 permite guardar video continuo solo en la PC, desactivado por defecto.
  - **US-06** («Nombre de la habitación de la cámara») se solapa con la asignación de ambientes de US-29.
  - **CA-08.4** debe agregar «PCs y cámaras» a lo que el familiar invitado ve en solo lectura.
  - **CA-09.1** («programa la eliminación de las grabaciones») debe precisar si incluye los movimientos y los datos guardados en la PC.
- **Punto abierto:** si el video continuo en la PC (US-38) necesita su propio consentimiento o basta con el de monitoreo.

### 5.2. Cronograma

- No se proponen cambios de fechas en los hitos. Las historias quedan «Sin sprint asignado»; el equipo debe asignarlas en la planificación si se aprueba el cambio.
- 51 SP equivalen a un 59 % más que el backlog actual, con el mismo calendario académico (riesgo nuevo, abajo). Por MoSCoW: Must 20 SP (US-28, US-29, US-31, US-35 y US-40), Should 21 SP y Could 10 SP.

### 5.3. Costo

- No se necesita hardware nuevo para una cámara; cada ambiente adicional necesita una webcam USB y, si la CPU no alcanza, otra PC (Restricción «Presupuesto limitado»).
- Cloudflare R2, precios publicados y consultados el 10/10/2026: almacenamiento estándar a 0,015 USD por GB-mes, operaciones de clase A a 4,50 USD por millón y de clase B a 0,36 USD por millón, salida de datos sin costo, y un nivel gratuito mensual de 10 GB-mes, 1 millón de operaciones de clase A y 10 millones de clase B.
- **Ejemplo (estimado):** una cámara a 60 MB por día con retención de 365 días acumula unos 21,9 GB, que cuestan unos 0,18 USD al mes después de los 10 GB gratuitos. Un archivo cada 5 minutos son 288 subidas al día por cámara (unas 8 640 al mes), dentro del nivel gratuito de clase A.

### 5.4. Riesgos

| Riesgo | Cambio | Mitigación propuesta |
|---|---|---|
| Riesgo 8 del Charter (protección de datos) | Aumenta: se guardan datos sensibles de salud y salen del Perú | Consentimiento aparte y opcional, retención elegida por el titular (máximo recomendado 1 año en la nube), cifrado en reposo en la PC, sin imágenes en la nube, registro de accesos a las repeticiones y revisión del Reglamento antes de implementar |
| Nuevo: crecimiento del almacenamiento | Los movimientos crecen con el número de cámaras y el plazo | Tarea diaria de eliminación, índice en PostgreSQL sin puntos, límite de espacio en la PC que borra primero el video |
| Nuevo: CPU de la PC insuficiente para varias cámaras | Cada cámara agrega carga; la elección es del usuario | Medidor de fps en el modo avanzado (US-32); más cámaras implican más PCs |
| Nuevo: aprobar por error una PC ajena | Phishing a distancia (RFC 8628, §5.4) | Pantalla de aprobación con los datos de la PC, solo el titular aprueba, límite de intentos y registro de actividad |
| Nuevo: robo o reemplazo de la PC | Credenciales guardadas en la PC | Almacén de credenciales del sistema operativo, desvinculación remota (US-31) y rotación del token de renovación (RFC 9700) |
| Nuevo: video continuo guardado en la vivienda | Uso indebido del video por quien acceda a la PC | Desactivado por defecto, cifrado en reposo, nunca se sube a la nube |
| Nuevo: carga de trabajo | 51 SP adicionales en el mismo calendario | Priorizar los Must y dejar los Could para después del piloto si no hay capacidad |

El equipo debe asignar la probabilidad y el impacto de los riesgos nuevos.

## 6. Entregables afectados

| Entregable | Efecto | Estado |
|---|---|---|
| Product Backlog (OE2-I3) | 13 historias y 57 criterios nuevos | Agregados el 10/10/2026 como «Sin sprint asignado» (`03-product-backlog/`) |
| Prototipos de alta fidelidad (OE2-I2) | Pantallas nuevas en la app móvil y en Te Tengo Captura | Brief de diseño en `05-prototipos/BRIEF-pcs-camaras-y-movimientos.md`; pantallas pendientes |
| Documentación de la arquitectura (OE2-I1) | Vinculación por RFC 8628, almacenamiento de objetos para movimientos, índice en PostgreSQL, carril de baja prioridad en el agente | Pendiente de esta aprobación |
| Agente de captura y Módulo de detección (OE3-I1) | Vinculación, varias cámaras, modo avanzado, guardado local | Pendiente |
| Aplicación móvil con backend (OE3-I2) | Vinculación, PCs y cámaras, actividad, consentimiento de movimientos, repetición | Pendiente |
| Informes de validación (OE4-I1 y OE4-I2) | Intervalos de confianza, resultados por condición y reporte de caídas sin alerta | Pendiente |
| Project Charter | Textos de la sección 3 | A la espera de esta aprobación |

## 7. Aprobación

| Rol | Nombre | Decisión (aprobado / rechazado / con observaciones) | Firma | Fecha |
|---|---|---|---|---|
| Asesor | | | | |
| Jefe de Proyecto | | | | |
| Jefe de Proyecto | | | | |

Observaciones:

&nbsp;

## Referencias

Cloudflare. (s.f.-a). *Data location*. Cloudflare R2 docs. Recuperado el 10 de octubre de 2026, de https://developers.cloudflare.com/r2/reference/data-location/

Cloudflare. (s.f.-b). *Pricing*. Cloudflare R2 docs. Recuperado el 10 de octubre de 2026, de https://developers.cloudflare.com/r2/pricing/

Congreso de la República del Perú. (2011). *Ley N.° 29733, Ley de Protección de Datos Personales*. https://www.gob.pe/institucion/congreso-de-la-republica/normas-legales/243470-29733

Denniss, W., Bradley, J., Jones, M., & Tschofenig, H. (2019). *OAuth 2.0 Device Authorization Grant* (RFC 8628). Internet Engineering Task Force. https://www.rfc-editor.org/rfc/rfc8628

Lodderstedt, T., Bradley, J., Labunets, A., & Fett, D. (2025). *Best current practice for OAuth 2.0 security* (RFC 9700, BCP 240). Internet Engineering Task Force. https://www.rfc-editor.org/rfc/rfc9700

Ministerio de Justicia y Derechos Humanos. (2024). *Decreto Supremo N.° 016-2024-JUS, que aprueba el Reglamento de la Ley N.° 29733, Ley de Protección de Datos Personales*. https://busquedas.elperuano.pe/dispositivo/SE/2349653-1
