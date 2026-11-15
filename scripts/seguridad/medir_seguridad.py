#!/usr/bin/env python3
"""Security measurement against the production API (paper, Table 2, Security scenarios).

Two throw-away accounts, each with its own household, made by crear_cuenta.sh:
  seguridad-a  the household under test: an isolated agent (scripts/latencia-e2e, its own folder
               ~/.tetengo-seguridad/agente) is linked to it and its synthetic webcam watches «Sala».
  seguridad-b  an outsider: another household that tries to reach A's camera.

  medir_seguridad.py vincular   link the isolated agent to A (approves the code with A's session),
                                grant A's consent, put the webcam in «Sala»
  medir_seguridad.py medir      run the agent and the attempts; results in --dir

Attempts (each one request, spaced out):
  legitimo     A opens and closes the live view                  expect 201, logged as access
  sin_consent. A opens it after revoking consent (then re-grants)  expect 409 SIN_CONSENTIMIENTO
  otro_hogar   B opens A's camera                                  expect 404 CAMARA_NO_ENCONTRADA
  sesion_ajena B changes the mode of A's open session              expect 404 SESION_NO_ENCONTRADA
  media_*      the media server without token / with a made-up
               token / with the token of an ended session          expect 401/403/404
The access log (GET /api/accesos-vista-en-vivo) and, when the API has it, the denial log
(GET /api/accesos-vista-en-vivo/denegados) are read before and after, as A.

Only session tokens are used (never passwords); they are refreshed and saved back with 0600.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = os.environ.get("TT_API", "https://api.tetengo.reqsai.tech")
DIR = Path.home() / ".tetengo-seguridad"
AQUI = Path(__file__).resolve().parent
LATENCIA = AQUI.parent / "latencia-e2e"
AGENTE = DIR / "agente"


def log(msg: str) -> None:
    print(f"[seguridad {time.strftime('%H:%M:%S')}] {msg}", flush=True)


# ------------------------------------------------------------------ HTTP
def pedir(metodo: str, url: str, token: str | None = None, cuerpo=None, timeout=15):
    """(status, json-or-text, elapsed_s). Never raises on HTTP errors."""
    datos = None if cuerpo is None else json.dumps(cuerpo).encode()
    req = urllib.request.Request(url if url.startswith("http") else API + url, data=datos, method=metodo)
    req.add_header("Api-Version", "1")
    if datos is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            st, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        st, raw = e.code, e.read()
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        return 0, str(e), time.monotonic() - t0
    dt = time.monotonic() - t0
    try:
        return st, json.loads(raw) if raw else None, dt
    except ValueError:
        return st, raw[:200].decode(errors="replace"), dt


class Cuenta:
    def __init__(self, alias: str):
        self.alias = alias
        self.ruta = DIR / f"{alias}.json"
        if not self.ruta.exists():
            sys.exit(f"falta {self.ruta}: corre crear_cuenta.sh {alias} …")
        self.s = json.loads(self.ruta.read_text())
        self.refrescar()

    def refrescar(self) -> None:
        st, s, _ = pedir("POST", "/api/sesiones/refresco", cuerpo={"tokenRefresco": self.s["tokenRefresco"]})
        if st != 200:
            sys.exit(f"{self.alias}: refresh failed ({st} {s}); sign in again with crear_cuenta.sh")
        self.s = s
        old = os.umask(0o077)
        try:
            self.ruta.write_text(json.dumps(s))
        finally:
            os.umask(old)

    @property
    def token(self) -> str:
        return self.s["tokenAcceso"]

    def __call__(self, metodo, url, cuerpo=None):
        return pedir(metodo, url, self.token, cuerpo)


# ------------------------------------------------------------------ household A setup
def camara_de(a: Cuenta) -> dict | None:
    st, cams, _ = a("GET", "/api/camaras")
    if st != 200 or not cams:
        return None
    return cams[0]


def consentir(a: Cuenta) -> None:
    st, r, _ = a("POST", "/api/hogar/consentimiento",
                 {"otorgadoPor": "Prueba de seguridad", "aceptadoPorAdultoMayor": True, "vistaEnVivoAceptada": True})
    if st not in (200, 201):
        sys.exit(f"grant consent failed: {st} {r}")


def entorno_agente() -> dict:
    env = dict(os.environ)
    env.update(TT_LAT_AGENTE=str(AGENTE), TT_LAT_SIN_TELEFONO="1", TT_LAT_API_URL=API)
    return env


def vincular(args) -> None:
    a = Cuenta("seguridad-a")
    trabajo = Path(args.dir)
    trabajo.mkdir(parents=True, exist_ok=True)
    env = entorno_agente() | {"TT_LAT_DIR": str(trabajo)}
    consentir(a)
    log("A's consent granted; starting the isolated agent in link mode")
    p = subprocess.Popen([str(LATENCIA / "medir_produccion.sh"), "vincular"], env=env,
                         stdout=open(trabajo / "vincular_salida.log", "w"), stderr=subprocess.STDOUT)
    vlog = trabajo / "vincular.log"
    aprobado = asignado = False
    limite = time.time() + 900
    while p.poll() is None and time.time() < limite:
        texto = vlog.read_text(errors="replace") if vlog.exists() else ""
        if not aprobado:
            m = re.search(r"Código para vincular esta PC: (\S+)", texto)
            if m:
                st, r, _ = a("POST", "/api/vinculaciones/aprobacion", {"codigo": m.group(1)})
                log(f"approve code: {st}")
                if st != 201:
                    p.terminate()
                    sys.exit(f"approval failed: {r}")
                aprobado = True
            elif "ya" in texto and "vinculada" in texto:
                aprobado = True
        if aprobado and not asignado:
            st, pcs, _ = a("GET", "/api/pcs")
            for pc in pcs if st == 200 else []:
                st2, cams, _ = a("GET", f"/api/pcs/{pc['id']}/camaras")
                for c in cams if st2 == 200 else []:
                    if not c.get("ambienteId"):
                        st3, r, _ = a("PUT", f"/api/camaras/{c['id']}/ambiente", {"nombreAmbiente": "Sala"})
                        log(f"webcam {c['id']} → Sala: {st3}")
                        asignado = st3 == 200
                    else:
                        asignado = True
        time.sleep(3)
    p.wait(timeout=120)
    log(f"link mode finished (exit {p.returncode}); camera: {camara_de(a)}")


def arrancar_agente(trabajo: Path) -> subprocess.Popen:
    """The isolated agent with the standing clip that «vincular» prepared (no fall)."""
    esc = Path(os.environ.get("TT_E2E_ESCRITORIO", AQUI.parents[2] / ".." / "te-tengo-desktop-pywebview")).resolve()
    if not esc.exists():
        esc = Path("/Users/jhosepmyr/Projects/te-tengo/te-tengo-desktop-pywebview")
    clips = sorted(Path(args_global.vinculo).glob("clips/fall-01.tsv"))
    if not clips:
        sys.exit("no clip: run «vincular» first with the same --vinculo dir")
    clip = clips[0].read_text().split("\t")[1].strip()
    env = entorno_agente() | {
        "TT_LAT_LLAVERO": str(AGENTE / "credenciales.json"),
        "PYTHON_KEYRING_BACKEND": "keyring.backends.fail.Keyring",
        "TT_LAT_INICIO_CAIDA": "100000",
    }
    return subprocess.Popen(
        [str(esc / ".venv/bin/python"), str(LATENCIA / "agente_produccion.py"), "--config", str(AGENTE / "config.toml"),
         "--video", clip, "--sin-interfaz", "--datos", str(AGENTE / "datos"), "--logs", str(AGENTE / "logs")],
        env=env, stdout=open(trabajo / "agente.log", "w"), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)


def esperar_conectada(a: Cuenta, segundos=120) -> dict:
    limite = time.time() + segundos
    while time.time() < limite:
        c = camara_de(a)
        if c and c.get("estadoConexion") not in (None, "DESCONECTADA"):
            return c
        time.sleep(3)
    sys.exit(f"camera not connected after {segundos}s: {camara_de(a)}")


# ------------------------------------------------------------------ measurement
def medir(args) -> None:
    trabajo = Path(args.dir)
    trabajo.mkdir(parents=True, exist_ok=True)
    a, b = Cuenta("seguridad-a"), Cuenta("seguridad-b")
    filas: list[dict] = []

    def anotar(caso, esperado, st, r, dt, extra=""):
        codigo = r.get("codigo") if isinstance(r, dict) else ""
        ok = st in esperado if isinstance(esperado, tuple) else st == esperado
        filas.append(dict(t=f"{time.time():.3f}", caso=caso, esperado=str(esperado), http=st, codigo=codigo or "",
                          ok=int(ok), ms=f"{dt * 1000:.0f}", extra=extra))
        log(f"{caso:14s} {st} {codigo or ''} {'OK' if ok else 'UNEXPECTED'}")

    agente = arrancar_agente(trabajo)
    try:
        consentir(a)
        cam = esperar_conectada(a)
        cid = cam["id"]
        log(f"camera {cid} {cam.get('estadoConexion')}")

        def leer_registros(etiqueta):
            st, acc, _ = a("GET", "/api/accesos-vista-en-vivo")
            st2, den, _ = a("GET", "/api/accesos-vista-en-vivo/denegados")
            (trabajo / f"accesos_{etiqueta}.json").write_text(json.dumps(acc, indent=1, default=str))
            (trabajo / f"denegados_{etiqueta}.json").write_text(json.dumps(den if st2 == 200 else {"http": st2}, indent=1))
            return (acc if st == 200 else []), (den if st2 == 200 else None)

        acc0, den0 = leer_registros("antes")
        n = args.n

        # 1. legitimate openings by A (also gives ended-session tokens for the media server)
        tokens_vencidos, url_ejemplo = [], None
        for _ in range(args.m):
            st, r, dt = a("POST", f"/api/camaras/{cid}/vista-en-vivo", {})
            anotar("legitimo", 201, st, r, dt)
            if st == 201:
                url_ejemplo = r.get("urlTransmision")
                time.sleep(args.pausa)
                st2, _, _ = a("DELETE", f"/api/vista-en-vivo/{r['sesionId']}")
                tokens_vencidos.append(r.get("urlTransmision"))
            time.sleep(args.pausa)

        # 2. B, another household, on A's camera and on A's open session
        for _ in range(n):
            anotar("otro_hogar", 404, *b("POST", f"/api/camaras/{cid}/vista-en-vivo", {}))
            time.sleep(args.pausa)
        st, abierta, _ = a("POST", f"/api/camaras/{cid}/vista-en-vivo", {})
        if st == 201:
            for _ in range(n):
                anotar("sesion_ajena", 404, *b("PATCH", f"/api/vista-en-vivo/{abierta['sesionId']}", {"modo": "SOLO_POSTURA"}))
                time.sleep(args.pausa)
            a("DELETE", f"/api/vista-en-vivo/{abierta['sesionId']}")

        # 3. the media server: no token, made-up token, token of an ended session
        if url_ejemplo:
            u = urllib.parse.urlsplit(url_ejemplo)
            q = dict(urllib.parse.parse_qsl(u.query))

            def con_token(tok):
                qq = dict(q)
                if tok is None:
                    qq.pop("token", None)
                else:
                    qq["token"] = tok
                return urllib.parse.urlunsplit(u._replace(query=urllib.parse.urlencode(qq)))

            for _ in range(n):
                anotar("media_sin_token", (401, 403, 404), *pedir("GET", con_token(None)))
                time.sleep(args.pausa)
                anotar("media_token_falso", (401, 403, 404), *pedir("GET", con_token(secrets.token_urlsafe(32))))
                time.sleep(args.pausa)
            for url in tokens_vencidos:
                anotar("media_sesion_terminada", (401, 403, 404), *pedir("GET", url))
                time.sleep(args.pausa)

        # 4. without consent (last: revoking turns the camera off; consent is re-granted afterwards)
        st, r, dt = a("DELETE", "/api/hogar/consentimiento")
        log(f"consent revoked: {st}")
        time.sleep(3)
        try:
            for _ in range(n):
                anotar("sin_consentimiento", 409, *a("POST", f"/api/camaras/{cid}/vista-en-vivo", {}))
                time.sleep(args.pausa)
        finally:
            consentir(a)
            log("consent re-granted")

        time.sleep(5)
        acc1, den1 = leer_registros("despues")
    finally:
        agente.terminate()
        try:
            agente.wait(20)
        except subprocess.TimeoutExpired:
            agente.kill()

    with open(trabajo / "intentos.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0]))
        w.writeheader()
        w.writerows(filas)
    resumen = {"api": API, "camara": cid, "n": n, "m": args.m, "por_caso": {}}
    for fila in filas:
        c = resumen["por_caso"].setdefault(fila["caso"], {"intentos": 0, "esperado": 0, "http": {}})
        c["intentos"] += 1
        c["esperado"] += fila["ok"]
        c["http"][str(fila["http"])] = c["http"].get(str(fila["http"]), 0) + 1
    resumen["accesos_nuevos"] = len(acc1) - len(acc0)
    if den0 is not None and den1 is not None:
        resumen["denegados_nuevos"] = len(den1) - len(den0)
        nuevos = den1[: len(den1) - len(den0)] if len(den1) >= len(den0) else den1
        por = {}
        for d in nuevos:
            k = f"{d.get('canal')}/{d.get('motivo')}"
            por[k] = por.get(k, 0) + 1
        resumen["denegados_por_motivo"] = por
    else:
        resumen["denegados_nuevos"] = "endpoint not available"
    (trabajo / "resumen.json").write_text(json.dumps(resumen, indent=1))
    print(json.dumps(resumen, indent=1))


# ------------------------------------------------------------------ confidentiality
def muestrear_red(pid: int, salida: Path, alto: list) -> None:
    """Every 2 s: nettop of the agent's process (process total + one row per connection)."""
    with open(salida, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["epoch", "fila", "bytes_in", "bytes_out"])
        while not alto:
            t = time.time()
            try:
                out = subprocess.run(["nettop", "-p", str(pid), "-L", "1", "-n", "-x", "-J", "bytes_in,bytes_out"],
                                     capture_output=True, text=True, timeout=10).stdout
            except subprocess.TimeoutExpired:
                out = ""
            for linea in out.splitlines()[1:]:
                partes = linea.rstrip(",").rsplit(",", 2)
                if len(partes) == 3:
                    w.writerow([f"{t:.3f}", partes[0], partes[1], partes[2]])
            f.flush()
            time.sleep(max(0.0, 2 - (time.time() - t)))


def ver_hls(url: str, segundos: float) -> int:
    """Plays the live view as the app's LL-HLS player would (playlist + segments); bytes read."""
    leidos, vistos, fin = 0, set(), time.time() + segundos
    def get(u):
        nonlocal leidos
        st, cuerpo, _ = pedir_bruto(u)
        leidos += len(cuerpo or b"")
        return st, cuerpo or b""
    def con_query(base, rel):
        u = urllib.parse.urljoin(base, rel)
        q = urllib.parse.urlsplit(base).query
        return u if "?" in u or not q else f"{u}?{q}"
    while time.time() < fin:
        st, maestra = get(url)
        if st != 200:
            time.sleep(1); continue
        lineas = [l for l in maestra.decode(errors="replace").splitlines() if l and not l.startswith("#")]
        variante = con_query(url, lineas[0]) if lineas and "#EXTINF" not in maestra.decode(errors="replace") else url
        st, lista = get(variante)
        for l in lista.decode(errors="replace").splitlines():
            if l and not l.startswith("#") and l not in vistos:
                vistos.add(l)
                get(con_query(variante, l))
        time.sleep(1)
    return leidos


def pedir_bruto(url: str):
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(url, timeout=15) as r:
            return r.status, r.read(), time.monotonic() - t0
    except urllib.error.HTTPError as e:
        return e.code, e.read(), time.monotonic() - t0
    except (urllib.error.URLError, TimeoutError, ConnectionError):
        return 0, b"", time.monotonic() - t0


def confidencialidad(args) -> None:
    """Idle monitoring, one live view, one fall: what leaves the home, per phase and destination."""
    trabajo = Path(args.dir)
    trabajo.mkdir(parents=True, exist_ok=True)
    a = Cuenta("seguridad-a")
    esc = Path("/Users/jhosepmyr/Projects/te-tengo/te-tengo-desktop-pywebview")
    py = esc / ".venv/bin/python"
    tsv = subprocess.run([str(py), str(LATENCIA / "preparar_clips.py"), str(esc / "datos/urfd"), str(trabajo / "clips"),
                          str(args.previa), "25", "fall-01"], capture_output=True, text=True, check=True).stdout
    _, clip, inicio, duracion, previa = tsv.strip().split("\t")
    inicio = float(inicio)
    log(f"clip {clip}: fall onset at {inicio:.1f} s")
    consentir(a)
    fases: list[dict] = []
    env = entorno_agente() | {"TT_LAT_LLAVERO": str(AGENTE / "credenciales.json"),
                              "PYTHON_KEYRING_BACKEND": "keyring.backends.fail.Keyring",
                              "TT_LAT_INICIO_CAIDA": f"{inicio}"}
    # Every HTTPS connection of the agent goes through the local CONNECT proxy of Case A, always in
    # «paso» (pass-through): it logs each tunnel's host and bytes (every 1 s and at close). What does
    # not honour the proxy (the RTSPS publisher of the live view) is seen by nettop.
    pto = trabajo / "proxy.puerto"
    proxy = subprocess.Popen([sys.executable, str(LATENCIA / "proxy_corte.py"), "--registro", str(trabajo / "proxy.jsonl"),
                              "--puerto-archivo", str(pto), "--control", str(trabajo / "proxy.control"), "--progreso", "1"],
                             stdout=open(trabajo / "proxy.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(60):
        if pto.exists() and pto.read_text().strip():
            break
        time.sleep(0.25)
    url_proxy = f"http://127.0.0.1:{pto.read_text().strip()}"
    for k in ("NO_PROXY", "no_proxy", "ALL_PROXY", "all_proxy"):
        env.pop(k, None)
    env.update(HTTPS_PROXY=url_proxy, HTTP_PROXY=url_proxy, https_proxy=url_proxy, http_proxy=url_proxy)
    t0 = time.time()
    agente = subprocess.Popen([str(py), str(LATENCIA / "agente_produccion.py"), "--config", str(AGENTE / "config.toml"),
                               "--video", clip, "--sin-interfaz", "--datos", str(AGENTE / "datos"), "--logs", str(AGENTE / "logs")],
                              env=env, stdout=open(trabajo / "agente.log", "w"), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    alto: list = []
    import threading
    hilo = threading.Thread(target=muestrear_red, args=(agente.pid, trabajo / "red.csv", alto), daemon=True)
    hilo.start()

    def esperar_hasta(t):
        while time.time() < t:
            if agente.poll() is not None:
                sys.exit(f"the agent exited ({agente.returncode}); see {trabajo / 'agente.log'}")
            time.sleep(0.5)

    try:
        cam = esperar_conectada(a)
        cid = cam["id"]
        caida = t0 + inicio  # approximate: the clip starts when the agent opens it (~a few s after t0)
        # Phase 1: idle monitoring, from 60 s after start to 3.5 min before the fall.
        ini_reposo = t0 + 60
        fin_reposo = caida - args.vivo - 150
        esperar_hasta(ini_reposo)
        fases.append({"fase": "monitoreo", "inicio": ini_reposo, "fin": fin_reposo})
        esperar_hasta(fin_reposo)
        # Phase 2: one live view, watched for args.vivo s, then closed.
        st, ses, _ = a("POST", f"/api/camaras/{cid}/vista-en-vivo", {})
        if st != 201:
            sys.exit(f"live view failed: {st} {ses}")
        ti = time.time()
        leidos = ver_hls(ses["urlTransmision"], args.vivo)
        a("DELETE", f"/api/vista-en-vivo/{ses['sesionId']}")
        tf = time.time()
        fases.append({"fase": "vista_en_vivo", "inicio": ti, "fin": tf + 5, "bytes_leidos_por_el_familiar": leidos})
        log(f"live view {tf - ti:.0f} s, {leidos} bytes read by the viewer")
        # Phase 3: idle again until shortly before the fall.
        fases.append({"fase": "monitoreo_tras_vista", "inicio": tf + 15, "fin": caida - 5})
        esperar_hasta(caida - 5)
        # Phase 4: the fall: event + clip upload.
        fases.append({"fase": "caida", "inicio": caida - 5, "fin": caida + args.tras_caida})
        esperar_hasta(caida + args.tras_caida)
    finally:
        alto.append(1)
        agente.terminate()
        try:
            agente.wait(20)
        except subprocess.TimeoutExpired:
            agente.kill()
        hilo.join(10)
        time.sleep(2)
        proxy.terminate()
        proxy.wait(10)
    (trabajo / "fases.json").write_text(json.dumps(fases, indent=1))
    st, alertas, _ = a("GET", "/api/alertas")
    (trabajo / "alertas.json").write_text(json.dumps(alertas, indent=1, default=str))
    analizar_conf(trabajo)


def analizar_conf(trabajo: Path) -> None:
    """Bytes sent from the home per phase and destination: HTTPS from the proxy log (every tunnel,
    however short), anything else (the RTSPS live-view publisher) from nettop's per-connection rows."""
    fases = json.loads((trabajo / "fases.json").read_text())
    # proxy tunnels: cumulative (t, up, down) series
    series: dict = {}
    hosts: dict = {}
    for linea in open(trabajo / "proxy.jsonl"):
        e = json.loads(linea)
        if e["evento"] == "connect" and e.get("resultado") == "ok":
            hosts[e["id"]] = f"{e['host']}:{e['puerto']}"
            series[e["id"]] = [(e["epoch"], 0, 0)]
        elif e["evento"] in ("progreso", "cierre") and e.get("id") in series:
            series[e["id"]].append((e["epoch"], e.get("subida", 0), e.get("bajada", 0)))
    def valor(serie, t):
        v = (0, 0)
        for (tt, up, dn) in serie:
            if tt <= t:
                v = (up, dn)
        return v
    # direct connections (not through the proxy): nettop rows not to 127.0.0.1
    directas: dict = {}
    for f in csv.DictReader(open(trabajo / "red.csv")):
        if "<->" not in f["fila"] or "<->127.0.0.1" in f["fila"]:
            continue
        directas.setdefault(f["fila"], []).append((float(f["epoch"]), int(f["bytes_out"]), int(f["bytes_in"])))
    resumen = []
    for fa in fases:
        ini, fin = fa["inicio"], fa["fin"]
        por: dict = {}
        conexiones: dict = {}
        for ident, serie in series.items():
            a0, a1 = valor(serie, ini), valor(serie, fin)
            up, dn = a1[0] - a0[0], a1[1] - a0[1]
            if serie[0][0] <= fin and serie[-1][0] >= ini:
                conexiones[hosts[ident]] = conexiones.get(hosts[ident], 0) + 1
            if up or dn:
                d = por.setdefault(hosts[ident], {"subida": 0, "bajada": 0})
                d["subida"] += up
                d["bajada"] += dn
        for fila, serie in directas.items():
            dentro = [m for m in serie if ini < m[0] <= fin]
            if not dentro:
                continue
            antes = [m for m in serie if m[0] <= ini]
            base = antes[-1] if antes else (0, 0, 0)
            destino = "directa " + fila.split("<->")[1]
            d = por.setdefault(destino, {"subida": 0, "bajada": 0})
            d["subida"] += dentro[-1][1] - base[1]
            d["bajada"] += dentro[-1][2] - base[2]
            conexiones[destino] = conexiones.get(destino, 0) + 1
        dur = fin - ini
        sub = sum(v["subida"] for v in por.values())
        resumen.append(dict(fa, duracion_s=round(dur, 1), subida_total_bytes=sub,
                            subida_bytes_por_min=round(sub / dur * 60) if dur > 0 else None,
                            por_destino=dict(sorted(por.items())), conexiones=dict(sorted(conexiones.items()))))
    (trabajo / "resumen_confidencialidad.json").write_text(json.dumps(resumen, indent=1))
    for r in resumen:
        print(f"{r['fase']:22s} {r['duracion_s']:7.1f} s  up {r['subida_total_bytes']:>10,} B  ({r['subida_bytes_por_min']:,} B/min)")
        for k, v in r["por_destino"].items():
            print(f"    {k:55s} up {v['subida']:>10,}  down {v['bajada']:>10,}")

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("modo", choices=["vincular", "medir", "confidencialidad", "analizar-conf"])
    ap.add_argument("--dir", default=str(Path.home() / f"seguridad-prod-{time.strftime('%Y-%m-%d-%H%M')}"))
    ap.add_argument("--vinculo", default=str(DIR / "vinculo"), help="dir of «vincular» (clip)")
    ap.add_argument("--n", type=int, default=10, help="attempts per denial case")
    ap.add_argument("--m", type=int, default=5, help="legitimate openings")
    ap.add_argument("--pausa", type=float, default=1.0)
    ap.add_argument("--previa", type=float, default=600, help="confidencialidad: standing seconds before the fall")
    ap.add_argument("--vivo", type=float, default=60, help="confidencialidad: live-view seconds")
    ap.add_argument("--tras-caida", type=float, default=120, help="confidencialidad: seconds observed after the fall")
    args_global = ap.parse_args()
    if args_global.modo == "vincular":
        args_global.dir = args_global.vinculo
        vincular(args_global)
    elif args_global.modo == "medir":
        medir(args_global)
    elif args_global.modo == "confidencialidad":
        confidencialidad(args_global)
    else:
        analizar_conf(Path(args_global.dir))
