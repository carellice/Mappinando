# Verifica della versione 1.0.3

Correzione dei controlli della mappa e della transizione tra anteprima e tutto schermo.

## Comportamento aggiornato

I controlli hanno target uniformi da 48 dp e margini da 16 dp. Espansione e centratura sono raccolte in una barra bianca; aggiunta è un’azione verde separata ma allineata. Sulle mappe basse gli strumenti si dispongono su una riga. Il suggerimento di pressione prolungata è fuori dalla cartografia; attribuzione e bussola rimangono nell’area visibile.

La transizione usa un unico avanzamento tra il rettangolo compatto misurato e lo schermo intero. Il layout sottostante mantiene dimensioni costanti anche quando Android mostra o nasconde le barre di sistema. La superficie TextureView resta della stessa dimensione: si anima il ritaglio centrato, evitando lo stiramento del precedente fotogramma del renderer nativo. I margini degli elementi SDK compensano il ritaglio.

Indietro ferma immediatamente la direzione corrente e inverte il movimento dalla posizione raggiunta. I comandi e i tocchi sulla mappa vengono bloccati durante il passaggio, così un doppio tocco non attiva accidentalmente un altro elemento.

## Verifiche eseguite

- 3 test Android su geometria dei controlli, mappe basse e blocco delle azioni.
- 2 regressioni che campionano i fotogrammi intermedi di apertura/chiusura e interrompono l’apertura a metà con Indietro.
- 2 flussi Android completi: tutto schermo/ritorno e creazione, preferiti, attività, filtri ed eliminazione.
- I test controllano che l’ancora compatta non si muova, che il rettangolo prosegua senza inversioni o salti e che rimangano lo stesso oggetto MapView e le stesse dimensioni della superficie nativa.
- 14 test unitari nella generazione automatica dell’APK e Android Lint senza errori.
- Controllo visivo della registrazione reale, anche nei fotogrammi intermedi, senza deformazioni di testo e segnaposti.
- Tocchi fisici sulla ricerca e su una tappa esterne alla mappa, più pressione prolungata all’interno: tutte le azioni ricevono correttamente l’input.

Le sette verifiche Android mirate sono state eseguite sul solo emulatore di test 5560, senza disinstallare l’app dal Pixel personale 5554. I test con dati usano preferenze isolate.

APK generato con lo script automatico: versione **1.0.3**, codice **4**, stessa firma debug per aggiornare le precedenti installazioni conservando i dati. Le prove di esecuzione sono su emulatore Android 15, non su telefono fisico.
