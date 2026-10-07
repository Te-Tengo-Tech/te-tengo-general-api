# Arquitectura lógica y física del sistema

**Sistema basado en estimación de pose para la detección de caídas en adultos mayores en su vivienda** (producto Te Tengo)

Universidad Peruana de Ciencias Aplicadas · Facultad de Ingeniería · Programa Académico de Ingeniería de Software

Autores: Gutierrez Soto, Jhosepmyr Orlando (u202317638); Riva Rodriguez, Elmer Augusto (u202220829)  
Asesor: Barrientos Padilla, Alfredo  
Lima, septiembre de 2026

> Versión en Markdown del documento `Arquitectura_Logica_Fisica_Gutierrez_Riva.docx`.

## Índice

1. [Introducción](#1-introducción)
2. [Arquitectura lógica](#2-arquitectura-lógica)
3. [Arquitectura física](#3-arquitectura-física)
4. [Modelo C4](#4-modelo-c4)
   - [4.1. Diagrama de contexto](#41-diagrama-de-contexto)
   - [4.2. Diagrama de contenedores](#42-diagrama-de-contenedores)
5. [Correspondencia entre vistas](#5-correspondencia-entre-vistas)
6. [Conclusiones](#6-conclusiones)
7. [Referencias](#7-referencias)

## 1. Introducción

Este documento presenta la arquitectura lógica y física del sistema basado en estimación de pose para la detección de caídas en adultos mayores en su vivienda, cuyo producto se denomina Te Tengo. Su propósito es describir cómo se organizan los componentes de software, dónde se despliegan y cómo se comunican durante el piloto, con el detalle necesario para revisar y aprobar la arquitectura.

Se presentan tres vistas complementarias. La arquitectura lógica organiza el software según el patrón de arquitectura en capas, en el que cada capa agrupa componentes con una responsabilidad común y se comunica con las demás mediante interfaces definidas (Richards, 2015). La arquitectura física se expresa con un diagrama de despliegue UML, que muestra los nodos (dispositivos, entornos de ejecución y servicios en la nube) y los artefactos desplegados en ellos (Object Management Group, 2017). Por último, el modelo C4 describe el sistema por niveles de abstracción; aquí se usan sus dos primeros niveles: el diagrama de contexto y el diagrama de contenedores (Brown, s.f.).

Todas las vistas usan los mismos nombres de componentes, de modo que cada elemento lógico puede rastrearse hasta el nodo físico y el contenedor C4 que lo implementan (sección 5). El alcance corresponde al piloto: una webcam USB instalada y configurada por el equipo del proyecto en una PC con Windows de la vivienda, y el análisis del video en la nube de Amazon Web Services (AWS).

## 2. Arquitectura lógica

La Figura 1 muestra la arquitectura lógica del sistema organizada en cinco capas de software (Presentación, Captura, Procesamiento de Video, Servicios y Persistencia Compartida) y un grupo de dispositivos e interfaces externas que quedan fuera del software, como la cámara y el servicio de notificaciones push. La vista es independiente de la infraestructura: define qué hace cada componente y con quién se comunica, sin indicar dónde se ejecuta. Su propósito es separar responsabilidades para que cada capa pueda modificarse o probarse sin afectar a las demás.

**Figura 1**

*Arquitectura lógica del sistema (patrón de arquitectura en capas)*

![Arquitectura lógica del sistema](logical-architecture-fall-detection-layers-v2.png)

*Nota.* Elaboración propia.

La Tabla 1 resume los componentes de cada capa y su responsabilidad principal.

**Tabla 1**

*Capas de la arquitectura lógica, componentes y responsabilidades*

| **Capa** | **Componentes** | **Responsabilidad** |
|---|---|---|
| **Captura** | Agente de captura | Lee la webcam USB con una configuración fija (vivienda, webcam y habitación), registra la cámara al iniciar, reduce el video a 480p y 5–10 fps, lo cifra y lo envía. No analiza el video y solo transmite con consentimiento y sin pausa activa. |
| **Procesamiento de Video** | Servicio de ingesta de video<br>Servicio de estimación de pose<br>Servicio de clasificación cinemática<br>Servicio de transmisión en vivo | Recibe el video y controla la calidad de los fotogramas, extrae los landmarks corporales, calcula parámetros cinemáticos y los compara con umbrales de referencia, sin entrenar modelos. Mantiene el búfer del clip y retransmite la vista en vivo a demanda. |
| **Servicios** | Backend API del sistema<br>Servicios de Aplicación | Expone la API REST y concentra la lógica del negocio: cuentas, perfil y consentimiento, cámaras y pausas, alertas y escalamiento, vista en vivo con registro de accesos, familiares, historial y eliminación de datos. |
| **Persistencia Compartida** | Persistencia central<br>Base de Datos Central<br>Persistencia de clips de video<br>Almacenamiento de clips | Guarda usuarios, consentimientos, cámaras, alertas, eventos y registro de accesos en una base de datos relacional, y los clips cifrados en un almacenamiento de objetos con política de retención. |
| **Presentación** | Aplicación del familiar/cuidador<br>Base de datos local del cliente | Recibe y gestiona las alertas, muestra el clip y la vista en vivo y permite configurar el monitoreo; guarda localmente las últimas alertas, el estado de las cámaras y las preferencias. |
| **Dispositivos e interfaces externas** | Cámaras RGB estándar<br>Servicio de notificaciones push | Captan la escena (una webcam USB en el piloto, sin procesamiento en el dispositivo) y entregan las alertas al celular aunque la aplicación esté cerrada. |

*Nota.* Elaboración propia.

El flujo principal de detección recorre las capas en el siguiente orden:

1.  Las Cámaras RGB estándar captan la escena y entregan el video al Agente de captura por USB (UVC).

2.  El Agente de captura reduce el video a 480p y 5–10 fps, lo cifra y lo envía por TLS al Servicio de ingesta de video, que descarta los fotogramas no válidos y mantiene el búfer del clip.

3.  El Servicio de estimación de pose detecta a la persona en cada fotograma y genera secuencias de landmarks corporales.

4.  El Servicio de clasificación cinemática calcula los parámetros cinemáticos, los compara con los umbrales y clasifica el evento; la caída se confirma cuando la persona permanece 30 s en el suelo.

5.  Los Servicios de Aplicación registran el evento en la Persistencia central, el clip de 6 s antes y 6 s después se guarda mediante la Persistencia de clips de video y se solicita la alerta al Servicio de notificaciones push.

6.  La Aplicación del familiar/cuidador recibe la alerta en menos de 10 s desde que la persona queda en el suelo, muestra el clip y, si el familiar lo solicita, abre la vista en vivo mediante el Servicio de transmisión en vivo; cada acceso queda registrado.

## 3. Arquitectura física

La Figura 2 presenta el diagrama de despliegue UML del piloto. Muestra los nodos físicos y de ejecución (la vivienda del adulto mayor, la nube de AWS en la región us-east-1, N. Virginia, y el smartphone del familiar) y los artefactos que se despliegan en cada uno. Su propósito es fijar la infraestructura concreta sobre la que se ejecuta la arquitectura lógica y las rutas de comunicación seguras entre la vivienda, la nube y el cliente.

En el diagrama, la seguridad del despliegue se apoya en cuatro medidas:

- **Cifrado en tránsito.** Todo el tráfico entre la vivienda, la nube y el smartphone viaja cifrado con TLS (HTTPS y WebRTC), y el proxy inverso es el único punto de entrada a la instancia.

- **Cifrado en reposo.** Los clips se guardan en Amazon S3, que cifra por defecto los objetos nuevos del lado del servidor (Amazon Web Services [AWS], s.f.-e); la base de datos se ubica en una subred privada y la API se conecta a ella por TLS.

- **Reglas de red.** Los Security Groups solo admiten HTTPS (443) y los puertos WebRTC hacia la EC2, SSH únicamente desde la IP del equipo de desarrollo y el puerto 5432 de RDS solo desde la EC2; la Network ACL añade un control por subred (AWS, s.f.-d).

- **Permisos mínimos.** La EC2 usa un rol de IAM con credenciales temporales, limitado al bucket de clips y a SNS, sin credenciales guardadas en el código (AWS, s.f.-f).

**Figura 2**

*Arquitectura física del sistema: diagrama de despliegue en Amazon Web Services*

![Diagrama de despliegue en AWS](deployment-diagram-fall-detection-aws-ec2.png)

*Nota.* Elaboración propia.

La Tabla 2 describe qué aloja cada nodo o servicio de la Figura 2 y por qué se eligió.

**Tabla 2**

*Nodos y servicios de la arquitectura física*

| **Nodo o servicio** | **Qué aloja** | **Por qué** |
|---|---|---|
| **Vivienda: PC con Windows y webcam USB** | Agente de captura (aplicación de escritorio Te Tengo Captura, Python 3.11 + OpenCV) y una webcam USB instalada por el equipo del proyecto. | La PC, encendida 24/7, solo captura, reduce, cifra y envía el video; el hogar no necesita hardware especial ni capacidad de análisis. |
| **Amazon EC2 (t3.small, Ubuntu Server + Docker Compose)** | Proxy inverso (Nginx + certificado TLS), Módulo de detección, Backend API del sistema y Servicio de transmisión en vivo (servidor de medios WebRTC). | Una instancia de rendimiento ampliable es de bajo costo para la carga reducida del piloto (AWS, s.f.-b); Docker Compose define y ejecuta los cuatro contenedores desde un único archivo (Docker, s.f.). |
| **Amazon RDS for PostgreSQL (db.t4g.micro)** | Base de Datos Central, en una subred privada. | Servicio administrado con respaldos automáticos y conexión SSL/TLS, sin acceso directo desde internet (AWS, s.f.-a). |
| **Amazon S3 y VPC Gateway Endpoint** | Almacenamiento de clips (bucket con cifrado del lado del servidor). | Almacenamiento de objetos con retención; el endpoint permite llegar a S3 desde la VPC sin internet gateway y sin costo adicional (AWS, s.f.-c). |
| **Amazon SNS** | Servicio de notificaciones push. | Entrega las alertas en Android (FCM) e iOS (APNs), aunque la aplicación esté cerrada (AWS, s.f.-g). |
| **Firewall de la VPC y AWS IAM** | Security Groups, Network ACL y rol de la instancia EC2. | Restringen el tráfico de entrada y limitan el acceso a S3 y SNS a lo estrictamente necesario (AWS, s.f.-d, s.f.-f). |
| **Smartphone del familiar (Android / iOS)** | Aplicación del familiar/cuidador y Base de datos local del cliente (SQLite). | Permite recibir alertas y consultar el historial reciente aunque la conexión móvil sea intermitente. |

*Nota.* Elaboración propia a partir de la documentación de AWS y Docker citada.

## 4. Modelo C4

El modelo C4 describe la arquitectura mediante diagramas jerárquicos que van de lo general a lo particular (Brown, s.f.). En esta sección se presentan los niveles de contexto y de contenedores.

### 4.1. Diagrama de contexto

El diagrama de contexto (Figura 3) representa el sistema como una sola caja y muestra quiénes lo usan y con qué sistemas externos se relaciona, sin detalles técnicos. Es el punto de partida para documentar un sistema porque ofrece una visión de conjunto comprensible para personas técnicas y no técnicas (Brown, s.f.).

**Figura 3**

*Diagrama de contexto del sistema (modelo C4, nivel 1)*

![Diagrama de contexto C4](c4/structurizr-contexto.png)

*Nota.* Elaboración propia.

Los actores y sistemas externos de la Figura 3 son los siguientes:

- **Familiar/cuidador (persona).** Recibe y atiende las alertas, ve la cámara en vivo a demanda y gestiona el nombre de la habitación, las pausas, el consentimiento y los familiares.

- **Adulto mayor (persona).** Vive en la vivienda monitoreada y es captado por la cámara; no opera ningún dispositivo, por lo que el sistema no depende de botones ni de sensores vestibles.

- **Cámaras RGB estándar (sistema externo).** Una webcam USB instalada y configurada por el equipo del proyecto que entrega video en vivo por USB (UVC), sin procesamiento en el dispositivo.

- **Servicio de notificaciones push (sistema externo).** Recibe del sistema las solicitudes de envío y entrega las alertas al celular aunque la aplicación esté cerrada.

### 4.2. Diagrama de contenedores

El diagrama de contenedores (Figura 4) amplía la caja del sistema y muestra sus contenedores, es decir, las aplicaciones y almacenes de datos que lo componen, junto con la tecnología principal de cada uno y la forma en que se comunican (Brown, s.f.). Los contenedores se agrupan con las mismas capas de la arquitectura lógica, lo que permite leer ambas vistas en paralelo.

**Figura 4**

*Diagrama de contenedores del sistema (modelo C4, nivel 2)*

![Diagrama de contenedores C4](c4/structurizr-contenedores.png)

*Nota.* Elaboración propia.

La Tabla 3 resume la tecnología y la responsabilidad de cada contenedor de la Figura 4.

**Tabla 3**

*Contenedores del sistema*

| **Contenedor** | **Tecnología** | **Responsabilidad** |
|---|---|---|
| **Aplicación del familiar/cuidador** | Aplicación móvil (tecnología por definir) | Alertas, clip, vista en vivo a demanda y registro de accesos, nombre de la habitación y pausas, consentimiento, familia e historial. |
| **Base de datos local del cliente** | SQLite | Últimas alertas, historial consultado, estado de las cámaras y preferencias. |
| **Agente de captura** | Aplicación de escritorio · Python + OpenCV | Lee la configuración fija, registra la cámara al iniciar y envía el video reducido y cifrado (480p · 5–10 fps, TLS) solo con consentimiento y sin pausa activa. |
| **Módulo de detección** | Python | Agrupa el Servicio de ingesta de video, el Servicio de estimación de pose y el Servicio de clasificación cinemática; reporta los eventos detectados y guarda el clip del evento. |
| **Servicio de transmisión en vivo** | Servidor de medios WebRTC | Retransmite la cámara a demanda en sesiones autorizadas por el Backend API del sistema; no está disponible con la cámara en pausa. |
| **Backend API del sistema** | REST API | Aloja los Servicios de Aplicación y la persistencia central y de clips de video; solicita las notificaciones push y genera enlaces temporales a los clips. |
| **Base de Datos Central** | Base de datos relacional | Usuarios, familiares, cámaras y pausas, consentimientos, alertas, eventos y registro de accesos a la vista en vivo. |
| **Almacenamiento de clips** | Almacenamiento de objetos | Clips cifrados (6 s antes y 6 s después del evento), con retención y eliminación por revocación. |

*Nota.* Elaboración propia.

## 5. Correspondencia entre vistas

La Tabla 4 relaciona cada componente de la arquitectura lógica con el nodo físico que lo aloja y con el contenedor C4 que lo representa. Todos los componentes tienen ubicación física y representación en el modelo C4, lo que confirma la consistencia entre las tres vistas.

**Tabla 4**

*Correspondencia entre la arquitectura lógica, la arquitectura física y el modelo C4*

| **Componente lógico (capa)** | **Arquitectura física (nodo / artefacto)** | **Modelo C4 (contenedor)** |
|---|---|---|
| Agente de captura (Captura) | PC de la vivienda / Agente de captura | Agente de captura |
| Servicio de ingesta de video, Servicio de estimación de pose y Servicio de clasificación cinemática (Procesamiento de Video) | Amazon EC2 / Módulo de detección | Módulo de detección |
| Servicio de transmisión en vivo (Procesamiento de Video) | Amazon EC2 / Servicio de transmisión en vivo | Servicio de transmisión en vivo |
| Backend API del sistema y Servicios de Aplicación (Servicios) | Amazon EC2 / Backend API del sistema | Backend API del sistema |
| Persistencia central y Persistencia de clips de video (Persistencia Compartida) | Amazon EC2 / Backend API del sistema | Backend API del sistema |
| Base de Datos Central (Persistencia Compartida) | Amazon RDS for PostgreSQL | Base de Datos Central |
| Almacenamiento de clips (Persistencia Compartida) | Amazon S3, vía VPC Gateway Endpoint | Almacenamiento de clips |
| Aplicación del familiar/cuidador (Presentación) | Smartphone del familiar / Aplicación móvil Te Tengo | Aplicación del familiar/cuidador |
| Base de datos local del cliente (Presentación) | Smartphone del familiar / SQLite | Base de datos local del cliente |
| Cámaras RGB estándar (externo) | Vivienda / Webcam USB | Cámaras RGB estándar (sistema externo) |
| Escena de la vivienda (externo) | Vivienda del adulto mayor | Adulto mayor (persona) |
| Servicio de notificaciones push (externo) | Amazon SNS | Servicio de notificaciones push (sistema externo) |

*Nota.* Elaboración propia.

El proxy inverso, el firewall de la VPC, el VPC Gateway Endpoint y AWS IAM solo aparecen en la arquitectura física porque protegen y encaminan la comunicación sin añadir funciones al sistema.

## 6. Conclusiones

La arquitectura lógica en capas separa la captura, el procesamiento de video, los servicios, la persistencia y la presentación, lo que permite ajustar el método de detección (estimación de pose y umbrales cinemáticos) sin modificar la aplicación móvil ni el agente de captura.

Como la PC de la vivienda solo captura, reduce, cifra y envía el video, el hogar requiere únicamente una PC con Windows y una webcam USB, y el análisis se concentra en la nube, donde se actualiza de forma centralizada.

La arquitectura física en AWS se ajusta a la escala del piloto: una instancia EC2 t3.small con Docker Compose aloja los cuatro contenedores, y Amazon RDS, Amazon S3 y Amazon SNS aportan la base de datos, el almacenamiento de clips y las notificaciones como servicios administrados, protegidos con TLS, cifrado en reposo, subred privada, reglas de firewall y permisos mínimos.

El modelo C4 y la correspondencia entre vistas muestran que los diagramas son consistentes: cada componente lógico tiene un nodo físico y un contenedor C4 asignados, por lo que la arquitectura queda definida como base para implementar el piloto.

## 7. Referencias

Amazon Web Services. (s.f.-a). *Amazon RDS for PostgreSQL*. Amazon Relational Database Service User Guide. https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html

Amazon Web Services. (s.f.-b). *Burstable performance instances*. Amazon EC2 User Guide. https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances.html

Amazon Web Services. (s.f.-c). *Gateway endpoints for Amazon S3*. AWS PrivateLink Guide. https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html

Amazon Web Services. (s.f.-d). *Infrastructure security in Amazon VPC*. Amazon VPC User Guide. https://docs.aws.amazon.com/vpc/latest/userguide/infrastructure-security.html

Amazon Web Services. (s.f.-e). *Protecting data with server-side encryption*. Amazon S3 User Guide. https://docs.aws.amazon.com/AmazonS3/latest/userguide/serv-side-encryption.html

Amazon Web Services. (s.f.-f). *Security best practices in IAM*. AWS Identity and Access Management User Guide. https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html

Amazon Web Services. (s.f.-g). *Sending mobile push notifications with Amazon SNS*. Amazon Simple Notification Service Developer Guide. https://docs.aws.amazon.com/sns/latest/dg/sns-mobile-application-as-subscriber.html

Brown, S. (s.f.). *The C4 model for visualising software architecture*. https://c4model.com/

Docker. (s.f.). *Docker Compose*. Docker Docs. https://docs.docker.com/compose/

Object Management Group. (2017). *OMG Unified Modeling Language (OMG UML)* (Versión 2.5.1). https://www.omg.org/spec/UML/2.5.1

Richards, M. (2015). *Software architecture patterns*. O’Reilly Media.
