# Riproduzione, profilo sorgente ed esportazioni — 24 settembre 2026

## Video bloccato a 11:18

Sul Pixel 9a, Grayjoy 2.5.0 era fermo a 678.841 ms. I log mostravano
`ProtocolException: unexpected end of stream` nel trasporto NewPipe e almeno
13 tentativi sullo stesso flusso. La classificazione come problema di rete
precedeva sempre il fallback al motore alternativo, impedendogli di intervenire.

- Il trasporto riprende dal primo byte non consegnato dopo una risposta troncata,
  con un numero limitato di riaperture.
- I parametri di range e numero richiesta vengono sostituiti, evitando duplicati
  e il riutilizzo del vecchio range nei tentativi successivi.
- Due errori online senza progresso significativo attivano il motore alternativo.
  Una vera assenza di rete continua ad attendere il ripristino della connessione.
- Esaurito il fallback, l'errore rimane visibile anziché mantenere uno spinner
  infinito. I comandi pausa/seek eseguiti durante il recupero restano rispettati.

Verifiche: risposta HTTP controllata interrotta a metà e recuperata byte per byte
sul Pixel; riproduzione reale dello stesso video oltre 11:18 sia a 720p sia a
1080p, con posizione osservata oltre 12 minuti e nessun nuovo errore nel processo
di grayTEST durante il controllo.

## Profilo Crunchyroll

Il plugin usa la sessione autenticata, senza una scelta casuale esplicita del
profilo. Il flusso di login dell'app si chiudeva però non appena trovava il cookie,
senza attendere una scelta consapevole del profilo.

- Aggiunta in Sorgenti → Crunchyroll la voce per scegliere il profilo tramite
  il sito ufficiale.
- Il login resta aperto fino alla conferma “Usa questo profilo”.
- I cookie vengono aggiornati dalla sessione selezionata e salvati nel profilo
  Grayjoy corrente; il client della sorgente viene ricreato con cache invalidata.
- La conferma è bloccata sulle pagine di login/scelta profilo e viene aggiornata
  anche per navigazioni del sito senza ricaricamento completo.

La scelta del profilo personale resta un'azione dell'utente. Il flusso non aggira
PIN, restrizioni o autenticazione della sorgente.

## Esportazione di file scaricati

- Ogni esportazione audio/video compare nei Job attivi e nella notifica generale.
- Avanzamento per l'intero gruppo, numero di elementi, titolo corrente e fase:
  preparazione, creazione del file multimediale e copia nella cartella.
- Annullamento dedicato con conferma: conserva originali e file già completati,
  eliminando solo l'output incompleto.
- I callback del progresso vengono limitati in frequenza e rimossi al termine
  o alla cancellazione; il cambio profilo interrompe i job del profilo precedente.

Verificata un'esportazione reale di un file completato sul Pixel e ripulita
soltanto la copia di prova. Test unitari dell'app e del backend superati;
5 test strumentati mirati superati, inclusi recupero HTTP e UI dei job.

Modifiche locali installate come grayTEST. Nessun nuovo tag o pubblicazione.
