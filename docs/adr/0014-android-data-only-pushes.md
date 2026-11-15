# 0014. Android pushes as data-only messages, opt-in per device

**Status:** accepted (2026-11-15).

## Context
- Every Android push was an FCM *notification* message: a top-level `notification` plus `android.notification` with `tag` = the alert id, `channel_id` = `alertas_caida`, priority `HIGH` and a 1 h `ttl` (`CargasPush`, `NotificadorPushFcm`; the same FCM HTTP v1 message goes through SNS `GCM` endpoints).
- **Measurement (2026-11-15, production):** a Samsung S7 (Android 8) had its Wi-Fi cut three times for 60 s while falls were detected. After each reconnection only **one** notice arrived (about 1 s later): the most recent push sent while it was offline. 3 of the 5 fall alerts sent while it was offline never reached the phone as pushes; the family only learned of them about 5 min later, through the escalation job.
- **FCM's rule** (Firebase, *Collapsible and non-collapsible messages*): "Notification messages are always collapsible and will ignore the collapse_key parameter"; "Except for notification messages, all messages are non-collapsible by default"; "For Android, there is a limit of 100 messages that can be stored without collapsing". FCM keeps only the latest notification message of an app for an offline device, which is what the measurement shows.
- The mobile app (te-tengo-mobile-flutter) is being changed in parallel to draw notifications itself from the message data; older app versions in the field still rely on the system drawing notification messages.

## Options considered
- **A per-alert `collapse_key`:** impossible. FCM ignores `collapse_key` for notification messages, and the `tag` already differs per alert.
- **Another push service (Azure Notification Hubs, Amazon SNS):** no help. Both deliver to Android through FCM (and to iOS through APNs), so the same collapsing applies; SNS is already a supported provider (ADR 0006) and shows it.
- **Data-only messages for every Android device:** fixes collapsing, but an older app that does not draw notifications would show nothing at all.
- **Data-only messages with an opt-in flag per device (chosen).**

## Decision
- **Registration:** `POST /api/dispositivos` takes an optional `avisoEnLaApp: boolean` (absent = `false`), stored in `dispositivos.aviso_en_la_app` (`not null default false`, migration V33) as sent on every registration, whatever the platform; `Dispositivo` returns it. An app that stops sending it goes back to notification messages on its next registration.
- **Delivery:** the flag travels in `NotificadorPush.Destino.avisoEnLaApp`; `Destino.soloDatos()` is true only for `ANDROID`. Those devices get a data-only FCM message: no top-level `notification`, no `android.notification`, no APNs or web push block; `android.priority: HIGH`, `android.ttl: 3600s`; `data` = the contract's data payload (`tipo`, `alertaId?`, `camaraId?`, `habitacion?`, `ocurridaEn`, `etiqueta?`) plus `titulo`, `cuerpo`, `grupo` (the former Android `tag`) and `canal` (`alertas_caida`, only for notices about an alert), all strings. Built by `CargasPush.mensajeFcmSoloDatos` (FCM HTTP v1 JSON, used in the SNS `GCM` payload) and `NotificadorPushFcm.mensajeSoloDatos` (Admin SDK).
- The FCM adapter already sends one message per device (`sendEach`), so each device gets its own message type; retries, result mapping and invalid-token handling are unchanged. SNS publishes per endpoint and picks the message of each device the same way. Every other device (no flag, iOS, web) gets exactly the message it got before.
- **iOS stays as it is.** APNs also keeps only the last notification per app for an offline device, and iOS has no equivalent of an Android data-only message that the app can turn into a visible notification reliably in the background. On iOS the app catches up by reading the active alerts when it reconnects or comes to the foreground, and the 5-minute escalation remains the safety net.

## Consequences
- Rollout is per device and backward compatible: until a phone runs an app version that registers `avisoEnLaApp: true`, it keeps notification messages and their collapsing.
- The app must draw the notification itself (channel `canal`, `tag` = `grupo`, title, body, label) in its background message handler; if it fails to, the user sees nothing. That is the app's responsibility and is tested there.
- An app the user force-stopped receives nothing either way: Android does not deliver FCM messages of any kind to a stopped app.
- FCM stores at most 100 non-collapsible messages per device while it is offline; beyond that it drops them all and the app must sync. Te Tengo's volume is far below it, and the 1 h lifetime still drops stale notices.
- FCM may deprioritize high-priority data messages that do not lead to a visible notification; the app shows one for every message it receives.

## References
- Firebase. (n.d.). *Collapsible and non-collapsible messages*. Firebase Cloud Messaging documentation. https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types
- Firebase. (n.d.). *Set and manage Android message priority*. https://firebase.google.com/docs/cloud-messaging/android/message-priority
