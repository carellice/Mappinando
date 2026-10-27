@file:Suppress("DEPRECATION")

package it.mappinando.app.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.graphics.Insets
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style

data class MapFocus(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double = 14.0,
    val token: Long = System.nanoTime(),
)

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val LAST_FOCUS_TOKEN_KEY = "it.mappinando.app.map.lastFocusToken"

/** A retained, fully native vector map. Long presses add a place at the actual coordinate. */
@Composable
fun NativeMap(
    places: List<Place>,
    selectedPlaceId: String?,
    focus: MapFocus?,
    onPlaceClick: (Place) -> Unit,
    onMapLongClick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    uiInsets: PaddingValues = PaddingValues(0.dp),
    preview: SearchResult? = null,
    onCameraIdle: (Double, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val margins = with(density) {
        Insets.of(
            (uiInsets.calculateLeftPadding(direction) + 16.dp).roundToPx(),
            (uiInsets.calculateTopPadding() + 16.dp).roundToPx(),
            (uiInsets.calculateRightPadding(direction) + 16.dp).roundToPx(),
            (uiInsets.calculateBottomPadding() + 16.dp).roundToPx(),
        )
    }
    val latestMargins by rememberUpdatedState(margins)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestClick by rememberUpdatedState(onPlaceClick)
    val latestLongClick by rememberUpdatedState(onMapLongClick)
    val latestPlaces by rememberUpdatedState(places)
    val latestCameraIdle by rememberUpdatedState(onCameraIdle)
    var mapError by remember { mutableStateOf(false) }
    var styleLoaded by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val savedMapState = rememberSaveable { Bundle() }
    val controller = remember {
        MapController(
            initialFocusToken = savedMapState.takeIf { it.containsKey(LAST_FOCUS_TOKEN_KEY) }
                ?.getLong(LAST_FOCUS_TOKEN_KEY),
        )
    }
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(savedMapState.takeUnless { it.isEmpty })
            contentDescription = "Mappa dei tuoi posti. Tieni premuto un punto per aggiungere un luogo."
        }
    }

    DisposableEffect(view, lifecycle) {
        var started = false
        var resumed = false
        var destroyed = false
        fun saveState() {
            view.onSaveInstanceState(savedMapState)
            // Save this after MapView writes its state, alongside the camera it belongs to.
            // A consumed focus must not replay when a retained tab is recomposed.
            controller.lastFocusToken?.let { savedMapState.putLong(LAST_FOCUS_TOKEN_KEY, it) }
                ?: savedMapState.remove(LAST_FOCUS_TOKEN_KEY)
        }
        fun stop() {
            if (resumed) { saveState(); view.onPause(); resumed = false }
            if (started) { view.onStop(); started = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (!destroyed) {
                when (event) {
                    Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
                    Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
                    Lifecycle.Event.ON_PAUSE -> if (resumed) { saveState(); view.onPause(); resumed = false }
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_DESTROY -> { stop(); view.onDestroy(); destroyed = true }
                    else -> Unit
                }
            }
        }
        val memoryCallbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { if (!destroyed) view.onLowMemory() }
            override fun onTrimMemory(level: Int) {
                if (!destroyed && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) view.onLowMemory()
            }
        }
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        lifecycle.addObserver(observer)
        val failure = MapView.OnDidFailLoadingMapListener { mapError = true }
        view.addOnDidFailLoadingMapListener(failure)
        view.getMapAsync { map ->
            if (!destroyed) {
                controller.map = map
                map.uiSettings.apply {
                    isLogoEnabled = false
                    isAttributionEnabled = true
                    attributionGravity = Gravity.BOTTOM or Gravity.START
                    isCompassEnabled = true
                    compassGravity = Gravity.BOTTOM or Gravity.END
                    isTiltGesturesEnabled = false
                }
                controller.updateUiMargins(latestMargins)
                if (savedMapState.isEmpty) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(42.5, 12.5)).zoom(5.0).build()
                }
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { latestCameraIdle(it.latitude, it.longitude) }
                }
                map.cameraPosition.target?.let { latestCameraIdle(it.latitude, it.longitude) }
                controller.moveToFocus()
                map.setOnMarkerClickListener { marker ->
                    val id = controller.markerIds[marker.id]
                    latestPlaces.firstOrNull { it.id == id }?.let { latestClick(it) }
                    true
                }
                map.addOnMapLongClickListener { point ->
                    latestLongClick(point.latitude, point.longitude)
                    true
                }
                controller.reloadStyle = {
                    if (!destroyed) {
                        mapError = false
                        styleLoaded = false
                        controller.styleReady = false
                        map.setStyle(Style.Builder().fromUri(MAP_STYLE)) {
                            if (!destroyed) {
                                mapError = false
                                styleLoaded = true
                                controller.styleReady = true
                                controller.redrawMarkers()
                                controller.moveToFocus()
                            }
                        }
                    }
                }
                controller.reloadStyle?.invoke()
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            view.removeOnDidFailLoadingMapListener(failure)
            controller.reloadStyle = null
            controller.map = null
            controller.icons.clear()
            if (!destroyed) { stop(); view.onDestroy(); destroyed = true }
        }
    }

    LaunchedEffect(attempt) {
        delay(20_000)
        if (!styleLoaded) mapError = true
    }

    Box(modifier.background(Color(0xFFE7EADD))) {
        AndroidView(factory = { view }, modifier = Modifier.matchParentSize().testTag("native-map-view"), update = {
            controller.context = context
            controller.updateUiMargins(margins)
            val markersChanged = controller.places != places || controller.selectedId != selectedPlaceId || controller.preview != preview
            controller.places = places
            controller.selectedId = selectedPlaceId
            controller.preview = preview
            controller.focus = focus
            if (markersChanged) controller.redrawMarkers()
            controller.moveToFocus()
        })
        if (mapError) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFAF9F4),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("La mappa si fa attendere", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Controlla la connessione. I tuoi luoghi sono sempre salvati sul dispositivo.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { attempt++; controller.reloadStyle?.invoke() }) { Text("Riprova") }
                }
            }
        } else if (!styleLoaded) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(50),
                color = Color(0xFFFAF9F4),
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color(0xFF264F3D))
                    Text("Apriamo la tua mappa…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private class MapController(initialFocusToken: Long? = null) {
    private var appliedMargins: Insets? = null
    var context: android.content.Context? = null
    var map: MapLibreMap? = null
    var places: List<Place> = emptyList()
    var selectedId: String? = null
    var preview: SearchResult? = null
    var focus: MapFocus? = null
    var styleReady = false
    var reloadStyle: (() -> Unit)? = null
    val markerIds = mutableMapOf<Long, String>()
    val icons = mutableMapOf<Pair<PlaceCategory, Boolean>, Icon>()
    var lastFocusToken: Long? = initialFocusToken
        private set
    private var renderedMarkers: List<Marker> = emptyList()

    fun updateUiMargins(margins: Insets) {
        val currentMap = map ?: return
        if (margins == appliedMargins) return
        appliedMargins = margins
        currentMap.uiSettings.setAttributionMargins(margins.left, 0, 0, margins.bottom)
        currentMap.uiSettings.setCompassMargins(0, 0, margins.right, margins.bottom)
    }

    fun redrawMarkers() {
        val currentMap = map ?: return
        val currentContext = context ?: return
        if (!styleReady) return
        renderedMarkers.forEach(currentMap::removeMarker)
        markerIds.clear()
        renderedMarkers = places.map { place ->
            val key = Pair(place.category, place.id == selectedId)
            val icon = icons.getOrPut(key) {
                IconFactory.getInstance(currentContext).fromBitmap(
                    createPin(place.category, place.id == selectedId, currentContext.resources.displayMetrics.density),
                )
            }
            currentMap.addMarker(
                MarkerOptions().position(LatLng(place.latitude, place.longitude)).icon(icon),
            ).also { markerIds[it.id] = place.id }
        } + listOfNotNull(preview?.takeIf { it.savedPlace(places) == null }?.let { result ->
            val category = result.category ?: PlaceCategory.VIEWS
            val icon = icons.getOrPut(category to true) {
                IconFactory.getInstance(currentContext).fromBitmap(createPin(category, true, currentContext.resources.displayMetrics.density))
            }
            currentMap.addMarker(MarkerOptions().position(LatLng(result.latitude, result.longitude)).icon(icon))
        })
    }

    fun moveToFocus() {
        val target = focus ?: return
        val currentMap = map ?: return
        if (target.token == lastFocusToken) return
        lastFocusToken = target.token
        currentMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), target.zoom),
            850,
        )
    }
}

private fun createPin(category: PlaceCategory, selected: Boolean, density: Float): Bitmap {
    val scale = density * if (selected) 1.22f else 1f
    val bitmap = Bitmap.createBitmap((54 * scale).toInt(), (65 * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val color = when (category) {
        PlaceCategory.RESTAURANTS -> 0xFFC37B62
        PlaceCategory.BARS -> 0xFFB28E42
        PlaceCategory.VIEWS -> 0xFF8D7CAA
        PlaceCategory.CULTURE -> 0xFF6F91B0
        PlaceCategory.SHOPPING -> 0xFFC17F97
        PlaceCategory.NATURE -> 0xFF5C8872
    }.toInt()
    // A subtle oval grounds the pin without requiring a view shadow layer.
    paint.color = 0x25000000
    canvas.drawOval(15f, 53f, 39f, 59f, paint)
    val body = Path().apply {
        moveTo(27f, 56f)
        cubicTo(22f, 48f, 6f, 37f, 6f, 26f)
        cubicTo(6f, -2f, 48f, -2f, 48f, 26f)
        cubicTo(48f, 37f, 32f, 48f, 27f, 56f)
        close()
    }
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawPath(body, paint)
    paint.color = color
    canvas.drawCircle(27f, 25f, 17f, paint)
    paint.color = AndroidColor.WHITE
    paint.strokeWidth = 1.85f
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    paint.style = Paint.Style.STROKE
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
    when (category) {
        PlaceCategory.RESTAURANTS -> {
            line(21f, 17f, 21f, 33f); line(18f, 17f, 18f, 23f); line(24f, 17f, 24f, 23f)
            canvas.drawArc(18f, 20f, 24f, 26f, 0f, 180f, false, paint)
            line(33f, 17f, 33f, 33f); line(30f, 17f, 30f, 26f); line(30f, 26f, 33f, 26f)
        }
        PlaceCategory.BARS -> {
            canvas.drawRoundRect(18f, 20f, 31f, 30f, 2f, 2f, paint)
            canvas.drawArc(28f, 21f, 36f, 28f, -90f, 180f, false, paint)
            line(17f, 33f, 33f, 33f); line(22f, 15f, 22f, 17f); line(27f, 15f, 27f, 17f)
        }
        PlaceCategory.VIEWS -> {
            val path = Path().apply { moveTo(16f, 32f); lineTo(24f, 20f); lineTo(29f, 27f); lineTo(33f, 22f); lineTo(39f, 32f); close() }
            canvas.drawPath(path, paint); canvas.drawCircle(33f, 16f, 2.5f, paint)
        }
        PlaceCategory.CULTURE -> {
            val path = Path().apply { moveTo(16f, 21f); lineTo(27f, 15f); lineTo(38f, 21f); close() }
            canvas.drawPath(path, paint); line(17f, 33f, 37f, 33f)
            listOf(20f, 27f, 34f).forEach { line(it, 24f, it, 30f) }
        }
        PlaceCategory.SHOPPING -> {
            canvas.drawRoundRect(18f, 22f, 36f, 34f, 2f, 2f, paint)
            canvas.drawArc(23f, 15f, 31f, 27f, 180f, 180f, false, paint)
        }
        PlaceCategory.NATURE -> {
            val path = Path().apply { moveTo(18f, 31f); cubicTo(17f, 18f, 26f, 16f, 36f, 15f); cubicTo(37f, 28f, 32f, 35f, 21f, 32f) }
            canvas.drawPath(path, paint); line(17f, 35f, 30f, 23f)
        }
    }
    return bitmap
}
