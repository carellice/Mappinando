# Rilasci GitHub

La distribuzione pubblica è in [carellice/Mappinando](https://github.com/carellice/Mappinando). Gli APK vengono allegati alle release GitHub, senza inserire i pacchetti binari nella cronologia Git.

## Preparazione

1. Installa Git, GitHub CLI, un JDK compatibile e l’SDK Android con build-tools 36.0.0.
2. Esegui `gh auth login` e verifica `gh auth status`.
3. Mantieni la stessa chiave di firma delle installazioni esistenti. La configurazione pubblica contiene soltanto l’impronta del certificato in `release.properties`, mai la chiave privata.
4. Salva le modifiche in un commit su `main` e sincronizzale con `origin/main`.

Gli APK attuali sono build debug firmate per l’installazione diretta. Su questo computer Gradle usa la chiave di sviluppo Android già utilizzata per le versioni precedenti. Custodisci una copia privata di quella chiave: rigenerarla su un altro computer produce una firma diversa e impedisce di aggiornare le installazioni esistenti. La pubblicazione su Google Play richiede una configurazione di release dedicata.

## Pubblicazione

Doppio clic su **PUBBLICA RELEASE.bat**, oppure dalla cartella del progetto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Publish-Release.ps1
```

Il rilascio normale incrementa la patch e il codice Android, per esempio `1.0.7 / 8` diventa `1.0.8 / 9`. Sono supportati anche:

```powershell
# Mostra il prossimo rilascio, senza build o modifiche.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Publish-Release.ps1 -DryRun

# Incremento minor: 1.0.7 -> 1.1.0.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Publish-Release.ps1 -Increment minor

# Riprende un rilascio preparato ma interrotto, senza nuovo incremento.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Publish-Release.ps1 -Resume

# Note personalizzate; il file deve essere salvato nel commit di partenza.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Publish-Release.ps1 -NotesFile .\docs\mie-note.md
```

Gli argomenti possono essere passati anche al BAT. In console automatizzate imposta `MAPPINANDO_NO_PAUSE=1` per evitare la pausa finale del BAT.

## Cosa fa lo script

1. Verifica account GitHub, repository configurata, branch, modifiche locali e sincronizzazione con il remoto. Un lock impedisce due pubblicazioni simultanee.
2. Usa lo script di build esistente, che esegue compilazione, test unitari e Lint prima di confermare la nuova versione in `version.properties`.
3. Controlla metadati, SHA-256 e certificato dell’APK. Non pubblica aggiornamenti con una firma diversa.
4. Crea il commit della sola versione e il tag `vX.Y.Z`; invia branch e tag con un push atomico, senza forzature.
5. Prepara una release in bozza e carica quattro allegati: APK, `.sha256`, `.build.json` e `Mappinando-update.json`.
6. Verifica che gli allegati siano caricati e abbiano le dimensioni previste. Quando GitHub espone il digest dell’asset, lo confronta anche con l’hash locale.
7. Pubblica la bozza come release stabile e più recente soltanto dopo queste verifiche.

La build locale e la pubblicazione sono due passaggi distinti: una build fallita non consuma una versione; un’interruzione di rete dopo una build riuscita conserva la versione e il file locale `.release-pending.json`. Usa **-Resume** per completarla. Non modificare i sorgenti mentre è in corso un rilascio. Lo script non sposta tag preesistenti e non sovrascrive gli allegati di una release già pubblicata.

Se esiste `docs/release-notes/X.Y.Z.md`, viene usato come testo della release; altrimenti viene preparato un testo generico. Le note possono essere specificate esplicitamente con `-NotesFile`.

## Configurazione e test

`release.properties` contiene il percorso pubblico della repository e l’impronta SHA-256 del certificato di firma. Il percorso viene incorporato nell’app tramite BuildConfig. Non aggiungere token, password o chiavi a questo file.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-ApkVersioning.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-GithubPublishing.ps1
```

I test usano dati locali isolati e non pubblicano nulla. APK, SDK locali, cache, file di configurazione della macchina, chiavi e credenziali sono esclusi da Git.
