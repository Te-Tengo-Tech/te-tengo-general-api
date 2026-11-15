"""Self-check of proxy_corte.py with a local echo server (no network needed).

Usage: python3 prueba_proxy_corte.py [--red <https url>]

Starts proxy_corte.py on a free port in each of its modes and checks, with raw CONNECT tunnels to a
local TCP echo server: pass-through relays bytes; an outage resets (``cortar``) or freezes and later
resumes (``congelar``) an open tunnel; a new CONNECT during the outage gets no answer (``agujero``)
and is reset on restore, or is reset at once (``rechazar``); after the restore new tunnels work; the
state changes are logged with epoch timestamps; a plain-HTTP request is answered 501. With
``--red <url>`` it also GETs that URL through the proxy with httpx (as the agent does, via
HTTPS_PROXY) and checks that the CONNECT host appears in the proxy's log.
"""

import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

AQUI = Path(__file__).resolve().parent


def eco() -> int:
    servidor = socket.socket()
    servidor.bind(("127.0.0.1", 0))
    servidor.listen()

    def atender(conexion: socket.socket) -> None:
        with conexion:
            try:
                while datos := conexion.recv(65536):
                    conexion.sendall(datos)
            except OSError:
                pass  # the proxy reset the tunnel: expected in the outage checks

    def aceptar() -> None:
        while True:
            conexion, _ = servidor.accept()
            threading.Thread(target=atender, args=(conexion,), daemon=True).start()

    threading.Thread(target=aceptar, daemon=True).start()
    return servidor.getsockname()[1]


def tunel(puerto_proxy: int, puerto_eco: int, espera: float = 2.0) -> tuple[socket.socket, bytes]:
    s = socket.create_connection(("127.0.0.1", puerto_proxy), timeout=espera)
    s.sendall(f"CONNECT 127.0.0.1:{puerto_eco} HTTP/1.1\r\nHost: 127.0.0.1:{puerto_eco}\r\n\r\n".encode())
    try:
        respuesta = s.recv(4096)
    except TimeoutError:
        respuesta = b"<timeout>"
    except ConnectionResetError:
        respuesta = b"<reset>"
    return s, respuesta


def ida_y_vuelta(s: socket.socket, datos: bytes = b"hola") -> bytes:
    try:
        s.sendall(datos)
        return s.recv(4096)
    except TimeoutError:
        return b"<timeout>"
    except (ConnectionResetError, BrokenPipeError):
        return b"<reset>"


class ProxyEnPrueba:
    def __init__(self, directorio: Path, nuevas: str, existentes: str) -> None:
        self.control = directorio / f"control-{nuevas}-{existentes}"
        self.registro = directorio / f"proxy-{nuevas}-{existentes}.jsonl"
        archivo_puerto = directorio / f"puerto-{nuevas}-{existentes}"
        archivo_puerto.unlink(missing_ok=True)
        self.proceso = subprocess.Popen([
            sys.executable, str(AQUI / "proxy_corte.py"), "--control", str(self.control),
            "--registro", str(self.registro), "--puerto-archivo", str(archivo_puerto),
            "--nuevas", nuevas, "--existentes", existentes,
        ])
        for _ in range(100):
            if archivo_puerto.exists():
                break
            time.sleep(0.05)
        self.puerto = int(archivo_puerto.read_text())

    def estado(self, estado: str) -> None:
        self.control.write_text(estado + "\n")
        time.sleep(0.3)

    def eventos(self) -> list[dict]:
        return [json.loads(x) for x in self.registro.read_text().splitlines()]

    def cerrar(self) -> None:
        if self.proceso.poll() is None:
            self.proceso.terminate()
            self.proceso.wait(5)


FALLOS: list[str] = []


def comprobar(condicion: bool, descripcion: str) -> None:
    print(("ok   " if condicion else "FAIL ") + descripcion)
    if not condicion:
        FALLOS.append(descripcion)


def main() -> int:
    puerto_eco = eco()
    directorio = Path(tempfile.mkdtemp(prefix="prueba-proxy-corte-"))
    (directorio / "red").mkdir()

    # agujero + cortar (the default)
    p = ProxyEnPrueba(directorio, "agujero", "cortar")
    s1, r = tunel(p.puerto, puerto_eco)
    comprobar(r.startswith(b"HTTP/1.1 200"), "paso: CONNECT answered 200")
    comprobar(ida_y_vuelta(s1) == b"hola", "paso: bytes relayed both ways")
    p.estado("corte")
    comprobar(ida_y_vuelta(s1) in (b"<reset>", b""), "corte/cortar: the open tunnel is reset")
    s2, r = tunel(p.puerto, puerto_eco, espera=1.5)
    comprobar(r == b"<timeout>", "corte/agujero: a new CONNECT gets no answer")
    p.estado("paso")
    s2.settimeout(1.5)
    try:
        resto = s2.recv(10)
    except ConnectionResetError:
        resto = b"<reset>"
    except TimeoutError:
        resto = b"<timeout>"
    comprobar(resto in (b"<reset>", b""), "restore: the held connection is reset (client retries)")
    s3, r = tunel(p.puerto, puerto_eco)
    comprobar(r.startswith(b"HTTP/1.1 200") and ida_y_vuelta(s3) == b"hola", "restore: new tunnels work")
    plano = socket.create_connection(("127.0.0.1", p.puerto), timeout=2)
    plano.sendall(b"GET http://example.invalid/x?secreto=1 HTTP/1.1\r\nHost: example.invalid\r\n\r\n")
    comprobar(plano.recv(100).startswith(b"HTTP/1.1 501"), "plain HTTP (not CONNECT) is answered 501")
    # SIGUSR1/SIGUSR2 switch too.
    p.proceso.send_signal(__import__("signal").SIGUSR1)
    time.sleep(0.3)
    p.proceso.send_signal(__import__("signal").SIGUSR2)
    time.sleep(0.3)
    p.cerrar()
    ev = p.eventos()
    estados = [(e["estado"], e["origen"]) for e in ev if e["evento"] == "estado"]
    comprobar(estados == [("corte", "archivo"), ("paso", "archivo"), ("corte", "señal"), ("paso", "señal")],
              f"state changes logged in order with their source: {estados}")
    comprobar(all(isinstance(e["epoch"], float) and abs(e["epoch"] - time.time()) < 60 for e in ev),
              "every log line has an epoch timestamp on this machine's clock")
    resultados = [e.get("resultado") for e in ev if e["evento"] == "connect"]
    comprobar(resultados == ["ok", "agujero", "ok"], f"CONNECTs logged with their result: {resultados}")
    comprobar(not any("secreto" in json.dumps(e) for e in ev), "query strings are never logged")

    # rechazar + congelar
    p = ProxyEnPrueba(directorio, "rechazar", "congelar")
    s1, r = tunel(p.puerto, puerto_eco)
    comprobar(ida_y_vuelta(s1) == b"hola", "paso (congelar): relayed")
    p.estado("corte")
    s1.settimeout(1.0)
    comprobar(ida_y_vuelta(s1, b"retenido") == b"<timeout>", "corte/congelar: the open tunnel stops relaying")
    _, r = tunel(p.puerto, puerto_eco, espera=1.5)
    comprobar(r in (b"<reset>", b""), f"corte/rechazar: a new CONNECT is reset at once ({r!r})")
    p.estado("paso")
    s1.settimeout(2.0)
    try:
        llegado = s1.recv(4096)
    except TimeoutError:
        llegado = b"<timeout>"
    comprobar(llegado == b"retenido", "restore (congelar): the frozen bytes are delivered")
    comprobar(ida_y_vuelta(s1) == b"hola", "restore (congelar): the tunnel keeps working")
    p.cerrar()

    if "--red" in sys.argv:
        url = sys.argv[sys.argv.index("--red") + 1]
        import httpx

        p = ProxyEnPrueba(directorio / "red", "agujero", "cortar")
        os.environ["HTTPS_PROXY"] = os.environ["HTTP_PROXY"] = f"http://127.0.0.1:{p.puerto}"
        os.environ.pop("NO_PROXY", None)
        os.environ.pop("no_proxy", None)
        with httpx.Client(timeout=httpx.Timeout(10.0, connect=5.0)) as cliente:
            codigo = cliente.get(url).status_code
        comprobar(codigo == 200, f"httpx via HTTPS_PROXY: GET {url} → {codigo}")
        p.estado("corte")
        inicio = time.time()
        try:
            with httpx.Client(timeout=httpx.Timeout(10.0, connect=5.0)) as cliente:
                cliente.get(url)
            error = "none"
        except httpx.TransportError as e:
            error = type(e).__name__
        comprobar(error != "none", f"httpx during the outage fails with {error} after {time.time() - inicio:.1f} s")
        p.estado("paso")
        p.cerrar()
        hosts = {e.get("host") for e in p.eventos() if e["evento"] == "connect"}
        comprobar(httpx.URL(url).host in hosts, f"the proxy saw CONNECT {hosts}")
    print(f"\n{'all checks passed' if not FALLOS else f'{len(FALLOS)} FAILED'} (logs in {directorio})")
    return 1 if FALLOS else 0


if __name__ == "__main__":
    sys.exit(main())
