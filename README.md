# FireHome

Una Home leggera per Fire TV Stick (Fire OS 5/7/8) al posto di quella Amazon: orologio, **TV Live**, le tue app, Impostazioni. Niente pubblicità, niente suggerimenti, niente altro. Alexa continua a funzionare (è del sistema, non della Home).

> **Non è un sistema operativo nuovo.** Il bootloader delle Fire Stick è bloccato da Amazon, quindi l'OS non si può sostituire. FireHome è una Home (launcher) più uno script ADB che disattiva le app Amazon inutili: il risultato pratico è simile, ma resta Fire OS sotto.
>
> Non funziona sui modelli nuovi con **Vega OS** (non Android: niente APK).

## Cosa c'è

| Schermata | Cosa fa |
|---|---|
| Home | Orologio, **TV Live**, **Ultimo canale**, **Duplica schermo** (tablet → TV), Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, Tutte le app, Impostazioni. Compaiono solo le app installate. |
| TV Live | Due gruppi: *App ufficiali* (RaiPlay, Mediaset Infinity, La7: si aprono con un click) e i canali della tua `playlist.m3u`, che partono al volo nel player. |
| Player | Su/Giù cambia canale, OK mostra il nome, Indietro esce. |
| Tutte le app | Via di fuga: qualsiasi app installata. |

## Installazione

1. **Compila l'APK**: ogni push su GitHub lo costruisce in *Actions → Build APK → Artifacts → FireHome-apk*.
   Per un link diretto: crea un tag (`git tag v0.1.0 && git push --tags`) e l'APK appare nelle *Releases*.
2. **Mettilo sulla Fire Stick**: installa l'app *Downloader* dall'Appstore, abilita *Impostazioni → My Fire TV → Opzioni sviluppatore → Installa app sconosciute → Downloader*, poi incolla il link dell'APK in Release.
   (Alternativa: `adb connect <ip>:5555 && adb install FireHome.apk`.)
3. Aprilo da *Le tue app*. Se qualcosa non va, prova `FireHome-debug.apk`.

### Il tasto Home

Qui Amazon mette ostacoli: da certi aggiornamenti di Fire OS 7 il tasto Home torna sempre alla Home Amazon e blocca le app che lo aggirano ([AFTVnews](https://www.aftvnews.com/amazon-blocks-custom-home-launchers-with-latest-fire-tv-software-update/)). Cosa fare:

1. Usa FireHome come app: funziona sempre.
2. Esperimento (non garantito su tutte le versioni): `./scripts/fire.sh home-off <ip>` disattiva la Home Amazon, così Home passa a FireHome. **Prima** verifica che da FireHome *Impostazioni* si apra. Se qualcosa va storto: `./scripts/fire.sh home-on <ip>`.

## Togliere il bloatware

Un solo comando, senza domande:

```bash
./scripts/fire.sh clean usb        # Fire Stick collegata col cavo micro-USB al PC
./scripts/fire.sh clean 192.168.1.50   # oppure via Wi-Fi, con l'IP della Fire Stick
```

Lo script rimuove le app Amazon dell'elenco `BLOAT` in `scripts/fire.sh` (shopping, GameCircle, Kindle, Audible, Amazon Music e Photos, IMDb/Freevee, note legali e di rilascio, assistenza clienti). Non tocca Alexa, Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, RaiPlay, Mediaset Infinity o La7. Poi scrive `fire-report.txt` con gli altri pacchetti Amazon rimasti, da guardare con calma.

Per tornare indietro: `./scripts/fire.sh restore usb` (o l'IP).

**Limiti da sapere:**
- Gli elenchi dei nomi vengono dalla community e non li ho potuti verificare su una Fire Stick vera. Un nome sbagliato viene saltato, non fa danni.
- Le pubblicità della Home le toglie solo FireHome (sostituisce la Home Amazon).
- Gli interruttori della privacy (*Pubblicità basata sugli interessi*, *Dati di utilizzo*) non si cambiano da ADB in modo verificato: falli a mano in *Impostazioni → Preferenze*.
- Gli aggiornamenti Amazon possono riattivare le app: rilancia `clean` dopo ogni aggiornamento.

**Collegamento USB.** Il micro-USB della Fire Stick è pensato per l'alimentazione, ma accetta anche ADB con un PC. Su Windows spesso servono driver aggiuntivi: se non va, usa il Wi-Fi.

## TV Live

Il progetto **non include flussi** di Rai, Mediaset o La7: sono ritrasmissioni non autorizzate, e non li fornisco né cerco. I tre canali *App ufficiali* aprono le app ufficiali; se una non è installata, il tile la segnala con "Installa dall'Appstore".

Per avere i canali al volo nel player, usa una **playlist M3U** di una fonte di cui hai i diritti, per esempio un abbonamento IPTV regolare che ti fornisce il link. Il file va così:

```
#EXTM3U
#EXTINF:-1 group-title="Generali",Nome canale
https://fonte-che-hai-diritto.esempio/live.m3u8
#EXTINF:-1 group-title="Film",Altro canale
https://fonte-che-hai-diritto.esempio/altro.mpd
```

Salvala come `playlist.m3u` e caricala sulla Fire Stick:

```bash
adb push playlist.m3u /sdcard/Android/data/app.firehome/files/playlist.m3u
```

Riapri FireHome: i canali compaiono in TV Live, raggruppati come `group-title`. Su/Giù cambia canale nel player. (Il percorso `/sdcard/Android/data/...` va verificato sulla tua versione di Fire OS: se `adb push` risponde *Permission denied*, dimmelo.)

Per modificare i tre canali ufficiali, usa `channels.json` (anche questo si carica con `adb push` nella stessa cartella).

## Personalizzare le app

In `Apps.kt` la lista `SHORTCUTS`: nome, colore e come riconoscere l'app (package esatto oppure parte del nome). Se BuddyFit non compare, controlla il nome in *Tutte le app* e correggi la ricerca.

## Struttura

```
app/src/main/kotlin/app/firehome/
  MainActivity.kt   schermate e navigazione
  Ui.kt             Home, TV Live, Tutte le app (Compose)
  Player.kt         player Media3/ExoPlayer
  Channels.kt       lettura di channels.json
  Apps.kt           app installate e scorciatoie
scripts/fire.sh     clean / restore / home-on / home-off via ADB (USB o Wi-Fi)
.github/workflows/  build dell'APK
```

## Stato

Scritto senza poter compilare né provare su un dispositivo reale (l'ambiente di sviluppo non ha l'Android SDK): la prima build su GitHub Actions è il vero banco di prova. Da verificare sulla Fire Stick: focus del telecomando, nomi dei pacchetti delle app, comportamento del tasto Home.
