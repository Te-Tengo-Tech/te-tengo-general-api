# 0006. Push provider chosen by configuration

**Status:** accepted.

## Context
- The architecture sends push notices through **Amazon SNS** (FCM on Android, APNs on iOS), but the AWS account and the SNS platform applications do not exist yet (BLOCKERS.md). A **Firebase** project does, and the app already receives pushes through Firebase Cloud Messaging (FlutterFire) on both platforms.
- The team wants to start with Firebase directly and move to SNS later **without changing the app**.
- Developers need to see pushes locally, including on the iOS simulator, and tests must never reach a real service.

## Decision
- `NotificadorPush` stays the only port; `tetengo.push.proveedor` (`TT_PUSH_PROVEEDOR`) picks one adapter: `registro` (default, logs), `fcm` (Firebase Admin SDK, FCM HTTP v1), `sns` (SNS mobile push) or `simulador` (`xcrun simctl push`, local only).
- **Every adapter sends the same content** (`ContenidoDelAviso`, `CargasPush`): title and body for the operating system, the data payload of the API contract, high priority. The FCM adapter's message is tested to serialize exactly like the `GCM` payload SNS sends.
- The port **reports what the service did** (`Resultado`): accepted devices, tokens no longer valid and new provider addresses. `alertas` deactivates rejected devices and stores the SNS endpoint ARN with the device (`V19`). The retry semantics of CA-16.4 do not change: a provider throws `FallaDePush` when the service does not respond or accepts no device.
- With SNS, endpoints are created lazily on a device's first push, so devices registered under Firebase move to SNS without registering again.

## Consequences
- Firebase → SNS is a configuration change: one FCM (`GCM`) platform application in SNS with the same Firebase credentials, its ARN in `TT_SNS_ARN_ANDROID` and `TT_SNS_ARN_IOS` (docs/NOTIFICATIONS.md).
- Because the app registers FCM tokens on iOS, an SNS `APNS` platform application would need the app to register APNs tokens instead; the payload is ready for it.
- Push copy now lives in the backend (`ContenidoDelAviso`) and must be kept in step with the app's in-app notices.
- The Firebase Admin SDK is a new dependency (Firestore and Cloud Storage excluded).
