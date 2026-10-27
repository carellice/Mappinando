package it.mappinando.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceValidation
import it.mappinando.app.map.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SearchMuted = Color(0xFF7A847B)
private val SearchForest = Color(0xFF345B46)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(
    places: List<Place>, center: SearchCenter, searcher: PlaceSearcher,
    onDismiss: () -> Unit, onResult: (SearchResult) -> Unit, onPickMap: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(SearchKind.ALL.name) }
    val kind = SearchKind.valueOf(kindName)
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var coordinatesOpen by rememberSaveable { mutableStateOf(false) }
    var latText by rememberSaveable { mutableStateOf("") }
    var lonText by rememberSaveable { mutableStateOf("") }
    var manualAddress by rememberSaveable { mutableStateOf(false) }
    var manualName by rememberSaveable { mutableStateOf("") }
    val local = remember(query, places, kind) {
        if (query.trim().length < 2) emptyList() else places.filter {
            (it.name.contains(query.trim(), true) || it.address.contains(query.trim(), true) || it.city.contains(query.trim(), true)) &&
                when (kind) {
                    SearchKind.ALL -> true
                    SearchKind.RESTAURANTS -> it.category == PlaceCategory.RESTAURANTS
                    SearchKind.CAFES -> it.category == PlaceCategory.BARS
                    SearchKind.SHOPS -> it.category == PlaceCategory.SHOPPING
                    SearchKind.CULTURE -> it.category == PlaceCategory.CULTURE
                }
        }
    }
    fun resetResults() { results = emptyList(); searched = false; error = null }
    fun choose(result: SearchResult) {
        if (manualAddress && manualName.isBlank()) return
        keyboard?.hide()
        onResult(if (manualAddress) result.copy(suggestedName = manualName.trim(), typeLabel = "Indirizzo da verificare", category = kind.placeCategory ?: result.category) else result)
    }
    fun search() {
        if (searching || query.trim().length < 3) return
        keyboard?.hide(); resetResults(); searching = true
        scope.launch {
            try {
                results = searcher.search(query, center, if (manualAddress) SearchKind.ALL else kind)
                searched = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Non riesco a cercare adesso. Controlla la connessione e riprova: puoi sempre scegliere un punto sulla mappa." }
            finally { searching = false }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFFF8F7F3)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().testTag("map-search-sheet"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("La prossima scoperta?", fontSize = 28.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold)
                Text("Cerca città, indirizzi e attività per nome. Scegli un risultato per vederlo sulla mappa, poi decidi se salvarlo.",
                    color = SearchMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                if (manualAddress) {
                    OutlinedTextField(manualName, { manualName = it.take(PlaceValidation.MAX_NAME_LENGTH) }, enabled = !searching,
                        label = { Text("Nome del locale da salvare") }, singleLine = true, isError = manualName.isBlank(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("search-manual-name"), shape = RoundedCornerShape(18.dp))
                    Text("Cerca il suo indirizzo e controlla il punto sulla mappa. Il nome verrà proposto solo nel form di salvataggio.",
                        fontSize = 12.sp, color = SearchMuted, modifier = Modifier.padding(bottom = 10.dp))
                }
                OutlinedTextField(query, { query = it.take(200); resetResults() }, enabled = !searching,
                    modifier = Modifier.fillMaxWidth().testTag("search-query"),
                    label = { Text(if (manualAddress) "Indirizzo del locale e città" else "Luogo, ristorante o indirizzo") }, placeholder = { Text(if (manualAddress) "Via e numero civico, città" else "Es. Nerino Dieci, Milano") },
                    singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(18.dp),
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; resetResults() }, enabled = !searching) { Icon(Icons.Outlined.Close, "Svuota ricerca") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
            }
            item {
                if (!manualAddress) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchKind.entries) { option ->
                        FilterChip(selected = kind == option, onClick = { kindName = option.name; resetResults() }, enabled = !searching,
                            label = { Text(option.label) }, modifier = Modifier.testTag("search-kind-${option.name}"))
                    }
                }
                }
                Text("Priorità alla zona della mappa. Aggiungi una città per cercare altrove.", fontSize = 11.sp, color = SearchMuted, lineHeight = 16.sp)
                Button(onClick = ::search, enabled = query.trim().length >= 3 && !searching && (!manualAddress || manualName.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp).testTag("search-submit"), shape = RoundedCornerShape(16.dp)) {
                    if (searching) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Outlined.TravelExplore, null, Modifier.size(19.dp))
                    Text(if (searching) "Cerco il tuo posto…" else "Cerca sulla mappa", Modifier.padding(start = 9.dp))
                }
            }
            if (local.isNotEmpty() && !manualAddress) {
                item { SearchSectionLabel("GIÀ TRA LE TUE TAPPE") }
                items(local, key = { "local-${it.id}" }) { place ->
                    SearchResultRow(place.name, place.address.ifBlank { place.city }, "${place.category.label} · Salvato", Icons.Outlined.BookmarkAdded,
                        Modifier.testTag("search-saved-${place.id}")) { choose(SearchResult.fromPlace(place)) }
                }
            }
            if (error != null) item {
                Surface(color = Color(0xFFF8EAE2), shape = RoundedCornerShape(18.dp), modifier = Modifier.testTag("search-error")) {
                    Text(error.orEmpty(), Modifier.padding(16.dp), color = Color(0xFF885C45), fontSize = 13.sp)
                }
            }
            if (searched && results.isEmpty()) item { Text("Nessun risultato. Prova con il nome del locale e la città, cambia filtro oppure scegli un punto sulla mappa.",
                fontSize = 13.sp, color = SearchMuted, modifier = Modifier.testTag("search-empty")) }
            val visible = results.filter { it.savedPlace(local) == null }
            val precise = visible.filter { !it.requiresAddressCheck && SearchRelevance.isGoodMatch(query, it) }
            val addresses = visible.filter { it.requiresAddressCheck }
            val similar = visible.filter { !it.requiresAddressCheck && !SearchRelevance.isGoodMatch(query, it) }
            if (precise.isNotEmpty()) item { SearchSectionLabel("DA ESPLORARE SULLA MAPPA") }
            items(precise) { result -> SearchProviderResult(result) { choose(result) } }
            if (addresses.isNotEmpty()) item {
                SearchSectionLabel("INDIRIZZI DA VERIFICARE")
                Text("Il nome cercato ha restituito questi indirizzi. Controlla che il punto corrisponda al locale.",
                    fontSize = 12.sp, color = SearchMuted, modifier = Modifier.padding(top = 6.dp).testTag("search-address-explanation"))
            }
            items(addresses) { result -> SearchProviderResult(result) { choose(result) } }
            if (similar.isNotEmpty()) item {
                SearchSectionLabel("ALTRI RISULTATI SIMILI")
                if (precise.isEmpty()) Text("Non ho trovato una corrispondenza precisa nel catalogo dei locali. Controlla anche la città dei risultati.",
                    fontSize = 12.sp, color = SearchMuted, modifier = Modifier.padding(top = 6.dp).testTag("search-similar-explanation"))
            }
            items(similar) { result -> SearchProviderResult(result) { choose(result) } }
            item {
                if (manualAddress) {
                    TextButton(onClick = { manualAddress = false; query = manualName.take(200); resetResults() }, enabled = !searching,
                        modifier = Modifier.fillMaxWidth().testTag("search-return-to-name")) { Text("Torna alla ricerca per nome") }
                } else {
                    TextButton(onClick = { manualName = query.take(PlaceValidation.MAX_NAME_LENGTH); query = ""; manualAddress = true; resetResults() }, enabled = !searching,
                        modifier = Modifier.fillMaxWidth().testTag("search-missing-place")) {
                        Icon(Icons.Outlined.AddLocationAlt, null, Modifier.size(20.dp))
                        Text("Non lo trovi? Aggiungilo per indirizzo", Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                HorizontalDivider(color = Color(0xFFE5E8DF))
                TextButton(onClick = { keyboard?.hide(); onPickMap() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(20.dp))
                    Text("Scegli direttamente sulla mappa", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = { coordinatesOpen = !coordinatesOpen }, modifier = Modifier.fillMaxWidth().testTag("search-coordinates-button")) {
                    Icon(Icons.Outlined.PinDrop, null, Modifier.size(19.dp))
                    Text("Inserisci le coordinate", Modifier.padding(start = 8.dp))
                }
                AnimatedVisibility(coordinatesOpen) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(latText, { latText = it.take(20) }, label = { Text("Latitudine") }, placeholder = { Text("45.4642") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.weight(1f).testTag("search-latitude"), singleLine = true)
                            OutlinedTextField(lonText, { lonText = it.take(20) }, label = { Text("Longitudine") }, placeholder = { Text("9.1900") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.weight(1f).testTag("search-longitude"), singleLine = true)
                        }
                        val latitude = latText.replace(',', '.').toDoubleOrNull()
                        val longitude = lonText.replace(',', '.').toDoubleOrNull()
                        Text("Latitudine da −90 a 90, longitudine da −180 a 180.", fontSize = 10.sp, color = SearchMuted, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { choose(SearchResult("", "", latitude!!, longitude!!)) },
                            enabled = latitude != null && latitude.isFinite() && latitude in -90.0..90.0 && longitude != null && longitude.isFinite() && longitude in -180.0..180.0,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("search-coordinate-add")) { Text("Mostra sulla mappa") }
                    }
                }
            }
            item { Text("Ricerca gratuita: Photon / © OpenStreetMap contributors e servizio indirizzi Android, se disponibile. Gli indirizzi suggeriti vanno verificati. Le tue tappe restano sul telefono.",
                color = SearchMuted, fontSize = 10.sp, lineHeight = 16.sp) }
        }
    }
}

@Composable
private fun SearchProviderResult(result: SearchResult, onClick: () -> Unit) {
    val icon = if (result.requiresAddressCheck) Icons.Outlined.LocationSearching else when (result.category) {
        PlaceCategory.RESTAURANTS -> Icons.Outlined.Restaurant
        PlaceCategory.BARS -> Icons.Outlined.LocalCafe
        PlaceCategory.SHOPPING -> Icons.Outlined.Storefront
        PlaceCategory.CULTURE -> Icons.Outlined.Museum
        else -> Icons.Outlined.Place
    }
    SearchResultRow(result.name, result.address, result.typeLabel, icon, Modifier.testTag("search-result"), onClick)
}

@Composable
private fun SearchSectionLabel(text: String) { Text(text, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = SearchMuted) }

@Composable
private fun SearchResultRow(name: String, address: String, label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SearchForest, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (label.isNotBlank()) Text(label, fontSize = 10.sp, color = SearchForest, modifier = Modifier.padding(top = 2.dp))
                if (address.isNotBlank()) Text(address, fontSize = 11.sp, color = SearchMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.Outlined.NearMe, "Mostra sulla mappa", Modifier.size(18.dp), tint = SearchMuted)
        }
    }
}
