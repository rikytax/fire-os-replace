#!/usr/bin/env python3
"""Pulizia della Fire Stick via ADB, senza domande.

  python3 scripts/firestick.py clean   <usb|IP> --stick     disattiva telemetria, pubblicità e app inutili
  python3 scripts/firestick.py check   <usb|IP> --stick     elenca cosa Amazon ha riattivato (dopo un aggiornamento)
  python3 scripts/firestick.py restore <usb|IP>             rimette tutto com'era prima di "clean"
  python3 scripts/firestick.py adb-off <usb|IP>             spegne il Debug ADB (chiude la porta 5555)

Prima sulla Fire Stick: Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB = ON.
Le app vengono solo disattivate per l'utente 0 (pm disable-user): niente viene disinstallato.
L'elenco dei pacchetti è in firestick-bloat.txt.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

LIST_FILE = Path(__file__).resolve().parent / "firestick-bloat.txt"
STATE_DIR = Path.home() / ".firestick-clean"
REPORT = Path("fire-report.txt")
BATCH = 8

PKG_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
# Pacchetti Android di base: senza, il sistema può non avviarsi più.
CORE_RE = re.compile(
    r"^(android"
    r"|com\.android\.(systemui|shell|phone|settings|tv\.settings|providers\..+)"
    r"|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)"
    r"|com\.google\.android\.overlay\.modules.*)$"
)
# I servizi che hai chiesto di tenere: nessun pacchetto con questi nomi viene toccato, qualunque cosa dica l'elenco.
KEEP_TOKENS = ("netflix", "firebat", "disney", "jellyfin", "buddyfit", "mediaset", "la7", "raiplay", "rainet")


def die(message, code=2):
    print(f"Errore: {message}", file=sys.stderr)
    sys.exit(code)


def adb(*args):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=60)
    except subprocess.TimeoutExpired:
        die("La Fire Stick non risponde.")
    return (r.stdout + r.stderr).replace("\r", "")


def connect(target):
    if shutil.which("adb") is None:
        die("adb non trovato: installa 'Android platform-tools' (vedi README) e riprova.")
    if target == "usb":
        rows = [line.split() for line in adb("devices").splitlines()[1:] if line.strip()]
        usb = [r for r in rows if ":" not in r[0]]  # i dispositivi di rete hanno la forma IP:porta
        ready = [r[0] for r in usb if r[1] == "device"]
        if ready:
            return ready[0]
        if any(r[1] == "unauthorized" for r in usb):
            die("Accetta 'Consenti debug USB?' sulla TV (spunta 'Consenti sempre') e riprova.")
        die("Nessuna Fire Stick via USB: controlla il cavo e il Debug ADB. Su Windows servono i driver: se non va, usa il Wi-Fi.")
    serial = target if ":" in target else f"{target}:5555"
    out = adb("connect", serial)
    if "connected" not in out:
        die(f"Connessione a {serial} fallita: {out.strip()}")
    if adb("-s", serial, "get-state").strip() != "device":
        die("Accetta 'Consenti debug USB?' sulla TV (spunta 'Consenti sempre') e riprova.")
    return serial


class Tv:
    def __init__(self, serial):
        self.serial = serial

    def sh(self, *cmd):
        return adb("-s", self.serial, "shell", *cmd)

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


# --- elenchi ---------------------------------------------------------------

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


# --- registro dello stato prima della pulizia ------------------------------

def record_path(serial):
    return STATE_DIR / (re.sub(r"[^A-Za-z0-9_.-]", "_", serial) + ".json")


def load_record(serial):
    path = record_path(serial)
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except ValueError:
        die(f"Il registro {path} è rovinato: non lo sovrascrivo. Controllalo o spostalo.")


def save_record(serial, pkgs, enabled, disabled):
    """Scrive com'erano i pacchetti PRIMA di toccarli. Una seconda esecuzione non sovrascrive la prima."""
    record = load_record(serial) or {"before": {}, "refused": []}
    for p in pkgs:
        state = "enabled" if p in enabled else "disabled" if p in disabled else "absent"
        if record["before"].get(p, "absent") == "absent":
            record["before"][p] = state  # un pacchetto assente allora e attivo ora si ripristina come attivo
    STATE_DIR.mkdir(exist_ok=True)
    record_path(serial).write_text(json.dumps(record, indent=1, sort_keys=True))
    return record


# --- comandi ---------------------------------------------------------------

def clean(tv, pkgs, stick, keep):
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
            skipped[p] = "gestisce gli ingressi TV (su una Fire Stick usa --stick)"

    todo = [p for p in pkgs if p in enabled and p not in skipped]
    save_record(tv.serial, pkgs, enabled, disabled)  # prima di cambiare qualsiasi cosa

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
    record = load_record(tv.serial)
    record["refused"] = sorted(set(record.get("refused", [])) | set(refused))
    record_path(tv.serial).write_text(json.dumps(record, indent=1, sort_keys=True))

    print(f"Disattivati: {len(done)}")
    for p in done:
        print(f"  {p}")
    print(f"Assenti su questa Fire Stick o già disattivati: {len([p for p in pkgs if p not in todo and p not in skipped])}")
    for p, why in skipped.items():
        print(f"Saltato {p}: {why}")
    if refused:
        print(f"Rifiutati da Fire OS ({len(refused)}), pacchetti protetti: è normale.")
        for p in refused:
            print(f"  {p}")
    if todo and not done:
        die("Fire OS ha rifiutato tutto: questo firmware (es. Fire OS 5) non permette di disattivare i pacchetti.", 1)
    write_report(tv, wanted(load_sections(), True, True), keep)
    if tv.sh("getprop", "ro.adb.secure").strip() == "0":
        print("Attenzione: questa Fire Stick accetta ADB da qualunque dispositivo in rete. Quando hai finito esegui 'adb-off'.")


def write_report(tv, known, keep):
    """Elenca i pacchetti Amazon ancora attivi che né l'elenco né [keep] conoscono, da rivedere con calma."""
    rest = sorted(p for p in tv.packages("-e")
                  if p.startswith("com.amazon.") and p not in known and not is_kept(p, keep))
    REPORT.write_text("Pacchetti Amazon ancora attivi, non presenti nell'elenco:\n" + "\n".join(rest) + "\n")
    print(f"Report: {REPORT} (pacchetti Amazon ancora attivi e sconosciuti: {len(rest)})")


def check(tv, pkgs):
    record = load_record(tv.serial)
    if record is None:
        print("Non hai ancora eseguito 'clean' su questa Fire Stick: non c'è niente da confrontare.")
        return 2
    ignore = set(record["refused"])
    back = [p for p in pkgs if p in tv.packages("-e") and p not in ignore]
    if not back:
        print("Tutto a posto: nessun pacchetto è stato riattivato.")
        return 0
    print(f"Riattivati da Amazon ({len(back)}): esegui di nuovo 'clean'.")
    for p in back:
        print(f"  {p}")
    return 1


def restore(tv):
    disabled = tv.packages("-d")
    record = load_record(tv.serial)
    if record:
        targets = [p for p, state in record["before"].items() if state == "enabled"]
    else:
        print("Nessun registro per questa Fire Stick: riattivo tutto l'elenco.")
        targets = wanted(load_sections(), True, True)
    targets = [p for p in targets if p in disabled]
    for p in targets:
        tv.sh("pm", "enable", "--user", "0", p)
    left = [p for p in targets if p in tv.packages("-d")]
    print(f"Riattivati: {len(targets) - len(left)}")
    for p in left:
        print(f"  non riattivato: {p}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["clean", "check", "restore", "adb-off"])
    parser.add_argument("target", help="'usb' (cavo micro-USB) oppure l'IP della Fire Stick")
    parser.add_argument("--stick", action="store_true", help="è una Fire Stick (non una TV Fire Edition): toglie anche la scheda Live")
    parser.add_argument("--extras", action="store_true", help="toglie anche Alexa extra (timer, multi-room), Silk e Ambient")
    args = parser.parse_args()

    tv = Tv(connect(args.target))
    sections = load_sections()
    pkgs = wanted(sections, args.stick, args.extras)
    if args.command == "clean":
        clean(tv, pkgs, args.stick, sections.get("keep", []))
    elif args.command == "check":
        sys.exit(check(tv, pkgs))
    elif args.command == "restore":
        restore(tv)
    else:
        tv.sh("settings", "put", "global", "adb_enabled", "0")
        print("Debug ADB spento. Per riaccenderlo: Impostazioni > My Fire TV > Opzioni sviluppatore.")


if __name__ == "__main__":
    main()
