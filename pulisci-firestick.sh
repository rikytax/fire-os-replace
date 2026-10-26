#!/usr/bin/env bash
# Nobara Linux: ./pulisci-firestick.sh [opzioni]. Pulisce la Fire Stick e installa FireHome. Vedi README.
cd "$(dirname "$0")" || exit 1
python3 scripts/firestick.py "$@"
status=$?
# Aperto dal menu delle applicazioni il terminale si chiuderebbe subito: si aspetta un tasto.
if [ -n "${FIREHOME_DESKTOP:-}" ]; then echo; read -r -p "Premi Invio per chiudere"; fi
exit $status
