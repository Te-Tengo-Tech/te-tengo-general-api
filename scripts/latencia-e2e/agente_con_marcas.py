"""Runs the desktop agent (te_tengo_captura) unchanged, adding two timing marks to its log.

Used by scripts/medir-latencia-e2e.sh with the agent's own interpreter
(`<desktop>/.venv/bin/python agente_con_marcas.py <agent arguments>`). It wraps
`FuenteArchivo.leer`, the video file source the agent uses with `--video` in real time, and logs
the wall-clock time (epoch seconds, this machine's clock) at which:

* the first frame of the file is handed to the agent (`LATENCIA primer_cuadro`);
* the first frame at or after the labelled fall onset is handed to the agent
  (`LATENCIA inicio_caida`): t0, the fall "happening in front of the camera".

The onset (seconds from the start of the file) comes from `TT_LAT_INICIO_CAIDA`. Nothing else in
the agent changes: sampling, MediaPipe, classifier, outbox and backend client are the real ones.
"""

import logging
import os
import sys
import time

from te_tengo_captura.__main__ import main
from te_tengo_captura.captura import fuentes

INICIO_CAIDA = float(os.environ["TT_LAT_INICIO_CAIDA"])
registro = logging.getLogger("latencia")
_marcas: set[str] = set()
_leer_original = fuentes.FuenteArchivo.leer


def _leer(self: fuentes.FuenteArchivo):  # type: ignore[no-untyped-def]
    leido = _leer_original(self)
    if leido is not None:
        instante = leido[0]
        ahora = time.time()
        if "primer_cuadro" not in _marcas:
            _marcas.add("primer_cuadro")
            registro.info("LATENCIA primer_cuadro epoch=%.6f instante=%.4f", ahora, instante)
        if "inicio_caida" not in _marcas and instante >= INICIO_CAIDA - 1e-6:
            _marcas.add("inicio_caida")
            registro.info("LATENCIA inicio_caida epoch=%.6f instante=%.4f", ahora, instante)
    return leido


fuentes.FuenteArchivo.leer = _leer  # type: ignore[method-assign]

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
