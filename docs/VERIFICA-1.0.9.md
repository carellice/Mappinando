# Verifica 1.0.9

Attività completate: onboarding animato, raccolta inizialmente vuota, posizione facoltativa e stabilità della mappa scorrendo le card.

## Verifiche automatiche

- 35 test unitari superati.
- 37 test Android superati su un emulatore dedicato Android 15 con Google APIs, senza usare i dati del dispositivo personale.
- Build debug e APK dei test compilati correttamente; lint senza errori.
- Verifica dello scorrimento reale di otto card con nomi, categorie e checklist differenti: il rettangolo della mappa resta invariato e la MapView mantiene la stessa istanza.
- Card con caratteri ingranditi al 160%: altezza uniforme per nomi lunghi, assenza di attività e checklist lunghe.
- Onboarding: tre passi, avanzamento, uscita anticipata, scelta della posizione e prosecuzione senza posizione; completamento persistente.
- Repository: nuova raccolta vuota e migrazione eseguita una volta sola. Conservati luoghi personali ed esempi modificati; rimossi solo gli esempi identici ai dati originali.
- Regressioni: creazione/modifica delle tappe, ricerca, anteprima, apertura a tutto schermo e inversione dell’animazione, controlli della mappa, UI e sicurezza degli aggiornamenti.

## Prove sul dispositivo

- Introduzione verificata visivamente; screenshot nel README.
- “Salta” apre la mappa vuota senza chiedere permessi. Il riavvio non ripropone l’introduzione.
- Tocco sul mirino: finestra nativa Android con opzioni precisa, approssimativa e rifiuto.
- Rifiuto: l’app resta usabile, il mirino permette di riprovare.
- Posizione precisa: fix GPS simulato vicino al Colosseo, centratura della mappa verificata visivamente.
- Posizione approssimativa: solo `ACCESS_COARSE_LOCATION` concesso, `ACCESS_FINE_LOCATION` negato; posizione di rete simulata e centratura sulla zona di Roma verificata. Provider e autorizzazioni di simulazione ripristinati dopo la prova.
- Richieste limitate a un fix: usata solo una posizione di sistema recente (massimo due minuti), oppure ricerca con timeout di 20 secondi. Annullamento quando l’app passa in background; nessuna cronologia salvata.

L’emulatore non fornisce una posizione di rete fisica: senza fix recenti, il caso di indisponibilità è previsto e consente di continuare con la ricerca manuale. Non sono richiesti account, API a pagamento o servizi Google Play nell’implementazione.

## Distribuzione

APK pubblicato con lo script `scripts/Publish-Release.ps1`, che ripete build, test unitari e lint, controlla la firma e allega il manifest degli aggiornamenti. Installare sopra una versione precedente per conservare la raccolta personale.

Riferimento tecnico: [permessi di posizione Android](https://developer.android.com/develop/sensors-and-location/location/permissions).
