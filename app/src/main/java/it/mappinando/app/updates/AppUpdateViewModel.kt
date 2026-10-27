package it.mappinando.app.updates

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.mappinando.app.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val client = GithubReleaseClient()
    private val preferences = application.getSharedPreferences("mappinando_updates", 0)
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private val prompt = MutableStateFlow(false)
    val shouldPrompt = prompt.asStateFlow()
    private var work: Job? = null
    private var cancelling = false
    private val folder get() = File(getApplication<Application>().cacheDir, "updates")
    private fun apkFile(release: AppRelease) = File(folder, "mappinando-${release.versionCode}.apk")

    init { checkForUpdates(force = false) }

    fun checkForUpdates(force: Boolean = true) {
        if (work?.isCompleted == false) return
        val elapsed = System.currentTimeMillis() - preferences.getLong("last_check", 0)
        if (!force && elapsed in 0 until 86_400_000L) return
        work = viewModelScope.launch {
            mutableState.value = UpdateState(UpdatePhase.CHECKING)
            try {
                val latest = client.latest()
                preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                if (latest == null || !latest.isNewerThan(BuildConfig.VERSION_CODE)) {
                    mutableState.value = UpdateState(UpdatePhase.CURRENT, message = if (latest == null) "Non ci sono ancora aggiornamenti pubblicati." else null)
                } else if (Build.VERSION.SDK_INT < latest.minSdk) {
                    mutableState.value = UpdateState(UpdatePhase.ERROR, message = "La nuova versione richiede una versione di Android più recente.")
                } else {
                    mutableState.value = UpdateState(UpdatePhase.AVAILABLE, latest)
                    prompt.value = !force && latest.versionCode > preferences.getInt("postponed_version", 0)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = UpdateState(UpdatePhase.ERROR, message = "Non riesco a controllare gli aggiornamenti. Controlla la connessione e riprova più tardi.")
            }
        }
    }

    fun consumePrompt() { prompt.value = false }
    fun postpone() {
        prompt.value = false
        mutableState.value.release?.let { preferences.edit().putInt("postponed_version", it.versionCode).apply() }
    }

    fun downloadUpdate() {
        val release = mutableState.value.release ?: return
        if (work?.isCompleted == false) return
        work = viewModelScope.launch {
            mutableState.value = UpdateState(UpdatePhase.DOWNLOADING, release)
            val partial = File(folder, "mappinando-${release.versionCode}.apk.part")
            try {
                withContext(Dispatchers.IO) {
                    check(folder.isDirectory || folder.mkdirs()) { "Impossibile preparare il download." }
                    val ready = apkFile(release)
                    if (ready.exists()) {
                        try {
                            ApkVerifier.verify(getApplication(), ready, release)
                            return@withContext
                        } catch (_: Exception) { check(ready.delete()) { "Impossibile sostituire il vecchio download." } }
                    }
                    client.download(release, partial) { bytes -> mutableState.value = UpdateState(UpdatePhase.DOWNLOADING, release, bytes) }
                    ApkVerifier.verify(getApplication(), partial, release)
                    check(partial.renameTo(ready)) { "Impossibile completare il download." }
                    folder.listFiles()?.filter { it != ready && it.name.matches(Regex("mappinando-\\d+\\.apk(?:\\.part)?")) }?.forEach { it.delete() }
                }
                mutableState.value = UpdateState(UpdatePhase.READY, release, release.sizeBytes)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val message = if (failure is IllegalArgumentException && !failure.message.isNullOrBlank()) failure.message
                    else "Download non completato. Controlla la connessione e riprova."
                mutableState.value = UpdateState(UpdatePhase.ERROR, release, message = message)
            } finally { partial.delete() }
        }
    }

    fun cancelDownload() {
        val release = mutableState.value.release ?: return
        val currentWork = work ?: return
        if (cancelling) return
        cancelling = true
        mutableState.value = mutableState.value.copy(message = "Annullamento del download…")
        viewModelScope.launch {
            currentWork.cancelAndJoin()
            mutableState.value = UpdateState(UpdatePhase.AVAILABLE, release)
            cancelling = false
        }
    }

    fun installMessage(message: String) { mutableState.value = mutableState.value.copy(message = message) }

    fun installReadyUpdate() {
        val current = mutableState.value
        val release = current.release ?: return
        if (current.phase != UpdatePhase.READY || work?.isCompleted == false) return
        val application = getApplication<Application>()
        if (!application.packageManager.canRequestPackageInstalls()) {
            installMessage("Consenti a Mappinando di installare aggiornamenti nelle impostazioni Android, poi riprova.")
            return
        }
        work = viewModelScope.launch {
            try {
                val file = apkFile(release)
                withContext(Dispatchers.IO) { ApkVerifier.verify(application, file, release) }
                val uri = FileProvider.getUriForFile(application, "${BuildConfig.APPLICATION_ID}.updates", file)
                application.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    clipData = ClipData.newRawUri("Aggiornamento Mappinando", uri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                installMessage("Conferma l’aggiornamento nella finestra di Android. Le tue tappe rimarranno salvate.")
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                installMessage("Non riesco ad avviare l’installazione. Puoi riprovare o scaricare l’APK dalla release GitHub.")
            }
        }
    }
}
