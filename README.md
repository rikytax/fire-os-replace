# FireHome (per Nobara Linux)

Toglie pubblicità, telemetria e app inutili da una Fire TV Stick e ne sostituisce la schermata Home con una veloce e pulita: orologio, **TV Live**, le tue app, **Duplica schermo**. Alexa continua a funzionare. Tutto si controlla dal tuo PC con **Nobara Linux** (vale anche per le altre Fedora).

Pensato per la **Fire TV Stick 3ª gen (Fire OS 7.7.1.7, build PS7717/5741)**: per questa stessa build altri hanno già completato l'intero procedimento su tre Fire Stick ([dumbtv](https://github.com/humblemuzzu/dumbtv/blob/main/catalog/devices/fireos-amazon-aftss.yaml)).

> **Non è un sistema operativo nuovo.** Il bootloader delle Fire Stick è bloccato da Amazon: l'OS resta Fire OS. FireHome sostituisce la Home, non il sistema. Non funziona sui modelli nuovi con **Vega OS** (non Android).

## Installazione (una volta sola)

```bash
./installa-nobara.sh
```

Chiede la password di sudo e fa quattro cose: installa `android-tools` e `python3` con dnf, aggiunge la regola USB per la Fire Stick (`/etc/udev/rules.d/51-firestick.rules`, senza gruppi né root), crea la voce **Pulisci Fire Stick** nel menu delle applicazioni e attiva un controllo settimanale che ti avvisa con una notifica se Amazon riattiva qualcosa.

## Uso

1. **Sulla Fire Stick:** *Impostazioni → My Fire TV → Opzioni sviluppatore → Debug ADB* = ON (se *Opzioni sviluppatore* manca: *Info*, premi OK sul nome del dispositivo 7 volte). Collegala al Wi-Fi di casa, o al PC con il cavo micro-USB.
2. **Sul PC:** apri **Pulisci Fire Stick** dal menu, oppure `./pulisci-firestick.sh`.
3. **Sulla TV:** la prima volta compare *Consenti debug USB?*: scegli *Consenti sempre da questo computer* e premi OK.

Il resto è automatico: trova la Fire Stick da sola (USB o rete), pulisce, scarica FireHome, prende la Home e **prova davvero** a premere Home per verificare. Se non la trova chiede l'IP (*Impostazioni → My Fire TV → Info → Rete*).

> **Repository privato?** Lo script scarica FireHome dalla Release del repository: se è privato il download fallisce. Rendi pubblico il repository (*Settings → General → Danger zone → Change visibility*), oppure scarica a mano `FireHome.apk` dalla pagina *Releases* e mettilo in `~/Download` (o accanto allo script): lo trova da solo. La pulizia funziona comunque.

## Pubblicità: cosa si può e cosa no

| Dove | Cosa fare | Come |
|---|---|---|
| **Home Amazon, telemetria, banner** | **Sì** | È il compito dello script e di FireHome. |
| **YouTube** | **Sì, con SmartTube** | `./pulisci-firestick.sh --youtube` installa [SmartTube](https://github.com/yuliskov/SmartTube) (open source, senza alcun codice per le pubblicità, con SponsorBlock e filtro Shorts) e disattiva l'app YouTube di Amazon. Alternativa ufficiale: YouTube Premium. Nota: usare un client non ufficiale va contro le condizioni di YouTube; è una tua scelta. |
| **Netflix, Prime Video, Disney+** | **No, solo col piano senza pubblicità** | Le pubblicità sono inserite nel flusso protetto da DRM: non si possono togliere in modo affidabile, e non ti aiuto a eludere la protezione. Se il tuo piano le include, cambia piano (Netflix, Disney+ e Prime Video hanno un'opzione senza pubblicità: controlla prezzi e disponibilità sul tuo account). |
| **Jellyfin** | Già senza | Non ha pubblicità. |
| **RaiPlay, Mediaset Infinity, La7** | No | Le pubblicità fanno parte della trasmissione; Infinity ha un abbonamento a pagamento. |
| **Saltarle o farle durare un attimo** | **No** | Nelle app a pagamento gli annunci sono inseriti dal server nello stesso flusso del programma, con tempi e conteggi decisi lato servizio: non c'è un punto in cui "accelerarli" o scavalcarli, e manometterli sarebbe aggirare il servizio, cosa che non faccio. Il piano senza pubblicità è l'unica via. |
| **DNS che blocca le pubblicità** | Poco | Aiuta contro telemetria e tracker, ma YouTube serve video e pubblicità dagli stessi server: [non si può bloccare dalla rete](https://github.com/humblemuzzu/dumbtv/blob/main/docs/YOUTUBE.md). |

Con `--youtube`, la prima volta, col telecomando in SmartTube: menu a sinistra → *Impostazioni → Generale → Nascondi contenuti* → **Nascondi Shorts ovunque**; *Impostazioni → Player → Preset video* → **1080p 60fps vp9** (mai `av01`: la Fire Stick non ha il decoder). "Alexa, cerca su YouTube" non apre SmartTube, perché Alexa chiama l'app Amazon.

## Comandi

```bash
./pulisci-firestick.sh                  # tutto: pulizia + FireHome come Home
./pulisci-firestick.sh --youtube        # ... e YouTube senza pubblicità
python3 scripts/firestick.py status     # memoria, spazio libero, stato di pulizia e Home
python3 scripts/firestick.py ram        # i processi che occupano più memoria e cosa sono (decide cosa togliere con i dati)
python3 scripts/firestick.py screenshot # schermata della TV in ~/Immagini
python3 scripts/firestick.py cache      # libera la cache delle app (utile: lo spazio è poco)
python3 scripts/firestick.py install app.apk   # installa un'app dal PC
python3 scripts/firestick.py hdmi       # diagnosi HDMI-CEC (Anynet+ dei Samsung): salva fire-hdmi.txt
python3 scripts/firestick.py discover-live  # indirizzi diretti dei canali nelle app Rai/Mediaset/La7
python3 scripts/firestick.py check      # cosa ha riattivato Amazon? (lo fa anche il timer settimanale)
python3 scripts/firestick.py restore    # torna a com'era prima
python3 scripts/firestick.py adb-off    # spegne il Debug ADB a lavoro finito
```

Opzioni: `--extras` (toglie anche Alexa oltre alla ricerca vocale: timer, multi-room, Silk, Ambient), `--no-home` (solo pulizia), `--apk FILE`, `--reboot`, `--tv` (TV con Fire OS integrato, non una Fire Stick). Un IP o `usb` come ultimo argomento salta la ricerca automatica.

> Il controllo settimanale funziona solo se il Debug ADB resta acceso. Se lo spegni con `adb-off` per sicurezza, il timer non riesce a raggiungere la Fire Stick e tace.

## Cosa fa la pulizia

| Passo | Cosa |
|---|---|
| 1. App Amazon inutili | Disattiva (non disinstalla) telemetria, pubblicità, ACR, shopping, Luna, tutorial, app dimostrative, Freevee, Amazon Music e Photos, scheda Live. |
| 2. Pacchetti protetti | Quelli che Fire OS non lascia disattivare restano, ma perdono l'esecuzione in background. |
| 3. Impostazioni | Spegne ad tracking, pubblicità basata sugli interessi, invio dati d'uso, video in anteprima nella Home; animazioni più rapide. |
| 4. YouTube (con `--youtube`) | Installa SmartTube (versione fissata, controllo SHA-256) e disattiva YouTube di Amazon. |
| 5. FireHome come Home | Scarica l'ultima FireHome, la installa, attiva il servizio Home e prova la pressione di Home. |
| 6. Report | `fire-report.txt` con i pacchetti Amazon ancora attivi che l'elenco non conosce. |

**Protezioni:** non tocca mai la Home Amazon, le Impostazioni, i pacchetti di base di Android, né Netflix, Prime Video, Disney+, Jellyfin, BuddyFit, Rai, Mediaset, La7. L'elenco `[keep]` in `scripts/firestick-bloat.txt` protegge anche Alexa, Appstore, tastiera, aggiornamenti e **duplica schermo / casting**. Lavora a gruppi di 8 e, se dopo un gruppo la Home non risponde più, lo rimette a posto e si ferma. Prima di toccare scrive com'era ogni cosa in `~/.firestick-clean/`, per un `restore` fedele.

## Come FireHome prende la Home

Su Fire OS il launcher Amazon è un pacchetto protetto: `set-home-activity` risponde "Success" e non cambia nulla, e il tasto Home non si può intercettare. Il trucco, preso da [dumbtv](https://github.com/humblemuzzu/dumbtv) (MIT):

1. Un **servizio di accessibilità** (`HomeGuard`) aspetta la finestra Home di Amazon. Vede solo il cambio di finestra, non legge lo schermo.
2. La **copre** con una finestra del colore di FireHome e **lancia FireHome tramite un allarme**: Android ritarda di 5 secondi gli avvii dopo Home, ma non quelli da un allarme con `min_futurity=0` (lo imposta lo script).
3. Premendo Home vedi circa 0,3 s di nero, poi FireHome. **Tenendo premuto Home** si apre ancora il menu rapido di Amazon (lì c'è anche *Mirroring*).

**Freno di sicurezza:** se FireHome non compare dopo 4 lanci di fila, il servizio si ferma finché apri un'altra app. In *Tutte le app* c'è il tile *Home Amazon* per tornare alla Home di Amazon di proposito.

**Rischio principale:** gli aggiornamenti Amazon possono riattivare pacchetti o spegnere il servizio (in passato hanno rotto i launcher di terzi). Il timer settimanale ti avvisa; `./pulisci-firestick.sh` rimette a posto.

## TV Live e Duplica schermo

Il progetto **non include flussi** di Rai, Mediaset o La7: sono ritrasmissioni non autorizzate. I tre tile *App ufficiali* aprono le app ufficiali; se una non è installata, il tile segnala "Installa dall'Appstore".

Per avere i canali al volo nel player usa una **playlist M3U** di una fonte di cui hai i diritti (per esempio un abbonamento IPTV regolare):

```
#EXTM3U
#EXTINF:-1 group-title="Generali",Nome canale
https://fonte-che-hai-diritto.esempio/live.m3u8
```

Salvala come `playlist.m3u` e caricala: `adb push playlist.m3u /sdcard/Android/data/app.firehome/files/playlist.m3u`. Su/Giù cambia canale. (Su Fire OS 7 il percorso dovrebbe funzionare; se risponde *Permission denied*, dimmelo.) I tre canali ufficiali si modificano con `channels.json`, nella stessa cartella.

**Collegamento diretto a un canale.** In `channels.json` un canale può avere `"url"` (la pagina ufficiale della diretta) e `"app"`: il tile apre quell'indirizzo con l'app indicata. Non ho indirizzi verificati per Mediaset e La7, quindi non li ho scritti a caso: lancia `python3 scripts/firestick.py discover-live` con le app installate, mandami `fire-live-discovery.txt` e collego i canali più visti (Rai 1, Canale 5, Rai 2, Rai 3, Italia 1, Rete 4, La7...) uno per uno. Finché non c'è, i tre tile aprono l'app ufficiale.

**Duplica schermo:** il tile apre le Impostazioni e ti dice dove andare (*Display e suoni → Abilita duplicazione schermo*): non esiste un'attività verificata che apra direttamente la schermata. Più veloce: **tieni premuto Home** e scegli *Mirroring*. Lo script non tocca i servizi di casting.

## La Home

Stile "ghiaccio": blu notte con azzurro, ispirato al tuo mockup ma adattato a ciò che una Fire Stick può davvero fare. Niente sfocature costose: solo gradienti, bordi tenui e un bagliore al focus.

- **Barra laterale** (sempre con icona e nome): *Home*, *TV Live*, *App*, *Impostazioni*.
- **In alto:** stato reale della rete (*Wi-Fi 5 GHz*, *Ethernet*...), la **RAM libera** e l'orologio.
- **Banner in primo piano:** un film o una serie di tendenza in Italia, con locandina di sfondo, voto, trama e il pulsante **Guarda su Netflix / Prime Video / Disney+...** (apre l'app giusta e ti dice cosa cercare; "Altro titolo" passa al successivo). Compare solo se hai attivato la chiave TMDB: altrimenti non c'è nessun banner e le app salgono in cima.
- **Le tue app:** una griglia a 4 colonne con tessere ampie: l'icona vera di ogni app, il nome e una descrizione (Netflix, Prime Video, Disney+, Jellyfin, YouTube, Spotify, BuddyFit), poi *Tutte le app*.
- **Controlli rapidi:** *Riprendi* (l'ultimo canale), *Duplica schermo* e le azioni di domotica. *TV Live* è nella barra laterale.
- **Consigliati per te:** locandine dei titoli disponibili sui tuoi servizi.

**Si adatta al televisore.** L'interfaccia si ridimensiona da sola in base alla risoluzione reale dello schermo: se cambi TV (720p, 1080p, 4K) non serve toccare nulla.

**Cosa ho lasciato fuori dal mockup, e perché:** gli *ingressi HDMI*, la PS5, le *AirPods* e il *Night Sound* (una Fire Stick non ha ingressi e non espone queste funzioni), e le barre di avanzamento di *Continua a guardare* (Netflix e le altre app non dicono dove sei arrivato).

### Attivare i suggerimenti (una volta sola, gratis)

I dati vengono da [TMDB](https://www.themoviedb.org/) (la disponibilità in streaming da JustWatch). Serve una chiave gratuita:

1. Crea un account su themoviedb.org e conferma l'email.
2. *Impostazioni → API → Richiedi una chiave* (uso personale). Ti danno una **API Key (v3)** di 32 caratteri.
3. Sul PC: `python3 scripts/firestick.py suggerimenti LA_TUA_CHIAVE`
4. Riapri FireHome: i suggerimenti compaiono dopo qualche secondo e si aggiornano ogni 12 ore.

Senza chiave la riga semplicemente non compare. *Questo prodotto usa l'API di TMDB ma non è approvato né certificato da TMDB.*

### Aggiornare FireHome sulla Fire Stick

Un solo comando, senza scaricare nulla a mano:

```bash
./pulisci-firestick.sh home 192.168.1.5
```

(con il tuo IP). **La prima volta dopo il passaggio alla firma fissa** (versione 0.3.0) lo script nota che l'app installata ha una firma diversa, salva i tuoi file (`playlist.m3u`, `channels.json`, `domotica.json`, `suggerimenti.json`), disinstalla, reinstalla e li rimette: capita una volta sola. Le versioni successive si aggiornano una sopra l'altra. La chiave di firma è inclusa nel progetto (`app/firehome.keystore`): non è un segreto, serve solo a far riconoscere le versioni come "la stessa app".

Lo script scarica da solo l'ultima versione dalla Release: se il repository è privato usa la GitHub CLI (`gh`, già autenticata con `gh auth login`) e controlla lo SHA-256. Reinstalla l'app mantenendo impostazioni e playlist.

## Domotica (Home Assistant)

I comandi vocali di Alexa per luci e prese funzionano già (lo script non li tocca: **non usare `--extras`**, che spegne la parte smart home di Alexa). In più FireHome può mostrare tile "Casa" in Home che chiamano [Home Assistant](https://www.home-assistant.io/). Si configura con `domotica.json`, da caricare come la playlist: `adb push domotica.json /sdcard/Android/data/app.firehome/files/domotica.json`.

```json
{
  "url": "http://192.168.1.10:8123",
  "actions": [
    { "name": "Luci salotto", "webhook": "id-segreto-del-webhook" },
    { "name": "Scena cinema", "webhook": "altro-id" }
  ]
}
```

Consigliato: in Home Assistant crea un'**automazione con trigger "Webhook"** per ogni azione (luci, scena, riscaldamento...) e metti qui solo l'id: sulla Fire Stick non resta nessuna credenziale. In alternativa si può chiamare un servizio, ma serve un token (`"token": "..."`, `"service": "light/toggle", "entity": "light.salotto"`): in quel caso usa un utente dedicato con permessi limitati, perché il token sta in un file leggibile sulla Fire Stick.

## Anynet+: la TV scollega la Fire Stick

Anynet+ è il nome Samsung dell'HDMI-CEC: la TV e la Fire Stick si scambiano comandi (accensione, ingresso attivo, standby). Con la voce la Fire Stick "sveglia" la TV e se il collegamento CEC si perde, la TV segnala il dispositivo come scollegato. **Non esiste un comando verificato per spegnere il CEC dalla Fire Stick**, quindi lo script non lo cambia di nascosto: lancia `python3 scripts/firestick.py hdmi` **subito dopo che succede**; salva `fire-hdmi.txt` con le impostazioni e il log, e stampa le cause note, dalla più semplice ([fonti](https://eu.community.samsung.com/t5/tv/amazon-fire-stick-no-longer-recognised-for-smart-remote/td-p/372241)):

1. **Alimentazione:** se la Fire Stick è alimentata dalla USB della TV (circa 0,5 A, ne serve 1 A), usa l'alimentatore a muro. È la causa più comune e la prova più economica.
2. **Porta HDMI:** prova HDMI 1.
3. **Sulla TV:** *Impostazioni → Generali → Gestione dispositivi esterni → Anynet+ (HDMI-CEC)*: spegni, riavvia la TV, riaccendi.
4. **Sulla Fire Stick:** *Impostazioni → Display e suoni → Controllo dispositivo HDMI CEC*: spegni, riavvia, riaccendi. Spegnerlo toglie l'accensione sincronizzata con la TV.
5. Se è iniziato dopo un aggiornamento della TV, controlla se ne esiste uno più recente.

Mandami `fire-hdmi.txt` se persiste.

## Idee per il futuro

- **AdGuard Home sul tuo PC Nobara** (con Podman) come DNS di tutta la casa: blocca tracker e pubblicità di rete per tutti i dispositivi, Fire Stick compresa. Servirebbe impostarlo come DNS sul router.
- **Notifiche domotica** (citofono, lavatrice finita) come messaggio sulla TV, da Home Assistant.
- **Telecomando dal PC:** inviare tasti e testo alla Fire Stick con `adb shell input` (scrivere le password con la tastiera del PC è molto più comodo).
- **Backup dei dati delle app** (Jellyfin, impostazioni) prima di un ripristino di fabbrica.
- **Contatore RAM e spazio libero nella Home di FireHome**, visto che sulla tua Fire Stick lo spazio è poco (circa 1,4 GB liberi).
- **Spegnimento programmato** e controllo della TV (CEC) dal PC.
- **Timer del sonno** dentro FireHome.

## Struttura

```
installa-nobara.sh       installa dnf, regola USB, voce di menu e timer settimanale
pulisci-firestick.sh     lancia lo script
scripts/firestick.py     ricerca, pulizia, SmartTube, FireHome, utilità, ripristino
scripts/firestick-bloat.txt   pacchetti: [base] [stick] [extras] [protected] [keep]
app/src/main/kotlin/app/firehome/   l'app (Compose): Home, TV Live, player, HomeGuard
.github/workflows/       build dell'APK e Release automatica
```

## Crediti

Metodo, protezioni, elenchi e meccanismo della Home vengono da due progetti MIT: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (registro prima di toccare, verifica rileggendo lo stato) e [humblemuzzu/dumbtv](https://github.com/humblemuzzu/dumbtv) (servizio Home, impostazioni su pubblicità, pacchetti protetti, SmartTube). Il codice qui è riscritto, non copiato; dove i due progetti si contraddicono (`tigris`, `logan`) ho scelto la via prudente.

## Stato

- Lo script è provato solo contro un `adb` simulato e su Ubuntu: **mai su una Fire Stick vera, né su Nobara**. Le parti specifiche di Nobara (`dnf`, regola `udev`, timer `systemd`) sono controllate solo nella sintassi. Ogni modifica alla Fire Stick è reversibile con `restore`.
- L'app compila su GitHub Actions ma non è stata provata su un dispositivo.
