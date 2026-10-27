#!/usr/bin/env python3
"""FireHome: pulisce la Fire Stick e ne sostituisce la Home. Un solo comando, nessuna domanda.

  python3 scripts/firestick.py            fa tutto: trova la Fire Stick, pulisce, installa FireHome, prende la Home
  python3 scripts/firestick.py check      elenca cosa Amazon ha riattivato (dopo un aggiornamento)
  python3 scripts/firestick.py restore    rimette tutto com'era prima
  python3 scripts/firestick.py adb-off    spegne il Debug ADB (chiude la porta 5555)
  (anche: clean = solo la pulizia, home = solo FireHome)

Utilità:
  python3 scripts/firestick.py status       memoria, spazio, stato della pulizia e della Home
  python3 scripts/firestick.py screenshot   salva una schermata della TV in Immagini
  python3 scripts/firestick.py cache        libera la cache delle app
  python3 scripts/firestick.py install F.apk [G.apk ...]   installa app dal computer
  python3 scripts/firestick.py hdmi         diagnosi HDMI-CEC (Anynet+ sui Samsung): salva fire-hdmi.txt
  python3 scripts/firestick.py discover-live   cerca i collegamenti diretti ai canali nelle app RaiPlay, Mediaset, La7

Opzioni: un IP o 'usb' come ultimo argomento per saltare la ricerca automatica; --youtube, --extras, --tv,
--no-home, --apk FILE, --reboot (vedi --help). Pensato per Nobara Linux (e altre Fedora).

Prima sulla Fire Stick: Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB = ON.
Le app vengono solo disattivate per l'utente 0 (pm disable-user): niente viene disinstallato.
L'elenco dei pacchetti è in firestick-bloat.txt.
"""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIST_FILE = HERE / "firestick-bloat.txt"
STATE_DIR = Path.home() / ".firestick-clean"
REPORT = Path("fire-report.txt")
BATCH = 8

APP_ID = "app.firehome"
GUARD = f"{APP_ID}/{APP_ID}.HomeGuard"
GUARD_LABEL = "FireHome tasto Home"
APK_URL = "https://github.com/rikytax/fire-os-replace/releases/latest/download/FireHome.apk"

SMARTTUBE = "org.smarttube.stable"
SMARTTUBE_URL = "https://github.com/yuliskov/SmartTube/releases/download/32.56s/SmartTube_stable_32.56_universal.apk"
SMARTTUBE_SHA256 = "4a35b90940612e048e4e9cb9e10379f7db1e84f30375994129f10a571ee04ce5"  # versione fissata e verificata
SMARTTUBE_VERSION_CODE = 2446
STOCK_YOUTUBE = "com.amazon.firetv.youtube"

COMMANDS = ("all", "clean", "home", "check", "restore", "adb-off", "status", "screenshot", "cache", "install", "hdmi", "discover-live")
PKG_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
# Pacchetti Android di base: senza, il sistema può non avviarsi più.
CORE_RE = re.compile(
    r"^(android"
    r"|com\.android\.(systemui|shell|phone|settings|tv\.settings|providers\..+)"
    r"|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)"
    r"|com\.google\.android\.overlay\.modules.*)$"
)
# I servizi che hai chiesto di tenere: nessun pacchetto con questi nomi viene toccato, qualunque cosa dica l'elenco.
KEEP_TOKENS = ("netflix", "firebat", "disney", "jellyfin", "buddyfit", "mediaset", "la7", "raiplay", "rainet", "smarttube")

# Opzioni su pubblicità e uso dei dati (stesse chiavi che scrivono gli interruttori delle Impostazioni).
PRIVACY = [
    ("secure", "amz_limit_ad_tracking", "1"),
    ("secure", "limit_ad_tracking", "1"),
    ("secure", "non_amz_limit_ad_tracking", "1"),
    ("secure", "advertisingIdApp:interestBasedAds:value", "0"),
    ("secure", "USAGE_METRICS_UPLOAD_ENABLED", "0"),
    ("secure", "usage_metrics_marketing_enabled", "0"),
    ("secure", "settingsApp:appUsageData:value", "0"),
    ("secure", "settingsApp:deviceUsageData:value", "0"),
    ("global", "heads_up_notifications_enabled", "0"),
    ("global", "com.amazon.tv.settings.featured_item_video_enabled", "0"),
    ("global", "com.amazon.tv.settings.featured_item_video_volume", "0.0"),
]
# Interfaccia più scattante. Solo Android 9 e precedenti: su Fire OS 8 a 0.5 i menu laterali non rispondono.
ANIMATIONS = [
    ("global", "window_animation_scale", "0.5"),
    ("global", "transition_animation_scale", "0.5"),
]

ADB = "adb"


def say(text=""):
    print(text, flush=True)


def die(message, code=2):
    print(f"\nErrore: {message}", file=sys.stderr)
    sys.exit(code)


# --- adb: ricerca, download, collegamento -----------------------------------

def adb(*args, timeout=60):
    try:
        r = subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        die("La Fire Stick non risponde.")
    return (r.stdout + r.stderr).replace("\r", "")


def find_adb():
    """adb di sistema (Nobara: sudo dnf install android-tools), oppure scaricato da Google (platform-tools)."""
    found = shutil.which("adb")
    if found:
        return found
    local = STATE_DIR / "platform-tools" / "adb"
    if local.exists():
        return str(local)
    say("adb non trovato: scarico Android platform-tools da Google (una volta sola).")
    say("(Meglio: sudo dnf install android-tools, o ./installa-nobara.sh)")
    try:
        STATE_DIR.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "pt.zip"
            url = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
            with urllib.request.urlopen(url, timeout=120) as r, open(archive, "wb") as f:
                shutil.copyfileobj(r, f)
            with zipfile.ZipFile(archive) as z:
                z.extractall(STATE_DIR)
    except Exception as e:
        die(f"Non riesco a scaricare adb ({e}). Installalo con: sudo dnf install android-tools")
    for tool in (STATE_DIR / "platform-tools").iterdir():
        tool.chmod(tool.stat().st_mode | 0o111)
    return str(local)


def state_of(serial):
    return adb("-s", serial, "get-state").strip()


def wait_authorized(serial, seconds=180):
    """Aspetta che sulla TV si accetti 'Consenti debug USB?'."""
    if state_of(serial) == "device":
        return
    say("Sulla TV compare 'Consenti debug USB?': scegli 'Consenti sempre da questo computer' e premi OK.")
    deadline = time.time() + seconds
    while time.time() < deadline:
        if state_of(serial) == "device":
            return
        time.sleep(3)
    die("Non hai accettato 'Consenti debug USB?' sulla TV: riprova.")


def is_amazon(serial):
    return adb("-s", serial, "shell", "getprop", "ro.product.manufacturer").strip().lower() == "amazon"


def usb_devices():
    rows = [line.split() for line in adb("devices").splitlines()[1:] if line.strip()]
    return [r[0] for r in rows if ":" not in r[0] and len(r) > 1 and r[1] in ("device", "unauthorized")]


def local_prefix():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))  # nessun pacchetto parte: serve solo a sapere quale rete si usa
        ip = s.getsockname()[0]
    except OSError:
        ip = socket.gethostbyname(socket.gethostname())
    finally:
        s.close()
    return ".".join(ip.split(".")[:3])


def scan_network():
    """Indirizzi della rete locale con la porta ADB (5555) aperta."""
    prefix = local_prefix()

    def probe(host):
        try:
            with socket.create_connection((host, 5555), timeout=1.5):
                return host
        except OSError:
            return None

    with concurrent.futures.ThreadPoolExecutor(64) as pool:
        return [h for h in pool.map(probe, [f"{prefix}.{i}" for i in range(1, 255)]) if h]


def connect(target):
    """Restituisce il numero di serie adb della Fire Stick."""
    if target == "usb":
        found = usb_devices()
        if not found:
            die("Nessuna Fire Stick via USB: controlla il cavo e il Debug ADB. Se non va, usa il Wi-Fi.")
        wait_authorized(found[0])
        return found[0]
    if target != "auto":
        serial = target if ":" in target else f"{target}:5555"
        if "connected" not in adb("connect", serial):
            die(f"Connessione a {serial} fallita: Debug ADB acceso? IP giusto?")
        wait_authorized(serial)
        return serial

    say("Cerco la Fire Stick...")
    for serial in usb_devices():
        wait_authorized(serial)
        if is_amazon(serial):
            return serial
    for host in scan_network():
        serial = f"{host}:5555"
        if "connected" in adb("connect", serial):
            wait_authorized(serial)
            if is_amazon(serial):
                return serial
    say("Non ho trovato nessuna Fire Stick da sola. Sulla Fire Stick: Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB = ON,")
    say("e l'IP è in Impostazioni > My Fire TV > Info > Rete.")
    if not sys.stdin.isatty():
        die("Rilancia con l'IP della Fire Stick come ultimo argomento.")
    ip = input("IP della Fire Stick: ").strip()
    return connect(ip)


class Tv:
    def __init__(self, serial):
        self.serial = serial

    def sh(self, *cmd, timeout=60):
        return adb("-s", self.serial, "shell", *cmd, timeout=timeout)

    def prop(self, name):
        return self.sh("getprop", name).strip()

    def packages(self, flag):
        """Nomi dei pacchetti attivi (-e) o disattivati (-d) per l'utente 0."""
        out = self.sh("pm", "list", "packages", flag, "--user", "0")
        return {line.split(":", 1)[1].split()[0] for line in out.splitlines() if line.startswith("package:")}

    def home(self):
        """Pacchetto che risponde al tasto Home, o None se la TV non lo dice."""
        out = self.sh("cmd", "package", "resolve-activity", "--brief",
                      "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        lines = out.strip().splitlines()
        m = re.match(r"^([A-Za-z0-9_.]+)/", lines[-1]) if lines else None
        return m.group(1) if m else None

    def handlers(self, *query):
        out = self.sh("cmd", "package", "query-activities", "--brief", *query)
        return set(re.findall(r"^\s*([A-Za-z0-9_.]+)/", out, re.M))

    def setting(self, namespace, key):
        value = self.sh("settings", "get", namespace, key).strip()
        return None if value == "null" else value

    def focus(self):
        out = self.sh("dumpsys", "window")
        m = re.search(r"mCurrentFocus=.*? (\S+)/(\S+?)\}?$", out, re.M)
        return f"{m.group(1)}/{m.group(2)}" if m else ""


# --- elenchi e registro -----------------------------------------------------

def load_sections():
    sections, current = {}, None
    for n, raw in enumerate(LIST_FILE.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1]
            sections[current] = []
        elif current is None:
            die(f"{LIST_FILE.name}, riga {n}: manca una sezione [base] prima di '{line}'.")
        elif not PKG_RE.match(line):
            die(f"{LIST_FILE.name}, riga {n}: '{line}' non è un nome di pacchetto.")
        elif CORE_RE.match(line):
            die(f"{LIST_FILE.name}, riga {n}: '{line}' è un pacchetto di base di Android, non si tocca.")
        else:
            sections[current].append(line)
    return sections


def is_kept(pkg, keep):
    """Da tenere: nella sezione [keep] (per prefisso) o uno dei servizi che hai chiesto di tenere."""
    return any(pkg == k or pkg.startswith(k + ".") for k in keep) or any(t in pkg.lower() for t in KEEP_TOKENS)


def wanted(sections, stick, extras):
    names = ["base"] + (["stick"] if stick else []) + (["extras"] if extras else [])
    return list(dict.fromkeys(p for name in names for p in sections.get(name, [])))


def record_path(serial):
    return STATE_DIR / (re.sub(r"[^A-Za-z0-9_.-]", "_", serial) + ".json")


def load_record(serial):
    """Il registro dello stato PRIMA della pulizia (None se non esiste). Una seconda esecuzione non lo sovrascrive."""
    path = record_path(serial)
    if not path.exists():
        return None
    try:
        record = json.loads(path.read_text())
    except ValueError:
        die(f"Il registro {path} è rovinato: non lo sovrascrivo. Controllalo o spostalo.")
    for key, empty in (("before", {}), ("refused", []), ("settings", {}), ("appops", []), ("home", {})):
        record.setdefault(key, empty)  # i registri di versioni precedenti non hanno tutte le chiavi
    return record


def new_record(serial):
    return load_record(serial) or {"before": {}, "refused": [], "settings": {}, "appops": [], "home": {}}


def save_record(serial, record):
    STATE_DIR.mkdir(exist_ok=True)
    record_path(serial).write_text(json.dumps(record, indent=1, sort_keys=True))


# --- 1. pacchetti -----------------------------------------------------------

def clean_packages(tv, record, pkgs, stick, keep):
    enabled, disabled = tv.packages("-e"), tv.packages("-d")
    if not enabled:
        die("La Fire Stick non ha elencato nessun pacchetto: riprova.")

    skipped = {}
    home = tv.home()
    settings = tv.handlers("-a", "android.settings.SETTINGS")  # chi apre le Impostazioni resta
    inputs = set() if stick else tv.handlers("-a", "android.intent.action.VIEW", "-t", "vnd.android.cursor.item/channel")
    for p in pkgs:
        if is_kept(p, keep):
            skipped[p] = "da tenere"
        elif p == home:
            skipped[p] = "è la Home"
        elif p in settings:
            skipped[p] = "apre le Impostazioni"
        elif p in inputs:
            skipped[p] = "gestisce gli ingressi TV (su una Fire Stick non usare --tv)"

    todo = [p for p in pkgs if p in enabled and p not in skipped]
    for p in pkgs:  # com'era ognuno PRIMA di toccarlo
        state = "enabled" if p in enabled else "disabled" if p in disabled else "absent"
        if record["before"].get(p, "absent") == "absent":
            record["before"][p] = state  # assente allora e attivo ora: si ripristina come attivo
    save_record(tv.serial, record)

    for i in range(0, len(todo), BATCH):
        batch = todo[i:i + BATCH]
        for p in batch:
            tv.sh("pm", "disable-user", "--user", "0", p)
        if home is not None and tv.home() != home:
            for p in batch:
                tv.sh("pm", "enable", "--user", "0", p)
            die(f"Dopo questo gruppo la Home non risponde più: l'ho rimesso a posto e mi fermo. Gruppo: {', '.join(batch)}")

    # Si legge lo stato invece di fidarsi dei messaggi: Fire OS rifiuta in silenzio i pacchetti protetti.
    still_on = tv.packages("-e")
    refused = [p for p in todo if p in still_on]
    done = [p for p in todo if p not in still_on]
    record["refused"] = sorted(set(record["refused"]) | set(refused))
    save_record(tv.serial, record)

    say(f"  disattivati: {len(done)}")
    for p, why in skipped.items():
        say(f"  saltato {p}: {why}")
    say(f"  assenti su questa Fire Stick o già disattivati: {len([p for p in pkgs if p not in todo and p not in skipped])}")
    if refused:
        say(f"  rifiutati da Fire OS ({len(refused)}), pacchetti protetti: è normale; li silenzio al passo successivo")
    if todo and not done:
        die("Fire OS ha rifiutato tutto: questo firmware (es. Fire OS 5) non permette di disattivare i pacchetti.", 1)
    return refused


# --- 2. pacchetti protetti: niente esecuzione in background ------------------

def silence_protected(tv, record, sdk, protected, refused, keep):
    ops = ["RUN_IN_BACKGROUND"] + (["RUN_ANY_IN_BACKGROUND"] if sdk >= 28 else [])
    installed = tv.packages("-e")
    done = []
    for p in dict.fromkeys(list(protected) + list(refused)):
        if p not in installed or is_kept(p, keep):
            continue
        for op in ops:
            tv.sh("appops", "set", p, op, "ignore")
        done.append(p)
    record["appops"] = sorted(set(record["appops"]) | set(done))
    save_record(tv.serial, record)
    say(f"  silenziati in background: {len(done)}")


# --- 3. impostazioni su pubblicità e privacy --------------------------------

def apply_settings(tv, record, sdk):
    items = PRIVACY + (ANIMATIONS if sdk <= 28 else [])
    changed = 0
    for namespace, key, value in items:
        slot = f"{namespace} {key}"
        if slot not in record["settings"]:
            record["settings"][slot] = tv.setting(namespace, key)  # null = non esisteva
        tv.sh("settings", "put", namespace, key, value)
        changed += tv.setting(namespace, key) == value
    save_record(tv.serial, record)
    say(f"  impostazioni applicate e confermate: {changed} su {len(items)}")
    if changed < len(items):
        say("  (Fire OS ne riscrive alcune: controlla Impostazioni > Preferenze > Impostazioni sulla privacy)")


# --- 4. FireHome come Home ---------------------------------------------------

def find_apk(given):
    """FireHome.apk: quello indicato, uno locale, oppure l'ultima versione pubblicata su GitHub."""
    candidates = [Path(given)] if given else [
        HERE.parent / "FireHome.apk",
        Path.home() / "Download" / "FireHome.apk",   # cartella in italiano
        Path.home() / "Downloads" / "FireHome.apk",  # cartella in inglese
        HERE.parent / "app/build/outputs/apk/release/app-release.apk",
    ]
    for c in candidates:
        if c.exists():
            return c
    if given:
        die(f"File non trovato: {given}")
    STATE_DIR.mkdir(exist_ok=True)
    apk = STATE_DIR / "FireHome.apk"
    say("  scarico FireHome dall'ultima versione pubblicata...")
    try:
        with urllib.request.urlopen(APK_URL, timeout=120) as r, open(apk, "wb") as f:
            shutil.copyfileobj(r, f)
    except Exception as e:
        say(f"  non riesco a scaricare FireHome ({e}). Se il repository è privato, scarica FireHome.apk dalla pagina Releases")
        say("  del repository e mettilo nella cartella Download (o accanto a questo script), poi rilancia: salto l'app per ora.")
        return None
    try:
        with urllib.request.urlopen(APK_URL + ".sha256", timeout=30) as r:
            expected = r.read().decode().split()[0].lower()
    except Exception:
        say("  attenzione: manca il file di controllo SHA-256, non posso verificare il download.")
        return apk
    if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
        apk.unlink()
        die("Il file scaricato non corrisponde al controllo SHA-256: non lo installo.")
    say("  controllo SHA-256 riuscito")
    return apk


def put_secure(tv, key, value):
    """settings put secure, con ripiego sul content provider (su alcune versioni 'settings' scarta in silenzio)."""
    tv.sh("settings", "put", "secure", key, value or '""')
    if (tv.setting("secure", key) or "") != value:
        tv.sh("content", "insert", "--uri", "content://settings/secure",
              "--bind", f"name:s:{key}", "--bind", f"value:s:{value}")


def guard_bound(dump, user):
    """True/False se il servizio è tra quelli attivi dell'utente; None se il formato non si riconosce."""
    current, inside, found, seen = None, False, False, False
    for line in dump.splitlines():
        if "User state[" in line:
            current, inside = None, False
        m = re.search(r"attributes:\{id=(\d+)[,}]", line)
        if m:
            current = m.group(1)
        if current == user and re.match(r"^\s*(Bound services|services):\s*\{", line):
            inside = True
            line = line.split("{", 1)[1]
        if inside:
            if f"Service[label={GUARD_LABEL}," in line:
                found = True
            if "}" in line:
                seen, inside = True, False
    return found if seen else None


def set_guard(tv, record, on):
    """Accende o spegne il servizio Home, lasciando intatti gli altri servizi di accessibilità."""
    home = record["home"]
    current = tv.setting("secure", "enabled_accessibility_services") or ""
    home.setdefault("a11y_before", current)
    home.setdefault("a11y_enabled_before", tv.setting("secure", "accessibility_enabled") or "0")
    others = [c for c in current.split(":") if c and c not in (GUARD, f"{APP_ID}/.HomeGuard")]
    put_secure(tv, "enabled_accessibility_services", "")  # azzerare prima fa riagganciare un servizio morto
    time.sleep(1)
    if on:
        put_secure(tv, "enabled_accessibility_services", ":".join(others + [GUARD]))
        put_secure(tv, "accessibility_enabled", "1")
    else:
        put_secure(tv, "enabled_accessibility_services", ":".join(others))
        put_secure(tv, "accessibility_enabled", "1" if others else "0")
    save_record(tv.serial, record)


def set_futurity(tv, record, on):
    """min_futurity=0: l'allarme parte subito e il lancio di FireHome non aspetta 5 secondi dopo Home."""
    home = record["home"]
    current = tv.setting("global", "alarm_manager_constants") or ""
    if on:
        home.setdefault("futurity_before", current)
    base = current if on else home.get("futurity_before", current)
    parts = [x for x in base.split(",") if x and not x.startswith("min_futurity=")]
    if on:
        parts.append("min_futurity=0")
    if parts:
        tv.sh("settings", "put", "global", "alarm_manager_constants", ",".join(parts))
    else:
        tv.sh("settings", "delete", "global", "alarm_manager_constants")
    save_record(tv.serial, record)


def install_home(tv, record, sdk, apk_arg):
    if sdk < 28:
        say("  Fire OS 6 o precedente: questo metodo non può prendere la Home. Salto.")
        return False
    if sdk >= 31:
        say("  Android 12 o successivo: questo metodo non è stato provato. Salto.")
        return False
    apk = find_apk(apk_arg)
    if apk is None:
        return False
    out = adb("-s", tv.serial, "install", "-r", str(apk), timeout=240)
    if "Success" not in out:
        say(f"  installazione di FireHome non riuscita: {out.strip().splitlines()[-1] if out.strip() else 'nessuna risposta'}")
        return False
    say("  FireHome installata")
    tv.sh("dumpsys", "deviceidle", "whitelist", f"+{APP_ID}")
    set_futurity(tv, record, True)
    set_guard(tv, record, True)

    user = tv.sh("am", "get-current-user").strip() or "0"
    bound = None
    for _ in range(15):
        bound = guard_bound(tv.sh("dumpsys", "accessibility"), user)
        if bound:
            break
        time.sleep(1)
    if bound is False:
        set_futurity(tv, record, False)
        set_guard(tv, record, False)
        say("  Fire OS non ha attivato il servizio Home: FireHome resta una normale app (in 'Le tue app').")
        return False

    # Prova vera: si porta in primo piano la Home di Amazon e si guarda se FireHome la sostituisce.
    tv.sh("input", "keyevent", "KEYCODE_WAKEUP")
    tv.sh("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME", "-p", "com.amazon.tv.launcher")
    for _ in range(8):
        time.sleep(1)
        if tv.focus().startswith(APP_ID):
            say("  prova superata: premendo Home si apre FireHome")
            return True
    say("  il servizio è attivo ma la prova non ha mostrato FireHome in primo piano: premi Home sul telecomando per controllare.")
    return True


def undo_home(tv, record):
    home = record["home"]
    tv.sh("am", "force-stop", APP_ID)
    if "futurity_before" in home:
        set_futurity(tv, record, False)
    if "a11y_before" in home:
        set_guard(tv, record, False)
        before = home["a11y_before"]
        put_secure(tv, "enabled_accessibility_services", before)
        put_secure(tv, "accessibility_enabled", home.get("a11y_enabled_before", "0"))
    tv.sh("dumpsys", "deviceidle", "whitelist", f"-{APP_ID}")
    tv.sh("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")


# --- YouTube senza pubblicità: SmartTube --------------------------------------

def download_verified(url, dest, sha256):
    if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest() == sha256:
        return
    say("  scarico SmartTube (circa 35 MB)...")
    STATE_DIR.mkdir(exist_ok=True)
    try:
        with urllib.request.urlopen(url, timeout=180) as r, open(dest, "wb") as f:
            shutil.copyfileobj(r, f)
    except Exception as e:
        die(f"Non riesco a scaricare {url} ({e}).")
    if hashlib.sha256(dest.read_bytes()).hexdigest() != sha256:
        dest.unlink()
        die("Il file scaricato non corrisponde al controllo SHA-256 previsto: non lo installo.")
    say("  controllo SHA-256 riuscito")


def version_code(tv, pkg):
    out = tv.sh("dumpsys", "package", pkg)
    m = re.search(r"versionCode=(\d+)", out)
    return int(m.group(1)) if m else 0


def install_youtube(tv, record):
    """SmartTube (open source, nessun codice per le pubblicità) al posto dell'app YouTube di Amazon."""
    say("YouTube senza pubblicità (SmartTube)")
    apk = STATE_DIR / "SmartTube.apk"
    download_verified(SMARTTUBE_URL, apk, SMARTTUBE_SHA256)
    for attempt in (1, 2):  # su Wi-Fi la sessione può cadere dopo il trasferimento, con l'app comunque installata
        if version_code(tv, SMARTTUBE) >= SMARTTUBE_VERSION_CODE:
            break
        adb("-s", tv.serial, "install", "-r", str(apk), timeout=600)
    if version_code(tv, SMARTTUBE) < SMARTTUBE_VERSION_CODE:
        say("  installazione di SmartTube non riuscita: riprova.")
        return False
    say("  SmartTube installata")
    enabled, disabled = tv.packages("-e"), tv.packages("-d")
    if STOCK_YOUTUBE in enabled:
        record["before"].setdefault(STOCK_YOUTUBE, "enabled")
        save_record(tv.serial, record)
        tv.sh("pm", "disable-user", "--user", "0", STOCK_YOUTUBE)
        say("  YouTube di Amazon disattivata (si riattiva con 'restore')")
    say("  Una volta sola, col telecomando, in SmartTube: menu a sinistra > Impostazioni > Generale > Nascondi contenuti >")
    say("  'Nascondi Shorts ovunque'; e Impostazioni > Player > Preset video > 1080p 60fps vp9 (mai av01).")
    return True


# --- utilità -----------------------------------------------------------------

def free_kb(tv):
    out = tv.sh("df", "-k", "/data").strip().splitlines()
    numbers = [int(x) for x in out[-1].split() if x.isdigit()] if out else []
    return numbers[2] if len(numbers) >= 3 else None  # colonne: totale, usato, disponibile


def mem_kb(tv, field):
    m = re.search(rf"^{field}:\s+(\d+) kB", tv.sh("cat", "/proc/meminfo"), re.M)
    return int(m.group(1)) if m else None


def megabytes(kb):
    return "?" if kb is None else f"{kb / 1024:.0f} MB"


def run_status(tv, sections):
    record = load_record(tv.serial)
    say(f"  memoria libera: {megabytes(mem_kb(tv, 'MemAvailable'))} su {megabytes(mem_kb(tv, 'MemTotal'))}")
    say(f"  spazio libero per le app: {megabytes(free_kb(tv))}")
    if record is None:
        say("  pulizia: non ancora eseguita")
        return
    disabled = tv.packages("-d")
    done = [p for p, state in record["before"].items() if state == "enabled" and p in disabled]
    say(f"  app Amazon disattivate: {len(done)}; protette e silenziate: {len(record['appops'])}")
    installed = APP_ID in tv.packages("-e")
    bound = GUARD in (tv.setting("secure", "enabled_accessibility_services") or "")
    say(f"  FireHome: {'installata' if installed else 'non installata'}; servizio Home: {'attivo' if bound else 'spento'}")
    say(f"  YouTube senza pubblicità (SmartTube): {'sì' if SMARTTUBE in tv.packages('-e') else 'no'}")


def pictures_dir():
    try:
        out = subprocess.run(["xdg-user-dir", "PICTURES"], capture_output=True, text=True, timeout=5).stdout.strip()
        if out:
            return Path(out)
    except (OSError, subprocess.TimeoutExpired):
        pass
    return Path.home() / "Pictures"


def run_screenshot(tv):
    r = subprocess.run([ADB, "-s", tv.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=60)
    if not r.stdout.startswith(b"\x89PNG"):
        die("La Fire Stick non ha restituito un'immagine. Riprova.")
    folder = pictures_dir()
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"firestick-{datetime.datetime.now():%Y%m%d-%H%M%S}.png"
    path.write_bytes(r.stdout)
    say(f"Schermata salvata: {path}")
    say("(Netflix e Prime Video escono neri: è la protezione dei contenuti.)")


def run_cache(tv):
    before = free_kb(tv)
    tv.sh("pm", "trim-caches", "4G", timeout=120)
    after = free_kb(tv)
    say(f"Spazio libero: {megabytes(before)} -> {megabytes(after)}")
    if before is not None and after is not None and after <= before:
        say("Niente da liberare (o Fire OS non ha permesso la pulizia).")


def run_install(tv, files):
    apks = [Path(f) for f in files]
    missing = [str(f) for f in apks if not f.is_file()]
    if not apks or missing:
        die(f"Indica uno o più file .apk esistenti (mancano: {', '.join(missing) or 'nessuno indicato'}).")
    for apk in apks:
        out = adb("-s", tv.serial, "install", "-r", str(apk), timeout=600)
        say(f"  {apk.name}: {'installata' if 'Success' in out else 'NON riuscita: ' + out.strip().splitlines()[-1]}")


def run_hdmi(tv):
    """Sola lettura: impostazioni CEC e righe recenti del log, per capire perché la TV (Anynet+) scollega la Fire Stick."""
    lines = ["Impostazioni HDMI-CEC lette dalla Fire Stick (non presente = Fire OS usa un'altra voce)"]
    for key in ("hdmi_control_enabled", "hdmi_control_auto_wakeup_enabled", "hdmi_control_auto_device_off_enabled"):
        lines.append(f"  {key} = {tv.setting('global', key) or '(non presente)'}")
    lines += ["", "dumpsys hdmi_control (inizio):"] + tv.sh("dumpsys", "hdmi_control").splitlines()[:80]
    log = [l for l in tv.sh("logcat", "-d", "-t", "3000").splitlines() if re.search(r"cec|hdmi|anynet", l, re.I)]
    lines += ["", f"Log recente su CEC/HDMI ({len(log[-200:])} righe):"] + log[-200:]
    Path("fire-hdmi.txt").write_text("\n".join(lines) + "\n")
    say("Salvato fire-hdmi.txt. Se il problema succede mentre usi la voce, lancia questo comando SUBITO dopo.")
    say()
    say("Cause note con Samsung (Anynet+) e come provare, dalla più semplice:")
    say("  1. Alimentazione: se la Fire Stick è alimentata dalla USB della TV (circa 0,5 A, ne serve 1 A), usa l'alimentatore a muro.")
    say("  2. Porta HDMI: prova HDMI 1, di solito la più compatibile con il CEC.")
    say("  3. Sulla TV: Impostazioni > Generali > Gestione dispositivi esterni > Anynet+ (HDMI-CEC): spegni, riavvia la TV, riaccendi.")
    say("  4. Sulla Fire Stick: Impostazioni > Display e suoni > Controllo dispositivo HDMI CEC: spegni, riavvia, riaccendi.")
    say("  5. Se è iniziato dopo un aggiornamento della TV, controlla se ce n'è uno più recente.")
    say("  Spegnere il CEC toglie l'accensione e lo spegnimento sincronizzati con la TV: provalo per capire se è lui la causa.")


def run_discover_live(tv):
    """Sola lettura: cerca nelle app dei canali gli schemi e gli indirizzi che aprono una diretta."""
    keys = ("raiplay", "rainet", "mediaset", "infinity", "la7")
    pkgs = sorted(p for p in tv.packages("-e") if any(k in p.lower() for k in keys))
    if not pkgs:
        die("Non trovo RaiPlay, Mediaset Infinity o La7: installale dall'Appstore e riprova.")
    lines = []
    for p in pkgs:
        lines.append(f"=== {p}")
        dump = tv.sh("dumpsys", "package", p).splitlines()
        lines += [l.rstrip() for l in dump if re.search(r"Scheme:|Authority:|Path:|android.intent.action.VIEW", l)]
    Path("fire-live-discovery.txt").write_text("\n".join(lines) + "\n")
    say(f"Salvato fire-live-discovery.txt ({len(pkgs)} app: {', '.join(pkgs)}).")
    say("Mandamelo: con quegli indirizzi posso collegare ogni tile a un canale preciso (campo \"url\" in channels.json).")


# --- comandi ----------------------------------------------------------------

def write_report(tv, known, keep, extra=None):
    """Elenca i pacchetti Amazon ancora attivi che né l'elenco né [keep] conoscono, da rivedere con calma."""
    rest = sorted(p for p in tv.packages("-e")
                  if p.startswith("com.amazon.") and p not in known and not is_kept(p, keep))
    lines = ["Pacchetti Amazon ancora attivi e non presenti nell'elenco:"] + rest + (extra or [])
    REPORT.write_text("\n".join(lines) + "\n")
    say(f"  report: {REPORT} (pacchetti Amazon ancora attivi e sconosciuti: {len(rest)})")


def preflight(tv):
    manufacturer = tv.prop("ro.product.manufacturer")
    if manufacturer.lower() != "amazon":
        die(f"Questa non sembra una Fire Stick (produttore: {manufacturer or 'sconosciuto'}).")
    sdk_text = tv.prop("ro.build.version.sdk")
    sdk = int(sdk_text) if sdk_text.isdigit() else 28
    say(f"Trovata: {tv.prop('ro.product.model')} - Android {tv.prop('ro.build.version.release')} (sdk {sdk}) - build {tv.prop('ro.build.display.id')}")
    return sdk


def run_clean(tv, sections, args, sdk):
    keep = sections.get("keep", [])
    record = new_record(tv.serial)
    say("[1/3] App Amazon inutili (telemetria, pubblicità, shopping)")
    refused = clean_packages(tv, record, wanted(sections, not args.tv, args.extras), not args.tv, keep)
    say("[2/3] Pacchetti protetti: niente esecuzione in background")
    silence_protected(tv, record, sdk, sections.get("protected", []), refused, keep)
    say("[3/3] Impostazioni su pubblicità e dati")
    apply_settings(tv, record, sdk)
    write_report(tv, wanted(sections, True, True), keep)
    if tv.prop("ro.adb.secure") == "0":
        say("Attenzione: questa Fire Stick accetta ADB da qualunque dispositivo in rete. Quando hai finito esegui 'adb-off'.")


def run_home(tv, sections, args, sdk):
    record = new_record(tv.serial)
    say("FireHome come schermata Home")
    return install_home(tv, record, sdk, args.apk)


def run_check(tv, sections, args):
    record = load_record(tv.serial)
    if record is None:
        say("Non hai ancora eseguito la pulizia su questa Fire Stick: non c'è niente da confrontare.")
        return 2
    enabled = tv.packages("-e")
    back = [p for p in wanted(sections, not args.tv, args.extras) if p in enabled and p not in set(record["refused"])]
    problems = []
    if record["home"].get("a11y_before") is not None and GUARD not in (tv.setting("secure", "enabled_accessibility_services") or ""):
        problems.append("il servizio Home di FireHome è stato spento")
    if back:
        problems.append(f"pacchetti riattivati da Amazon: {len(back)}")
    if not problems:
        say("Tutto a posto: nessun pacchetto riattivato e la Home è ancora FireHome.")
        return 0
    say("Da sistemare: " + "; ".join(problems) + ". Esegui di nuovo il comando principale.")
    for p in back:
        say(f"  {p}")
    return 1


def run_restore(tv, sections):
    record = load_record(tv.serial)
    if not record:
        say("Nessun registro per questa Fire Stick: riattivo tutto l'elenco.")
        record = new_record(tv.serial)
        record["before"] = {p: "enabled" for p in wanted(sections, True, True)}
    undo_home(tv, record)
    for slot, old in record["settings"].items():
        namespace, key = slot.split(" ", 1)
        if old is None:
            tv.sh("settings", "delete", namespace, key)
        else:
            tv.sh("settings", "put", namespace, key, old)
    for p in record["appops"]:
        for op in ("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"):
            tv.sh("appops", "set", p, op, "default")
    disabled = tv.packages("-d")
    targets = [p for p, state in record["before"].items() if state == "enabled" and p in disabled]
    for p in targets:
        tv.sh("pm", "enable", "--user", "0", p)
    left = [p for p in targets if p in tv.packages("-d")]
    if SMARTTUBE in tv.packages("-e"):
        say("SmartTube resta installata (per toglierla: adb uninstall org.smarttube.stable).")
    say(f"Ripristinato: {len(targets) - len(left)} pacchetti, {len(record['settings'])} impostazioni, Home Amazon.")
    for p in left:
        say(f"  non riattivato: {p}")


def main():
    for stream in (sys.stdout, sys.stderr):  # la console di Windows può non reggere le lettere accentate
        stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", nargs="?", default="all", help="all (predefinito), clean, home, check, restore, adb-off, status, screenshot, cache, install, hdmi, discover-live")
    parser.add_argument("target", nargs="?", default="auto", help="'usb', un IP, oppure niente per cercarla da solo")
    parser.add_argument("more", nargs="*", help="altri file .apk (solo per 'install')")
    parser.add_argument("--youtube", action="store_true", help="installa SmartTube (YouTube senza pubblicità) e disattiva YouTube di Amazon")
    parser.add_argument("--tv", action="store_true", help="è una TV con Fire OS integrato, non una Fire Stick: non toglie la scheda Live")
    parser.add_argument("--extras", action="store_true", help="toglie anche Alexa extra (timer, multi-room), Silk e Ambient")
    parser.add_argument("--no-home", action="store_true", help="non installare FireHome e non prendere la Home")
    parser.add_argument("--apk", help="usa questo FireHome.apk invece di scaricare l'ultimo")
    parser.add_argument("--reboot", action="store_true", help="riavvia la Fire Stick alla fine")
    args = parser.parse_args()
    if args.command not in COMMANDS:  # 'firestick.py 192.168.1.50' = comando predefinito su quell'IP
        args.command, args.target = "all", args.command
    files = []
    if args.command == "install":  # 'install a.apk b.apk': tutti gli argomenti sono file
        files, args.target = [args.target] + args.more, "auto"

    global ADB
    ADB = find_adb()
    tv = Tv(connect(args.target))
    sdk = preflight(tv)
    sections = load_sections()

    if args.command in ("all", "clean"):
        run_clean(tv, sections, args, sdk)
    if args.youtube and args.command in ("all", "clean"):
        install_youtube(tv, new_record(tv.serial))
    if args.command == "home" or (args.command == "all" and not args.no_home):
        run_home(tv, sections, args, sdk)
    if args.command == "check":
        sys.exit(run_check(tv, sections, args))
    if args.command == "restore":
        run_restore(tv, sections)
    if args.command == "status":
        run_status(tv, sections)
    if args.command == "screenshot":
        run_screenshot(tv)
    if args.command == "cache":
        run_cache(tv)
    if args.command == "install":
        run_install(tv, files)
    if args.command == "hdmi":
        run_hdmi(tv)
    if args.command == "discover-live":
        run_discover_live(tv)
    if args.command == "adb-off":
        tv.sh("settings", "put", "global", "adb_enabled", "0")
        say("Debug ADB spento. Per riaccenderlo: Impostazioni > My Fire TV > Opzioni sviluppatore.")
    if args.command in ("all", "clean", "home"):
        say("\nFatto. Per tornare indietro: python3 scripts/firestick.py restore")
        if args.reboot:
            tv.sh("svc", "power", "reboot")


if __name__ == "__main__":
    main()
