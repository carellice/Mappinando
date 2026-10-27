# Verifica della versione 1.0.1

Aggiornamento del 26 ottobre 2026: generazione automatica dell’APK con incremento di versione e nuova navigazione flottante.

## Generazione reale

Eseguito `scripts/Build-Apk.ps1` con incremento predefinito patch. Compilazione, test unitari e Android Lint sono riusciti; lo script ha prodotto `releases/Mappinando-1.0.1-debug.apk`, SHA-256 e metadati `.build.json`. Solo dopo il successo, `version.properties` è passato da `1.0.0` / codice `1` a `1.0.1` / codice `2`.

Firma APK verificata con `apksigner`; `aapt` conferma versione incorporata **1.0.1**, codice **2**, package `it.mappinando.app`. Un successivo `-DryRun` propone **1.0.2**, codice **3**, senza modificare la versione salvata.

## Test

| Gruppo | Esito |
|---|---|
| Validazione dominio | 8 test superati |
| Persistenza e UI Android | 12 test superati su emulatore API 35 |
| Script di versionamento, PowerShell 5.1 | 12 test superati |
| Android Lint | Nessun errore |

I test dello script verificano patch/minor/major, overflow, anteprima senza modifiche, errore di build, metadati incoerenti, pubblicazione riuscita, pacchetti già presenti, collisioni durante la build, rollback dopo promozione parziale, lock concorrente, cronologia dei codici e due generazioni consecutive.

Il test Android completo continua a coprire creazione, attività, preferiti, stato visitato, filtri ed eliminazione. Controlla inoltre che i tre pulsanti del nuovo dock espongano correttamente la selezione accessibile.

## Controllo visivo

Installato e avviato l’APK generato dallo script. Verificati dock sospeso, ombra, margini, selettore animato, testi, icone, sovrapposizione alle liste e attribuzione cartografica visibile. Verificato anche il layout orizzontale e il ritorno alla mappa dopo rotazione. Una registrazione reale delle transizioni è in `mappinando-navigation.mp4`.

La barra non intercetta i tocchi negli spazi trasparenti laterali. Le liste hanno spazio finale per portare gli ultimi elementi sopra il dock; mappa e carosello mantengono liberi i propri controlli.

Le prove Android sono su emulatore, senza telefono fisico. L’APK usa la stessa chiave debug del progetto, per poter aggiornare la precedente installazione; la firma di pubblicazione su Google Play resta un passaggio separato.
