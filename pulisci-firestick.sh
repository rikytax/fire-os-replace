#!/bin/sh
# Linux: ./pulisci-firestick.sh. Pulisce la Fire Stick e installa FireHome. Serve python3.
cd "$(dirname "$0")" || exit 1
exec python3 scripts/firestick.py "$@"
