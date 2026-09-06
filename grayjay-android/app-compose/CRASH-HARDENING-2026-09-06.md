# Ulteriore audit di robustezza

Branch `optimize`, dopo il commit 705e1e5. Nessuna pubblicazione.

## Log esaminati

- grayTEST: tre log Java delle versioni 1.5.2, 1.6.0 e 1.9.1; nessun nuovo
  log spontaneo dopo le correzioni precedenti. Le ultime uscite Android
  corrispondono a installazioni e runner dei test.
- Grayjoy normale, letta senza modificarla: ultimo log Java del 28 agosto,
  versione 1.9.0, per una chiave duplicata nella coda. La coda ha gia una
  protezione dedicata. Presente anche il vecchio errore di avvio del servizio
  PC-link. L'uscita del 6 settembre per memoria scarsa avveniva in background:
  non e un'eccezione Java e non dimostra un nuovo crash di questa build.

## Correzioni

1. Suggerimenti di ricerca: normalizzazione degli spazi e deduplicazione senza
   distinzione maiuscole/minuscole prima della LazyColumn. Due sorgenti non
   possono piu produrre la stessa chiave con "Android" e "android".
2. Server Cast e PC-link: numero di worker e coda di attesa limitati; le
   connessioni in eccesso vengono chiuse. Lo stop chiude anche i socket client
   e interrompe le letture in ingresso bloccate.
3. Riavvio del server Cast: ogni nuova sessione crea un executor utilizzabile,
   senza riutilizzare quello chiuso dalla disconnessione precedente.
4. PC-link: anche se l'ultimo abbinamento viene rimosso durante l'avvio,
   startForegroundService riceve la promozione richiesta prima dello stop.
5. Ultima connessione PC: l'intervallo di persistenza usa l'ultima scrittura,
   non l'ultimo ping; prima, ping frequenti impedivano ogni salvataggio.
6. Aggiornamenti: callback e finalizzazioni dei tentativi annullati non
   modificano il tentativo successivo. Ogni tentativo ha un file temporaneo
   distinto; i nomi di versione non possono introdurre separatori di percorso.
   Gestiti redirect relativi, mantenendo HTTPS.
7. Login: il renderer WebView terminato viene rimosso e distrutto senza
   riutilizzarlo; la pagina mostra un errore gestito. Callback tardivi e
   cancellazioni non agiscono su un'Activity gia chiusa.
8. Coda download: un record malformato non causa piu lo scarto di tutti gli
   altri. Preparazioni interrotte tornano in coda; i record validi e le pause
   vengono conservati.

## Verifica e limiti

Superati 241 test JVM (app e backend) e 20 test strumentali su grayTEST.
Build debug e release riuscite. Checksum della libreria principale identico
prima e dopo i test; nessuna scrittura nella Grayjoy normale.

Test dedicati per suggerimenti duplicati, stop/riavvio e saturazione dei
server, socket bloccati, file temporanei concorrenti, dati di coda malformati
e salvataggio periodico dello stato PC. Test sul telefono limitati a grayTEST,
con dati isolati, backup preventivo e confronto del checksum della libreria.

Non sono stati forzati crash del renderer, modificati accessi alle sorgenti o
provati aggiornamenti reali che avrebbero installato un'altra app. La
cancellazione di un trasferimento HTTP bloccato resta soggetta al timeout di
lettura, ma la sua finalizzazione non puo danneggiare il trasferimento nuovo.
Restano i limiti di verifica hardware e le segnalazioni lint preesistenti gia
descritti nell'audit precedente.

Riferimenti ufficiali usati per il ciclo di vita:

- [Gestione della terminazione WebView](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination)
- [Avvio e arresto dei servizi Android](https://developer.android.com/develop/background-work/services)
