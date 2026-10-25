#!/usr/bin/env bash
# Pulizia della Fire Stick via ADB, senza domande.
# Uso: ./scripts/fire.sh <clean|restore|home-off|home-on> <usb | ip-fire-stick>
#
#   clean     rimuove le app Amazon dell'elenco BLOAT e salva fire-report.txt
#   restore   rimette tutte le app rimosse
#   home-off  disattiva la Home Amazon (esperimento, vedi README)
#   home-on   riattiva la Home Amazon
#
# Prima sulla Fire Stick: Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB = ON.
# Via USB: collega il cavo micro-USB al PC e accetta il messaggio che compare sulla TV.
# Via Wi-Fi: usa l'IP che trovi in Impostazioni > Dispositivo > Info > Rete.
#
# Reversibile: le app vengono nascoste solo per l'utente 0 (pm uninstall -k --user 0),
# quindi "restore" le ripristina senza reinstallare nulla. Alexa, Netflix, Prime Video,
# Disney+, Jellyfin, BuddyFit, RaiPlay, Mediaset e La7 non compaiono nell'elenco e non vengono toccate.
set -euo pipefail

CMD="${1:-}"
TARGET="${2:-}"
[[ -n $CMD && -n $TARGET ]] || { sed -n '2,4p' "$0"; exit 1; }

# Solo questi pacchetti vengono rimossi. Sono nomi che conosco dalla community:
# se un nome non esiste sulla tua Fire Stick viene semplicemente saltato.
BLOAT=(
  com.amazon.alexashopping        # Alexa Shopping (non è l'assistente vocale)
  com.amazon.ags.app              # GameCircle
  com.amazon.tv.csapp             # Assistenza clienti
  com.amazon.tv.legal.notices     # Note legali
  com.amazon.tv.releasenotes      # Note di rilascio
  com.amazon.imdb.tv.android.app  # Freevee / IMDb TV
  com.amazon.bueller.music        # Amazon Music
  com.amazon.bueller.photos       # Amazon Photos
  com.amazon.kindle               # Kindle
  com.audible.application         # Audible
)

HOME_PKG=com.amazon.tv.launcher
REPORT=fire-report.txt

if [[ $TARGET == usb ]]; then
  SERIAL=$(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /:/ { print $1; exit }')
  [[ -n $SERIAL ]] || { echo "Nessuna Fire Stick trovata via USB: controlla il cavo e Debug ADB."; exit 1; }
else
  adb connect "$TARGET:5555" | grep -q connected || { echo "Connessione ADB fallita verso $TARGET"; exit 1; }
  SERIAL="$TARGET:5555"
fi

adbsh() { adb -s "$SERIAL" shell "$@" | tr -d '\r'; }
INSTALLED=$(adbsh pm list packages)
installed() { grep -qx "package:$1" <<<"$INSTALLED"; }

clean() {
  for p in "${BLOAT[@]}"; do
    if ! installed "$p"; then echo "assente  $p"; continue; fi
    if adbsh pm uninstall -k --user 0 "$p" | grep -q Success; then
      echo "rimosso  $p"
    else
      echo "errore   $p"
    fi
  done
  report
}

# Elenca tutti i pacchetti Amazon che restano, così li puoi guardare con calma senza che lo script ti chieda nulla.
report() {
  {
    echo "Pacchetti Amazon ancora installati, da rivedere con calma:"
    adbsh pm list packages | grep -E 'package:com\.amazon\.' | sed 's/^package://' | sort || true
  } > "$REPORT"
  echo "Report salvato in $REPORT ($(($(wc -l < "$REPORT") - 1)) pacchetti Amazon ancora presenti)"
}

case "$CMD" in
  clean)
    clean ;;
  restore)
    for p in "${BLOAT[@]}"; do
      adbsh cmd package install-existing --user 0 "$p" >/dev/null 2>&1 || true
    done
    adbsh pm enable "$HOME_PKG" >/dev/null
    echo "Ripristinato." ;;
  home-off)
    # ESPERIMENTO: disattiva la Home Amazon. Installa e prova FireHome PRIMA di farlo,
    # e controlla che "Impostazioni" si apra ancora. Se qualcosa va storto: home-on.
    adbsh pm disable-user --user 0 "$HOME_PKG" ;;
  home-on)
    adbsh pm enable "$HOME_PKG" >/dev/null
    echo "Home Amazon riattivata." ;;
  *)
    echo "Comando sconosciuto: $CMD"; exit 1 ;;
esac
