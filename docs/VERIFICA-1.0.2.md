# Verifica della versione 1.0.2

Aggiornamento del 26 ottobre 2026: mappa immersiva, nuova icona, intestazioni comprimibili, raccolta per città e dettaglio del luogo a pagina intera.

## Controlli

| Gruppo | Esito |
|---|---|
| Validazione e raggruppamento città | 14 test unitari superati |
| Repository e migrazione dei dati | 10 test Android superati |
| Editor e dettaglio | 5 test Android superati |
| Intestazioni comprimibili e gruppi di città | 2 test Android superati |
| Flusso completo e mappa a tutto schermo | 2 test Android superati |
| Android Lint | Nessun errore |

I due flussi completi sono stati rieseguiti dopo aver adeguato il controllo al componente cartografico nativo e rifinito la transizione di ritorno. Le prove strumentali usano un emulatore API 35 dedicato e preferenze isolate; non cancellano i dati del Pixel personale.

## Dati già salvati

I test verificano che l’aggiunta della città conservi identificativi, note, preferiti, stato visitato e attività. I vecchi indirizzi nel formato esplicito `indirizzo · città` consentono di recuperare la città. Gli indirizzi non strutturati non producono città inventate: il campo può essere completato nell’editor. I gruppi non distinguono maiuscole/minuscole e sono ordinati secondo la lingua italiana.

## Interfaccia

Verificati visivamente l’espansione della mappa, il ritorno con Indietro, il dettaglio a pagina intera, le intestazioni prima e dopo lo scorrimento e la nuova icona nel sistema Android. L’app mantiene una sola mappa nativa passando tra anteprima e tutto schermo. Filtri e comando per le attività completate restano raggiungibili anche a intestazione compressa.

La nuova icona è vettoriale: i livelli adattivi sono disponibili da API 26 e la variante monocromatica da API 33. La versione viene mostrata nell’app tramite BuildConfig.

## APK

Generato con lo script automatico: **1.0.2**, codice **3**. Compilazione, test unitari e lint superati; firma verificata con apksigner e versione incorporata confermata con aapt. L’APK è firmato per la prova diretta con la stessa chiave debug delle versioni precedenti.

Mappa e geocodifica richiedono Internet; quando il provider Android non fornisce la città, questa resta compilabile a mano. Le prove di esecuzione sono su emulatore e non su telefono fisico.
