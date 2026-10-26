# FireHome

Due strumenti per togliere il superfluo da una Fire TV Stick:

1. **La pulizia** (`scripts/firestick.py`): un comando che disattiva telemetria, pubblicità, shopping e app inutili di Amazon, lasciando Alexa, Prime Video, duplica schermo e le tue app. È la parte che funziona davvero su Fire OS.
2. **FireHome** (l'app): un hub leggero con TV Live, le tue app, Duplica schermo e Impostazioni. È un'**app**, non la Home di sistema.

> **Cosa non si può fare.** Né sostituire l'OS (bootloader bloccato) né sostituire la Home: Fire OS la riserva al launcher Amazon, che è un pacchetto *protetto*. Lo hanno provato su Fire Stick reali: `set-home-activity` risponde "Success" e il tasto Home porta comunque ad Amazon; `pm disable-user` sul launcher è rifiutato (`Cannot disable a protected package`) ([dettagli](https://github.com/Gathin23/cleanyourtv/blob/main/docs/TROUBLESHOOTING.md#fire-tv-fire-os-debloat-works-the-launcher-cannot-take-home)). Non funziona sui modelli nuovi con **Vega OS** (non Android).

## 1. Pulire la Fire Stick

### Prima, una volta sola

- Sulla Fire Stick: *Impostazioni → My Fire TV → Opzioni sviluppatore → Debug ADB* = ON. Se *Opzioni sviluppatore* manca: *Info*, premi OK sul nome del dispositivo 7 volte.
- Sul computer servono **Python 3** e **adb** ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools), si scompatta e basta).
- Collega la Fire Stick: **via Wi-Fi** (consigliato) con l'IP che trovi in *Info → Rete*, oppure **via USB** con il cavo micro-USB al PC. L'USB funziona, ma su Windows spesso servono driver aggiuntivi.
- La prima volta accetta "Consenti debug USB?" sulla TV e spunta "Consenti sempre".

### Il comando

```bash
python3 scripts/firestick.py clean 192.168.1.50 --stick     # via Wi-Fi
python3 scripts/firestick.py clean usb --stick              # via cavo USB
```

(Su Windows: `py scripts\firestick.py ...`.) Non fa domande. `--stick` serve perché una Fire Stick non ha sintonizzatore né ingressi HDMI, quindi si può togliere anche la scheda "Live" di Amazon; **su una TV con Fire OS integrato non usarlo**.

| Opzione | Cosa fa |
|---|---|
| `clean` | Disattiva i pacchetti dell'elenco e scrive `fire-report.txt` con gli altri pacchetti Amazon che restano, da guardare con calma. |
| `check` | Dice cosa Amazon ha riattivato (per esempio dopo un aggiornamento). Esce con 1 se qualcosa è tornato. |
| `restore` | Rimette tutto com'era **prima** di `clean`. |
| `adb-off` | Spegne il Debug ADB e chiude la porta di rete 5555. Fallo a lavoro finito. |
| `--extras` | Toglie anche Alexa oltre alla ricerca vocale (timer, musica, multi-room), Silk e Ambient. |

### Come funziona (e perché è prudente)

- **Disattiva, non disinstalla** (`pm disable-user --user 0`): tutto è reversibile.
- **Registro prima di toccare:** scrive in `~/.firestick-clean/` com'era ogni pacchetto. `restore` lo rilegge: ciò che avevi già disattivato tu resta disattivato. Una seconda esecuzione non sovrascrive il primo registro.
- **Protezioni:** non tocca mai la Home Amazon, le Impostazioni, i pacchetti di base di Android, né i servizi che hai chiesto di tenere (Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, Rai, Mediaset, La7). L'elenco `[keep]` in `scripts/firestick-bloat.txt` protegge anche Alexa, Appstore, tastiera, aggiornamenti e **duplica schermo / casting**.
- **A gruppi di 8**, controllando dopo ognuno che la Home risponda ancora; se no, rimette a posto il gruppo e si ferma.
- **Verifica rileggendo lo stato**, non fidandosi dei messaggi: Fire OS rifiuta in silenzio i pacchetti protetti. Quelli rifiutati sono elencati (è normale).
- L'elenco (`scripts/firestick-bloat.txt`) è un file di testo: metti `#` davanti a una riga per tenere quell'app.

### Dopo un aggiornamento Amazon

Gli aggiornamenti possono riattivare pacchetti e aggiungerne di nuovi. Riattiva il Debug ADB, poi:

```bash
python3 scripts/firestick.py check 192.168.1.50 --stick     # cosa è tornato?
python3 scripts/firestick.py clean 192.168.1.50 --stick     # rimette a posto
```

### Quello che la pulizia NON toglie

- **Il banner e la riga "Sponsorizzati" della Home.** Li scarica il launcher Amazon (non un pacchetto che si possa spegnere). La community prova con un **DNS che blocca le pubblicità** (AdGuard DNS sul router, oppure DNS privato su Fire OS 8), con risultati misti: su alcune Fire Stick non basta. Questo progetto non lo automatizza perché non l'ho potuto verificare.
- **Le opzioni sulla privacy.** Vanno spente a mano, perché si sincronizzano con l'account Amazon: *Impostazioni → Preferenze → Impostazioni sulla privacy* → *Pubblicità basata sugli interessi*, *Dati di utilizzo del dispositivo*, *Raccogli dati di utilizzo delle app* tutte su OFF; poi *Contenuti in primo piano* → riproduzione automatica di video e audio OFF.
- **Su Fire OS 5** (verificato sul Fire TV Box del 2015) Amazon rifiuta del tutto `pm disable-user`: se succede, lo strumento lo riconosce e si ferma. Su Fire OS 6, 7 e 8 funziona, ma alcuni pacchetti di telemetria restano protetti.

## 2. FireHome (l'app)

| Schermata | Cosa fa |
|---|---|
| Home | Orologio, **TV Live**, **Ultimo canale**, **Duplica schermo** (tablet → TV), Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, Tutte le app, Impostazioni. Compaiono solo le app installate. |
| TV Live | Due gruppi: *App ufficiali* (RaiPlay, Mediaset Infinity, La7: si aprono con un click) e i canali della tua `playlist.m3u`, che partono al volo nel player. |
| Player | Su/Giù cambia canale, OK mostra il nome, Indietro esce. |
| Tutte le app | Via di fuga: qualsiasi app installata. |

### Installazione

1. **Compila l'APK**: ogni push su GitHub lo costruisce in *Actions → Build APK → Artifacts → FireHome-apk*.
   Per un link diretto: crea un tag (`git tag v0.1.0 && git push --tags`) e l'APK appare nelle *Releases*.
2. **Mettilo sulla Fire Stick**: installa l'app *Downloader* dall'Appstore, abilita *Impostazioni → My Fire TV → Opzioni sviluppatore → Installa app sconosciute → Downloader*, poi incolla il link dell'APK in Release. (Alternativa: `adb connect <ip>:5555 && adb install FireHome.apk`.)
3. Aprilo da *Le tue app*. Se qualcosa non va, prova `FireHome-debug.apk`.

Poiché non può essere la Home, si apre da *Le tue app*. Da provare per averlo a portata di mano: spostarlo in prima posizione in *Le tue app* (tasto Opzioni sul telecomando); oppure chiedere ad Alexa di aprirlo per nome. Esiste anche una via della community per intercettare il tasto Home (*Launcher Manager Mini*), ma è un'app di terzi che Amazon ha già rotto con aggiornamenti: non è inclusa.

### TV Live

Il progetto **non include flussi** di Rai, Mediaset o La7: sono ritrasmissioni non autorizzate, e non li fornisco né cerco. I tre tile *App ufficiali* aprono le app ufficiali; se una non è installata, il tile segnala "Installa dall'Appstore".

Per avere i canali al volo nel player, usa una **playlist M3U** di una fonte di cui hai i diritti, per esempio un abbonamento IPTV regolare che ti fornisce il link:

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

Riapri FireHome: i canali compaiono in TV Live, raggruppati come `group-title`. (Il percorso `/sdcard/Android/data/...` va verificato sulla tua versione di Fire OS: se `adb push` risponde *Permission denied*, scrivimelo.) Per modificare i tre canali ufficiali usa `channels.json`, che si carica nella stessa cartella.

### Personalizzare le app

In `Apps.kt` la lista `SHORTCUTS`: nome, colore e come riconoscere l'app (package esatto oppure parte del nome). Se BuddyFit non compare, controlla il nome in *Tutte le app* e correggi la ricerca.

## Struttura

```
scripts/firestick.py          pulizia: clean / check / restore / adb-off
scripts/firestick-bloat.txt   elenco dei pacchetti ([base], [stick], [extras], [keep])
app/src/main/kotlin/app/firehome/
  MainActivity.kt   schermate e navigazione
  Ui.kt             Home, TV Live, Tutte le app (Compose)
  Player.kt         player Media3/ExoPlayer
  Channels.kt       channels.json e playlist.m3u
  Apps.kt           app installate e scorciatoie
.github/workflows/  build dell'APK
```

## Crediti

Il metodo (registro prima di toccare, protezioni, verifica rileggendo lo stato, controllo dopo gli aggiornamenti) e i nomi dei pacchetti vengono da [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (licenza MIT), in particolare dalle liste provate su Fire Stick 4K 2018, Lite 2020, 4K Max 2021 e 4K 2023. Il codice qui è riscritto, non copiato.

## Stato

- `scripts/firestick.py` è provato solo contro un `adb` simulato (stato, pacchetti protetti, Home che smette di rispondere, firmware che rifiuta tutto, registro e ripristino): **mai su una Fire Stick vera**. Il primo giro vero sarà il collaudo.
- L'app compila su GitHub Actions ma non è stata provata su un dispositivo: da verificare focus del telecomando, tile *Duplica schermo*, caricamento della playlist.
