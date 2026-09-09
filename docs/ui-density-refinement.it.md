# Gerarchia e densità adattive

Questa revisione distingue il layout standard da quello compatto. Quest'ultimo si attiva
sui viewport telefonici sotto 390 dp, con altezza inferiore a 650 dp oppure con testo
ingrandito almeno al 125%. Il riferimento Pixel 9a a 411 × 923 dp / testo 100% e i tablet
mantengono la gerarchia standard.

## Layout compatto

- Home: niente titolo duplicato sotto le tab; progressi di caricamento restano visibili
  quando necessari. Più spazio ai video dalla prima schermata.
- Schede: margini ridotti, testo meglio distribuito, metadati secondari essenziali e
  ripple della selezione contenuto negli angoli della scheda.
- Playlist locali e remote: barra principale con Riproduci tutto e menu azioni;
  download, rinomina e copia locale restano accessibili nel menu. Il menu si apre
  completamente e mantiene margine dalla navigazione di sistema.
- Selezione playlist: azioni a icone, senza il grande pulsante testuale che comprimeva il conteggio.
- Prefs: icone decorative omesse, riassunti brevi e pulsante informativo per le spiegazioni
  che non entrano. La nota sul riavvio per il cambio lingua è nel dialogo di scelta.
- Raccolta e ricerca: rimossi header/riquadri ripetitivi; restano conteggio, ricerca e azioni.
- Canale: identità in una riga compatta, descrizione espandibile e azioni secondarie a icone.
- Navigazione: barra meno alta con etichetta solo sulla destinazione selezionata;
  tutte le icone mantengono le descrizioni di accessibilità.

Non viene alterata la scala del testo impostata in Android: vengono usati ruoli tipografici
più appropriati e una disposizione diversa dei componenti.

## Verifiche

- Stessa matrice di sei viewport descritta in `layout-audit-2026-09.it.md`.
- 105 nuove catture, incluse le azioni dei menu sui tre profili compatti.
- Controlli aggiuntivi: primo video subito visibile in Home/playlist; velocità di riproduzione
  già visibile nelle Prefs; menu download/rinomina raggiungibile anche con testo grande.
- 252 test JVM senza errori.
- 27 test UI superati; il test di cattura condizionale è eseguito separatamente per ogni profilo.

Le catture sono nella cartella di lavoro `layout-ux-refinement-20260908/final`, esterna al repository.
Test eseguiti sull'emulatore con dati sintetici. Nessuna pubblicazione o modifica al telefono scollegato.
