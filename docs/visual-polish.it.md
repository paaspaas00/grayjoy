# Revisione visiva finale

La revisione è stata guidata dalle catture, usando titoli di lunghezza diversa e fotografie
di prova (crediti NASA in `app-compose/src/androidTest/IMAGE-CREDITS.md`). Le immagini sono
solo nel pacchetto di test: non vengono aggiunte ai contenuti dell'app.

## Difetti corretti

- **Schede compatte:** rimossa l'altezza riservata artificialmente a due righe di titolo.
  Il contenuto determina l'altezza; autore e metadato secondario condividono una zona compatta.
- **Canale su telefono piccolo:** intestazione su una riga, azioni secondarie nel menu,
  ordinamento accanto alle tab anziché in una riga aggiuntiva sotto la ricerca.
- **Tablet:** colonna di lettura limitata a 760 dp e centrata; pulsante autore limitato a 360 dp.
  Il player e il miniplayer mantengono la propria geometria. Rimosso il marchio duplicato
  nell'header Home quando la barra laterale mostra già il nome dell'app.
- **Interazione:** aree primarie del titolo e della miniatura e area autore verificate con
  tocchi reali; il long tap sull'autore continua a selezionare il video.

## Controllo visivo

Confrontate le sei configurazioni di dimensioni/DPI già documentate, più telefono piccolo
e tablet orizzontale in tema scuro. Controllati proporzioni, testi tagliati, spazi vuoti,
accesso ai controlli, menu, miniplayer e tastiera. I test automatici sono una protezione
contro regressioni funzionali, non il criterio per dichiarare gradevole una schermata.

Catture: cartella di lavoro `visual-polish-20260908/final`, esterna al repository.
I report del dispositivo salvano anche dimensioni/DPI effettivamente applicati.

Nessuna pubblicazione; telefono scollegato e non modificato.

## Seconda revisione del canale compatto

Il confronto del canale scuro mostrava ancora troppo spazio nei controlli prima dei video.
La ricerca usa ora una superficie discreta alta almeno 48 dp, testo breve e conteggio
integrato. L'intestazione non ripete il nome già presente nella barra superiore; l'azione
Segui conserva un bersaglio di 48 dp ma non ha più il grande disco colorato. Eliminata
la riga separata «Video (n)» e ridotti gli intervalli verticali del blocco compatto.

Nel profilo piccolo la prima scheda risale di circa 80 dp. Verificate visivamente la
schermata scura e la ricerca focalizzata con tastiera, più la configurazione con caratteri
ingranditi. Il layout standard di riferimento non viene modificato. Verificate anche
digitazione, cancellazione e azione Cerca della tastiera.

Nuove catture: `channel-density-20260908/final`, esterne al repository. Solo emulatore.
