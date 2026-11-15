# Paper artifacts

This branch is the backend behind the paper (alert flow, consent and access log) with the tests and scripts that the paper cites. The capture agent, the classifier and the classifier evaluation are in the companion repository.

| Paper | Where |
|---|---|
| Alert flow: delivery records retried until delivered or expired, escalation (Sections 3.1, 3.5; Table 2, reliability) | `src/main/java/tech/tetengo/api/alertas/`, `src/main/java/tech/tetengo/api/shared/infrastructure/push/`; tests `alertas/application/EscalamientoIntegrationTest.java`, `shared/infrastructure/push/NotificadorPushFcmTest.java` |
| Live view only with consent; every access logged (Table 2, security) | `src/main/java/tech/tetengo/api/monitoreo/`; tests `monitoreo/interfaces/rest/VistaEnVivoIntegrationTest.java`, `monitoreo/interfaces/rest/AccesosIntegrationTest.java`, `monitoreo/domain/model/AccesoVistaEnVivoTest.java`, `monitoreo/infrastructure/mediamtx/MediaMtxIntegrationTest.java` |
| End-to-end alert latency against the deployment, with a physical phone (Section 5, RQ3) | `scripts/latencia-e2e/medir_produccion.sh` (real capture agent replaying URFD falls; phone timed with `adb logcat` and the clock offset of `desfase_adb.py`); analysis `scripts/latencia-e2e/analizar_produccion.py` |
| Local end-to-end run without the deployment | `scripts/medir-latencia-e2e.sh` |
| Clip retention (Section 3.5: not defined yet) | `src/main/resources/application.yml`, `retencion-de-clips` |

Test paths are relative to `src/test/java/tech/tetengo/api/`. Run the tests with `./mvnw verify` (Docker is needed for the integration tests).
