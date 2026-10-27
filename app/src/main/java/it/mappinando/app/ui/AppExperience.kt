package it.mappinando.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mappinando.app.data.AppPreferences
import it.mappinando.app.map.AppLocationViewModel
import it.mappinando.app.updates.AppUpdateViewModel

@Composable
internal fun AppExperience(updates: AppUpdateViewModel, location: AppLocationViewModel) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var welcome by remember { mutableStateOf(!preferences.onboardingComplete) }
    val state by location.state.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (location.hasPermission()) location.locate() else location.denied()
    }
    val requestLocation: () -> Unit = {
        if (location.hasPermission()) location.locate()
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, location) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) location.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); location.cancel() }
    }
    AnimatedContent(welcome, transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(240)) }, label = "Il primo viaggio") { showWelcome ->
        if (showWelcome) OnboardingScreen { useLocation ->
            preferences.completeOnboarding(); welcome = false
            if (useLocation) requestLocation()
        } else MappinandoApp(updateManager = updates, locationState = state, onLocate = requestLocation, onLocationMessageConsumed = location::consumeMessage)
    }
}
