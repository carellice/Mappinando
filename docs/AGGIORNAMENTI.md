# Aggiornamenti dall’app

Apri le informazioni di Mappinando dal pulsante con la bussola, poi **Aggiornamenti dell’app**. La funzione legge le release stabili della repository pubblica configurata in `release.properties`.

All’avvio l’app controlla al massimo una volta al giorno. Se trova una versione più recente, propone di aggiornarla quando nessun form o dettaglio è aperto e la mappa non è in transizione. Un avviso rimandato non viene riproposto per la stessa versione; la ricerca manuale resta disponibile.

## Download e installazione

1. **Scarica aggiornamento** recupera l’APK nella cache privata dell’app e mostra l’avanzamento. È possibile annullare il download.
2. Prima di considerarlo pronto vengono controllati dimensioni, hash SHA-256, identificativo dell’app, versione incorporata e certificati di firma rispetto all’installazione corrente.
3. **Installa aggiornamento** apre l’installer Android. Se serve, viene prima mostrata la pagina di sistema per autorizzare le installazioni da Mappinando.
4. Android chiede la conferma finale. L’installazione non è silenziosa. Aggiornando senza disinstallare, le tappe e le attività vengono conservate.

La finestra di aggiornamento si può chiudere durante il download e riaprire dalle informazioni. Il ViewModel conserva il lavoro durante la rotazione della schermata. Se Android termina il processo, è possibile ricontrollare la release e riprendere il flusso; un APK già presente viene riutilizzato soltanto dopo una nuova verifica. I download parziali non vengono installati.

## Formato della release

Lo script di pubblicazione allega `Mappinando-update.json` insieme all’APK. Esempio di struttura, con hash e dimensioni puramente illustrativi:

```json
{
  "schemaVersion": 1,
  "applicationId": "it.mappinando.app",
  "versionName": "1.0.7",
  "versionCode": 8,
  "minSdk": 26,
  "apk": "Mappinando-1.0.7-debug.apk",
  "sha256": "<64 caratteri esadecimali>",
  "sizeBytes": 61100000
}
```

Il confronto usa il codice Android numerico, non l’ordine alfabetico delle versioni. Bozze e prerelease non vengono proposte. Il tag deve corrispondere alla versione e gli allegati devono appartenere alla repository configurata; il download segue soltanto collegamenti HTTPS di GitHub e dei suoi host di distribuzione ammessi.

Il manifest descrive l’APK, ma la fiducia finale nella firma viene verificata rispetto all’app installata. Il FileProvider espone soltanto la cartella degli aggiornamenti e concede all’installer l’accesso temporaneo al file selezionato. Non sono necessari permessi di lettura o scrittura dell’archivio condiviso.

## Connessione e privacy

Non serve un account GitHub, né sono presenti token nell’app. La lettura usa le API pubbliche GitHub e i download delle release. GitHub può limitare temporaneamente le richieste o non essere raggiungibile: il controllo automatico non interrompe l’uso della mappa e il controllo manuale permette di riprovare. Non vengono inviati luoghi, note o attività durante gli aggiornamenti.

Riferimenti: [release GitHub](https://docs.github.com/en/rest/releases/releases), [FileProvider Android](https://developer.android.com/reference/androidx/core/content/FileProvider), [autorizzazione alle installazioni](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES).
