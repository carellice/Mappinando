package it.mappinando.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceCity
import it.mappinando.app.data.PlaceTask

private val CollectionPaper = Color(0xFFF8F7F3)
private val CollectionInk = Color(0xFF213E34)
private val CollectionGreen = Color(0xFF345B46)
private val CollectionLime = Color(0xFFDBF0A5)
private val CollectionMuted = Color(0xFF7A847B)
private val CollectionLine = Color(0xFFE5E8DF)
private val CollectionStatuses = listOf("Tutti", "Da visitare", "Visitati", "Preferiti")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacesScreen(
    allPlaces: List<Place>,
    filtered: List<Place>,
    category: PlaceCategory?,
    status: String,
    onCategory: (PlaceCategory?) -> Unit,
    onStatus: (String) -> Unit,
    onPlace: (Place) -> Unit,
    onFavorite: (Place) -> Unit,
    onAdd: () -> Unit,
    bottomClearance: Dp,
    onAbout: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val groups = remember(filtered) { PlaceCity.group(filtered) }
    val cityCount = remember(allPlaces) { PlaceCity.group(allPlaces).count { it.city.isNotBlank() } }
    LaunchedEffect(category, status) {
        listState.scrollToItem(0)
        scroll.state.heightOffset = 0f
    }

    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        CollapsingCollectionHeader(
            title = "La tua collezione",
            subtitle = "${allPlaces.size} luoghi · $cityCount città",
            scroll = scroll,
            tag = "places-header",
            onAbout = onAbout,
            actions = {
                IconButton(onClick = { filtersOpen = true }) {
                    Icon(Icons.Outlined.Tune, "Filtra i luoghi", tint = CollectionGreen)
                }
                FilledIconButton(onClick = onAdd, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.Add, "Aggiungi un posto")
                }
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("places-list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "collection-filters") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatusFilters(status, onStatus)
                    CategoryFilters(category, onCategory)
                }
            }
            if (filtered.isEmpty()) {
                item(key = "collection-empty") {
                    CollectionEmptyState(
                        icon = Icons.Outlined.Bookmarks,
                        title = "C’è spazio per nuove scoperte.",
                        body = if (allPlaces.isEmpty()) "Salva il tuo primo luogo e porta con te le cose che vuoi fare." else "Nessun luogo con questi filtri. Puoi cambiarli dalla barra in alto.",
                        action = "Aggiungi un posto", onAction = onAdd,
                    )
                }
            }
            groups.forEach { group ->
                val groupKey = PlaceCity.groupingKey(group.places.first())
                item(key = "city:$groupKey") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 9.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.LocationCity, null, Modifier.size(21.dp), tint = CollectionGreen)
                        Text(
                            group.city.ifBlank { "Senza città" },
                            modifier = Modifier.weight(1f).padding(start = 9.dp).semantics { heading() }
                                .testTag("city-heading:$groupKey"),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CollectionInk,
                        )
                        Text(
                            "${group.places.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CollectionGreen,
                            modifier = Modifier.background(CollectionLime.copy(alpha = .65f), CircleShape)
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                        )
                    }
                    if (group.city.isBlank()) {
                        Text(
                            "Aggiungi la città modificando il luogo.",
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp),
                            fontSize = 11.sp, color = CollectionMuted,
                        )
                    }
                }
                items(group.places, key = { "place:${it.id}" }) { place ->
                    CollectionPlaceCard(
                        place = place,
                        onClick = { onPlace(place) },
                        onFavorite = { onFavorite(place) },
                        modifier = Modifier.padding(horizontal = 24.dp).animateItem(),
                    )
                }
            }
        }
    }
    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = { filtersOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollectionPaper,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ritrova il posto giusto.", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CollectionInk, modifier = Modifier.weight(1f))
                    IconButton(onClick = { filtersOpen = false }) { Icon(Icons.Outlined.Close, "Chiudi filtri") }
                }
                Text("COME VUOI ESPLORARE?", Modifier.padding(horizontal = 24.dp), color = CollectionMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                StatusFilters(status, onStatus)
                Text("CATEGORIA", Modifier.padding(horizontal = 24.dp), color = CollectionMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                CategoryFilters(category, onCategory)
                Button(onClick = { filtersOpen = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp), shape = RoundedCornerShape(17.dp)) {
                    Text("Mostra risultati")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksScreen(
    places: List<Place>,
    onPlace: (Place) -> Unit,
    onToggleTask: (Place, PlaceTask) -> Unit,
    onAdd: () -> Unit,
    bottomClearance: Dp,
    onAbout: () -> Unit,
) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val total = places.sumOf { it.tasks.size }
    val done = places.sumOf { place -> place.tasks.count { it.done } }
    val grouped = places.filter { place -> place.tasks.any { showDone || !it.done } }

    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        CollapsingCollectionHeader(
            title = "Piccoli grandi piani.",
            subtitle = "Un buon motivo per andare a vedere.",
            scroll = scroll,
            tag = "tasks-header",
            onAbout = onAbout,
            actions = {
                IconToggleButton(
                    checked = showDone,
                    onCheckedChange = { showDone = it },
                    modifier = Modifier.semantics { contentDescription = "Mostra attività completate" },
                ) {
                    Icon(if (showDone) Icons.Outlined.DoneAll else Icons.Outlined.Checklist, null, tint = if (showDone) CollectionGreen else CollectionMuted)
                }
                FilledIconButton(onClick = onAdd, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.Add, "Aggiungi un posto")
                }
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("tasks-list"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = bottomClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "ideas-summary") {
                Surface(color = CollectionLime, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(29.dp), tint = CollectionGreen)
                        Column(Modifier.padding(start = 15.dp)) {
                            Text("${total - done} idee da vivere", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = CollectionInk)
                            Text("$done attività completate su $total", fontSize = 12.sp, color = CollectionInk.copy(alpha = .75f), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item(key = "ideas-section") {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Le tue liste", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CollectionInk, modifier = Modifier.weight(1f).semantics { heading() })
                    Text(if (showDone) "Anche completate" else "Da vivere", color = CollectionMuted, fontSize = 11.sp)
                }
            }
            if (grouped.isEmpty()) {
                item(key = "ideas-empty") {
                    CollectionEmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = if (total == 0) "Le belle idee meritano un posto." else "Hai dato vita a tutte le tue idee!",
                        body = if (total == 0) "Apri un luogo e annota un piatto da provare, una mostra o un tramonto." else "Aggiungi un’altra idea a un luogo o mostra le attività completate dalla barra in alto.",
                        action = "Esplora un nuovo posto", onAction = onAdd,
                    )
                }
            }
            items(grouped, key = { it.id }) { place ->
                Surface(
                    shape = RoundedCornerShape(24.dp), color = Color.White,
                    border = BorderStroke(1.dp, CollectionLine),
                    modifier = Modifier.fillMaxWidth().animateItem(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlace(place) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(place.category.icon(), null, tint = place.category.tint(), modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CollectionInk)
                                val city = PlaceCity.resolvedCity(place)
                                Text(if (city.isBlank()) place.category.label else "$city · ${place.category.label}", color = CollectionMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Outlined.ArrowOutward, "Apri ${place.name}", Modifier.size(18.dp), tint = CollectionGreen)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 9.dp), color = CollectionLine)
                        place.tasks.filter { showDone || !it.done }.forEach { task ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                                    .toggleable(value = task.done, role = Role.Checkbox, onValueChange = { onToggleTask(place, task) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = task.done, onCheckedChange = null)
                                Text(
                                    task.title, fontSize = 14.sp, color = if (task.done) CollectionMuted else CollectionInk,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingCollectionHeader(
    title: String,
    subtitle: String,
    scroll: TopAppBarScrollBehavior,
    tag: String,
    onAbout: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val collapse = scroll.state.collapsedFraction
    LargeTopAppBar(
        modifier = Modifier.testTag(tag),
        title = {
            Column {
                Text(
                    title, fontSize = (28f - 7f * collapse).sp, lineHeight = (34f - 8f * collapse).sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-.8).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = CollectionInk,
                )
                Box(Modifier.height((20f * (1f - collapse)).dp).clipToBounds()) {
                    Text(
                        subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = CollectionMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha((1f - collapse * 1.8f).coerceIn(0f, 1f)),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onAbout, modifier = Modifier.semantics { contentDescription = "Informazioni su Mappinando" }) {
                BrandMark(Modifier.size(31.dp))
            }
        },
        actions = actions,
        expandedHeight = 144.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = CollectionPaper, scrolledContainerColor = CollectionPaper,
            navigationIconContentColor = CollectionGreen, actionIconContentColor = CollectionGreen,
        ),
        scrollBehavior = scroll,
    )
}

@Composable
private fun StatusFilters(status: String, onStatus: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollectionStatuses.forEach { filter ->
            FilterChip(
                selected = status == filter, onClick = { onStatus(filter) },
                label = { Text(filter, fontSize = 12.sp) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CollectionLime, selectedLabelColor = CollectionInk),
            )
        }
    }
}

@Composable
private fun CollectionPlaceCard(place: Place, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, CollectionLine)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryArtwork(place.category, Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(place.category.label, color = place.category.tint(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(place.name, color = CollectionInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp))
                Text(if (place.visited) "✓ Visitato" else "↗ Da visitare", color = CollectionMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (place.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    if (place.favorite) "Rimuovi ${place.name} dai preferiti" else "Aggiungi ${place.name} ai preferiti",
                    tint = if (place.favorite) Color(0xFFBD7765) else CollectionMuted,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionEmptyState(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(70.dp).background(CollectionLime, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(31.dp), tint = CollectionGreen)
        }
        Text(title, color = CollectionInk, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, color = CollectionMuted, fontSize = 13.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)) { Text(action) }
    }
}
