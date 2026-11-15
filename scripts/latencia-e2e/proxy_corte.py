"""Local HTTP CONNECT proxy that emulates the household link failing (Case A of the reliability test).

Usage: python3 proxy_corte.py --puerto-archivo <file> --control <file> --registro <file.jsonl>
                              [--puerto 0] [--nuevas agujero|rechazar] [--existentes cortar|congelar]

The isolated desktop agent runs with ``HTTPS_PROXY``/``HTTP_PROXY`` pointing here (httpx and
websockets honour them), so every agent→API connection (events, heartbeat, clip and landmarks
uploads to storage, token renewal, the PC and live-view WebSockets) is a CONNECT tunnel through this
process. Nothing on the Mac changes: no Wi-Fi toggling, no system or network settings.

Two states, switched by writing ``paso`` or ``corte`` to the control file (polled every 50 ms), or
with SIGUSR1 (→ corte) / SIGUSR2 (→ paso):

* ``paso`` (pass-through): each CONNECT opens a TCP connection to the requested host and relays bytes
  both ways.
* ``corte`` (outage), emulating a lost uplink:
  - new connections (``--nuevas``): ``agujero`` (default) accepts the CONNECT and never answers, as a
    SYN to an unreachable network that gets no reply (the client hits its own timeout);
    ``rechazar`` resets the connection at once (as «network unreachable»/DNS failure).
    Held connections are reset when the link comes back (the client then retries).
  - existing tunnels (``--existentes``): ``cortar`` (default) resets both ends (RST to the agent,
    close to the server); ``congelar`` stops relaying (bytes stay in the kernel buffers, as packets
    lost in flight) and resumes when the link comes back, unless an end gave up meanwhile.

Every state change and connection is written as one JSON line with ``epoch`` = ``time.time()``, the
same clock as the agent's log and the harness (``registro``): ``estado``, ``connect`` (id, host, port,
result: ``ok``/``agujero``/``rechazada``/``error``, upstream connect time), ``cierre`` (bytes each way,
duration, reason) and ``no_connect`` (a plain-HTTP request, answered 501: something not tunnelled).
Only host:port is logged, never paths, headers or payloads (the storage URLs carry SAS tokens).
"""

import argparse
import asyncio
import contextlib
import itertools
import json
import os
import signal
import socket
import struct
import sys
import time
from pathlib import Path

ESTADOS = ("paso", "corte")


class Registro:
    def __init__(self, ruta: Path) -> None:
        self._archivo = ruta.open("a", buffering=1)

    def __call__(self, evento: str, **datos: object) -> None:
        linea = {"epoch": round(time.time(), 6), "evento": evento, **datos}
        self._archivo.write(json.dumps(linea, ensure_ascii=False) + "\n")


def _abortar(escritor: asyncio.StreamWriter | None) -> None:
    """Closes with RST (SO_LINGER 0), as a dropped connection, not a graceful FIN."""
    if escritor is None:
        return
    with contextlib.suppress(Exception):
        sock = escritor.get_extra_info("socket")
        if sock is not None:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
    with contextlib.suppress(Exception):
        escritor.transport.abort()


class Tunel:
    def __init__(self, ident: int, host: str, puerto: int) -> None:
        self.ident, self.host, self.puerto = ident, host, puerto
        self.inicio = time.time()
        self.subida = self.bajada = 0
        self.cliente: asyncio.StreamWriter | None = None
        self.servidor: asyncio.StreamWriter | None = None
        self.motivo = ""
        self.fluye = asyncio.Event()  # cleared while frozen
        self.fluye.set()


class Proxy:
    def __init__(self, registro: Registro, nuevas: str, existentes: str) -> None:
        self.registro = registro
        self.nuevas = nuevas
        self.existentes = existentes
        self.estado = "paso"
        self.tuneles: dict[int, Tunel] = {}
        self.retenidas: dict[int, asyncio.StreamWriter] = {}
        self._ids = itertools.count(1)

    # ------------------------------------------------------------------ state

    def cambiar(self, estado: str, origen: str) -> None:
        if estado not in ESTADOS or estado == self.estado:
            return
        self.estado = estado
        afectados = list(self.tuneles.values())
        self.registro(
            "estado", estado=estado, origen=origen, tuneles_abiertos=len(afectados),
            retenidas=len(self.retenidas), nuevas=self.nuevas, existentes=self.existentes,
        )
        if estado == "corte":
            for tunel in afectados:
                if self.existentes == "cortar":
                    tunel.motivo = "cortado"
                    _abortar(tunel.cliente)
                    _abortar(tunel.servidor)
                else:
                    tunel.fluye.clear()
                    for extremo in (tunel.cliente, tunel.servidor):
                        with contextlib.suppress(Exception):
                            extremo.transport.pause_reading()  # type: ignore[union-attr]
        else:
            for ident, escritor in list(self.retenidas.items()):
                self.registro("cierre", id=ident, motivo="agujero_liberado")
                _abortar(escritor)
            self.retenidas.clear()
            for tunel in afectados:
                for extremo in (tunel.cliente, tunel.servidor):
                    with contextlib.suppress(Exception):
                        extremo.transport.resume_reading()  # type: ignore[union-attr]
                tunel.fluye.set()

    # ------------------------------------------------------------------ connections

    async def atender(self, lector: asyncio.StreamReader, escritor: asyncio.StreamWriter) -> None:
        ident = next(self._ids)
        try:
            cabecera = await asyncio.wait_for(lector.readuntil(b"\r\n\r\n"), 30)
        except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, TimeoutError, ConnectionError):
            escritor.close()
            return
        primera = cabecera.split(b"\r\n", 1)[0].decode("latin-1")
        partes = primera.split()
        if len(partes) != 3 or partes[0].upper() != "CONNECT":
            metodo = partes[0] if partes else "?"
            destino = partes[1].split("?", 1)[0] if len(partes) > 1 else "?"
            self.registro("no_connect", id=ident, metodo=metodo, destino=destino)
            escritor.write(b"HTTP/1.1 501 Not Implemented\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            with contextlib.suppress(Exception):
                await escritor.drain()
            escritor.close()
            return
        host, _, puerto_txt = partes[1].rpartition(":")
        host = host.strip("[]")
        puerto = int(puerto_txt or 443)
        if self.estado == "corte":
            if self.nuevas == "rechazar":
                self.registro("connect", id=ident, host=host, puerto=puerto, resultado="rechazada")
                _abortar(escritor)
                return
            self.registro("connect", id=ident, host=host, puerto=puerto, resultado="agujero")
            self.retenidas[ident] = escritor
            inicio = time.time()
            with contextlib.suppress(Exception):
                await lector.read()  # until the client gives up (EOF/RST) or we reset it on restore
            if self.retenidas.pop(ident, None) is not None:
                self.registro("cierre", id=ident, motivo="agujero_cliente_desistio",
                              duracion_s=round(time.time() - inicio, 3))
            with contextlib.suppress(Exception):
                escritor.close()
            return
        t0 = time.time()
        try:
            srv_lector, srv_escritor = await asyncio.wait_for(asyncio.open_connection(host, puerto), 15)
        except (OSError, TimeoutError) as error:
            self.registro("connect", id=ident, host=host, puerto=puerto, resultado="error",
                          error=type(error).__name__)
            escritor.write(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            with contextlib.suppress(Exception):
                await escritor.drain()
            escritor.close()
            return
        if self.estado == "corte":  # the link went down while connecting upstream
            self.registro("connect", id=ident, host=host, puerto=puerto, resultado="rechazada",
                          nota="corte durante la conexión")
            _abortar(srv_escritor)
            _abortar(escritor)
            return
        tunel = Tunel(ident, host, puerto)
        tunel.cliente, tunel.servidor = escritor, srv_escritor
        self.tuneles[ident] = tunel
        self.registro("connect", id=ident, host=host, puerto=puerto, resultado="ok",
                      conexion_s=round(time.time() - t0, 4))
        escritor.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        try:
            await escritor.drain()
            await asyncio.gather(
                self._copiar(tunel, lector, srv_escritor, "subida"),
                self._copiar(tunel, srv_lector, escritor, "bajada"),
            )
        finally:
            self.tuneles.pop(ident, None)
            for extremo in (escritor, srv_escritor):
                with contextlib.suppress(Exception):
                    extremo.close()
            self.registro("cierre", id=ident, host=host, subida=tunel.subida, bajada=tunel.bajada,
                          duracion_s=round(time.time() - tunel.inicio, 3), motivo=tunel.motivo or "fin")

    async def _copiar(self, tunel: Tunel, lector: asyncio.StreamReader,
                      escritor: asyncio.StreamWriter, sentido: str) -> None:
        try:
            while True:
                datos = await lector.read(65536)
                if not datos:
                    break
                await tunel.fluye.wait()  # frozen: hold what was already read until restore
                escritor.write(datos)
                await escritor.drain()
                if sentido == "subida":
                    tunel.subida += len(datos)
                else:
                    tunel.bajada += len(datos)
        except (ConnectionError, OSError, asyncio.CancelledError):
            tunel.motivo = tunel.motivo or f"error_{sentido}"
        finally:
            with contextlib.suppress(Exception):
                if escritor.can_write_eof():
                    escritor.write_eof()


async def vigilar_control(proxy: Proxy, control: Path) -> None:
    ultimo = None
    while True:
        try:
            texto = control.read_text().strip()
        except FileNotFoundError:
            texto = ""
        if texto != ultimo:
            ultimo = texto
            if texto in ESTADOS:
                proxy.cambiar(texto, "archivo")
        await asyncio.sleep(0.05)


async def principal(args: argparse.Namespace) -> None:
    registro = Registro(Path(args.registro))
    proxy = Proxy(registro, args.nuevas, args.existentes)
    servidor = await asyncio.start_server(proxy.atender, "127.0.0.1", args.puerto, limit=1 << 16)
    puerto = servidor.sockets[0].getsockname()[1]
    bucle = asyncio.get_running_loop()
    bucle.add_signal_handler(signal.SIGUSR1, proxy.cambiar, "corte", "señal")
    bucle.add_signal_handler(signal.SIGUSR2, proxy.cambiar, "paso", "señal")
    detener = asyncio.Event()
    bucle.add_signal_handler(signal.SIGTERM, detener.set)
    bucle.add_signal_handler(signal.SIGINT, detener.set)
    if args.control:
        control = Path(args.control)
        if not control.exists():
            control.write_text("paso\n")
        asyncio.create_task(vigilar_control(proxy, control))
    registro("inicio", puerto=puerto, pid=os.getpid(), nuevas=args.nuevas, existentes=args.existentes)
    if args.puerto_archivo:
        temporal = Path(args.puerto_archivo + ".tmp")
        temporal.write_text(f"{puerto}\n")
        temporal.replace(args.puerto_archivo)
    async with servidor:
        await detener.wait()
    registro("fin", tuneles_abiertos=len(proxy.tuneles))


def main() -> int:
    analizador = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    analizador.add_argument("--puerto", type=int, default=0)
    analizador.add_argument("--puerto-archivo", help="where the listening port is written once ready")
    analizador.add_argument("--control", help="file holding «paso» or «corte»")
    analizador.add_argument("--registro", required=True, help="JSON-lines log")
    analizador.add_argument("--nuevas", choices=("agujero", "rechazar"), default="agujero")
    analizador.add_argument("--existentes", choices=("cortar", "congelar"), default="cortar")
    asyncio.run(principal(analizador.parse_args()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
