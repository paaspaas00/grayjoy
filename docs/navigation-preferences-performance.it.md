# Navigazione e preferenze: verifica sul telefono

Dispositivo: Redmi 22011119UY, Android 13, ARM64, 1080×2400 a 440 dpi, caratteri 1,0.
Installazione e prove esclusivamente sul pacchetto grayTEST; nessuna modifica al pacchetto
Grayjoy principale, ai dati delle librerie o alle impostazioni di densità del telefono.

## Interventi

- Sostituita la transizione con due pagine simultanee con una sola pagina entrante.
  Dissolvenza e lieve scala vengono aggiornate nel layer grafico, per 140 ms anziché
  240 ms. L'uscita salva subito lo stato della pagina; nessun contenuto nascosto resta
  attivo a intercettare input o Back.
- Le 27 preferenze sono ora elementi lazy indipendenti, con chiavi e tipi di contenuto.
  Le sezioni mantengono intestazioni, divisori e angoli di gruppo.
- Layout delle righe standard a tre slot misurati una sola volta: icona, testo,
  azione. Conservati dimensioni dei controlli, testi, impostazioni e palette.
- Cache di prefetch specifica per le righe leggere delle preferenze, più ampia di
  quella usata nelle liste con miniature. La cache è limitata e ridotta sui dispositivi
  con poche risorse; non vengono mantenute intere pagine fuori schermo.

La granularità degli elementi e il rinvio delle letture dello stato animato seguono
le [indicazioni ufficiali di Compose](https://developer.android.com/develop/ui/compose/performance/bestpractices).

## Misure

Sequenza ripetuta sul telefono: Home/Prefs/Raccolta/Prefs e sei swipe nelle preferenze,
con una passata di riscaldamento. Misure `dumpsys gfxinfo`, debug ARM64, senza registrazione
video durante i campioni. Non sono benchmark di release e non misurano caricamenti remoti.

Scroll preferenze, campione iniziale → campione finale:

- mediana frame: 13 → 11 ms;
- 95° percentile: 20 → 19 ms;
- 99° percentile: 61 → 21 ms;
- deadline mancate: 7,11% → 5,26%.

Il cambio tab ha meno sovrapposizione e un'animazione più breve; rimangono picchi di prima
composizione attorno a 250 ms (99° percentile dei frame nella sequenza). Non si dichiara
quindi un tempo garantito di apertura né l'assenza assoluta di scatti. I contatori di jank
della navigazione sono variabili tra le passate e non dimostrano da soli un miglioramento.

I test verificano l'eliminazione immediata della pagina uscente, il ripristino del suo
stato e la composizione lazy delle righe. Catture del telefono e dell'emulatore confrontate
per controllare anche la resa visiva, non soltanto i contatori.

Log e immagini di verifica sono esterni al repository, in `phone-ui-check-20260908`.
Nessuna pubblicazione.
