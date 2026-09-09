# Immagini, date, opzioni avanzate e Shorts

## Immagini e icone dei canali

Le schede riutilizzano le immagini già note del canale, anche quando il singolo risultato
non include un avatar. Il confronto tiene conto della sorgente e degli alias degli URL;
nomi uguali con immagini diverse non vengono usati come ripiego ambiguo.

La cache dei metadati dei canali è separata per profilo. I canali visibili possono essere
aggiornati in background, con richieste deduplicate e al massimo due operazioni concorrenti.

La cache delle immagini conserva i file codificati nella directory cache dell'app:

- miniature: verifica dopo 24 ore;
- avatar: verifica dopo 7 giorni;
- immagini vecchie ancora disponibili mostrate durante la verifica e in caso di errore;
- ETag e Last-Modified usati per evitare il trasferimento del corpo quando il server risponde 304;
- tentativi falliti limitati da un intervallo di 15 minuti, con recupero al ritorno della rete;
- limite di 128 MiB, eliminando per prime le immagini usate meno recentemente;
- al massimo due trasferimenti concorrenti e 8 MiB per singola risposta;
- nessuna scansione periodica in background: controlli all'utilizzo e al ritorno dell'app;
- cache in memoria per evitare di rileggere il disco per ogni scheda con la stessa icona.

Le intestazioni fanno parte dell'identità della cache. Cookie e Authorization non vengono
inoltrati automaticamente a un host diverso durante un reindirizzamento.

## Date relative

Le etichette di pubblicazione si basano sul timestamp originale, non sul testo salvato
nel feed. Un orologio condiviso aggiorna i testi visibili una volta al minuto e al ritorno
in primo piano. Anche le etichette della cronologia vengono ricalcolate. Questo non provoca
richieste di rete né il ricaricamento delle miniature.

## Avanzate

Il pannello nelle preferenze include la ricostruzione della cache dei contenuti del profilo,
le versioni disponibili dei plugin e le revisioni dei motori. La lettura delle versioni
avviene sui file locali, fuori dal thread dell'interfaccia, senza avviare V8.

La revisione Grayjay identifica il codice adattato nel fork; `+` indica modifiche locali.
Versione e commit di NewPipe sono dichiarati in `engine-versions.properties`, usato anche
per scegliere la dipendenza effettiva durante la build.

La ricostruzione richiede conferma e non elimina cronologia, playlist, file scaricati,
plugin installati, autorizzazioni o accessi. La riproduzione attiva non viene reinizializzata.

## Copertina multimediale

Media3 riceve un caricatore di bitmap condiviso con la cache delle immagini, con le
intestazioni corrette e immagini limitate a 256 pixel sul lato maggiore. Le notifiche
riutilizzano la copertina invece di scaricarla nuovamente a ogni aggiornamento.

Il test con il controller multimediale Android verifica la presenza di una bitmap nella
descrizione della sessione. La trasmissione Bluetooth della copertina dipende dal supporto
AVRCP Cover Art del sistema e dell'autoradio; A2DP trasporta l'audio, non questa immagine.
Non vengono cambiate le impostazioni Bluetooth di Android.

Riferimenti: [MediaSession e BitmapLoader](https://developer.android.com/reference/androidx/media3/session/MediaSession.Builder#setBitmapLoader(androidx.media3.common.util.BitmapLoader))
e [servizio AVRCP di Android](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/b2dfcaf71aa0342edc790966f93e75ed6743c6f7/android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java).

## Modalità Brainrot per gli Shorts

Disattivata per impostazione predefinita e non proposta sui tablet. Abilitandola, gli Shorts
della Home si aprono in fullscreen verticale. Lo swipe passa al video precedente o successivo;
viene riprodotta soltanto la pagina selezionata, mentre le pagine adiacenti mostrano una miniatura.
Il feed continua a caricare pagine quando ci si avvicina alla fine. Lo Short si ripete finché
non viene cambiato; uscendo viene ripristinata la modalità di ripetizione precedente.

## Ritorno alle playlist e anteprime di ricerca

Il tap su un video dentro una playlist locale o remota avvia ora esplicitamente la coda di
quella playlist. Riducendo il player si torna quindi alla stessa pagina e allo stesso punto
di scorrimento da cui era stato aperto il video, anche se nel frattempo la riproduzione è
passata a un elemento successivo.

Per YouTube le anteprime dello slider sono disponibili con entrambi i backend. Il backend
NewPipe converte direttamente i `Frameset` e conserva l'elenco reale degli sprite, senza
ricostruirne gli URL; il backend plugin continua a leggere la specifica storyboard dalla
pagina di visione. Le celle usano la cache su disco dello sprite e un errore dovuto a un URL
firmato scaduto forza una sola rigenerazione limitata nel tempo, senza scaricare un'immagine
per ogni movimento del cursore.

## Verifica

Test isolati per cache fresca, revalidazione, errori e invalidazione; recupero degli avatar;
date relative; conferma del comando di ricostruzione; metadati multimediali; scorrimento Shorts
in entrambe le direzioni, attivazione esplicita e ritorno al player normale; selezione della
cella storyboard e ripristino della playlist e del suo scroll.

I test di invalidazione usano directory temporanee, senza accedere alle librerie dell'utente.
La compatibilità con una specifica autoradio richiede una prova sul relativo hardware.
