# Riordino playlist e verifica adattiva — settembre 2026

## Riordino

La vecchia implementazione spostava l'elemento reale nella LazyColumn mentre ne correggeva
anche la traslazione e animava il posizionamento delle altre righe. Le coordinate lette
durante il layout potevano quindi far oscillare l'elemento tra origine e destinazione.

Il nuovo dialogo usa:

- una sola scheda sospesa, separata dalla lista e ancorata al dito;
- uno slot della stessa altezza della scheda, con bordo e colore del tema;
- soglie sui punti medi delle righe e una piccola isteresi per evitare rimbalzi;
- acquisizione del gesto sul contenitore stabile, nella zona della maniglia da 48 dp;
- scroll normale dal testo e scroll automatico ai bordi durante il trascinamento;
- animazione di atterraggio nello slot prima di aggiornare la bozza dell'ordine;
- salvataggio solo con OK; annullamento e modifiche concorrenti non duplicano né
  reintroducono elementi eliminati.

## Matrice dell'emulatore

Dispositivo dedicato: `Grayjoy_Layout_Audit`, Android 15 / API 35 x86-64,
Android Emulator 37.1.11, accelerazione WHPX. L'eseguibile è stato installato con gli
strumenti SDK; l'immagine Android è una copia di quella già disponibile sul PC.
Il dispositivo virtuale preesistente e il telefono non sono stati modificati.

| Profilo | Pixel | DPI | Area logica approssimativa | Testo |
| --- | --- | --- | --- | --- |
| Riferimento Pixel 9a | 1080 × 2424 | 420 | 411 × 923 dp | 100% |
| Telefono piccolo | 720 × 1280 | 360 | 320 × 569 dp | 100% |
| Telefono medio | 1080 × 2160 | 480 | 360 × 720 dp | 100% |
| Caratteri grandi | 1080 × 2160 | 480 | 360 × 720 dp | 140% |
| Tablet verticale | 1200 × 1920 | 240 | 800 × 1280 dp | 100% |
| Tablet orizzontale | 1920 × 1200 | 240 | 1280 × 800 dp | 100% |

La matrice usa dati sintetici e lo stesso profilo grafico standard del riferimento;
non contiene account, esportazioni o contenuti privati. Ogni giro attraversa Home,
Seguiti, Cerca, Raccolta, Prefs (inizio/fine), Sorgenti, playlist locale, riordino,
selezione, Now Playing, miniplayer, canale e playlist remota. Vengono conservate
17 catture per profilo, incluse le viste di ricerca dopo l'interazione con il campo.

## Correzioni di layout

- Le miniature si restringono sui display sotto 390 dp, lasciando spazio ai titoli.
  La larghezza di riferimento di 184 dp sul Pixel 9a resta invariata.
- Con poco spazio o testo ingrandito, l'altezza delle schede tiene conto di titolo,
  metadati e pulsante del canale, evitando sovrapposizioni.
- I pulsanti audio/video delle playlist passano su righe separate quando le etichette
  non hanno spazio sufficiente. Su larghezza normale restano affiancati.
- Titoli dell'header limitati a due righe; titoli delle playlist più compatti sui piccoli schermi.
- Conteggio della selezione compatto sui display stretti; nessuna parola spezzata verticalmente.
- Etichette della navigazione su una riga anche con testo ingrandito.
- Playlist locali aperte tramite navigazione esterna evidenziano correttamente Raccolta.
- Verificati player/miniplayer e barra di sistema sui tablet nelle due orientazioni.

## Ripetibilità

Il test `GrayjayAppTest#captureResponsiveScreens` produce PNG e alberi semantici con dati
in memoria. `scripts/audit-layouts.ps1` imposta la matrice: per sicurezza rifiuta seriali
che non iniziano con `emulator-`. Le immagini di questa esecuzione sono nella cartella
di lavoro `layout-audit-20260908/final`, esterna al repository.

Riferimento: [documentazione dell'emulatore Android](https://developer.android.com/studio/run/emulator-commandline).

## Esito

- Tutti i sei percorsi di cattura completati: 102 PNG complessivi.
- 251 test JVM senza errori.
- Suite UI: 25 test superati e un test di cattura condizionale saltato nella suite ordinaria;
  quest'ultimo è stato eseguito separatamente con successo per ciascuno dei sei profili.
- Verificato l'avvio reale di grayTEST nell'emulatore, senza dialoghi di crash.
- Nessuna pubblicazione GitHub e nessuna installazione sul telefono scollegato.
