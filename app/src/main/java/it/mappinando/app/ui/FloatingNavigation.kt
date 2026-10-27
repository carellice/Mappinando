package it.mappinando.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val NavigationDockHeight = 72.dp
internal val NavigationDockGap = 18.dp

/** A detached island: its transparent margins do not intercept touches on the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloatingNavigationDock(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val lime = Color(0xFFDBF0A5)
    val deepGreen = Color(0xFF213E34)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().height(NavigationDockHeight),
            shape = RoundedCornerShape(34.dp),
            color = deepGreen,
            shadowElevation = 22.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .14f)),
        ) {
            BoxWithConstraints(
                Modifier.background(Brush.linearGradient(listOf(Color(0xFF345A47), Color(0xFF1C352D))))
                    .padding(8.dp),
            ) {
                // The active destination occupies half the island, the other two a quarter.
                val quarter = maxWidth / 4
                val indicatorOffset by animateDpAsState(
                    targetValue = quarter * selected.ordinal,
                    animationSpec = spring(dampingRatio = .79f, stiffness = Spring.StiffnessMediumLow),
                    label = "Scorrimento isola di navigazione",
                )
                Surface(
                    modifier = Modifier.offset(x = indicatorOffset).width(quarter * 2).height(56.dp),
                    shape = CircleShape,
                    color = lime,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .25f)),
                ) {
                    Box(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent))))
                }
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    Tab.entries.forEach { destination ->
                        val active = destination == selected
                        val weight by animateFloatAsState(
                            if (active) 2f else 1f,
                            spring(dampingRatio = .79f, stiffness = Spring.StiffnessMediumLow),
                            label = "Spazio ${destination.label}",
                        )
                        val iconColor by animateColorAsState(
                            if (active) lime else Color(0xFFD3DFD3), tween(180),
                            label = "Icona ${destination.label}",
                        )
                        val iconBackground by animateColorAsState(
                            if (active) deepGreen else Color.Transparent, tween(180),
                            label = "Medaglione ${destination.label}",
                        )
                        val iconScale by animateFloatAsState(
                            if (active) 1f else .92f,
                            spring(dampingRatio = .65f, stiffness = Spring.StiffnessMedium),
                            label = "Accento ${destination.label}",
                        )
                        Box(Modifier.weight(weight)) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(destination.label) } },
                                state = rememberTooltipState(),
                            ) {
                                Row(
                                    Modifier.fillMaxSize().clip(CircleShape)
                                        .selectable(selected = active, role = Role.Tab) {
                                            if (!active) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelect(destination)
                                            }
                                        }
                                        .semantics { contentDescription = destination.label }
                                        .testTag("tab-${destination.name}"),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(36.dp).scale(iconScale).clip(CircleShape).background(iconBackground),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(destination.icon, null, Modifier.size(21.dp), tint = iconColor)
                                    }
                                    AnimatedVisibility(
                                        visible = active,
                                        enter = fadeIn(tween(180, delayMillis = 55)) + expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(tween(70)) + shrinkHorizontally(tween(170)),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(7.dp))
                                            Column(
                                                Modifier.padding(end = 10.dp).clearAndSetSemantics {},
                                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                            ) {
                                                Text(
                                                    destination.label,
                                                    color = deepGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    when (destination) {
                                                        Tab.MAP -> "Segui la curiosità"
                                                        Tab.PLACES -> "Il tuo piccolo atlante"
                                                        Tab.TASKS -> "Idee da vivere"
                                                    },
                                                    color = deepGreen.copy(alpha = .72f),
                                                    fontSize = 8.sp,
                                                    lineHeight = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
