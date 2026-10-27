package it.mappinando.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import it.mappinando.app.data.Place
import it.mappinando.app.map.MapFocus
import it.mappinando.app.map.NativeMap
import it.mappinando.app.map.SearchResult
import kotlin.math.roundToInt

/** The same native map moves between two measured rectangles; no duplicate view or layout swap. */
@Composable
internal fun MapScene(
    compactBounds: Rect,
    windowSize: IntSize,
    expansion: Float,
    fullscreen: Boolean,
    transitioning: Boolean,
    systemPadding: PaddingValues,
    places: List<Place>,
    selectedPlaceId: String?,
    focus: MapFocus?,
    onPlaceClick: (Place) -> Unit,
    onMapLongClick: (Double, Double) -> Unit,
    onToggleFullscreen: () -> Unit,
    onRecenter: () -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    preview: SearchResult?,
    previewSaved: Boolean,
    onSavePreview: () -> Unit,
    onClosePreview: () -> Unit,
    onCameraIdle: (Double, Double) -> Unit,
    locating: Boolean = false,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val progress = expansion.coerceIn(0f, 1f)
    val full = Rect(0f, 0f, windowSize.width.toFloat(), windowSize.height.toFloat())
    val frame = lerp(compactBounds, full, progress)
    val corner = (28f * (1f - progress)).dp
    val viewportPadding = PaddingValues(
        start = systemPadding.calculateStartPadding(direction) * progress,
        top = systemPadding.calculateTopPadding() * progress,
        end = systemPadding.calculateEndPadding(direction) * progress,
        bottom = systemPadding.calculateBottomPadding() * progress,
    )
    // Resize a window onto a full-sized TextureView, not the GL surface itself. Repeated
    // surface resizes stretch the previous buffer while MapLibre prepares its next frame.
    val horizontalCrop = with(density) { ((windowSize.width - frame.width) / 2f).coerceAtLeast(0f).toDp() }
    val verticalCrop = with(density) { ((windowSize.height - frame.height) / 2f).coerceAtLeast(0f).toDp() }
    val nativeChromePadding = PaddingValues(
        start = horizontalCrop + viewportPadding.calculateStartPadding(direction),
        top = verticalCrop + viewportPadding.calculateTopPadding(),
        end = horizontalCrop + viewportPadding.calculateEndPadding(direction),
        bottom = verticalCrop + viewportPadding.calculateBottomPadding(),
    )
    val controlsAlpha by animateFloatAsState(if (transitioning) 0f else 1f, tween(140), label = "Comparsa strumenti mappa")
    var previewSnapshot by remember { mutableStateOf(preview) }
    SideEffect { if (preview != null) previewSnapshot = preview }
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.absoluteOffset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
            .size(with(density) { frame.width.coerceAtLeast(1f).toDp() }, with(density) { frame.height.coerceAtLeast(1f).toDp() })
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFFE7EADD))
            .border((1f - progress).dp, Color(0xFFE5E8DF), RoundedCornerShape(corner))
            .testTag(if (fullscreen) "fullscreen-map" else "map-preview"),
    ) {
        Box(
            Modifier.wrapContentSize(Alignment.Center, unbounded = true)
                .requiredSize(with(density) { windowSize.width.toDp() }, with(density) { windowSize.height.toDp() }),
        ) {
            NativeMap(
                places, selectedPlaceId, focus, onPlaceClick, onMapLongClick,
                modifier = Modifier.matchParentSize(), uiInsets = nativeChromePadding,
                preview = preview, onCameraIdle = onCameraIdle,
            )
        }
        if (!transitioning) {
            MapControls(
                locating = locating,
                placeCount = places.size, fullscreen = fullscreen, transitioning = false,
                onToggleFullscreen = onToggleFullscreen, onRecenter = onRecenter, onAdd = onAdd,
                onSearch = onSearch,
                modifier = Modifier.matchParentSize().padding(viewportPadding).graphicsLayer { alpha = controlsAlpha },
            )
            AnimatedVisibility(
                visible = fullscreen && preview != null,
                modifier = Modifier.matchParentSize().padding(viewportPadding).padding(start = 16.dp, end = 16.dp, bottom = 48.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(280)) { it / 8 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 10 },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    previewSnapshot?.let { MapSearchPreview(it, previewSaved, onSavePreview, onClosePreview, Modifier.widthIn(max = 420.dp).fillMaxWidth()) }
                }
            }
        } else {
            // A resize must not turn a second tap into a map gesture or a place selection.
            Box(Modifier.matchParentSize().blockMapTransitionTouches())
        }
    }
    }
}

internal fun Modifier.blockMapTransitionTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
