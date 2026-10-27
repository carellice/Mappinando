package it.mappinando.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCity
import it.mappinando.app.data.PlaceValidation
import java.util.Locale

private val DetailPaper = Color(0xFFF8F7F3)
private val DetailInk = Color(0xFF213E34)
private val DetailGreen = Color(0xFF345B46)
private val DetailLime = Color(0xFFDBF0A5)
private val DetailMuted = Color(0xFF7B867D)
private val DetailLine = Color(0xFFE6E9E0)

/** The parent owns enter/exit transitions; this page never creates a sheet or dialog window. */
@Composable
fun PlaceDetailScreen(
    place: Place,
    onDismiss: () -> Unit,
    onEdit: (Place) -> Unit,
    onDelete: (Place) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleVisited: () -> Unit,
    onToggleTask: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onNavigate: () -> Unit,
) {
    var confirmDelete by rememberSaveable(place.id) { mutableStateOf(false) }
    var taskText by rememberSaveable(place.id) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val city = PlaceCity.resolvedCity(place)
    val scroll = key(place.id) { rememberScrollState() }
    val addTask = {
        val value = taskText.trim()
        if (value.isNotEmpty() && place.tasks.size < PlaceValidation.MAX_TASKS) {
            onAddTask(value)
            taskText = ""
            focusManager.clearFocus()
        }
    }
    Surface(Modifier.fillMaxSize().testTag("place-detail-screen"), color = DetailPaper) {
        Scaffold(
            containerColor = DetailPaper,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .height(64.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("place-detail-back"),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White),
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Chiudi dettaglio", tint = DetailInk) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("IL TUO PICCOLO ATLANTE", color = DetailMuted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                        Text("Un posto speciale", fontSize = 15.sp, color = DetailInk, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White),
                    ) {
                        Icon(
                            if (place.favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            if (place.favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                            tint = if (place.favorite) Color(0xFFB96C65) else DetailGreen,
                        )
                    }
                }
            },
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).imePadding()
                    .verticalScroll(scroll).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                DetailIllustration(place, city)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(place.name, fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.9).sp, color = DetailInk)
                    if (place.address.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = DetailGreen)
                            Text(place.address, fontSize = 13.sp, lineHeight = 21.sp, color = DetailMuted)
                        }
                    }
                    Text(
                        String.format(Locale.US, "%.5f, %.5f", place.latitude, place.longitude),
                        fontSize = 10.sp, color = DetailMuted, letterSpacing = .5.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = !place.visited, onClick = { if (place.visited) onToggleVisited() },
                        label = { Text("Da scoprire") }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = detailChipColors(), shape = CircleShape,
                    )
                    FilterChip(
                        selected = place.visited, onClick = { if (!place.visited) onToggleVisited() },
                        label = { Text("Visitato") }, modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = if (place.visited) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null,
                        colors = detailChipColors(), shape = CircleShape,
                    )
                }
                if (place.note.isNotBlank()) {
                    Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailEyebrow("DA RICORDARE")
                            Text(place.note, fontSize = 14.sp, lineHeight = 23.sp, color = DetailInk)
                        }
                    }
                }
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cose da fare qui", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DetailInk, modifier = Modifier.weight(1f))
                        if (place.tasks.isNotEmpty()) {
                            Text(
                                "${place.tasks.count { it.done }}/${place.tasks.size}", color = DetailGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.background(DetailLime, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    if (place.tasks.isEmpty()) {
                        Text("Un piatto da provare, una mostra, un tramonto. Da cosa cominci?", fontSize = 13.sp, lineHeight = 21.sp, color = DetailMuted)
                    }
                    place.tasks.forEach { task ->
                        PlaceTaskRow(task, onToggle = { onToggleTask(task.id) }, onDelete = { onDeleteTask(task.id) })
                    }
                    OutlinedTextField(
                        value = taskText, onValueChange = { taskText = it.take(160) },
                        modifier = Modifier.fillMaxWidth().testTag("detail-task-input"),
                        enabled = place.tasks.size < PlaceValidation.MAX_TASKS,
                        placeholder = { Text("Aggiungi un’idea…", fontSize = 14.sp) }, singleLine = true,
                        supportingText = if (place.tasks.size >= PlaceValidation.MAX_TASKS) { { Text("Hai raggiunto il limite di attività per questo luogo.") } } else null,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DetailGreen, unfocusedBorderColor = DetailLine,
                            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            cursorColor = DetailGreen,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTask() }),
                        trailingIcon = {
                            IconButton(onClick = { addTask() }, enabled = taskText.isNotBlank()) { Icon(Icons.Outlined.Add, "Aggiungi attività", tint = DetailGreen) }
                        },
                    )
                }
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DetailGreen, contentColor = Color.White),
                ) {
                    Text("Portami qui", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Outlined.ArrowOutward, null, Modifier.size(20.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onEdit(place) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Modifica luogo", color = DetailGreen)
                    }
                    TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = Color(0xFFA65C53))
                        Spacer(Modifier.width(8.dp)); Text("Elimina", color = Color(0xFFA65C53))
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = DetailPaper,
            title = { Text("Eliminare questo luogo?", color = DetailInk) },
            text = { Text("“${place.name}” e le sue attività verranno rimossi dalla tua mappa.", color = DetailMuted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(place) }) { Text("Elimina luogo", color = Color(0xFFA65C53)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla", color = DetailGreen) } },
        )
    }
}

@Composable
private fun DetailIllustration(place: Place, city: String) {
    Box(Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(28.dp)).background(DetailGreen)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawCircle(DetailLime.copy(alpha = .08f), w * .4f, Offset(w * .95f, h * .2f))
            val route = Path().apply {
                moveTo(w * .54f, h * 1.15f)
                cubicTo(w * .37f, h * .3f, w * .97f, h * .98f, w * .91f, h * .1f)
            }
            drawPath(route, DetailLime.copy(alpha = .5f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
            drawCircle(DetailLime, 3.dp.toPx(), Offset(w * .91f, h * .1f))
            drawCircle(DetailLime.copy(alpha = .1f), 51.dp.toPx(), Offset(w * .79f, h * .57f))
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.64f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("UN POSTO NEL TUO ATLANTE", color = DetailLime.copy(alpha = .85f), fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
            Text(city.ifBlank { "Un angolo\ndi mondo" }, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(place.category.label, color = DetailLime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).size(70.dp).background(DetailLime, CircleShape), contentAlignment = Alignment.Center) {
            Icon(place.category.icon(), null, Modifier.size(34.dp), tint = DetailGreen)
        }
    }
}

@Composable
private fun DetailEyebrow(text: String) {
    Text(text, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = DetailMuted)
}

@Composable
private fun detailChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White, labelColor = DetailMuted,
    selectedContainerColor = DetailLime, selectedLabelColor = DetailInk, selectedLeadingIconColor = DetailGreen,
)
