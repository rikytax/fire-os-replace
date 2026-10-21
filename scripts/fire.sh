#!/usr/bin/env bash
# Gestione della Fire Stick via ADB.
# Uso: ./scripts/fire.sh <debloat|restore|home-off|home-on|packages> <ip-fire-stick>
#
# Prima: Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB = ON.
# Tutto è reversibile: i pacchetti sono rimossi solo per l'utente 0 (--user 0, con -k),
# quindi "restore" li ripristina senza reinstallare nulla.
set -u

CMD="${1:-}"
IP="${2:-}"
[ -n "$CMD" ] && [ -n "$IP" ] || { sed -n '2,3p' "$0"; exit 1; }

# Elenco prudente, raccolto dalla community: NON è stato verificato su ogni modello.
# I pacchetti assenti vengono semplicemente saltati. Alexa, Wi-Fi e Impostazioni non sono toccati.
BLOAT=(
  com.amazon.alexashopping        # Alexa Shopping
  com.amazon.ags.app              # GameCircle
  com.amazon.tv.csapp             # Assistenza clienti
  com.amazon.tv.legal.notices     # Note legali
  com.amazon.tv.releasenotes      # Note di rilascio
  com.amazon.imdb.tv.android.app  # Freevee / IMDb TV
  com.amazon.bueller.music        # Amazon Music
  com.amazon.bueller.photos       # Amazon Photos
)

HOME_PKG=com.amazon.tv.launcher

adb connect "$IP:5555" >/dev/null || { echo "Connessione ADB fallita"; exit 1; }
sh() { adb -s "$IP:5555" shell "$@" | tr -d '\r'; }
has() { sh pm list packages | grep -qx "package:$1"; }

case "$CMD" in
  debloat)
    for p in "${BLOAT[@]}"; do
      if has "$p"; then sh pm uninstall -k --user 0 "$p" | sed "s|^|$p: |"; else echo "$p: assente, salto"; fi
    done ;;
  restore)
    for p in "${BLOAT[@]}"; do sh cmd package install-existing --user 0 "$p" | sed "s|^|$p: |"; done
    sh pm enable "$HOME_PKG" ;;
  home-off)
    # ESPERIMENTO: disattiva la Home Amazon. Installa e prova FireHome PRIMA di farlo,
    # e controlla che "Impostazioni" si apra ancora. Se qualcosa va storto: home-on.
    sh pm disable-user --user 0 "$HOME_PKG" ;;
  home-on)
    sh pm enable "$HOME_PKG" ;;
  packages)
    sh pm list packages | grep -i amazon | sort ;;
  *)
    echo "Comando sconosciuto: $CMD"; exit 1 ;;
esac
