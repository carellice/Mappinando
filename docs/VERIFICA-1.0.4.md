# Verifica della versione 1.0.4

Ricerca nella mappa a tutto schermo, attività per nome e anteprima prima del salvataggio.

## Comportamento

- La mappa estesa ha una barra di ricerca raggiungibile mentre la si esplora. Il centro viene aggiornato al termine dei movimenti della mappa.
- La ricerca trova indirizzi, città e punti di interesse per nome. I filtri comprendono Ristoranti, Bar e caffè, Negozi e Da visitare. Una ricerca generica come `ristoranti` cerca vicino al centro della mappa.
- Scegliere un risultato sposta e ingrandisce la stessa mappa nativa, mostra un pin provvisorio e un’anteprima animata. La raccolta non cambia finché non si sceglie Aggiungi alle tappe e si conferma il form con Salva.
- Nome, indirizzo, città e categoria riconosciuta vengono proposti nel form. La chiusura dell’anteprima non salva niente. Annullare il form riporta all’anteprima.
- Le tappe già salvate sono ricercabili anche offline: Apri tappa mostra il luogo esistente senza duplicarlo.
- Il MapView mantiene dimensioni costanti durante l’espansione e il ritorno, come nella versione precedente. La selezione può spostare la camera anche mentre lo stile cartografico si sta caricando.

## Verifiche

Eseguiti sull’emulatore privato Android 15 `emulator-5560`, con preferenze isolate:

- 4 test del flusso di ricerca: ricerca a tutto schermo e coordinate effettive della camera prima del salvataggio; salvataggio esplicito con categoria corretta; apertura della tappa esistente; chiusura senza salvare; errore di rete, nuovo tentativo e rimozione dei risultati precedenti al cambio della query.
- 3 test del parser Photon: coordinate GeoJSON nel giusto ordine, nome e indirizzo, categoria del locale, elementi incompleti o non validi e duplicati.
- 3 test di disposizione dei comandi, 2 regressioni dell’animazione e 2 flussi completi già presenti, aggiornati al nuovo passaggio di anteprima.
- Totale: **14 test Android superati**. Output locale in `.tools/search-regression-results.txt`.
- **19 test unitari superati**, di cui 5 nuovi su query, filtri, codifica dei parametri e riconoscimento delle tappe già salvate.
- Android Lint: **0 errori**, 20 avvisi e 1 suggerimento.

Prova reale nell’app, senza sostituire il servizio di ricerca: `Nerino Dieci Milano` con filtro Ristoranti restituisce **Nerino Dieci Trattoria**, Via Nerino 10, Milano. La selezione centra la mappa e mostra il pin con il pulsante Aggiungi alle tappe. La raccolta di esempio resta di sei luoghi. Schermate reali controllate visivamente in [ANTEPRIMA.md](ANTEPRIMA.md).

## Servizi e dati

Photon su `photon.komoot.io` fornisce i risultati OpenStreetMap. Le chiamate partono solo su invio esplicito, sono serializzate e hanno timeout e cache temporanea. Non vengono effettuate richieste a ogni carattere. Il centro della mappa viene arrotondato a quattro decimali per la priorità geografica; non si usa il GPS. La copertura non comprende necessariamente tutte le attività. Android Geocoder resta come alternativa per gli indirizzi e per la città proposta dopo una pressione prolungata.

Riferimenti primari consultati: [progetto Photon e condizioni del servizio pubblico](https://github.com/komoot/photon), [API Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md). Informazioni e crediti aggiornati anche nell’app e nel README.

## Pacchetto

APK **1.0.4**, codice **5**, generato con `scripts/Build-Apk.ps1`. Firma debug verificata e compatibile con gli aggiornamenti precedenti. L’installazione sul Pixel personale usa `install -r`, senza disinstallare né cancellare la raccolta. I test automatici sono stati eseguiti esclusivamente sull’emulatore privato. Le prove riguardano emulatori Android, non telefoni fisici.
