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
