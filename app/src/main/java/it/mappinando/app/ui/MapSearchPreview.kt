package it.mappinando.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.map.SearchResult
import it.mappinando.app.map.openAddressInGoogleMaps

internal val SearchResultSaver = listSaver<SearchResult?, String>(
    save = { result -> result?.let { listOf(it.name, it.address, it.latitude.toString(), it.longitude.toString(), it.city, it.category?.name.orEmpty(), it.typeLabel, it.savedPlaceId.orEmpty(), it.suggestedName) }.orEmpty() },
    restore = { values -> if (values.size !in 8..9) null else SearchResult(values[0], values[1], values[2].toDouble(), values[3].toDouble(), values[4],
        values[5].takeIf(String::isNotEmpty)?.let(PlaceCategory::valueOf), values[6], values[7].takeIf(String::isNotEmpty), values.getOrElse(8) { "" }) },
)

@Composable
internal fun MapSearchPreview(result: SearchResult, saved: Boolean, onSave: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mapsError by remember(result) { mutableStateOf(false) }
    Surface(modifier.testTag("map-search-preview"), shape = RoundedCornerShape(26.dp), color = Color(0xFFFAF9F4), shadowElevation = 10.dp) {
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 7.dp)) {
                    Text(if (saved) "GIÀ TRA LE TUE TAPPE" else result.typeLabel.ifBlank { "DA ESPLORARE" }.uppercase(java.util.Locale.ITALY),
                        color = Color(0xFF64806C), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(result.name.ifBlank { "Un nuovo punto da scoprire" }, Modifier.padding(top = 4.dp), fontSize = 19.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF213E34), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("map-preview-close")) { Icon(Icons.Outlined.Close, "Chiudi anteprima", tint = Color(0xFF64806C)) }
            }
            Row(Modifier.padding(top = 5.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Place, null, Modifier.size(15.dp), tint = Color(0xFF64806C))
                Text(result.address.ifBlank { String.format(java.util.Locale.ITALY, "%.5f, %.5f", result.latitude, result.longitude) },
                    fontSize = 12.sp, color = Color(0xFF7A847B), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (result.requiresAddressCheck) {
                Text("Punto cercato per «${result.suggestedName}». Verifica che l’indirizzo corrisponda al locale prima di salvarlo.",
                    Modifier.padding(top = 8.dp, end = 8.dp).testTag("map-preview-address-check"),
                    fontSize = 12.sp, color = Color(0xFF885C45), lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp).height(48.dp).testTag("map-preview-save"), shape = RoundedCornerShape(16.dp)) {
                Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.AddLocationAlt, null, Modifier.size(19.dp))
                Text(if (saved) "Apri tappa" else "Aggiungi alle tappe", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { mapsError = !openAddressInGoogleMaps(context, result) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).height(48.dp).testTag("map-preview-google-maps"),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF345B46).copy(alpha = .25f)),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                Text("Verifica su Google Maps", Modifier.padding(start = 8.dp))
            }
            if (mapsError) {
                Text("Non riesco ad aprire Google Maps. Installa Google Maps o un browser e riprova.",
                    Modifier.padding(top = 8.dp, end = 8.dp).testTag("map-preview-google-maps-error"),
                    color = Color(0xFF885C45), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}
