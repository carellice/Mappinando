package it.mappinando.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mappinando.app.BuildConfig
import it.mappinando.app.updates.UpdatePhase
import it.mappinando.app.updates.UpdateState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdatesSheet(
    state: UpdateState, onDismiss: () -> Unit, onCheck: () -> Unit,
    onDownload: () -> Unit, onCancelDownload: () -> Unit, onInstall: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFFF8F7F3)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp).testTag("app-updates-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = Color(0xFFDBF0A5), shape = CircleShape) {
                Icon(Icons.Outlined.SystemUpdate, null, Modifier.padding(16.dp).size(30.dp), tint = Color(0xFF345B46))
            }
            Text("Un nuovo passo.", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
            Text("Mappinando ${BuildConfig.VERSION_NAME} installata", color = Color(0xFF7A847B), fontSize = 13.sp)
            val release = state.release
            Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (state.phase) {
                        UpdatePhase.IDLE -> Text("Controlla se c’è una nuova versione del tuo piccolo atlante.")
                        UpdatePhase.CHECKING -> {
                            CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 3.dp)
                            Text("Controllo le release su GitHub…")
                        }
                        UpdatePhase.CURRENT -> {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF345B46))
                            Text("Tutto aggiornato.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(state.message ?: "Hai già l’ultima versione di Mappinando.", fontSize = 13.sp)
                        }
                        UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY -> {
                            Text("Versione ${release?.versionName}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${String.format(Locale.ITALY, "%.1f", (release?.sizeBytes ?: 0) / 1_048_576.0)} MB · Le tue tappe restano al loro posto.", fontSize = 12.sp, color = Color(0xFF7A847B))
                            if (state.phase == UpdatePhase.DOWNLOADING) {
                                val progress = (state.downloadedBytes.toFloat() / (release?.sizeBytes ?: 1)).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("update-progress"))
                                Text("Download ${(progress * 100).toInt()}%", fontSize = 13.sp)
                            } else if (state.phase == UpdatePhase.READY) {
                                Text("Download verificato. Pronto da installare.", color = Color(0xFF345B46), fontSize = 13.sp)
                            }
                            if (!release?.notes.isNullOrBlank() && state.phase == UpdatePhase.AVAILABLE) {
                                Text("Cosa cambia", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(release!!.notes.take(1500), fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                        UpdatePhase.ERROR -> Text(state.message ?: "Non riesco a completare l’aggiornamento. Riprova tra poco.", color = Color(0xFF885C45), fontSize = 14.sp)
                    }
                    if (state.phase != UpdatePhase.ERROR && state.phase != UpdatePhase.CURRENT && state.message != null) {
                        Text(state.message, fontSize = 12.sp, color = Color(0xFF7A847B), lineHeight = 19.sp)
                    }
                }
            }
            when (state.phase) {
                UpdatePhase.CHECKING -> Unit
                UpdatePhase.DOWNLOADING -> TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth().testTag("update-cancel")) { Text("Annulla download") }
                UpdatePhase.AVAILABLE -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("update-download")) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(20.dp)); Text("Scarica aggiornamento", Modifier.padding(start = 8.dp))
                }
                UpdatePhase.READY -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("update-install")) { Text("Installa aggiornamento") }
                UpdatePhase.ERROR -> Button(onClick = if (release == null) onCheck else onDownload,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("update-retry")) { Text("Riprova") }
                else -> Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("update-check")) { Text("Controlla aggiornamenti") }
            }
            Text("Android ti chiederà di confermare l’installazione. Non occorrono un account GitHub o un abbonamento.",
                fontSize = 11.sp, color = Color(0xFF7A847B), lineHeight = 18.sp)
            TextButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) { Text("Vedi le release su GitHub") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (state.phase == UpdatePhase.AVAILABLE) "Più tardi" else "Chiudi") }
        }
    }
}
