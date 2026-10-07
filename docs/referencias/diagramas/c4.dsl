workspace "Sistema basado en estimación de pose para la detección de caídas" "Modelo C4 del sistema Te Tengo. Los contenedores usan los nombres de la arquitectura lógica v2." {

    model {
        familiar = person "Familiar/cuidador" "Recibe y atiende las alertas; ve la cámara en vivo a demanda; gestiona el nombre de la habitación, pausas, consentimiento y familiares." "user"
        adultoMayor = person "Adulto mayor" "Vive en la vivienda monitoreada; no opera ningún dispositivo." "user"

        camaras = softwareSystem "Cámaras RGB estándar" "Una webcam USB instalada y configurada por el equipo del proyecto (piloto); sin procesamiento en el dispositivo." "external"
        push = softwareSystem "Servicio de notificaciones push" "Servicio externo que entrega las alertas al celular aunque la aplicación esté cerrada." "external"

        sistema = softwareSystem "Sistema basado en estimación de pose para la detección de caídas" "Detecta caídas y movimientos inestables a partir del video y alerta a los familiares." "system" {

            group "Capa de Presentación" {
                mobileApp = container "Aplicación del familiar/cuidador" "Alertas, clip, vista en vivo a demanda y registro de accesos, nombre de la habitación y pausas, consentimiento, familia e historial." "Aplicación móvil · tecnología por definir" "clientApp"
                localDb = container "Base de datos local del cliente" "Últimas alertas, historial consultado, estado de las cámaras y preferencias." "SQLite" "database"
            }

            group "Capa de Captura" {
                captureAgent = container "Agente de captura" "Lee su configuración fija (vivienda, webcam y habitación) y registra la cámara al iniciar; reduce, cifra y envía el video solo con consentimiento y sin pausa activa. Sin análisis local." "Aplicación de escritorio · Python + OpenCV" "capture"
            }

            group "Capa de Procesamiento de Video" {
                detectionModule = container "Módulo de detección" "Servicio de ingesta de video, Servicio de estimación de pose y Servicio de clasificación cinemática: caída (confirmada tras 30 s en el suelo), movimiento inestable, recuperación y detección no confiable." "Python" "processing"
                liveStream = container "Servicio de transmisión en vivo" "Retransmite la cámara a demanda del familiar (desde el inicio o desde una alerta); no disponible con la cámara en pausa." "Servidor de medios WebRTC" "processing"
            }

            group "Capa de Servicios" {
                backendApi = container "Backend API del sistema" "Servicios de Aplicación: cuentas, cámaras, consentimiento, alertas, escalamiento, vista en vivo y registro de accesos, familiares e historial; persistencia central y de clips de video." "REST API" "api"
            }

            group "Capa de Persistencia Compartida" {
                centralDb = container "Base de Datos Central" "Usuarios, familiares, cámaras y pausas, consentimientos, alertas, eventos y registro de accesos a la vista en vivo." "Base de datos relacional" "database"
                clipStorage = container "Almacenamiento de clips" "Clips cifrados de los eventos (6 s antes y 6 s después), con retención y eliminación por revocación." "Almacenamiento de objetos" "database"
            }
        }

        // Contexto
        familiar -> sistema "Recibe alertas y gestiona el monitoreo"
        adultoMayor -> camaras "Es captado en su vivienda"
        camaras -> sistema "Entrega video en vivo" "USB (UVC)"
        sistema -> push "Solicita el envío de alertas"

        // Cliente
        familiar -> mobileApp "Usa"
        mobileApp -> backendApi "Consulta, configura y gestiona alertas" "HTTPS/REST"
        mobileApp -> localDb "Lee y escribe datos locales"

        // Captura
        camaras -> captureAgent "Entrega video en vivo" "USB (UVC)"
        captureAgent -> detectionModule "Envía video cifrado (480p · 5-10 fps)" "TLS"
        captureAgent -> backendApi "Registra la cámara y consulta el estado de captura" "HTTPS/REST"

        // Procesamiento
        detectionModule -> backendApi "Reporta eventos detectados"
        detectionModule -> clipStorage "Guarda el clip del evento"
        detectionModule -> liveStream "Reenvía el video en vivo"
        mobileApp -> liveStream "Solicita y recibe la vista en vivo a demanda" "WebRTC"

        // Servicios
        backendApi -> liveStream "Autoriza la sesión de vista en vivo"
        backendApi -> centralDb "Lee y escribe datos" "SQL"
        backendApi -> clipStorage "Genera enlaces temporales y elimina clips"
        backendApi -> push "Solicita el envío de alertas"
        mobileApp -> push "Se suscribe y recibe las notificaciones push"
    }

    views {
        systemContext sistema "contexto" {
            include *
            include adultoMayor
            title "Diagrama de contexto — Sistema Te Tengo"
            autolayout tb 300 150
        }

        container sistema "contenedores" {
            include *
            include adultoMayor
            title "Diagrama de contenedores — Sistema Te Tengo"
            autolayout tb 300 150
        }

        styles {
            element "user" {
                background "#4A6FA5"
                color "#ffffff"
                shape "Person"
            }

            element "external" {
                background "#999999"
                color "#ffffff"
            }

            element "system" {
                background "#1168BD"
                color "#ffffff"
            }

            element "clientApp" {
                background "#438DD5"
                color "#ffffff"
                shape "MobileDevicePortrait"
            }

            element "capture" {
                background "#2E6295"
                color "#ffffff"
            }

            element "processing" {
                background "#6BA3DC"
                color "#000000"
            }

            element "api" {
                background "#1168BD"
                color "#ffffff"
            }

            element "database" {
                background "#85BBF0"
                color "#000000"
                shape "Cylinder"
            }

            theme default
        }
    }

    configuration {
        scope softwareSystem
    }
}
