# Verifica della versione 1.0.6

Nell’anteprima di un risultato della ricerca, sotto Aggiungi alle tappe, compare il pulsante secondario **Verifica su Google Maps**. È disponibile anche per indirizzi da verificare e tappe già salvate.

Il collegamento apre l’indirizzo selezionato nell’app Google Maps. Se non è installata o non può essere aperta, viene usato il normale collegamento web nel browser. Per punti senza indirizzo si passano latitudine e longitudine. Il collegamento usa la sintassi [Maps URLs documentata da Google](https://developers.google.com/maps/documentation/urls/get-started), che non richiede una chiave API.

Non viene attivata la navigazione e non viene salvata automaticamente una tappa. L’anteprima rimane nello stato dell’app quando si torna da Google Maps. Se non sono disponibili né Maps né un browser, compare un messaggio leggibile nella scheda.

## Verifiche

- **19 test Android superati** sul solo emulatore privato `emulator-5562`, con preferenze isolate per i flussi.
- Tre nuove verifiche degli intent: indirizzo con caratteri speciali e parametri codificati correttamente; coordinate anche negative e alternativa nel browser; assenza di Maps e browser senza crash.
- Il flusso completo verifica che il pulsante passi l’indirizzo del risultato, mantenga visibile l’anteprima e non modifichi la raccolta prima del salvataggio esplicito.
- **31 test unitari superati** e Android Lint con **0 errori**, 20 avvisi e 1 suggerimento.
- Output Android in `.tools/google-maps-regression-results.txt`.
- Prova reale: dal punto trovato in via della Bufalotta 871 il pulsante avvia il pacchetto `com.google.android.apps.maps`. Tornando all’app, la stessa anteprima e i sei luoghi di esempio sono ancora presenti. Non sono state avviate indicazioni né aggiunte tappe. Schermata verificata in [ANTEPRIMA.md](ANTEPRIMA.md).

Versione **1.0.6**, codice **7**, generata con lo script di incremento automatico. Firma debug e hash verificati; aggiornamento sul Pixel personale con `install -r`, conservando i dati. La funzione resta gratuita e non usa servizi Places con fatturazione.
