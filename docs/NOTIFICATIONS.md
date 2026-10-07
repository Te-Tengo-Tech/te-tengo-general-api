# Notifications: e-mail and push

The API sends e-mail (password recovery, invitations) and push notices (API contract §7) through two ports of `shared`, `NotificadorCorreo` and `NotificadorPush`. A property chooses the adapter of each one, so switching providers never touches the code or the mobile app.

| Port | Property (variable) | Values | Default |
|---|---|---|---|
| `NotificadorCorreo` | `tetengo.correo.proveedor` (`TT_CORREO_PROVEEDOR`) | `registro`, `ses` | `registro`; `ses` with the `local` profile |
| `NotificadorPush` | `tetengo.push.proveedor` (`TT_PUSH_PROVEEDOR`) | `registro`, `fcm`, `sns`, `simulador` | `registro`; `sns` with the `local` profile |

Any other value leaves the port without an adapter, so the API does not start. Integration tests replace both ports with recording fakes (`CorreoDePrueba`, `PushDePrueba`) and never call a real service; the AWS adapters have their own tests against Floci ([ADR 0005](adr/0005-floci-local-aws-emulator.md)).

## E-mail
| Provider | What it does | Configuration |
|---|---|---|
| `registro` | Logs the recipient and subject; the body (with one-time links) only at `DEBUG` | — |
| `ses` | Amazon SES v2 `SendEmail`, plain UTF-8 text | `TT_SES_REMITENTE` (a verified SES identity, required), `TT_SES_REGION`, `TT_SES_ENDPOINT` (blank: AWS); credentials from the AWS SDK default chain (instance role) |

A SES failure is logged and not propagated [implementation choice]: password recovery must answer the same whether the account exists or not (CA-03.2).

## Push
### What every provider sends
`ContenidoDelAviso` builds one content per notice, and every provider sends the same:
- **Title and body** (`notification` in FCM, `aps.alert` in APNs). The operating system shows them when the app is closed or in the background (CA-16.2). The copy is the prototype's, word for word (see [Copy](#copy)), with the older adult's first name, the room with its article and the time of the household, America/Lima (CA-16.1).
- **Label** (`URGENTE · CAÍDA`, `SEVERIDAD MEDIA`, `SEGUIMIENTO`), when the prototype's notice shows one: the iOS subtitle (`aps.alert.subtitle`; in FCM `apns.payload.aps.alert.subtitle`) and the data key `etiqueta`, since Android notifications have no subtitle.
- **Data payload** of the API contract, all strings: `tipo`, `alertaId`, `camaraId`, `habitacion`, `ocurridaEn` (ISO-8601 UTC), plus `etiqueta` when the notice has a label; absent values are left out. The app (`lib/core/notificaciones/mensaje_push.dart`, `lib/app/push.dart`) routes on these keys and ignores the others.
- **High priority and the default sound** (`android.priority: high`, `apns-priority: 10`): a fall must arrive in less than 10 s (CA-16.1).

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
| `fcm` | Firebase Cloud Messaging HTTP v1 (Firebase Admin SDK), one message per registered token | `TT_FCM_CREDENCIALES`: path of the Firebase service-account JSON key (never commit it; `.gitignore` covers the usual names) |
| `sns` | Amazon SNS mobile push: one platform endpoint per device, created or reused on the first push and stored with the device (`dispositivos.referencia_push`); the message carries `GCM` (FCM v1 `fcmV1Message`) and `APNS` / `APNS_SANDBOX` payloads, and SNS picks the one of the endpoint's platform | `TT_SNS_ARN_ANDROID`, `TT_SNS_ARN_IOS` (platform application ARNs, both required), `TT_SNS_REGION`, `TT_SNS_ENDPOINT` (blank: AWS); credentials from the AWS SDK default chain |
| `simulador` | The booted iOS simulator (`xcrun simctl push booted <bundle-id> -`), once per notice whatever the tokens; local only | `TT_SIMULADOR_BUNDLE_ID` (default `tech.tetengo.teTengo`, the app's bundle id) |

### Failures and invalid tokens
- **The service does not respond, or accepts none of the devices** (FCM `UNAVAILABLE`, SNS throttling, `simctl` failing): the adapter throws `FallaDePush`, the error is logged and the notice is queued; `ReintentarAvisos` retries it every 15 s, up to 20 times (CA-16.4). The alert is still listed when the app opens.
- **Some devices fail for a transient reason while others accept it:** the notice counts as delivered and the failure is logged, so the devices that got it do not get it twice [implementation choice].
- **The service rejects a token** (FCM `UNREGISTERED`, `SENDER_ID_MISMATCH` or `INVALID_ARGUMENT`; SNS `EndpointDisabled`): the device is deactivated (`dispositivos.activo = false`) and gets no more notices until the phone registers the token again with `POST /api/dispositivos`, which reactivates it and, for SNS, re-enables its endpoint. If every device was rejected, the notice is not retried.
- `xcrun` missing (CI, Linux) with `simulador`: a warning, nothing sent.

### iOS tokens
The app uses FlutterFire on both platforms, so it registers **FCM registration tokens on iOS too**, not APNs device tokens. Therefore:
- with `fcm`, Firebase delivers to iOS through APNs (the APNs authentication key must be uploaded to the Firebase project);
- with `sns`, `TT_SNS_ARN_IOS` must be an **FCM (`GCM`) platform application**, which can be the same ARN as Android. An `APNS` platform application only works if the app registers its APNs token instead (`FirebaseMessaging.getAPNSToken()`), a change in the app. The APNs payload already includes `gcm.message_id` so FlutterFire would handle it.

## Switching from Firebase to SNS (configuration only)
The team starts with Firebase directly and moves to SNS later; neither the app nor the database needs a change:
1. In AWS, create an SNS platform application of platform **`GCM`** (Firebase Cloud Messaging) with the **same Firebase project's service-account JSON** as credential (FCM HTTP v1).
2. Set `TT_PUSH_PROVEEDOR=sns`, `TT_SNS_ARN_ANDROID` and `TT_SNS_ARN_IOS` to that ARN (one application serves both, see above) and `TT_SNS_REGION`; give the instance role `sns:CreatePlatformEndpoint`, `sns:GetEndpointAttributes`, `sns:SetEndpointAttributes` and `sns:Publish` on it. Remove `TT_FCM_CREDENCIALES`.
3. Restart the API. Devices registered under Firebase keep their tokens: each one gets its SNS endpoint on its first push.

Going back is the same: `TT_PUSH_PROVEEDOR=fcm` ignores the stored endpoint ARNs.

## Local testing
`./gradlew bootRun` uses the `local` profile and Floci from `compose.yaml`:
- **E-mail** goes to Floci's SES. Read it (links included) at http://localhost:4566/_aws/ses; `curl -X DELETE http://localhost:4566/_aws/ses` empties it.
- **Push** goes to Floci's SNS, which creates the `te-tengo-android` and `te-tengo-ios` platform applications and captures every push instead of sending it: http://localhost:4566/_aws/sns/push-notifications (filter with `?EndpointArn=...`). The `Payload` field is what FCM or APNs would receive.

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
- `TT_PUSH_PROVEEDOR=registro` / `TT_CORREO_PROVEEDOR=registro` only log, as before.
