# Navigazione indietro — audit settembre 2026

Il tasto nell'header e il gesto di navigazione usano lo stesso percorso per le pagine.
Una gesture annullata non deve cambiare pagina, selezione o posizione del player.
Il gesto del sistema ha precedenza per tastiera e finestre modali; le pagine sottostanti
non intercettano Back mentre sono coperte dal player o sono in uscita.

| Vista / sottovista | Comportamento indietro |
| --- | --- |
| Home e relativi feed | Delega ad Android: ritorno al launcher / app precedente, senza fermare la riproduzione |
| Seguiti, Cerca, Raccolta, Prefs | Home |
| Sorgenti aperto dalla navigazione tablet | Home |
| Sorgenti aperto da Prefs o Cerca | Vista di provenienza |
| Canale aperto da feed, ricerca, playlist o altro canale | Vista effettiva di provenienza, con stato di scroll salvato |
| Canale aperto da Now Playing | Now Playing dello stesso contenuto, se ancora attivo |
| Playlist locale | Raccolta → Playlist, con posizione precedente della lista |
| Playlist remota da canale / ricerca | Canale / ricerca di provenienza |
| Now Playing senza playlist | Minimizza mantenendo il contesto di navigazione |
| Now Playing con playlist | Minimizza e mostra la playlist attiva mantenendo la posizione di scroll da cui è stato avviato il video; nessuno scorrimento automatico sul brano corrente |
| Player fullscreen | Esce solo dal fullscreen; il successivo Back minimizza |
| Player PiP | Resta sotto il controllo del sistema; il ritorno all'app mantiene il player |
| Selezione video in Raccolta, Seguiti o playlist | Esce dalla selezione prima di cambiare pagina |
| Gestisci iscrizioni con selezione | Azzera la selezione, senza lasciare la gestione |
| Gestisci iscrizioni senza selezione | Torna a Seguiti |
| Ricerca canale / cronologia / playlist con focus | Chiude tastiera e focus prima della navigazione |
| Opzioni player, cast, filtri, ordinamenti, export | Dismiss della sheet Material 3, senza navigazione della pagina sottostante |
| Rinomina, creazione, conferme eliminazione, scelta playlist | Chiude il dialogo senza confermare modifiche |
| Riordino playlist | Annulla la bozza; solo OK salva l'ordine |
| Prefs: scelte lingua, tema, qualità, velocità, backend | Chiude il dialogo di scelta |
| Profili, computer associati, importazioni, dettagli aggiornamento | Dismiss del dialogo; il lavoro in background segue i pulsanti di annullamento dedicati |
| Login sorgente, scanner QR, selettore documenti, installatore APK | Navigazione dell'Activity o del componente Android dedicato |

## Correzioni rilevate

- La navigazione a singolo ID perdeva il genitore nei percorsi canale → playlist → canale.
  Ora viene conservata una cronologia limitata a 40 destinazioni, incluse le origini dal player.
- Le schermate mantenute sotto Now Playing potevano consumare il gesto prima del player.
  Gli handler delle pagine sono attivi solo sulla pagina effettivamente visibile.
- Il ripristino dell'animazione dopo annullamento poteva essere cancellato insieme alla coroutine:
  la trasformazione viene ora azzerata anche in quel caso e rispetta il bordo di partenza.
- Sorgenti, quando aperto dalla barra laterale tablet, poteva uscire dall'app anziché tornare a Home.
- Le selezioni non consumavano Back in modo uniforme. Ora vengono chiuse prima della pagina.
- Cronologia di navigazione e stati salvati sono separati tra profili.
- Il riordino usava un'altezza fissa di 52 dp. Ora usa la geometria misurata, scorre ai bordi,
  conserva la bozza durante aggiornamenti e scarta ID eliminati/duplicati prima del salvataggio.

## Compatibilità e verifica

Il minimo resta Android 9 / API 28. Non vengono chiamate direttamente API del sistema
non disponibili nelle vecchie versioni: gli handler passano da AndroidX Activity Compose.
Il progresso predittivo viene usato dove fornito dal sistema, mentre pulsante Back e
vecchie versioni completano normalmente la stessa azione. Dialoghi e sheet mantengono
il supporto della versione Material 3 già utilizzata dal progetto.

Riferimento: [documentazione Android sul predictive back](https://developer.android.com/develop/ui/compose/system/predictive-back).

Test aggiunti: cronologia e percorso del player; riordino e aggiornamenti concorrenti;
selezione con gesto annullato/confermato; conservazione dello scroll anche quando cambia brano; trascinamento nel dialogo.
I test UI usano dati in memoria, senza accedere a librerie o file scaricati degli utenti.

Esito finale: build pulita riuscita, 247 test JVM e 24 test UI superati sul Pixel collegato
(API 37). Verificato anche l'avvio dell'app reale grayTEST, senza dialoghi di crash.
Gli hash delle librerie principale e privata sono invariati prima/dopo i test.
Non è stato eseguito un test hardware su Android precedenti; rimane il percorso compatibile AndroidX.

Il ritorno non cerca più il brano corrente nelle pagine remote e non avvia richieste aggiuntive
per centrarlo: la posizione resta quella salvata dalla lista. L'indicatore di riproduzione
è indipendente dalla posizione di scroll.

## Revisione navigazione — 9 ottobre 2026

Audit dei percorsi Home, Seguiti/gestione, Cerca, Raccolta/cronologia/playlist/download,
canali, playlist remote, Sorgenti, Prefs, job, Now Playing, fullscreen e PiP. Nessuna
modifica estetica o di layout fa parte di questo intervento.

| Percorso | Correzione / comportamento previsto |
| --- | --- |
| Entrata o ritorno a Cerca | La textbox non riceve focus automaticamente; la tastiera si apre su richiesta esplicita |
| Cambio filtro di Raccolta | Tastiera e focus vengono chiusi prima di cambiare sottopagina |
| Playlist con selezione → freccia nell'header | Prima esce dalla selezione; solo il successivo Back lascia la playlist, come il gesto di sistema |
| Pagina qualsiasi → scheda di un job | Il job conserva la pagina reale di provenienza, incluse playlist/canale e contesto del player |
| Sorgenti aperto da Prefs → sottopagina → Back | Viene conservata anche l'origine Prefs della pagina Sorgenti |
| Miniplayer → chiusura con X | Ferma la riproduzione senza spostare l'utente dalla pagina che sta consultando |
| Miniplayer → espansione trascinata e annullata | Resta sulla pagina corrente; non apre la playlist di riproduzione |
| Now Playing → minimizzazione completata | Conserva il ritorno alla playlist attiva e la sua posizione precedente |
| Link video esterno mentre si consulta un canale/playlist | Apre Now Playing senza cancellare la pagina sottostante |
| Ricreazione dell'Activity con Now Playing aperto | L'animazione viene inizializzata nello stato espanso salvato, evitando la chiusura immediata |

I modelli dei canali e delle playlist visitati restano disponibili finché presenti nella
cronologia di navigazione (massimo 40 origini), nella pagina attiva o nella playlist in
riproduzione. Le pagine non più raggiungibili vengono rimosse dalla cache di navigazione.
La cronologia salvata mantiene compatibilità con il formato precedente.
Per la ricreazione del processo vengono salvati anche i metadati minimi di instradamento
dei canali non seguiti e delle playlist remote: ID, titolo e identità della sorgente.
Non entrano nel Bundle descrizioni, elenchi di video, credenziali o immagini. Entrambe
le collezioni hanno un limite di 42 elementi e 16.384 caratteri ciascuna. Due test di
ripristino svuotano esplicitamente i cataloghi del ViewModel prima della ricreazione.

Le playlist remote conservano inoltre sei snapshot completi per 15 minuti: contenuti,
ordine e cursore di paginazione. Il ritorno non ricostruisce soltanto la prima pagina.
Anche i canali conservano sei snapshot per 15 minuti, includendo tab selezionata,
ricerca e pagine già caricate. I caricamenti annullati riprendono correttamente al
ritorno; un cambio di tab cancella anche il paging precedente e i risultati obsoleti
non possono modificare la tab nuova.
I callback sono associati alla generazione della richiesta, così una risposta o un
`finally` vecchio non può modificare una playlist aperta successivamente, nemmeno se si
torna allo stesso ID. Cambio profilo, backend, autenticazione o invalidazione cache
annullano le richieste e invalidano questi snapshot; una pagina ancora visibile si ricarica.

Il caricamento completo non restituisce più i video della nuova playlist quando cambia
la navigazione. La paginazione termina con errore in presenza di otto pagine consecutive
senza nuovi elementi oppure dopo mille pagine; un cursore ripetuto resta valido se il
pager continua ad aggiungere video. In caso di errore non parte una riproduzione o una
copia locale incompleta. Now Playing si apre soltanto quando la coda richiesta è pronta.
L'annullamento di un download remoto non interrompe un'operazione diversa sulla playlist.

La pagina degli Shorts resta ancorata all’ID richiesto/in riproduzione: inserimenti,
riordini e nuove pagine del feed non valgono come una swipe verso un altro video.
La selezione aspetta che il pager abbia completato il layout delle chiavi corrispondenti.

Regressioni aggiunte: otto test UI con dati in memoria e player inattivo, più casi unitari
per i percorsi di chiusura/ripristino e 1.000 sequenze generate di cronologia. I test non
leggono né modificano la libreria reale.

Verifica integrata del 9 ottobre: 378 test JVM superati, 43 test strumentati superati sul
Pixel prima della disconnessione e 48 test Android sulla build finale nell’emulatore API 35.
L’emulatore ha rilevato il blocco HTTP presente sulle versioni Android precedenti: ripristinata
nel manifest Compose l’opzione già presente nel manifest legacy, necessaria per sorgenti
HTTP e trasporti locali. Dopo la correzione anche gli otto test HTTP sono passati.
Build debug e release e lint vitale release superati. Nessun nuovo crash osservato nelle prove.

Il filtro della cronologia è stato provato sul Pixel con un elemento reale: identificazione
dalla categoria YouTube, persistenza e inclusione/esclusione nei filtri musicali corrette.
Il precaricamento Shorts è verificato anche con test di byte effettivamente riutilizzati,
join della risoluzione in corso, inversione della swipe e completamenti tardivi dopo cambio
profilo. La cache è limitata a cinque descrittori e 3 MiB di prefissi in RAM, con URL validi
per al massimo tre minuti; il traffico speculativo si ferma se il player deve ribufferare.
I risultati di settembre riportati sopra restano riferiti al loro round.
