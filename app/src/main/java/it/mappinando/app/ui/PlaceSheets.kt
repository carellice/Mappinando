package it.mappinando.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceCity
import it.mappinando.app.data.PlaceTask
import it.mappinando.app.data.PlaceValidation
import java.util.Locale

private val SheetPaper = Color(0xFFF8F7F3)
private val SheetInk = Color(0xFF213E34)
private val SheetGreen = Color(0xFF345B46)
private val SheetLime = Color(0xFFDBF0A5)
private val SheetLine = Color(0xFFE6E9E0)
private val SheetMuted = Color(0xFF7B867D)
private val DraftTasksSaver = listSaver<SnapshotStateList<PlaceTask>, String>(
    save = { tasks -> tasks.flatMap { listOf(it.id, it.title, it.done.toString()) } },
    restore = { values ->
        mutableStateListOf<PlaceTask>().apply {
            values.chunked(3).filter { it.size == 3 }.forEach { add(PlaceTask(id = it[0], title = it[1], done = it[2].toBoolean())) }
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceEditorSheet(
    initial: Place?,
    latitude: Double,
    longitude: Double,
    suggestedName: String = "",
    suggestedAddress: String = "",
    suggestedCity: String = "",
    onDismiss: () -> Unit,
    onSave: (Place) -> Unit,
    suggestedCategory: PlaceCategory? = null,
) {
    val formKey = initial?.id ?: "$latitude:$longitude:$suggestedName"
    var name by rememberSaveable(formKey) { mutableStateOf(initial?.name ?: suggestedName.take(100)) }
    var address by rememberSaveable(formKey) { mutableStateOf(initial?.address ?: suggestedAddress) }
    var city by rememberSaveable(formKey) { mutableStateOf(initial?.let(PlaceCity::resolvedCity) ?: suggestedCity) }
    var cityEdited by rememberSaveable(formKey) { mutableStateOf(false) }
    LaunchedEffect(suggestedCity) {
        if (initial == null && !cityEdited && city.isBlank() && suggestedCity.isNotBlank()) {
            city = PlaceCity.normalize(suggestedCity).take(PlaceValidation.MAX_CITY_LENGTH)
        }
    }
    var categoryName by rememberSaveable(formKey) { mutableStateOf((initial?.category ?: suggestedCategory ?: PlaceCategory.VIEWS).name) }
    var visited by rememberSaveable(formKey) { mutableStateOf(initial?.visited ?: false) }
    var favorite by rememberSaveable(formKey) { mutableStateOf(initial?.favorite ?: false) }
    var note by rememberSaveable(formKey) { mutableStateOf(initial?.note ?: "") }
    var nextTask by rememberSaveable(formKey) { mutableStateOf("") }
    var attemptedSave by rememberSaveable(formKey) { mutableStateOf(false) }
    val taskList = rememberSaveable(formKey, saver = DraftTasksSaver) { mutableStateListOf<PlaceTask>().apply { addAll(initial?.tasks.orEmpty()) } }
    val nameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val addDraftTask = {
        val value = nextTask.trim()
        if (value.isNotEmpty() && taskList.size < PlaceValidation.MAX_TASKS) {
            taskList.add(PlaceTask(title = value))
            nextTask = ""
        }
    }
    val save = {
        attemptedSave = true
        if (name.isNotBlank()) {
            // A final unsubmitted idea is kept when the user taps Save directly.
            addDraftTask()
            val category = PlaceCategory.valueOf(categoryName)
            val result = initial?.copy(
                name = name.trim(), address = address.trim(), city = PlaceCity.normalize(city), category = category,
                latitude = latitude, longitude = longitude, note = note.trim(),
                visited = visited, favorite = favorite, tasks = taskList.toList(),
            ) ?: Place(
                name = name.trim(), address = address.trim(), city = PlaceCity.normalize(city), category = category,
                latitude = latitude, longitude = longitude, note = note.trim(),
                visited = visited, favorite = favorite, tasks = taskList.toList(),
            )
            focusManager.clearFocus()
            onSave(result)
        } else {
            nameFocusRequester.requestFocus()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetPaper,
        contentColor = SheetInk,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionEyebrow(if (initial == null) "UNA NUOVA SCOPERTA" else "IL TUO ANGOLO DI MONDO")
                    Text(if (initial == null) "Segna un bel posto." else "Modifica il luogo.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Chiudi senza salvare", tint = SheetInk) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(17.dp), tint = SheetGreen)
                Text(coordinateLabel(latitude, longitude), fontSize = 12.sp, color = SheetMuted)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Come si chiama? *") },
                placeholder = { Text("Il tuo prossimo posto del cuore") },
                isError = attemptedSave && name.isBlank(),
                supportingText = if (attemptedSave && name.isBlank()) { { Text("Dai un nome al luogo per salvarlo.") } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester).testTag("editor-name"),
                shape = RoundedCornerShape(16.dp),
                colors = sheetFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it.take(PlaceValidation.MAX_CITY_LENGTH); cityEdited = true },
                label = { Text("Città") },
                placeholder = { Text("Es. Milano") },
                supportingText = { Text("Raggruppa questo luogo nella sua città.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor-city"),
                shape = RoundedCornerShape(16.dp),
                colors = sheetFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(300) },
                label = { Text("Indirizzo") },
                placeholder = { Text("Una via, una città, un indizio") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = sheetFieldColors(),
                maxLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionEyebrow("CHE TIPO DI POSTO È?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PlaceCategory.entries.forEach { category ->
                        FilterChip(
                            selected = categoryName == category.name,
                            onClick = { categoryName = category.name },
                            label = { Text("${category.emoji}  ${category.label}", fontSize = 13.sp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = sheetChipColors(),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionEyebrow("LA TUA RELAZIONE CON QUESTO POSTO")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !visited, onClick = { visited = false }, label = { Text("Da scoprire") }, colors = sheetChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = visited, onClick = { visited = true }, label = { Text("Già visitato") }, colors = sheetChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = favorite, role = Role.Checkbox, onValueChange = { favorite = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = favorite, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = SheetGreen))
                    Spacer(Modifier.width(10.dp))
                    Text("È un posto del cuore", color = SheetInk, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(17.dp), tint = Color(0xFFB96C65))
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(2000) },
                label = { Text("Una nota per il futuro") },
                placeholder = { Text("Perché vuoi tornarci? Cosa ti incuriosisce?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = sheetFieldColors(),
                minLines = 3,
                maxLines = 6,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.animateContentSize()) {
                Text("Cose da fare qui", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Trasforma un segnaposto in un piccolo progetto.", color = SheetMuted, fontSize = 13.sp)
                taskList.toList().forEach { task ->
                    PlaceTaskRow(
                        task = task,
                        onToggle = { val index = taskList.indexOfFirst { it.id == task.id }; if (index >= 0) taskList[index] = task.copy(done = !task.done) },
                        onDelete = { taskList.removeAll { it.id == task.id } },
                    )
                }
                OutlinedTextField(
                    value = nextTask,
                    onValueChange = { nextTask = it.take(160) },
                    enabled = taskList.size < PlaceValidation.MAX_TASKS,
                    supportingText = if (taskList.size >= PlaceValidation.MAX_TASKS) {
                        { Text("Hai raggiunto il limite di attività per questo luogo.") }
                    } else null,
                    placeholder = { Text("Es. provare il risotto della casa", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("editor-task-input"),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = sheetFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraftTask() }),
                    trailingIcon = {
                        IconButton(onClick = { addDraftTask() }, enabled = nextTask.isNotBlank()) {
                            Icon(Icons.Outlined.Add, "Aggiungi attività", tint = if (nextTask.isBlank()) SheetMuted else SheetGreen)
                        }
                    },
                )
            }
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("editor-save"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SheetGreen, contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (initial == null) "Salva sulla mia mappa" else "Salva le modifiche", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun PlaceTaskRow(task: PlaceTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (task.done) Color(0xFFEDF1E7) else Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag("place-task-${task.id}")
                .toggleable(value = task.done, onValueChange = { onToggle() }, role = Role.Checkbox)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = SheetGreen))
            Text(
                task.title,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 10.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (task.done) SheetMuted else SheetInk,
                textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, "Elimina attività ${task.title}", Modifier.size(17.dp), tint = SheetMuted) }
        }
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(text, color = SheetMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SheetGreen,
    unfocusedBorderColor = SheetLine,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = SheetInk,
    unfocusedTextColor = SheetInk,
    focusedLabelColor = SheetGreen,
    unfocusedLabelColor = SheetMuted,
    cursorColor = SheetGreen,
)

@Composable
private fun sheetChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White,
    labelColor = SheetMuted,
    selectedContainerColor = SheetLime,
    selectedLabelColor = SheetInk,
    selectedLeadingIconColor = SheetGreen,
)

private fun coordinateLabel(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
