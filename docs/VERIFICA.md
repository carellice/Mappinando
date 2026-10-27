# Verifica della versione 1.0.0

Verifica eseguita il 26 ottobre 2026 su Windows, con JDK 21, Android SDK 36 ed emulatore Android 15 / API 35 x86_64 con accelerazione hardware.

| Controllo | Esito |
|---|---|
| Compilazione APK debug | Superato |
| Test unitari di validazione | 8 superati, 0 falliti |
| Test Android sul dispositivo virtuale | 12 superati, 0 falliti |
| Android Lint | 0 errori; segnalazioni non bloccanti di aggiornamento/API/stile |
| Firma APK v2 | Verificata con apksigner |
| Avvio, cartografia, pin e nomi dei luoghi | Verificati sull’APK finale |
| Layout mappa, raccolta, dettaglio e attività | Ispezionati visivamente |
| Rotazione verticale/orizzontale | Verificata, nessun arresto dell’app |
| Permessi di posizione | Esclusi dal manifest finale |

## Cosa coprono i test

Gli 8 test unitari coprono normalizzazione, campi obbligatori, coordinate, limiti dei testi e coerenza degli identificativi delle attività.

I 7 test del repository su Android verificano creazione degli esempi solo al primo avvio, raccolta vuota persistente, aggiunta/modifica/eliminazione, preferiti, visitati, checklist, rigetto di dati non validi e recupero da record JSON danneggiati.

I 4 test Compose delle schede verificano nome obbligatorio, salvataggio di categoria e attività ancora nel campo, azioni del dettaglio e conferma di eliminazione.

Il test completo attraversa inserimento di coordinate anche negative, creazione di luogo e attività, salvataggio, preferito, visitato, completamento, filtri, visualizzazione delle attività completate ed eliminazione confermata. Usa dati isolati dalle preferenze dell’app.

## Limiti della verifica

Le prove di esecuzione sono state svolte su emulatore API 35; non è stato collegato un telefono fisico. Il servizio Android Geocoder può non essere disponibile nell’immagine AOSP: il flusso completo usa coordinate manuali. La ricerca su un telefono dipende dal provider Android del dispositivo. Gli errori di rete e servizio mostrano un messaggio con la possibilità di scegliere sulla mappa o inserire coordinate.

L’APK consegnato è firmato con la chiave di sviluppo, per l’installazione diretta e la prova. La pubblicazione su Google Play richiede una firma di release dedicata.
