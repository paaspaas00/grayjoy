# Secondo audit di stabilità — 12 settembre 2026

Questo passaggio approfondisce importazioni, cancellazione delle coroutine,
paginazione, Cast/collegamento PC e selezione degli aggiornamenti. Si aggiunge
all'audit precedente; non sostituisce un test continuativo di lunga durata.

## Difetti corretti

- **Lavori asincroni sostituiti:** il `finally` di una richiesta annullata poteva
  rimuovere dal registro una nuova richiesta con la stessa chiave. Un registro
  condiviso controlla ora l'identità del lavoro prima di rimuoverlo e gestisce
  anche completamento immediato e scope già cancellato. Applicato a download,
  metadati, icone dei canali, importazioni e paginazione degli extra.
- **Commenti e consigliati:** richieste indipendenti non si annullano più tra loro;
  risultati ed errori di lavori cancellati non aggiornano lo stato successivo.
- **Importazioni e profili:** la chiusura del dialogo e il cambio profilo annullano
  il lavoro; repository e preferenze vengono catturati prima delle sospensioni.
  Il pulsante di conferma non avvia due importazioni contemporaneamente.
  La cancellazione non viene trasformata in un normale errore di installazione.
  Le scritture della libreria sono su IO e la costruzione dei modelli su Default.
  I dati già importati non vengono eliminati annullando.
- **Importazioni grandi:** il percorso NewPipe ora copia DB e ZIP su file
  temporanei con buffer da 64 KiB, evitando copie compresse/decompresse complete
  nella heap. Limiti su input, espansione totale e numero di entry; anche i dati
  contenuti in entry dichiarate directory sono conteggiati. DB duplicati,
  provider che non avanzano e cancellazione sono gestiti con pulizia dei file.
- **Date e progresso importati:** valori estremi non fanno traboccare la
  conversione secondi/millisecondi, né producono progressi errati o negativi.
- **Esportazione:** preparazione della destinazione e copia su IO, controllo
  della cancellazione durante la copia, eliminazione degli output parziali;
  il Transformer non parte se la richiesta è già stata annullata.
- **Aggiornamenti:** la lista GitHub viene confrontata per versione numerica,
  escludendo bozze e prerelease. Una vecchia release ripubblicata non nasconde
  quella più recente. Tag malformati o con componenti numeriche fuori scala
  non sono interpretati eliminando silenziosamente parti della versione.
- **Parser HTTP locali:** limite aggregato agli header, limite per riga e per
  corpo; rifiuto di header duplicati, framing ambiguo, Content-Length invalido
  e richieste troncate. Il parser è condiviso dai due server locali.
- **Timestamp PC:** valori negativi o estremi non eludono il controllo di
  freschezza tramite overflow di `abs(now - timestamp)`.
- **Cast HLS:** playlist figlie senza estensione vengono riconosciute tramite
  i tag HLS. Gli URL locali rimangono stabili tra ricaricamenti; il registro
  ha un limite di dimensione e le risposte di sessioni precedenti non possono
  ripopolarlo dopo un cambio video.
- **Cast e byte range:** supportati i range suffix; rifiutati range invertiti,
  multipli o con overflow. `Content-Range` riflette i byte effettivamente
  consegnati e non produce un estremo finale `*` non valido. Risposte parziali
  incoerenti vengono rifiutate. Lettura dei manifest limitata a 2 MiB.

## Test e stress ripetibili

Suite JVM finale: **261 test app + 33 test backend**, senza errori o fallimenti.
Build debug completata. Il controllo lint completo della release **non è pulito**:
sono presenti segnalazioni pregresse di traduzioni/plurali mancanti, opt-in Media3,
lettura di risorse Compose e dichiarazione hardware della fotocamera. Non sono
stati disabilitati controlli né aggiunta una baseline per nasconderli. Questo esito
del controllo completo aggiorna la formulazione sintetica del primo rapporto.
Esito finale: 146 errori lint, 356 warning e 4 suggerimenti. Le 146 righe segnalate
come errore sono già presenti in HEAD; i nuovi opt-in necessari in questo audit
sono stati dichiarati. Le segnalazioni restano aperte e non equivalgono a 146
crash riproducibili.

I test generativi usano seed fissi e verificano proprietà/invarianti; non sono
una campagna di fuzzing coverage-guided dell'intera applicazione:

| Area | Casi e oracolo |
| --- | --- |
| HTTP | 20.000 richieste generate, framing e allocazioni entro i limiti |
| Byte range | 30.000 coppie di estremi, confronto con aritmetica BigInteger |
| Versioni | 20.000 confronti, tuple numeriche e antisimmetria |
| Coroutine | 5.000 operazioni di sostituzione/cancellazione e pulizia tardiva |
| Progresso importato | 10.004 durate, inclusi estremi Long; valore finito e rapporto corretto |
| Registro HLS | 10.000 ricaricamenti identici e 10.000 finestre mobili da sei segmenti |

Sul Pixel 9a, usando esclusivamente grayTEST:

- Test con socket reali sul proxy Cast: master e playlist figlia senza estensione,
  20 ricaricamenti con URL stabili, stream completo, range chiuso, suffix, range
  invalido e HEAD. Il server upstream è una sorgente sintetica controllata.
- Importazione dal percorso di produzione di un ZIP con un database SQLite
  sintetico da oltre 20 MiB: dati letti correttamente, file sorgente invariato,
  nessun temporaneo rimasto.
- Entrambi i test strumentati passati. Installazione con `adb install -r` e runner
  avviato manualmente, senza disinstallare grayTEST. Hash della libreria e delle
  preferenze identici prima e dopo i test. L'app Grayjoy pubblicata non è stata
  modificata.
- `ApplicationExitInfo` non mostra nuovi crash/ANR di grayTEST nei controlli:
  le uscite registrate sono dovute ad aggiornamenti APK e avvio/fine del runner.

## Limiti

Non è stato verificato un ricevitore Chromecast/FCast fisico in questo passaggio.
I test di rete sono locali e deterministici. I test sul parser non misurano tutte
le combinazioni di server/CDN esterni; quelli sulle coroutine non costituiscono
una prova formale dell'intero ViewModel. Nessun nuovo tag o pubblicazione.
