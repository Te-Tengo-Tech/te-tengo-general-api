"""agente_produccion.py plus the marks of the link-outage test (Case A, medir_produccion.sh corte).

Used with the agent's own interpreter, like agente_produccion.py (same isolation: JSON credential
store, synthetic webcam, no updates, its own data directory). Importing agente_produccion applies
all of its changes; this module only adds log lines, nothing in the agent's behaviour changes:

* ``LATENCIA inicio_caida n=<k> epoch=…``: the first frame at or after each labelled fall onset of
  the concatenated clip (``TT_CORTE_INICIOS``, seconds from the start of the file, comma separated),
  t0 of every fall (agente_con_marcas.py only marks the first one).
* ``CORTE http <método> <host><ruta> → <código|excepción> <s> [evento=<id> tipo=<tipo>]``: every
  HTTP request the agent makes (events, heartbeat, token renewal, clip and landmark uploads, PC
  state), with its outcome and duration, and for ``POST /api/agente/eventos`` the event id and type
  read from the body. Each line is one delivery attempt: the retries the outbox makes during the
  outage and after it. Only scheme-less host and path are logged: never the query (the storage
  upload URLs carry SAS tokens), headers or bodies.
* ``CORTE ws <url> → abierto|<excepción>``: each WebSocket connection attempt (PC and live-view
  control channels), host and path only.
* ``CORTE encolar <qué> <id> espera=<s>``: how long the capture thread waited to put an event or a
  clip into the outbox (``ColaEnvios.agregar_evento``/``agregar_clip`` take the same lock that
  ``ColaEnvios.enviar`` holds during its HTTP attempts), logged when ≥ 0.05 s.
* ``CORTE lectura_atrasada desde=<epoch> hasta=<epoch> max=<s>``: a stretch in which the clip's
  frames were handed to the agent late (wall clock behind the clip's real-time schedule by > 0.5 s):
  the capture thread was blocked, so a real webcam would have dropped those frames.
"""

import json
import logging
import os
import sys
import time

import agente_produccion  # noqa: F401 (isolation and LATENCIA evento … alerta … marks)
import httpx

from te_tengo_captura import __main__ as principal
from te_tengo_captura.backend import transmision
from te_tengo_captura.captura import fuentes
from te_tengo_captura.envios import cola

registro = logging.getLogger("latencia")
INICIOS = [float(x) for x in os.environ.get("TT_CORTE_INICIOS", "").split(",") if x.strip()]
_marcados: set[int] = set()
_leer_anterior = fuentes.FuenteArchivo.leer
_ritmo: dict[str, float] = {}  # origen (wall − instant), atraso desde, atraso máximo


def _leer(self: fuentes.FuenteArchivo):  # type: ignore[no-untyped-def]
    leido = _leer_anterior(self)
    if leido is not None:
        instante = leido[0]
        ahora = time.time()
        origen = _ritmo.setdefault("origen", ahora - instante)
        atraso = ahora - (origen + instante)
        if atraso > 0.5:
            _ritmo.setdefault("desde", ahora - atraso)
            _ritmo["max"] = max(_ritmo.get("max", 0.0), atraso)
        elif atraso < 0.2 and "desde" in _ritmo:
            registro.info("CORTE lectura_atrasada desde=%.3f hasta=%.3f max=%.2fs",
                          _ritmo.pop("desde"), ahora, _ritmo.pop("max"))
        for n, inicio in enumerate(INICIOS, 1):
            if n not in _marcados and instante >= inicio - 1e-6:
                _marcados.add(n)
                registro.info("LATENCIA inicio_caida n=%d epoch=%.6f instante=%.4f", n, time.time(), instante)
    return leido


fuentes.FuenteArchivo.leer = _leer  # type: ignore[method-assign]

_enviar_original = httpx.Client.send


def _enviar(self: httpx.Client, request: httpx.Request, **kwargs):  # type: ignore[no-untyped-def]
    destino = f"{request.url.host}{request.url.path}"
    extra = ""
    if request.method == "POST" and request.url.path.endswith("/api/agente/eventos"):
        try:
            cuerpo = json.loads(request.content or b"{}")
            extra = f" evento={cuerpo.get('eventoId')} tipo={cuerpo.get('tipo')}"
        except (ValueError, httpx.RequestNotRead):
            pass
    inicio = time.time()
    try:
        respuesta = _enviar_original(self, request, **kwargs)
    except Exception as error:
        registro.info(
            "CORTE http %s %s → %s %.3fs inicio=%.6f%s",
            request.method, destino, type(error).__name__, time.time() - inicio, inicio, extra,
        )
        raise
    registro.info(
        "CORTE http %s %s → %d %.3fs inicio=%.6f%s",
        request.method, destino, respuesta.status_code, time.time() - inicio, inicio, extra,
    )
    return respuesta


httpx.Client.send = _enviar  # type: ignore[method-assign]

_conectar_original = transmision._conectar


def _conectar(url: str, token: str):  # type: ignore[no-untyped-def]
    destino = httpx.URL(url)
    try:
        conexion = _conectar_original(url, token)
    except Exception as error:
        registro.info("CORTE ws %s%s → %s", destino.host, destino.path, type(error).__name__)
        raise
    registro.info("CORTE ws %s%s → abierto", destino.host, destino.path)
    return conexion


transmision._conectar = _conectar
# CanalTransmision binds _conectar as a default argument at definition time: patch the default too.
_valores = list(transmision.CanalTransmision.__init__.__defaults__ or ())
_valores = [_conectar if v is _conectar_original else v for v in _valores]
transmision.CanalTransmision.__init__.__defaults__ = tuple(_valores)


def _medir_encolado(nombre: str):  # type: ignore[no-untyped-def]
    original = getattr(cola.ColaEnvios, nombre)

    def envuelto(self, primero, *args, **kwargs):  # type: ignore[no-untyped-def]
        inicio = time.time()
        resultado = original(self, primero, *args, **kwargs)
        espera = time.time() - inicio
        if espera >= 0.05:
            ident = getattr(primero, "evento_id", primero)
            tipo = getattr(getattr(primero, "tipo", None), "value", "")
            registro.info("CORTE encolar %s %s %s espera=%.3fs inicio=%.6f", nombre, ident, tipo, espera, inicio)
        return resultado

    setattr(cola.ColaEnvios, nombre, envuelto)


for _nombre in ("agregar_evento", "agregar_clip", "agregar_puntos_clip"):
    _medir_encolado(_nombre)

if __name__ == "__main__":
    if "--config" not in sys.argv or "--datos" not in sys.argv:
        sys.exit("agente_corte.py needs --config and --datos (isolated from a real install)")
    sys.exit(principal.main(sys.argv[1:]))
