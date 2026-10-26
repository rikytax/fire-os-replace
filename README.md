# FireHome

Toglie pubblicità, telemetria e app inutili da una Fire TV Stick e ne sostituisce la schermata Home con una veloce e pulita: orologio, **TV Live**, le tue app, **Duplica schermo**. Alexa continua a funzionare.

Pensato per la **Fire TV Stick 3ª gen (Fire OS 7.7.1.7, build PS7717/5741)**. Per questa stessa build altri hanno già completato l'intero procedimento su tre Fire Stick ([dumbtv](https://github.com/humblemuzzu/dumbtv/blob/main/catalog/devices/fireos-amazon-aftss.yaml)).

> **Non è un sistema operativo nuovo.** Il bootloader delle Fire Stick è bloccato da Amazon: l'OS resta Fire OS. FireHome sostituisce la Home (con un trucco, spiegato sotto), non il sistema. Non funziona sui modelli nuovi con **Vega OS** (non Android).

## Come si usa

1. **Sulla Fire Stick:** *Impostazioni → My Fire TV → Opzioni sviluppatore → Debug ADB* = ON. Se *Opzioni sviluppatore* manca: *Info*, premi OK sul nome del dispositivo 7 volte. Collega la Fire Stick al Wi-Fi di casa (o al computer con il cavo micro-USB).
2. **Sul computer:** scarica questa cartella e fai **doppio clic** su:
   - Windows: `PULISCI-FIRESTICK.bat` (se manca Python lo installa da solo)
   - Mac: `pulisci-firestick.command` (se non si apre: tasto destro → *Apri*)
   - Linux: `./pulisci-firestick.sh`
3. **Sulla TV:** la prima volta compare *Consenti debug USB?*: scegli *Consenti sempre da questo computer* e premi OK. Il resto è automatico.

Lo script trova da solo la Fire Stick (USB o rete), scarica `adb` se manca e fa tutto senza domande. Se non la trova chiede l'IP (*Impostazioni → My Fire TV → Info → Rete*).

## Cosa fa, in ordine

| Passo | Cosa |
|---|---|
| 1. App Amazon inutili | Disattiva (non disinstalla) telemetria, pubblicità, ACR, shopping, Luna, tutorial, app dimostrative, Freevee, Amazon Music e Photos, scheda Live. |
| 2. Pacchetti protetti | Quelli che Fire OS non lascia disattivare restano, ma perdono l'esecuzione in background. |
| 3. Impostazioni | Spegne ad tracking, pubblicità basata sugli interessi, invio dati d'uso, video in anteprima nella Home; animazioni più rapide. |
| 4. FireHome come Home | Scarica l'ultima FireHome, la installa, attiva il servizio Home e **prova davvero** premendo Home: se FireHome si apre, lo dice. |
| 5. Report | `fire-report.txt` con i pacchetti Amazon ancora attivi che l'elenco non conosce. |

**Protezioni:** non tocca mai la Home Amazon, le Impostazioni, i pacchetti di base di Android, né Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, Rai, Mediaset, La7. L'elenco `[keep]` in `scripts/firestick-bloat.txt` protegge anche Alexa, Appstore, tastiera, aggiornamenti e **duplica schermo / casting**. Lavora a gruppi di 8 e, se dopo un gruppo la Home non risponde più, lo rimette a posto e si ferma. Scrive prima com'era ogni cosa in `~/.firestick-clean/`.

### Altri comandi

```bash
python3 scripts/firestick.py check      # dopo un aggiornamento: cosa ha riattivato Amazon?
python3 scripts/firestick.py            # lo stesso comando rimette a posto
python3 scripts/firestick.py restore    # torna a com'era prima (pacchetti, impostazioni, Home Amazon)
python3 scripts/firestick.py adb-off    # spegne il Debug ADB a lavoro finito
```

Opzioni: `--extras` (toglie anche Alexa oltre alla ricerca vocale: timer, multi-room, Silk, Ambient), `--no-home` (solo pulizia), `--apk FILE`, `--reboot`, `--tv` (per una TV con Fire OS integrato, non una Fire Stick). Su Windows: `py scripts\firestick.py ...`.

## Come FireHome prende la Home

Su Fire OS il launcher Amazon è un pacchetto protetto: `set-home-activity` risponde "Success" e non cambia nulla, e il tasto Home non si può intercettare. Il trucco, preso da [dumbtv](https://github.com/humblemuzzu/dumbtv) (MIT):

1. Un **servizio di accessibilità** (`HomeGuard`) aspetta che compaia la finestra Home di Amazon. Vede solo il cambio di finestra, non legge lo schermo.
2. La **copre** subito con una finestra del colore di FireHome e **lancia FireHome tramite un allarme**: Android ritarda di 5 secondi gli avvii dopo Home, ma non quelli che partono da un allarme con `min_futurity=0` (lo imposta lo script).
3. Premendo Home da un'app vedi circa 0,3 s di nero, poi FireHome. **Tenendo premuto Home** si apre ancora il menu rapido di Amazon (lì c'è anche *Mirroring*).

**Freno di sicurezza:** se FireHome non compare dopo 4 lanci di fila, il servizio si ferma finché apri un'altra app, così un guasto non diventa un ciclo infinito. In *Tutte le app* c'è il tile *Home Amazon* per tornare alla Home di Amazon di proposito.

**Se la Home torna quella di Amazon** (aggiornamento, azzeramento delle impostazioni): `python3 scripts/firestick.py` la rimette a posto. Amazon ha già rotto in passato i launcher di terzi con aggiornamenti: è il rischio principale di questo metodo.

## Cosa NON toglie

- **Gli aggiornamenti Amazon** restano attivi (li ho lasciati apposta). Possono riattivare pacchetti: dopo ogni aggiornamento rilancia lo script. Bloccarli richiede un filtro DNS sul dispositivo (personalDNSfilter, con un permesso VPN da accettare a mano): non è incluso.
- **Qualche riga di impostazioni** che Fire OS riscrive: controlla in *Impostazioni → Preferenze → Impostazioni sulla privacy*.
- **Su Fire OS 5/6** il metodo della Home non funziona (lo script lo riconosce e salta quel passo).

## TV Live

Il progetto **non include flussi** di Rai, Mediaset o La7: sono ritrasmissioni non autorizzate. I tre tile *App ufficiali* aprono le app ufficiali; se una non è installata, il tile segnala "Installa dall'Appstore".

Per avere i canali al volo nel player usa una **playlist M3U** di una fonte di cui hai i diritti (per esempio un abbonamento IPTV regolare):

```
#EXTM3U
#EXTINF:-1 group-title="Generali",Nome canale
https://fonte-che-hai-diritto.esempio/live.m3u8
```

Salvala come `playlist.m3u` e caricala: `adb push playlist.m3u /sdcard/Android/data/app.firehome/files/playlist.m3u`. Riapri FireHome: i canali compaiono in TV Live. (Su Fire OS 8 `/sdcard/Android/data/...` è chiusa ad adb; su Fire OS 7 dovrebbe funzionare: se risponde *Permission denied*, scrivimelo.) Su/Giù cambia canale. I tre canali ufficiali si modificano con `channels.json`, nella stessa cartella.

## Duplica schermo

Il tile apre le Impostazioni di Fire TV e ti dice dove andare (*Display e suoni → Abilita duplicazione schermo*): non ho trovato un'attività che apra direttamente la schermata. Più veloce: **tieni premuto Home** e scegli *Mirroring*. Lo script non tocca i servizi di casting (`cast.sink` e simili).

## Installazione manuale dell'app

L'APK si scarica dalle [Releases](https://github.com/rikytax/fire-os-replace/releases) (`FireHome.apk`, con `FireHome.apk.sha256`). Senza lo script resta una normale app in *Le tue app*; per usarla come Home serve il passo 4 (`python3 scripts/firestick.py home`).

## Struttura

```
PULISCI-FIRESTICK.bat / pulisci-firestick.command / pulisci-firestick.sh   lanciatori
scripts/firestick.py          tutto: ricerca, pulizia, impostazioni, installazione, verifica, ripristino
scripts/firestick-bloat.txt   pacchetti: [base] [stick] [extras] [protected] [keep]
app/src/main/kotlin/app/firehome/
  MainActivity.kt   schermate e navigazione
  HomeGuard.kt      servizio che prende il tasto Home
  Ui.kt             Home, TV Live, Tutte le app (Compose)
  Player.kt         player Media3/ExoPlayer
  Channels.kt       channels.json e playlist.m3u
  Apps.kt           app installate e scorciatoie
.github/workflows/  build dell'APK (con tag v* pubblica la Release)
```

## Crediti

Metodo, protezioni, elenchi e meccanismo della Home vengono da due progetti MIT, a cui va il merito: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (registro prima di toccare, protezioni, verifica rileggendo lo stato, controllo dopo gli aggiornamenti) e [humblemuzzu/dumbtv](https://github.com/humblemuzzu/dumbtv) (servizio Home con allarme e copertura, impostazioni su pubblicità, pacchetti protetti). Il codice qui è riscritto, non copiato; dove i due progetti si contraddicono (per esempio `tigris` e `logan`) ho scelto la via prudente e li ho lasciati attivi.

## Stato

- `scripts/firestick.py` è provato solo contro un `adb` simulato (pacchetti protetti, Home che smette di rispondere, servizio non agganciato, firmware che rifiuta tutto, registro e ripristino, ricerca in rete, download con SHA-256): **mai su una Fire Stick vera**. Ogni modifica è reversibile con `restore`.
- L'app compila su GitHub Actions ma non è stata provata su un dispositivo: da verificare presa della Home, focus del telecomando, tile *Duplica schermo*, playlist.
