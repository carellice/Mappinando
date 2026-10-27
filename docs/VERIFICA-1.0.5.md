# Verifica della versione 1.0.5

Miglioramento della ricerca mantenendo servizi gratuiti, senza chiavi API o abbonamenti.

## Cambiamenti

- Photon richiede fino a 30 risultati invece di 10.
- Le ricerche per nome e indirizzo consultano anche Android Geocoder, se disponibile, senza interrompersi quando Photon restituisce risultati parziali. Un problema di una fonte non elimina i risultati dell’altra.
- Nome e città determinano la pertinenza; la distanza ordina i risultati a parità di corrispondenza. I duplicati con lo stesso nome normalizzato entro 40 metri sono uniti conservando categoria, città e indirizzo. Le raccolte risultanti hanno un limite di 40 elementi.
- Per un nome con prefisso come Bar o Caffè viene eseguito al massimo un tentativo Photon con il nome integro quando mancano corrispondenze pertinenti.
- Gli indirizzi risolti dal dispositivo senza una scheda del locale riconoscibile sono separati come Indirizzi da verificare. Il nome digitato rimane una proposta per il form, senza inventare una scheda del locale nel catalogo.
- La nuova opzione Non lo trovi? Aggiungilo per indirizzo permette di specificare nome e indirizzo separatamente, controllare la mappa e salvare esplicitamente.
- Nessuna ricerca a ogni carattere, cache temporanee con scadenza più breve per risultati vuoti, richieste serializzate per fonte, timeout, cancellazione e gestione degli errori.

## Caso segnalato dall’utente

Query reale: **Arrosticini Divini Roma**, filtro Ristoranti.

Photon restituisce altre sedi e locali simili. Sul dispositivo di prova Google APIs Android 15, Android Geocoder restituisce invece **Via della Bufalotta, 871, La Cinquina - Bufalotta RM**, coordinate circa **41.9694171, 12.5499763**. L’app mostra questo indirizzo prima dei risultati simili, con l’avviso che occorre verificare la corrispondenza con il locale. L’indirizzo è coerente con quello pubblicato sul [sito ufficiale](https://www.arrosticinidivini.it/contatti/).

Non è una conferma che il ristorante sia presente nel catalogo Photon né una garanzia di copertura completa. La disponibilità e le risposte del geocoder dipendono dal dispositivo. Non è stata inserita nel codice dell’app una voce fissa per il locale segnalato.

## Test e prove

- **31 test unitari superati**: 14 di dominio, 5 dei modelli di ricerca, 5 di pertinenza/duplicati e 7 di aggregazione gratuita. Verificano anche fonti indipendenti, errore di una o entrambe, cancellazione, cache, tentativo alternativo, rifiuto delle città come ristoranti e suggerimenti di indirizzo chiaramente non verificati.
- **16 test Android superati** sul solo emulatore privato `emulator-5562`: i 14 della versione precedente, più il flusso di indirizzo incerto e l’aggiunta di un locale assente cercando l’indirizzo separatamente. I dati dei flussi sono isolati dalle preferenze dell’utente.
- **1 prova aggiuntiva con servizi reali superata**: la ricerca combinata restituisce l’indirizzo romano con il nome digitato conservato soltanto come proposta. Il test è facoltativo e richiede `-e liveSearch true`; non introduce dipendenze di rete nei test di regressione ordinari.
- Android Lint: **0 errori**, 20 avvisi e 1 suggerimento.
- Schermate reali controllate visivamente: elenco con indirizzo e risultati simili distinti, mappa centrata sul punto, avviso leggibile e pulsante facoltativo Aggiungi alle tappe. La raccolta di esempio rimane di sei luoghi.

Output locali: `.tools/free-search-regression-results.txt`, `.tools/free-search-live-results.txt`, `.tools/free-search-live-log.txt`. Le schermate sono in [ANTEPRIMA.md](ANTEPRIMA.md).

## Pacchetto e installazione

Versione **1.0.5**, codice **6**, generata con lo script di incremento automatico. Firma debug e hash del pacchetto verificati. Aggiornamento del Pixel personale tramite `install -r`, senza disinstallare o cancellare la raccolta. Le prove riguardano emulatori Android, non un telefono fisico.
