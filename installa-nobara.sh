#!/usr/bin/env bash
# Nobara Linux (e altre Fedora): installa gli strumenti, la regola USB, la voce di menu e il controllo settimanale.
# Si lancia una volta sola: ./installa-nobara.sh   (chiede la password di sudo per dnf e per la regola udev)
set -euo pipefail
REPO="$(cd "$(dirname "$0")" && pwd)"

echo "1/4 Strumenti (adb, Python, notifiche)"
sudo dnf install -y android-tools python3 libnotify

echo "2/4 Permesso USB per la Fire Stick (Amazon, vendor 1949), senza gruppi né root"
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="1949", TAG+="uaccess"' | sudo tee /etc/udev/rules.d/51-firestick.rules >/dev/null
sudo udevadm control --reload-rules
sudo udevadm trigger --subsystem-match=usb

echo "3/4 Voce nel menu delle applicazioni: 'Pulisci Fire Stick'"
mkdir -p "$HOME/.local/share/applications"
cat > "$HOME/.local/share/applications/firehome.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Pulisci Fire Stick
Comment=Pulisce la Fire Stick e installa FireHome
Exec=env FIREHOME_DESKTOP=1 "$REPO/pulisci-firestick.sh"
Icon=network-wireless
Terminal=true
Categories=Utility;
DESKTOP
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true

echo "4/4 Controllo settimanale: ti avvisa se Amazon riattiva qualcosa (serve il Debug ADB acceso)"
mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/firehome-check.service" <<UNIT
[Unit]
Description=FireHome: controlla se Amazon ha riattivato qualcosa

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'python3 "$REPO/scripts/firestick.py" check </dev/null; rc=\$?; [ \$rc -eq 1 ] && notify-send "Fire Stick" "Amazon ha riattivato qualcosa: rilancia Pulisci Fire Stick." ; exit 0'
UNIT
cat > "$HOME/.config/systemd/user/firehome-check.timer" <<UNIT
[Unit]
Description=FireHome: controllo settimanale

[Timer]
OnCalendar=weekly
Persistent=true
RandomizedDelaySec=1h

[Install]
WantedBy=timers.target
UNIT
systemctl --user daemon-reload
systemctl --user enable --now firehome-check.timer

echo
echo "Fatto. Apri 'Pulisci Fire Stick' dal menu, oppure: ./pulisci-firestick.sh"
echo "(Se hai collegato la Fire Stick via USB prima di questo passaggio, scollega e ricollega il cavo.)"
