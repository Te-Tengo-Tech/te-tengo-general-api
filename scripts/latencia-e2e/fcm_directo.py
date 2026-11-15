"""FCM → device alone: direct FCM HTTP v1 sends to ONE test device token, timed on this machine.

Usage (with an interpreter that has google-auth and requests, e.g.
`uv run --with google-auth --with requests python fcm_directo.py …`):

    python fcm_directo.py <service-account.json> <token file> <n> <output.csv> [pause_s]

Each message mirrors what the API sends for a fall (NotificadorPushFcm): a notification block on
the channel `alertas_caida`, Android priority HIGH, and a data block with `tipo=PRUEBA_LATENCIA` and
a sequence number, so the app treats it as an unknown type. For every send it records, on this
machine's clock, when the request left (t_envio) and when FCM answered (t_respuesta), and FCM's
message name. The receipt on the device (t4) comes from the device's log (analizar_android.py).
Only send to a token of a device you control: this script never reads tokens from a database.
"""

import json
import sys
import time

import google.auth.transport.requests as gr
import requests
from google.oauth2 import service_account


def main() -> int:
    credenciales, archivo_token, n, salida = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    pausa = float(sys.argv[5]) if len(sys.argv) > 5 else 4.0
    cred = service_account.Credentials.from_service_account_file(
        credenciales, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
    )
    cred.refresh(gr.Request())
    proyecto = json.load(open(credenciales))["project_id"]
    token = open(archivo_token).read().strip()
    url = f"https://fcm.googleapis.com/v1/projects/{proyecto}/messages:send"
    sesion = requests.Session()
    with open(salida, "w") as archivo:
        archivo.write("i,t_envio,t_respuesta,http,mensaje\n")
        for i in range(n):
            cuerpo = {
                "message": {
                    "token": token,
                    "notification": {"title": f"Prueba de latencia {i}", "body": "Te Tengo (prueba local)"},
                    "data": {"tipo": "PRUEBA_LATENCIA", "secuencia": str(i)},
                    "android": {"priority": "HIGH", "ttl": "600s",
                                "notification": {"channel_id": "alertas_caida"}},
                }
            }
            t_envio = time.time()
            r = sesion.post(url, json=cuerpo, headers={"Authorization": f"Bearer {cred.token}"}, timeout=20)
            t_respuesta = time.time()
            nombre = r.json().get("name", "") if r.ok else r.text.replace("\n", " ")[:200]
            archivo.write(f"{i},{t_envio:.6f},{t_respuesta:.6f},{r.status_code},{nombre}\n")
            archivo.flush()
            print(i, r.status_code, f"{t_respuesta - t_envio:.3f}s", flush=True)
            time.sleep(pausa)
    return 0


if __name__ == "__main__":
    sys.exit(main())
