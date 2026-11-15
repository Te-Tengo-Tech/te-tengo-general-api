"""Runs the desktop agent as a linked PC against the production API, isolated from any real install.

Used by scripts/latencia-e2e/medir_produccion.sh with the agent's own interpreter
(`<desktop>/.venv/bin/python agente_produccion.py <agent arguments>`). Everything the agent does
(device flow, PC and camera tokens, heartbeat, classifier, outbox, clip upload) is the real code of
te-tengo-desktop-pywebview; only these points change:

* **Credential store:** the PC's tokens, its installation id and the local data key go to a JSON
  file (`TT_LAT_LLAVERO`, mode 0600) instead of the macOS Keychain, so a real Te Tengo Captura
  installed on this Mac (service «TeTengoCaptura» in the Keychain) is never read or overwritten.
  `PYTHON_KEYRING_BACKEND` is set to keyring's failing backend by the caller as a safety net.
* **Webcams:** the PC reports one synthetic webcam, «Video URFD (medición)», instead of listing the
  Mac's cameras; the family puts it in a room in the app, and with `--video` it plays the clip.
* **PC name:** «Medición latencia (Mac)» in the app (`TT_LAT_NOMBRE_PC`).
* **Updates:** no update channel (the agent never looks for or installs a new version).
* **Marks:** agente_con_marcas.py's t0 marks (`TT_LAT_INICIO_CAIDA`), and the alert id the API
  returns for each event (`LATENCIA evento <id> alerta <id>`): the Android notification's tag.

The data directory (`--datos`) and the configuration file (`--config`) must be the isolated ones the
caller passes; without `--config` the agent would read the real installation's file.
"""

import functools
import json
import logging
import os
import sys
import threading
from pathlib import Path

import agente_con_marcas  # noqa: F401 (wraps FuenteArchivo.leer: t0 marks)
import keyring
import keyring.errors

from te_tengo_captura import __main__ as principal
from te_tengo_captura import agente_pc, coordinador
from te_tengo_captura.backend.cliente import ClienteBackend
from te_tengo_captura.captura.dispositivos import CAP_AVFOUNDATION, Dispositivo

registro = logging.getLogger("latencia")
ID_WEBCAM = "tt-latencia-urfd"
NOMBRE_WEBCAM = "Video URFD (medición)"


class LlaveroArchivo:
    """The part of a keyring backend the agent uses, in a JSON file only this user can read."""

    def __init__(self, ruta: Path) -> None:
        self._ruta = ruta
        self._candado = threading.Lock()

    def _leer(self) -> dict[str, str]:
        try:
            return dict(json.loads(self._ruta.read_text()))
        except FileNotFoundError:
            return {}

    def _escribir(self, valores: dict[str, str]) -> None:
        self._ruta.parent.mkdir(parents=True, exist_ok=True)
        temporal = self._ruta.with_suffix(".tmp")
        descriptor = os.open(temporal, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "w") as archivo:
            json.dump(valores, archivo)
        os.replace(temporal, self._ruta)

    def get_password(self, servicio: str, usuario: str) -> str | None:
        with self._candado:
            return self._leer().get(f"{servicio}/{usuario}")

    def set_password(self, servicio: str, usuario: str, secreto: str) -> None:
        with self._candado:
            valores = self._leer()
            valores[f"{servicio}/{usuario}"] = secreto
            self._escribir(valores)

    def delete_password(self, servicio: str, usuario: str) -> None:
        with self._candado:
            valores = self._leer()
            if valores.pop(f"{servicio}/{usuario}", None) is None:
                raise keyring.errors.PasswordDeleteError(usuario)
            self._escribir(valores)


_llavero = LlaveroArchivo(Path(os.environ["TT_LAT_LLAVERO"]))
keyring.get_keyring = lambda: _llavero  # type: ignore[assignment,return-value]


def _listar() -> list[Dispositivo]:
    return [Dispositivo(ID_WEBCAM, NOMBRE_WEBCAM, 0, CAP_AVFOUNDATION)]


_nombre_pc = os.environ.get("TT_LAT_NOMBRE_PC", "Medición latencia (Mac)")
coordinador.nombre_pc = lambda: _nombre_pc  # type: ignore[assignment]
agente_pc.nombre_pc = lambda: _nombre_pc  # type: ignore[assignment]
principal.Coordinador = functools.partial(  # type: ignore[misc]
    coordinador.Coordinador, listar=_listar, plataforma=None
)

_publicar_original = ClienteBackend.publicar_evento


def _publicar(self, evento):  # type: ignore[no-untyped-def]
    recibido, duplicado = _publicar_original(self, evento)
    registro.info(
        "LATENCIA evento %s alerta %s duplicado=%s", recibido.evento_id, recibido.alerta_id, duplicado
    )
    return recibido, duplicado


ClienteBackend.publicar_evento = _publicar  # type: ignore[method-assign]

if __name__ == "__main__":
    if "--config" not in sys.argv or "--datos" not in sys.argv:
        sys.exit("agente_produccion.py needs --config and --datos (isolated from a real install)")
    sys.exit(principal.main(sys.argv[1:]))
