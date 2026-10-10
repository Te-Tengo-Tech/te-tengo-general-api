# Notifications: e-mail and push

The API sends e-mail (password recovery, invitations) and push notices (API contract §7) through two ports of `shared`, `NotificadorCorreo` and `NotificadorPush`. A property chooses the adapter of each one, so switching providers never touches the code or the mobile app.

| Port | Property (variable) | Values | Default |
|---|---|---|---|
| `NotificadorCorreo` | `tetengo.correo.proveedor` (`TT_CORREO_PROVEEDOR`) | `registro`, `ses`, `smtp` | `registro`; `ses` with the `local` profile |
| `NotificadorPush` | `tetengo.push.proveedor` (`TT_PUSH_PROVEEDOR`) | `registro`, `fcm`, `sns`, `simulador` | `registro`; `sns` with the `local` profile |

Any other value leaves the port without an adapter, so the API does not start. Integration tests replace both ports with recording fakes (`CorreoDePrueba`, `PushDePrueba`) and never call a real service; the AWS adapters have their own tests against Floci ([ADR 0005](adr/0005-floci-local-aws-emulator.md)) and the SMTP adapter against a Mailpit container.

## E-mail
| Provider | What it does | Configuration |
|---|---|---|
| `registro` | Logs the recipient and subject; the body (with one-time links) only at `DEBUG` | — |
| `ses` | Amazon SES v2 `SendEmail`, plain UTF-8 text | `TT_SES_REMITENTE` (a verified SES identity, required), `TT_SES_REGION`, `TT_SES_ENDPOINT` (blank: AWS); credentials from the AWS SDK default chain (instance role) |
| `smtp` | Any SMTP relay through Spring's `JavaMailSender`: the same message as `ses` (sender, recipient, subject, plain UTF-8 text body) | `TT_SMTP_REMITENTE` (required) and Spring Boot's standard `spring.mail.*`: `SPRING_MAIL_HOST` (required), `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`; STARTTLS required (`TT_SMTP_STARTTLS`, default `true`) |

A failure of SES or of the SMTP relay is logged and not propagated [implementation choice]: password recovery must answer the same whether the account exists or not (CA-03.2).

### SMTP
`smtp` works with any SMTP relay that supports STARTTLS: a transactional e-mail service, a mail server of your own domain or your organization's relay. Use the host, port (usually `587` for STARTTLS), user name and password the relay gives, and a sender address it is allowed to send from (often a verified domain or address; the relay's own documentation says what it requires and what limits it applies):

```bash
TT_CORREO_PROVEEDOR=smtp
TT_SMTP_REMITENTE="Te Tengo <no-responder@example.com>"
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<user>
SPRING_MAIL_PASSWORD=<password>   # from the server's secrets, never committed
```

- **STARTTLS** is enabled and required (`mail.smtp.starttls.enable` and `mail.smtp.starttls.required`), so the password never travels in clear text; the API refuses to send if the relay does not offer it. `TT_SMTP_STARTTLS=false` turns it off, only for a local relay without TLS. Connection, read and write time out after 10 s [implementation choice]. Any other JavaMail setting goes in `spring.mail.properties.*`.
- **Startup.** Spring Boot creates the mail sender only when `SPRING_MAIL_HOST` is set, so `registro` and `ses` need no mail settings. `smtp` without `SPRING_MAIL_HOST` or `TT_SMTP_REMITENTE` stops the API at startup.
- **Health.** With `SPRING_MAIL_HOST` set, `/actuator/health` includes a `mail` component that connects to the relay. It is not part of the liveness and readiness groups, so a relay outage never restarts or unroutes the API; `MANAGEMENT_HEALTH_MAIL_ENABLED=false` removes it.
- **Locally**, any SMTP catcher works, e.g. a Mailpit container (`docker run -p 1025:1025 -p 8025:8025 axllent/mailpit:v1.31.2`) with `TT_CORREO_PROVEEDOR=smtp SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025 TT_SMTP_STARTTLS=false TT_SMTP_REMITENTE=no-responder@tetengo.test ./gradlew bootRun`; read the mail at http://localhost:8025.

### Links
The password-reset and invitation e-mails carry a link to the app's `/nueva-contrasena?token={token}` and `/invitacion/{token}` routes (`lib/app/rutas.dart` of the mobile app) under `TT_ENLACE_BASE`, which has no trailing slash. `TT_ENLACE_RECUPERACION` and `TT_ENLACE_INVITACION` replace a whole template instead; `{token}` is the URL-safe one-time token.

| Client | `TT_ENLACE_BASE` | Links |
|---|---|---|
| Native app (default) | `tetengo://app` | `tetengo://app/nueva-contrasena?token=…`, `tetengo://app/invitacion/…` |
| PWA, hash routing (e.g. Cloudflare Pages) | `https://te-tengo.pages.dev/app/#` | `https://te-tengo.pages.dev/app/#/nueva-contrasena?token=…`, `…/app/#/invitacion/…` |

One base serves every e-mail, so a deployment chooses one client. With the PWA base, the link opens the PWA in the browser, even on a phone that has the native app.

## Push
### What every provider sends
`ContenidoDelAviso` builds one content per notice, and every provider sends the same:
- **Title and body** (`notification` in FCM, `aps.alert` in APNs). The operating system shows them when the app is closed or in the background (CA-16.2). The copy is the prototype's, word for word (see [Copy](#copy)), with the older adult's first name, the room with its article and the time of the household, America/Lima (CA-16.1).
- **Label** (`URGENTE · CAÍDA`, `SEVERIDAD MEDIA`, `SEGUIMIENTO`), when the prototype's notice shows one: the iOS subtitle (`aps.alert.subtitle`; in FCM `apns.payload.aps.alert.subtitle`) and the data key `etiqueta`, since Android notifications have no subtitle.
- **Data payload** of the API contract, all strings: `tipo`, `alertaId`, `camaraId`, `habitacion`, `ocurridaEn` (ISO-8601 UTC), plus `etiqueta` when the notice has a label; absent values are left out. The app (`lib/core/notificaciones/mensaje_push.dart`, `lib/app/push.dart`) routes on these keys and ignores the others.
- **High priority and the default sound** (`android.priority: high`, `apns-priority: 10`, web push `Urgency: high`): a fall must arrive in less than 10 s (CA-16.1).
- **Urgency** (`CargasPush`, hotfix 0.3.1):
  - **Lifetime 1 h** [implementation choice]: Android `ttl: 3600s`, APNs `apns-expiration`, web push header `TTL: 3600`. A phone that comes back online later gets nothing stale; the alert is in the app anyway.
  - **Grouping**: Android `notification.tag`, APNs `apns-collapse-id` and `aps.thread-id`, web `notification.tag` are the `alertaId` (`camara-<camaraId>` for camera notices, the type otherwise), so a later notice of the same alert replaces the earlier one on screen.
  - **Android channel** `alertas_caida` for every notice about an alert (the app creates it with high importance at start); other notices use the app's default channel. `apns-push-type: alert`.
  - **Urgent notices** (`TipoAviso.urgente()`: `ALERTA_CAIDA`, `ALERTA_MOVIMIENTO_INESTABLE`, `ALERTA_ACTUALIZADA_A_CAIDA`, `CAIDA_CONFIRMADA`, `ALERTA_ESCALADA`, `SIN_CONTACTO_SECUNDARIO`): `aps.interruption-level: time-sensitive` (it breaks through Focus; the app needs the Time Sensitive Notifications capability, otherwise iOS delivers it as `active`), web `requireInteraction: true` (no `renotify`: the PWA's service worker shows every push itself, and Firebase's own notification then replaces it by `tag` without alerting twice). The others are `active`.
- **Web push** (platform `WEB`, the PWA): the FCM message also carries a `webpush` block with the same title and body and, when `TT_PWA_URL` is set, `fcm_options.link`, the page a click opens: the alert's screen (`<TT_PWA_URL>#/alerta/<alertaId>`) for alert notices, the PWA otherwise. FCM applies the block of the token's platform, so one message serves phones and browsers.

### Delivery (outbox)
`EnvioDeAvisos` never sends inside a database transaction or on the household agent's request thread:
1. The notice is saved in `avisos_pendientes` together with the change that calls for it (the alert, the escalation, the attended alert…), so a rolled-back change sends nothing and a notice is never lost if the API stops.
2. After the commit, `DespachoDeAvisos` (`@Async @TransactionalEventListener`) makes the first attempt: it reads the notice and its destinations in one transaction, sends with none open, and records the result in another.
3. A notice that was not delivered stays queued and `ReintentarAvisos` retries it every 15 s until its deadline (`vence_en`): **30 min** for urgent notices (`tetengo.push.ventana-urgente`), which are also retried while nobody in the family has an active device, and stop when the alert is attended or marked a false alarm; `reintento-cada × intentos-maximos` (5 min) for the others, which are dropped at once when nobody has an active device.
4. When a phone registers (`POST /api/dispositivos`), the household's queued notices are attempted right away, so a phone that re-registers during a fall gets it.

Each attempt is logged: `INFO` when delivered (how many devices), `ERROR` the first time a notice is not delivered (no active device, or the service failed) and when it is given up, `WARN` for the attempts in between. The FCM adapter also logs every send with the device's platform and token fingerprint (first 12 hex characters of its SHA-256; the token itself is never logged) and FCM's message id or error code. The alert's `estado_aviso` (`ENVIANDO`, `ENTREGADO`, `REINTENTANDO`, `NO_ENTREGADO`) and `notificada_en` record how the notice that opened it went (API contract §5, §7).

### Copy
Sources are in `te-tengo-mobile-flutter/docs/references/`: the screen PNGs (`screens/`) and the interactive prototype (`prototype/prototipo.html`, function named in brackets). The example values are the prototype's: Rosa, the Sala, 10:42. `EnvioDeAvisos` reads, for the household in context and right before each attempt (`DetalleDeAvisos`), what the text needs beyond the data payload: the older adult's first name (`hogares` public API `AdultoMayorDelHogar`, the first word of the registered name, as the app's `nombrePila`), the alert's type and start, the household's escalation wait (US-10) and the first name of whoever attended the alert (`cuentas`). Queued notices store nothing more, and none of this goes in the data payload.

| `tipo` | Title | Body | Label | Source |
|---|---|---|---|---|
| `ALERTA_CAIDA` | Posible caída de Rosa en la Sala | 10:42 · Toca para ver qué hacer y llamarla. | URGENTE · CAÍDA | screen 44 (lock screen) |
| `ALERTA_MOVIMIENTO_INESTABLE` | Rosa tuvo un movimiento inestable en la Sala | 10:39 · No es una caída. Revisa cómo está. | SEVERIDAD MEDIA | screen 49 (lock screen) |
| `ALERTA_ACTUALIZADA_A_CAIDA` | Ahora: posible caída de Rosa en la Sala | 10:41 · Empezó como movimiento inestable a las 10:39. | URGENTE · CAÍDA | screen 51 (lock screen); the second time is when the alert began |
| `CAIDA_CONFIRMADA` | Rosa sigue en el suelo | Caída confirmada a las 10:43. La alerta sigue activa. | — | prototipo.html [`confirmar`], in-app notice |
| `SE_LEVANTO` | Rosa se levantó | 10:45 · Se puso de pie en la Sala. Confirma cómo está. | SEGUIMIENTO | screen 57 (banner); label from prototipo.html [`levanta`], lock screen |
| `ALERTA_ATENDIDA` | Carmen atendió la alerta | 10:46 · Caída en la Sala. | — | prototipo.html [`otroAtiende`], in-app notice; it also quotes the member's note, which the API does not take, so the body ends at the room |
| `ALERTA_ESCALADA` | Nadie atendió la alerta: te toca | Pasaron 5 min sin respuesta. Eres el contacto secundario. | the alert's: URGENTE · CAÍDA or SEVERIDAD MEDIA | prototipo.html [`escalar`], lock screen, the secondary contact's notice |
| `SIN_CONTACTO_SECUNDARIO` | No hay a quién escalar | Pasaron 5 min y no hay contacto secundario. | the alert's | prototipo.html [`escalar`], lock screen |
| `CAMARA_DESCONECTADA` | La cámara de la Sala se desconectó | Revisa el cable de la cámara, que la PC esté encendida y el internet de la casa. | — | screen 28 (banner); prototipo.html [`desconectar`], lock screen |
| `CAMARA_RECONECTADA` | La cámara de la Sala volvió a estar en línea | El monitoreo se restableció a las 10:52. | — | screen 29 (in-app toast) |
| `DETECCION_NO_CONFIABLE` | La detección no es confiable en la Sala | Hace más de 5 minutos que la cámara no ve bien a Rosa. Revisa la luz y el encuadre. | — | screen 36 (banner); prototipo.html [`noConfiable`], lock screen |
| `PAUSA_FINALIZADA` | La cámara de la Sala se reactivó | Terminó la pausa a las 11:42. | — | prototipo.html [`finPausa`], in-app toast; screen 35 adds the length («de 1 hora»), see [BLOCKERS.md](BLOCKERS.md) |
| `DATOS_ELIMINADOS` | Se eliminaron las grabaciones | Se borraron las grabaciones al revocar el consentimiento. | — | **no prototype source** [implementation choice], see [BLOCKERS.md](BLOCKERS.md) |

Rooms outside the app's list (`Sala`, `Sala comedor`, `Dormitorio`, `Cocina`, `Pasillo`, `Comedor`) go without an article («en Cuarto de Rosa»). If a value cannot be read, the text leaves it out («Posible caída en la Sala»); a notice is never held back for it. The `registro` provider logs the data payload at `INFO` and the title and body, which name the older adult, only at `DEBUG`.

### Providers
| Provider | Sends to | Configuration |
|---|---|---|
| `registro` | Nobody: logs the notice | — |
| `fcm` | Firebase Cloud Messaging HTTP v1 (Firebase Admin SDK), one message per registered token: Android, iOS and web (PWA) | `TT_FCM_CREDENCIALES`: path of the Firebase service-account JSON key (never commit it; `.gitignore` covers the usual names); `TT_PWA_URL` (optional, HTTPS) for the web notification's link |
| `sns` | Amazon SNS mobile push: one platform endpoint per device, created or reused on the first push and stored with the device (`dispositivos.referencia_push`); the message carries `GCM` (FCM v1 `fcmV1Message`, web push block included) and `APNS` / `APNS_SANDBOX` payloads, and SNS picks the one of the endpoint's platform. Web devices only with `TT_SNS_ARN_WEB`; otherwise they are skipped with a warning | `TT_SNS_ARN_ANDROID`, `TT_SNS_ARN_IOS` (platform application ARNs, both required), `TT_SNS_ARN_WEB` (optional, an FCM application; may be the Android ARN), `TT_SNS_REGION`, `TT_SNS_ENDPOINT` (blank: AWS); credentials from the AWS SDK default chain; `TT_PWA_URL` as with `fcm` |
| `simulador` | The booted iOS simulator (`xcrun simctl push booted <bundle-id> -`), once per notice whatever the native tokens; web devices are skipped; local only | `TT_SIMULADOR_BUNDLE_ID` (default `tech.tetengo.teTengo`, the app's bundle id) |

### Failures and invalid tokens
- **Transient FCM errors** (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`) are retried by the FCM adapter for the failed devices only, up to 3 sends, after FCM's `Retry-After` when it sends one (otherwise 1 s, then 2 s), never waiting more than 10 s [implementation choice].
- **The service does not respond, or accepts none of the devices** (FCM `UNAVAILABLE` after those retries, `INVALID_ARGUMENT`, SNS throttling, `simctl` failing): the adapter throws `FallaDePush`, the error is logged and the notice stays queued; `ReintentarAvisos` retries it (see [Delivery](#delivery-outbox), CA-16.4). The alert is still listed when the app opens.
- **Some devices fail for a transient reason while others accept it:** the notice counts as delivered and the failure is logged, so the devices that got it do not get it twice [implementation choice].
- **The service says a token no longer exists** (FCM `UNREGISTERED` or `SENDER_ID_MISMATCH`; SNS `EndpointDisabled`): the device is deactivated (`dispositivos.activo = false`, `desactivado_en`) and gets no more notices until the phone registers a token again with `POST /api/dispositivos`. The app learns it from `GET /api/dispositivos/{id}` and registers a new token. If every device was rejected, an urgent notice keeps being retried (a phone may register again); others are dropped.
- **FCM `INVALID_ARGUMENT` does not deactivate the device**: FCM also answers it for a payload it rejects, which would deactivate every device. It is logged as an `ERROR` and the notice is retried.
- `xcrun` missing (CI, Linux) with `simulador`: a warning, nothing sent.
- **A provider that cannot reach web devices** (`sns` without `TT_SNS_ARN_WEB`, `simulador`) skips them: they are neither delivered nor deactivated, and a notice whose only devices are web ones is not retried.
- `TT_PWA_URL` must be HTTPS: FCM rejects a message with another link as `INVALID_ARGUMENT`, so no web notice would ever be delivered; the API does not start with one.

### Web push (PWA)
The PWA registers its FCM web push token (FlutterFire `getToken` with the Firebase project's VAPID key, service worker `firebase-messaging-sw.js`) with `plataforma: "WEB"`. Nothing else is needed on the backend side:
- **`fcm`**: the same service-account key sends to web tokens. Set `TT_PWA_URL` to the PWA's address (e.g. `https://te-tengo.pages.dev/app/`) so a click opens it.
- **`sns`**: create a platform application of platform `GCM` with the same Firebase project's credential, or reuse the Android one, and set `TT_SNS_ARN_WEB`. Locally, Floci gets `te-tengo-web` created.
- On iPhone, web push only reaches a PWA added to the home screen (iOS 16.4 or later), after the user allows notifications.

### iOS tokens
The app uses FlutterFire on both platforms, so it registers **FCM registration tokens on iOS too**, not APNs device tokens. Therefore:
- with `fcm`, Firebase delivers to iOS through APNs (the APNs authentication key must be uploaded to the Firebase project);
- with `sns`, `TT_SNS_ARN_IOS` must be an **FCM (`GCM`) platform application**, which can be the same ARN as Android. An `APNS` platform application only works if the app registers its APNs token instead (`FirebaseMessaging.getAPNSToken()`), a change in the app. The APNs payload already includes `gcm.message_id` so FlutterFire would handle it.

## Switching from Firebase to SNS (configuration only)
The team starts with Firebase directly and moves to SNS later; neither the app nor the database needs a change (for the PWA, also set `TT_SNS_ARN_WEB`, see [Web push](#web-push-pwa)):
1. In AWS, create an SNS platform application of platform **`GCM`** (Firebase Cloud Messaging) with the **same Firebase project's service-account JSON** as credential (FCM HTTP v1).
2. Set `TT_PUSH_PROVEEDOR=sns`, `TT_SNS_ARN_ANDROID` and `TT_SNS_ARN_IOS` to that ARN (one application serves both, see above) and `TT_SNS_REGION`; give the instance role `sns:CreatePlatformEndpoint`, `sns:GetEndpointAttributes`, `sns:SetEndpointAttributes` and `sns:Publish` on it. Remove `TT_FCM_CREDENCIALES`.
3. Restart the API. Devices registered under Firebase keep their tokens: each one gets its SNS endpoint on its first push.

Going back is the same: `TT_PUSH_PROVEEDOR=fcm` ignores the stored endpoint ARNs.

## Local testing
`./gradlew bootRun` uses the `local` profile and Floci from `compose.yaml`:
- **E-mail** goes to Floci's SES. Read it (links included) at http://localhost:4566/_aws/ses; `curl -X DELETE http://localhost:4566/_aws/ses` empties it.
- **Push** goes to Floci's SNS, which creates the `te-tengo-android`, `te-tengo-ios` and `te-tengo-web` platform applications and captures every push instead of sending it: http://localhost:4566/_aws/sns/push-notifications (filter with `?EndpointArn=...`). The `Payload` field is what FCM or APNs would receive.

To see a push on a phone or simulator, override the provider:
- **iOS simulator** (macOS with Xcode, app installed on the booted simulator):
  ```bash
  TT_PUSH_PROVEEDOR=simulador ./gradlew bootRun
  ```
  `EnvioDeAvisos` only calls the provider when a member has a registered device. If the app on the simulator registers none (no Firebase configuration in the app), register one for the demo account with `DEMO_DISPOSITIVO_SIMULADOR=1 ./scripts/seed-demo.sh`, or `POST /api/dispositivos {"tokenPush":"simulador-ios","plataforma":"IOS"}` with its token. Tap the notification to check the app opens the right screen.
- **Real phones through Firebase** (project `te-tengo-9ad70`):
  ```bash
  TT_PUSH_PROVEEDOR=fcm TT_FCM_CREDENCIALES=~/.config/te-tengo/fcm.json ./gradlew bootRun
  ```
  Keep the key outside the repository (`chmod 600`).
- `TT_PUSH_PROVEEDOR=registro` / `TT_CORREO_PROVEEDOR=registro` only log, as before. To try `smtp` locally, see [SMTP](#smtp).
