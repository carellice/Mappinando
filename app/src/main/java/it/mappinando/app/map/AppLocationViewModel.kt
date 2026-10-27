package it.mappinando.app.map

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LocationState(val busy: Boolean = false, val focus: MapFocus? = null, val message: String? = null)

/** One foreground request, cancelled on timeout/background. No location history is stored. */
class AppLocationViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LocationState())
    val state = mutableState.asStateFlow()
    private var request: kotlinx.coroutines.Job? = null

    fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED }

    fun denied() { mutableState.value = LocationState(message = "Puoi esplorare e salvare luoghi anche senza posizione.") }
    fun consumeMessage() { mutableState.value = mutableState.value.copy(message = null) }
    fun cancel() { request?.cancel(); mutableState.value = mutableState.value.copy(busy = false) }

    @Suppress("MissingPermission")
    fun locate() {
        if (request?.isActive == true) return
        if (!hasPermission()) { denied(); return }
        request = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            try {
                val manager = getApplication<Application>().getSystemService(LocationManager::class.java)
                if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
                    mutableState.value = LocationState(message = "La posizione del dispositivo è disattivata. Attivala nelle impostazioni Android e riprova.")
                    return@launch
                }
                val fine = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                    .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                // Android throttles approximate fixes. A recent system fix avoids waiting
                // for another sensor cycle; older cached positions are never used.
                val cached = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                    .filter(::isRecent).maxByOrNull { it.elapsedRealtimeNanos }
                val location = cached ?: withTimeoutOrNull(20_000) {
                    coroutineScope {
                        val results = Channel<Location?>(Channel.UNLIMITED)
                        val jobs = providers.map { provider -> launch {
                            val result = try {
                                suspendCancellableCoroutine<Location?> { continuation ->
                                    val signal = CancellationSignal()
                                    continuation.invokeOnCancellation { signal.cancel() }
                                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(getApplication())) {
                                        if (continuation.isActive) continuation.resume(it)
                                    }
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                            results.send(result)
                        } }
                        var found: Location? = null
                        repeat(providers.size) { if (found == null) found = results.receive()?.takeIf(::isRecent) }
                        jobs.forEach { it.cancel() }
                        found
                    }
                }
                mutableState.value = if (location == null) LocationState(message = "Non riesco a trovare la posizione ora. Puoi riprovare dal mirino sulla mappa o cercare un indirizzo.")
                else LocationState(focus = MapFocus(location.latitude, location.longitude, if (fine) 14.5 else 12.5))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = LocationState(message = "Posizione non disponibile. Puoi cercare un luogo o riprovare dal mirino.") }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }

    private fun isRecent(location: Location): Boolean {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return age in 0..120_000_000_000L && location.latitude.isFinite() && location.longitude.isFinite()
    }
}
