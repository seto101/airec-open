"""Transcribe en esta PC las grabaciones que están en el teléfono y le devuelve el texto.

Busca por adb las grabaciones de la app Grabador (ec.paul.grabador) que no tienen transcripción
precisa, las baja, las transcribe con faster-whisper (modelo grande, en local, sin ninguna API) y
deja `<nombre>.transcripcion.json` junto al audio en el teléfono. La app lo muestra al volver a ella.

Uso:
  python pc/transcribir.py                 # todas las pendientes
  python pc/transcribir.py --solo 20261113173457
  python pc/transcribir.py --forzar        # rehace también las ya transcritas
Requisitos: adb con el teléfono conectado (depuración USB o inalámbrica) y `pip install faster-whisper`.
"""
import argparse
import datetime as dt
import json
import os
import subprocess
import sys
import time

PAQUETE = "ec.paul.grabador"
REMOTO = f"/sdcard/Android/data/{PAQUETE}/files/grabaciones"
ADB = os.environ.get("ADB", os.path.expanduser("~/Android/Sdk/platform-tools/adb"))
LOCAL = os.path.expanduser(os.environ.get("GRABADOR_LOCAL", "~/.local/share/airec-open/grabaciones"))
VERSION_FORMATO = 1


def adb(*args, serie=None, check=True, texto=True):
    cmd = [ADB] + (["-s", serie] if serie else []) + list(args)
    r = subprocess.run(cmd, capture_output=True, text=texto)
    if check and r.returncode != 0:
        raise SinTelefono(f"adb falló: {' '.join(args)}: {r.stderr.strip()}")
    return r.stdout


class SinTelefono(Exception):
    pass


def conectados():
    lineas = [l.split() for l in adb("devices").splitlines()[1:] if l.strip()]
    return [l[0] for l in lineas if len(l) > 1 and l[1] == "device"]


def elegir_telefono(pedido):
    if pedido:
        return pedido
    listos = conectados()
    if not listos:
        # Depuración inalámbrica ya vinculada: el teléfono se anuncia por mDNS, aunque haya cambiado de IP.
        for linea in adb("mdns", "services", check=False).splitlines():
            partes = linea.split()
            if len(partes) >= 3 and partes[1] == "_adb-tls-connect._tcp":
                adb("connect", partes[2], check=False)
        listos = conectados()
    if not listos:
        raise SinTelefono("No hay teléfono por adb. Activa la depuración (USB o inalámbrica).")
    # Si el mismo teléfono sale dos veces (IP y nombre mDNS), da igual cuál; mejor la IP.
    return sorted(listos, key=lambda s: "._tcp" in s)[0]


def pendientes(serie, forzar, solo):
    nombres = adb("shell", f"ls {REMOTO}", serie=serie, check=False).split()
    audios = sorted({n[:-5] for n in nombres if n.endswith(".opus")}, reverse=True)
    if solo:
        audios = [a for a in audios if a in solo]
    out = []
    for a in audios:
        if f"{a}.transcripcion.json" in nombres and not forzar:
            previo = adb("shell", f"cat {REMOTO}/{a}.transcripcion.json", serie=serie, check=False)
            try:
                if json.loads(previo).get("origen") == "pc":
                    continue  # ya tiene la precisa
            except json.JSONDecodeError:
                pass
        out.append(a)
    return out


def hms(s):
    s = int(s)
    return f"{s // 3600}:{s % 3600 // 60:02d}:{s % 60:02d}"


def cargar_audio(ruta):
    """Audio a 16 kHz mono float32 con ffmpeg (sin depender de PyAV, que choca de versión con faster-whisper)."""
    import numpy as np
    ffmpeg = os.environ.get("FFMPEG") or os.path.join(os.path.dirname(sys.executable), "ffmpeg")
    if not os.path.exists(ffmpeg):
        ffmpeg = "ffmpeg"
    crudo = subprocess.run(
        [ffmpeg, "-nostdin", "-v", "error", "-i", ruta, "-f", "s16le", "-ac", "1", "-ar", "16000", "-"],
        capture_output=True, check=True,
    ).stdout
    return np.frombuffer(crudo, np.int16).astype(np.float32) / 32768.0


def leer_vocabulario(ruta):
    """Nombres y términos propios, uno por línea (# comenta). Se le dan a Whisper como pista."""
    if not ruta or not os.path.exists(ruta):
        return None
    palabras = [l.strip() for l in open(ruta, encoding="utf-8") if l.strip() and not l.startswith("#")]
    return ", ".join(palabras) or None


def transcribir(modelo, ruta, hilos, vocabulario=None):
    t0 = time.time()
    segs, info = modelo.transcribe(
        cargar_audio(ruta), language="es", vad_filter=True, beam_size=5, condition_on_previous_text=False,
        # La pista mejora mucho las palabras propias; a veces Whisper copia su primera palabra al
        # inicio del texto, y eso se quita abajo.
        initial_prompt=f"Términos: {vocabulario}." if vocabulario else None,
        hotwords=vocabulario,
        word_timestamps=True,
    )
    out = []
    ultimo_aviso = 0
    for s in segs:
        out.extend(partir(s))
        if len(out) - ultimo_aviso >= 20:
            ultimo_aviso = len(out)
            trans = time.time() - t0
            print(f"    {hms(s.end)} / {hms(info.duration)}  ({s.end / max(trans, 0.1):.1f}x tiempo real)", flush=True)
    if out and vocabulario and out[0]["t"].lower().startswith("términos"):
        out[0]["t"] = out[0]["t"][len("términos"):].lstrip(" :,.").capitalize()
    return out, info.duration, time.time() - t0


MAX_SEG = 12.0  # una frase de la app no pasa de ~12 s: si no, «seguir la lectura» se queda quieto


def partir(seg):
    """Corta un segmento de Whisper en frases: al final de cada oración, o cada MAX_SEG segundos."""
    palabras = list(seg.words or [])
    if not palabras:
        t = seg.text.strip()
        return [{"i": round(seg.start, 2), "f": round(seg.end, 2), "t": t}] if t else []
    frases, actual = [], []
    for w in palabras:
        actual.append(w)
        fin_oracion = w.word.rstrip().endswith((".", "?", "!", "…"))
        largo = w.end - actual[0].start
        if (fin_oracion and largo >= 2.0) or largo >= MAX_SEG:
            frases.append(actual)
            actual = []
    if actual:
        frases.append(actual)
    out = []
    for f in frases:
        texto = "".join(w.word for w in f).strip()
        if texto:
            out.append({"i": round(f[0].start, 2), "f": round(f[-1].end, 2), "t": texto})
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serie", help="dispositivo adb (si hay varios)")
    ap.add_argument("--modelo", default="large-v3-turbo")
    ap.add_argument("--hilos", type=int, default=8, help="núcleos para whisper (la PC tiene 14; dejar aire)")
    ap.add_argument("--solo", nargs="*", help="nombres de grabación concretos")
    ap.add_argument("--forzar", action="store_true")
    ap.add_argument("--vocabulario", default=os.path.expanduser("~/.config/grabador/vocabulario.txt"),
                    help="archivo con nombres y términos propios, uno por línea")
    ap.add_argument("--vigilar", type=int, metavar="SEG",
                    help="no terminar: cada SEG segundos busca el teléfono y transcribe lo nuevo")
    a = ap.parse_args()

    if a.vigilar:
        os.nice(19)  # que no frene la PC mientras se trabaja
        print(f"Vigilando cada {a.vigilar} s (Ctrl+C para salir)…", flush=True)
        while True:
            try:
                # El modelo (~2 GB) se carga solo si hay algo y se suelta al terminar la vuelta.
                pasada(a, None, callado=True)
            except SinTelefono:
                pass  # teléfono fuera de la red o sin depuración: se vuelve a mirar luego
            except Exception as e:  # noqa: BLE001 — un fallo puntual no debe tumbar la vigilancia
                print(f"[{dt.datetime.now():%H:%M}] error: {e}", flush=True)
            time.sleep(a.vigilar)
    try:
        pasada(a, None)
    except SinTelefono as e:
        sys.exit(str(e))


def pasada(a, modelo, callado=False):
    """Una vuelta: transcribe lo pendiente. Devuelve el modelo cargado para reutilizarlo."""
    serie = elegir_telefono(a.serie)
    lista = pendientes(serie, a.forzar, a.solo)
    if not lista:
        if not callado:
            print("Nada pendiente: todas las grabaciones del teléfono tienen transcripción precisa.")
        return modelo
    print(f"[{dt.datetime.now():%H:%M}] {len(lista)} por transcribir en {serie}: {', '.join(lista)}", flush=True)
    vocabulario = leer_vocabulario(a.vocabulario)
    if vocabulario:
        print(f"Vocabulario: {vocabulario}")

    if modelo is None:
        from faster_whisper import WhisperModel  # import tardío: tarda y no hace falta si no hay nada
        print(f"Cargando {a.modelo} (la primera vez lo baja, ~1,6 GB)…", flush=True)
        modelo = WhisperModel(a.modelo, device="cpu", compute_type="int8", cpu_threads=a.hilos)

    os.makedirs(LOCAL, exist_ok=True)
    for n in lista:
        print(f"\n▸ {n}", flush=True)
        audio = os.path.join(LOCAL, f"{n}.opus")
        adb("pull", f"{REMOTO}/{n}.opus", audio, serie=serie)
        segmentos, duracion, tardo = transcribir(modelo, audio, a.hilos, vocabulario)
        doc = {
            "version": VERSION_FORMATO,
            "origen": "pc",
            "modelo": a.modelo,
            "idioma": "es",
            "creado": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
            "duracion": round(duracion, 2),
            "segmentos": segmentos,
        }
        local_json = os.path.join(LOCAL, f"{n}.transcripcion.json")
        with open(local_json, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False)
        # Se sube con otro nombre y se renombra: la app nunca ve un archivo a medias.
        adb("push", local_json, f"{REMOTO}/{n}.transcripcion.json.tmp", serie=serie)
        adb("shell", f"mv {REMOTO}/{n}.transcripcion.json.tmp {REMOTO}/{n}.transcripcion.json", serie=serie)
        print(f"  ✓ {len(segmentos)} frases, {hms(duracion)} en {tardo / 60:.1f} min ({duracion / max(tardo, 1):.1f}x) → teléfono")
    print("\nListo. Abre la app (o vuelve a ella) para ver las transcripciones.", flush=True)
    return modelo


if __name__ == "__main__":
    main()
