# Verifica della versione 1.0.7

Prima release pubblica su [carellice/Mappinando](https://github.com/carellice/Mappinando), con APK nella [release v1.0.7](https://github.com/carellice/Mappinando/releases/tag/v1.0.7).

## Repository e icona

- README con logo PNG generato dall’asset vettoriale, schermate reali, installazione, privacy e guide di sviluppo e rilascio.
- Sorgenti, risorse e documentazione pubblicati. APK locali, SDK, cache, credenziali e chiavi private esclusi dalla cronologia Git. Controllo dei file selezionati prima del primo push.
- Logo Android scalato al 78% attorno al centro nelle varianti foreground, monocromatica e completa, conservando il fondo. Controllo visivo del logo nel ritaglio circolare e delle schermate reali.

## Pubblicazione

Eseguito `Publish-Release.ps1`: incremento automatico da **1.0.6 / codice 7** a **1.0.7 / codice 8**, build, test, Lint, verifica della firma, commit della versione e tag Git. Caricati APK, hash, metadati di build e `Mappinando-update.json`.

Durante la prima prova è stato corretto il recupero dei metadati delle bozze GitHub, passando dal tag all’ID della release. Il comando **-Resume** ha completato la stessa build senza un nuovo incremento. La release è stata pubblicata soltanto dopo la verifica degli allegati. Le correzioni agli strumenti sono salvate separatamente in main; il tag dell’APK conserva i sorgenti dell’app da cui è stato compilato.

## Aggiornamento reale dall’app

Sul solo emulatore privato Google APIs Android 15 `emulator-5562`:

1. Installata la build di sviluppo 1.0.6 con il nuovo sistema di aggiornamento.
2. Avviata l’app: rilevata automaticamente la release pubblica 1.0.7, senza token o login GitHub.
3. Scaricato l’APK reale da GitHub nella cache privata, **61.165.842 byte**. L’app ha verificato hash, package, versione e firma e ha mostrato Pronto da installare.
4. Autorizzata l’origine dalla normale pagina Android e confermato Aggiorna nell’installer di sistema.
5. Android ha completato l’installazione: versione **1.0.7**, codice **8**.
6. Il digest del file delle preferenze delle tappe è rimasto identico prima e dopo l’aggiornamento. Non è stata eseguita alcuna disinstallazione o cancellazione dei dati.
7. Riaperta la nuova app e controllate le release manualmente: schermata Tutto aggiornato, con 1.0.7 installata.

Le schermate sono in [ANTEPRIMA.md](ANTEPRIMA.md). Le prove riguardano emulatori Android, non telefoni fisici.

## Controlli automatici

- **35 test unitari superati**.
- **26 test Android superati**, compresi parser delle release, rifiuto di metadati non validi o di APK di altre applicazioni, firma dell’APK reale, confronto della versione, download e installazione come azioni separate, errori e regressioni della ricerca e della mappa. I flussi usano preferenze isolate.
- **12 test del versionamento APK** e **6 test della pubblicazione** superati senza operazioni GitHub nei test isolati.
- Android Lint: **0 errori**, 24 avvisi e 1 suggerimento.
- Firma dell’APK compatibile con le installazioni precedenti. Certificato SHA-256 pubblico dichiarato in `release.properties`; nessuna chiave privata pubblicata.

Il Pixel personale viene aggiornato con lo stesso APK tramite `install -r`, conservando la raccolta. Il permesso di installare da Mappinando viene richiesto soltanto quando l’utente sceglie l’installazione dall’app.
