package it.mappinando.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MapControlGreen = Color(0xFF345B46)
private val MapControlInk = Color(0xFF213E34)
private val MapControlLine = Color(0xFFE5E8DF)

/**
 * A single, aligned set of map actions. The parent supplies any safe-area padding;
 * this overlay applies the same 16dp internal margin in both map presentations.
 * Transparent margins remain available to map gestures.
 */
@Composable
internal fun MapControls(
    placeCount: Int,
    fullscreen: Boolean,
    transitioning: Boolean,
    onToggleFullscreen: () -> Unit,
    onRecenter: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    locating: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("map-controls")) {
        val compactHeight = maxHeight < 240.dp
        val countWidth = (maxWidth - if (compactHeight) 205.dp else 96.dp).coerceAtLeast(0.dp)
        if (fullscreen && onSearch != null) {
            Surface(
                onClick = onSearch, enabled = !transitioning,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp)
                    .widthIn(max = countWidth).height(48.dp).testTag("map-search"),
                shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .98f), shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = MapControlGreen)
                    Text("Cerca luoghi e attività", color = MapControlInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = if (fullscreen && onSearch != null) 76.dp else 16.dp)
                .widthIn(max = countWidth).testTag("map-place-count"),
            shape = CircleShape,
            color = Color.White.copy(alpha = .97f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .75f)),
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).background(MapControlGreen, CircleShape))
                Text(
                    "$placeCount ${if (placeCount == 1) "luogo" else "luoghi"}",
                    color = MapControlInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val tools: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = .98f),
                border = BorderStroke(1.dp, MapControlGreen.copy(alpha = .08f)),
                shadowElevation = 4.dp,
            ) {
                if (compactHeight) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MapFullscreenControl(fullscreen, !transitioning, onToggleFullscreen)
                        VerticalDivider(Modifier.height(24.dp), color = MapControlLine)
                        MapRecenterControl(!transitioning, onRecenter, locating)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MapFullscreenControl(fullscreen, !transitioning, onToggleFullscreen)
                        HorizontalDivider(Modifier.width(24.dp), color = MapControlLine)
                        MapRecenterControl(!transitioning, onRecenter, locating)
                    }
                }
            }
        }
        val add: @Composable () -> Unit = {
            MapActionControl(
                icon = Icons.Outlined.Add, description = "Aggiungi un posto", tag = "map-add",
                enabled = !transitioning, onClick = onAdd, primary = true,
            )
        }
        if (compactHeight) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { tools(); add() }
        } else {
            Column(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { tools(); add() }
        }
    }
}

@Composable
private fun MapFullscreenControl(fullscreen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    MapActionControl(
        icon = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
        description = if (fullscreen) "Riduci mappa" else "Apri mappa a tutto schermo",
        tag = "map-fullscreen-toggle", enabled = enabled, onClick = onClick,
    )
}

@Composable
private fun MapRecenterControl(enabled: Boolean, onClick: () -> Unit, locating: Boolean) {
    if (locating) {
        Box(Modifier.size(48.dp).testTag("map-locating"), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MapControlGreen)
        }
        return
    }
    MapActionControl(
        icon = Icons.Outlined.CenterFocusStrong, description = "Vai alla mia posizione",
        tag = "map-recenter", enabled = enabled, onClick = onClick,
    )
}

@Composable
private fun MapActionControl(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).testTag(tag),
        shape = RoundedCornerShape(if (primary) 18.dp else 14.dp),
        color = if (primary) MapControlGreen else Color.Transparent,
        contentColor = (if (primary) Color.White else MapControlGreen).copy(alpha = if (enabled) 1f else .45f),
        shadowElevation = if (primary) 4.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(if (primary) 25.dp else 22.dp))
        }
    }
}
