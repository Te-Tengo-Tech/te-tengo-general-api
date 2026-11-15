# Paper artifacts

This branch is the backend behind the paper (alert flow, consent and access log) with the tests and scripts that the paper cites. The capture agent, the classifier and the classifier evaluation are in the companion repository.

| Paper | Where |
|---|---|
| Alert flow: delivery records retried until delivered or expired, escalation (Sections 3.1, 3.5; Table 2, reliability) | `src/main/java/tech/tetengo/api/alertas/`, `src/main/java/tech/tetengo/api/shared/infrastructure/push/`; tests `alertas/application/EscalamientoIntegrationTest.java`, `shared/infrastructure/push/NotificadorPushFcmTest.java` |
| Live view only with consent; every access logged (Table 2, security) | `src/main/java/tech/tetengo/api/monitoreo/`; tests `monitoreo/interfaces/rest/VistaEnVivoIntegrationTest.java`, `monitoreo/interfaces/rest/AccesosIntegrationTest.java`, `monitoreo/domain/model/AccesoVistaEnVivoTest.java`, `monitoreo/infrastructure/mediamtx/MediaMtxIntegrationTest.java` |
| End-to-end alert latency against the deployment, with a physical phone (Section 5, RQ3) | `scripts/latencia-e2e/medir_produccion.sh` (real capture agent replaying URFD falls; phone timed with `adb logcat` and the clock offset of `desfase_adb.py`); analysis `scripts/latencia-e2e/analizar_produccion.py` |
| Outage scenarios (Table 2, reliability) | `scripts/latencia-e2e/medir_produccion.sh corte` (`corte.sh`): Case A, the household link (agent → API) cut while falls are detected, through a local CONNECT proxy (`proxy_corte.py`, agent `agente_corte.py`; analysis `analizar_corte.py`); Case B, `TT_CORTE_LADO=telefono`, the phone loses its network and `analizar_lado_telefono.py` records when it had a route |
| Android pushes sent as data-only messages so notices queued while the phone is offline do not collapse (Table 2, reliability) | `docs/adr/0014-android-data-only-pushes.md`; `src/main/java/tech/tetengo/api/shared/infrastructure/push/` (`CargasPush.java`); tests `shared/infrastructure/push/NotificadorPushFcmTest.java`, `alertas/interfaces/rest/PushDeAlertasIntegrationTest.java` |
| Local end-to-end run without the deployment | `scripts/medir-latencia-e2e.sh` |
| Clip retention (Section 3.5: not defined yet) | `src/main/resources/application.yml`, `retencion-de-clips` |

Test paths are relative to `src/test/java/tech/tetengo/api/`. Run the tests with `./gradlew test` (Docker is needed for the integration tests).
