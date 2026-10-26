#!/bin/bash
# Mac: doppio clic. Pulisce la Fire Stick e installa FireHome. Serve python3 (macOS lo propone da solo).
cd "$(dirname "$0")" || exit 1
python3 scripts/firestick.py "$@"
echo
read -r -p "Premi Invio per chiudere"
