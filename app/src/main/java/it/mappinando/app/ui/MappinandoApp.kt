package it.mappinando.app.ui

import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.mappinando.app.R
import it.mappinando.app.data.*
import it.mappinando.app.map.MapFocus
import it.mappinando.app.map.PlaceSearch
import it.mappinando.app.map.SearchResult
import it.mappinando.app.map.PlaceSearcher
import it.mappinando.app.map.SearchCenter
import it.mappinando.app.updates.AppUpdateViewModel
import it.mappinando.app.updates.UpdatePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

private val Paper = Color(0xFFF8F7F3)
private val Ink = Color(0xFF213E34)
private val Forest = Color(0xFF345B46)
private val Lime = Color(0xFFDBF0A5)
private val Muted = Color(0xFF7A847B)
private val Line = Color(0xFFE5E8DF)

@Composable
fun MappinandoTheme(content: @Composable () -> Unit) {
    val font = FontFamily(
        Font(R.font.manrope, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Color.White,
            primaryContainer = Lime, onPrimaryContainer = Ink,
            secondary = Forest, secondaryContainer = Color(0xFFEBF0E4),
            background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink,
            surfaceVariant = Color(0xFFEEF0E9), onSurfaceVariant = Muted,
            outline = Line, outlineVariant = Line,
        ),
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = font),
            displayMedium = base.displayMedium.copy(fontFamily = font),
            displaySmall = base.displaySmall.copy(fontFamily = font),
            headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = base.headlineMedium.copy(fontFamily = font),
            headlineSmall = base.headlineSmall.copy(fontFamily = font),
            titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleMedium = base.titleMedium.copy(fontFamily = font),
            titleSmall = base.titleSmall.copy(fontFamily = font),
            bodyLarge = base.bodyLarge.copy(fontFamily = font, lineHeight = 25.sp),
            bodyMedium = base.bodyMedium.copy(fontFamily = font),
            bodySmall = base.bodySmall.copy(fontFamily = font),
            labelLarge = base.labelLarge.copy(fontFamily = font),
            labelMedium = base.labelMedium.copy(fontFamily = font),
            labelSmall = base.labelSmall.copy(fontFamily = font),
        ),
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}

internal enum class Tab(val label: String, val icon: ImageVector) {
    MAP("Esplora", Icons.Outlined.Map),
    PLACES("I miei luoghi", Icons.Outlined.Bookmarks),
    TASKS("Da fare", Icons.Outlined.Checklist),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MappinandoApp(searchProvider: PlaceSearcher? = null, updateManager: AppUpdateViewModel? = null) {
    val context = LocalContext.current
    val repository = remember { PlaceRepository(context.applicationContext) }
    val places by repository.places.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(Tab.MAP.name) }
    val tab = Tab.valueOf(tabName)
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryName?.let(PlaceCategory::valueOf)
    var status by rememberSaveable { mutableStateOf("Tutti") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var mapFullscreen by rememberSaveable { mutableStateOf(false) }
    var detailSnapshot by remember { mutableStateOf<Place?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var newLatitude by rememberSaveable { mutableDoubleStateOf(45.4642) }
    var newLongitude by rememberSaveable { mutableDoubleStateOf(9.19) }
    var suggestedName by rememberSaveable { mutableStateOf("") }
    var suggestedAddress by rememberSaveable { mutableStateOf("") }
    var suggestedCity by rememberSaveable { mutableStateOf("") }
    var suggestedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var searchPreview by rememberSaveable(stateSaver = SearchResultSaver) { mutableStateOf<SearchResult?>(null) }
    var searchCenter by remember { mutableStateOf(SearchCenter(45.4642, 9.19)) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var updatesOpen by rememberSaveable { mutableStateOf(false) }
    val updateState = updateManager?.state?.collectAsStateWithLifecycle()?.value
    val updatePrompt = updateManager?.shouldPrompt?.collectAsStateWithLifecycle()?.value ?: false
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) updateManager?.installReadyUpdate()
        else updateManager?.installMessage("Android non ha autorizzato l’installazione. Puoi consentirla e riprovare quando vuoi.")
    }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val compactHeight = LocalConfiguration.current.screenHeightDp < 620
    val screenState = rememberSaveableStateHolder()
    // Keep the compact layout identical even while Android hides or restores its bars.
    val stableSystemInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val systemPadding = stableSystemInsets.asPaddingValues()
    val navigationBottom = systemPadding.calculateBottomPadding()
    val dockClearance = NavigationDockHeight + NavigationDockGap + navigationBottom + 12.dp
    var compactMapBounds by remember { mutableStateOf<Rect?>(null) }
    var mapWindowSize by remember { mutableStateOf(IntSize.Zero) }
    val mapExpansion = remember { Animatable(if (mapFullscreen) 1f else 0f) }
    val mapProgress = mapExpansion.value
    val mapTransitioning = mapExpansion.isRunning || kotlin.math.abs(mapProgress - if (mapFullscreen) 1f else 0f) > .001f
    LaunchedEffect(updatePrompt, editorOpen, searchOpen, detailOpen, aboutOpen, mapTransitioning) {
        if (updatePrompt && !editorOpen && !searchOpen && !detailOpen && !aboutOpen && !mapTransitioning) {
            updatesOpen = true; updateManager?.consumePrompt()
        }
    }
    LaunchedEffect(mapFullscreen) {
        val target = if (mapFullscreen) 1f else 0f
        if (mapExpansion.value != target) {
            mapExpansion.animateTo(target, tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val geocoder = remember { PlaceSearch(context) }
    val selected = places.firstOrNull { it.id == selectedId }
    val detailVisibility = remember { MutableTransitionState(false) }
    detailVisibility.targetState = detailOpen && selected != null
    SideEffect { if (selected != null && detailOpen) detailSnapshot = selected }
    val detailPlace = selected ?: detailSnapshot
    MapImmersiveMode(enabled = mapFullscreen && !mapTransitioning && mapProgress >= .999f && !detailVisibility.currentState && !detailVisibility.targetState && !editorOpen && !searchOpen && !updatesOpen && !aboutOpen)
    fun message(text: String) { scope.launch { snackbars.showSnackbar(text) } }
    fun safely(action: () -> Unit) { runCatching(action).onFailure { message(it.message ?: "Non riesco a salvare. Riprova.") } }
    fun requestMapPresentation(fullscreen: Boolean) {
        if (!fullscreen) searchPreview = null
        // Stop before the next frame, rather than letting the old direction advance once more.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mapExpansion.stop()
            mapFullscreen = fullscreen
        }
    }
    fun openPlace(place: Place) {
        searchPreview = null
        selectedId = place.id
        detailSnapshot = place
        focus = MapFocus(place.latitude, place.longitude)
        detailOpen = true
    }
    fun addAt(latitude: Double, longitude: Double, name: String = "", address: String = "", city: String = "", suggestedCategory: PlaceCategory? = null) {
        newLatitude = latitude; newLongitude = longitude
        suggestedName = name; suggestedAddress = address; suggestedCity = city
        suggestedCategoryName = suggestedCategory?.name
        editingId = null; editorOpen = true; searchOpen = false
        focus = MapFocus(latitude, longitude)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (city.isBlank()) scope.launch {
            val resolved = try { geocoder.reverse(latitude, longitude)?.city.orEmpty() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "" }
            if (editorOpen && editingId == null && newLatitude == latitude && newLongitude == longitude) suggestedCity = resolved
        }
    }
    fun previewResult(result: SearchResult) {
        searchOpen = false
        tabName = Tab.MAP.name
        categoryName = null
        selectedId = result.savedPlace(places)?.id
        searchPreview = result
        focus = MapFocus(result.latitude, result.longitude, if (result.typeLabel == "Città") 12.0 else 16.0)
        requestMapPresentation(true)
    }
    val filteredPlaces = remember(places, category, status) {
        places.filter { place ->
            (category == null || place.category == category) && when (status) {
                "Da visitare" -> !place.visited
                "Visitati" -> place.visited
                "Preferiti" -> place.favorite
                else -> true
            }
        }
    }
    val mapPlaces = remember(places, category) { places.filter { category == null || it.category == category } }
    BackHandler(enabled = tab != Tab.MAP && !editorOpen && !detailOpen && !searchOpen && !aboutOpen) {
        tabName = Tab.MAP.name
    }
    BackHandler(enabled = mapFullscreen && !detailOpen && !editorOpen && !searchOpen && !aboutOpen) { requestMapPresentation(false) }
    BackHandler(enabled = detailOpen && !editorOpen && !searchOpen && !aboutOpen) { detailOpen = false }
    BackHandler(enabled = searchPreview != null && !detailOpen && !editorOpen && !searchOpen && !aboutOpen) {
        searchPreview = null; selectedId = null
    }

    Box(Modifier.fillMaxSize().onSizeChanged { mapWindowSize = it }) {
    Scaffold(
        modifier = Modifier.graphicsLayer { alpha = 1f - mapProgress }.semantics {
            if (mapFullscreen || mapTransitioning || detailOpen) hideFromAccessibility()
        },
        containerColor = Paper,
        contentWindowInsets = stableSystemInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbars, Modifier.padding(bottom = dockClearance)) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (tab == Tab.MAP) BrandHeader(onAbout = { aboutOpen = true })
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
                modifier = Modifier.weight(1f), label = "Schermata",
            ) { currentTab ->
                screenState.SaveableStateProvider(currentTab.name) {
                when (currentTab) {
                    Tab.MAP -> Column(Modifier.fillMaxSize().padding(bottom = dockClearance)) {
                        if (!compactHeight) {
                            Column(Modifier.padding(horizontal = 24.dp)) {
                                Text("Il mondo, a modo tuo.", fontSize = 28.sp, letterSpacing = (-1).sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                                Text("I posti del cuore. Quelli di domani.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                            }
                            SearchEntry(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) { searchOpen = true }
                        }
                        CategoryFilters(category, { categoryName = it?.name })
                        Box(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp).padding(top = 12.dp)
                                .onGloballyPositioned { coordinates ->
                                    if (tab == Tab.MAP) {
                                        val measured = coordinates.boundsInRoot()
                                        if (measured.width > 0f && measured.height > 0f) compactMapBounds = measured
                                    }
                                }
                                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFE7EADD))
                                .testTag("map-compact-anchor"),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 9.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.TouchApp, null, Modifier.size(13.dp), tint = Muted)
                            Text("Tieni premuto sulla mappa per salvare un posto", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(start = 5.dp))
                        }
                        if (!compactHeight) {
                            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("La tua prossima tappa", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { tabName = Tab.PLACES.name }) {
                                    Text("Vedi tutti", fontSize = 11.sp)
                                    Icon(Icons.Outlined.ArrowForward, null, Modifier.padding(start = 5.dp).size(14.dp))
                                }
                            }
                            if (mapPlaces.isEmpty()) {
                                Text(if (places.isEmpty()) "La tua mappa comincia da un posto. Aggiungilo con +." else "Nessun luogo con questi filtri. Prova un’altra categoria.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                            } else LazyRow(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(mapPlaces.sortedBy { it.visited }, key = { it.id }) { place ->
                                    CompactPlaceCard(place, onClick = { openPlace(place) }, onFavorite = { safely { repository.toggleFavorite(place.id) } })
                                }
                            }
                        }
                    }
                    Tab.PLACES -> PlacesScreen(
                        places, filteredPlaces, category, status,
                        onCategory = { categoryName = it?.name }, onStatus = { status = it },
                        onPlace = ::openPlace, onFavorite = { safely { repository.toggleFavorite(it.id) } },
                        onAdd = { searchOpen = true },
                        bottomClearance = dockClearance,
                        onAbout = { aboutOpen = true },
                    )
                    Tab.TASKS -> TasksScreen(places, onPlace = ::openPlace, onToggleTask = { place, task -> safely { repository.toggleTask(place.id, task.id) } }, onAdd = { searchOpen = true }, bottomClearance = dockClearance, onAbout = { aboutOpen = true })
                }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = !mapFullscreen && !detailOpen,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(220)) + slideInVertically(tween(340)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(260)) { it / 2 },
    ) {
    FloatingNavigationDock(
        selected = tab,
        onSelect = { if (!mapTransitioning && !mapFullscreen) tabName = it.name },
        modifier = Modifier.windowInsetsPadding(stableSystemInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp).padding(bottom = NavigationDockGap),
    )
    }
    if (mapProgress > 0f || mapTransitioning) {
        Box(Modifier.fillMaxSize().blockMapTransitionTouches())
    }
    AnimatedVisibility(
        visible = tab == Tab.MAP,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)), exit = fadeOut(tween(120)),
    ) {
        screenState.SaveableStateProvider("native-map-canvas") {
            val anchor = compactMapBounds
            if (anchor != null && mapWindowSize.width > 0 && mapWindowSize.height > 0) {
                MapScene(
                    compactBounds = anchor, windowSize = mapWindowSize, expansion = mapProgress,
                    fullscreen = mapFullscreen, transitioning = mapTransitioning || tab != Tab.MAP,
                    systemPadding = systemPadding, places = mapPlaces, selectedPlaceId = selectedId, focus = focus,
                    onPlaceClick = { if (!mapTransitioning && tab == Tab.MAP) openPlace(it) },
                    onMapLongClick = { latitude, longitude -> if (!mapTransitioning && tab == Tab.MAP) addAt(latitude, longitude) },
                    onToggleFullscreen = { if (!mapTransitioning) requestMapPresentation(!mapFullscreen) },
                    onRecenter = {
                        searchPreview = null
                        val target = mapPlaces.firstOrNull { it.id == selectedId } ?: mapPlaces.firstOrNull()
                        focus = MapFocus(target?.latitude ?: 45.4642, target?.longitude ?: 9.19, 12.8)
                    },
                    onAdd = { searchOpen = true },
                    onSearch = { searchOpen = true },
                    preview = searchPreview, previewSaved = searchPreview?.savedPlace(places) != null,
                    onClosePreview = { searchPreview = null; selectedId = null },
                    onSavePreview = {
                        searchPreview?.let { result ->
                            val saved = result.savedPlace(places)
                            if (saved != null) openPlace(saved)
                            else addAt(result.latitude, result.longitude, result.suggestedName.ifBlank { result.name }, result.address, result.city, result.category)
                        }
                    },
                    onCameraIdle = { latitude, longitude -> searchCenter = SearchCenter(latitude, longitude) },
                )
            }
        }
    }
    AnimatedVisibility(
        visibleState = detailVisibility,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(240)) + slideInHorizontally(tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 7 } + scaleIn(tween(420), initialScale = .965f),
        exit = fadeOut(tween(220)) + slideOutHorizontally(tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 6 } + scaleOut(tween(350), targetScale = .97f),
        label = "Apertura del luogo",
    ) {
        detailPlace?.let { place ->
            PlaceDetailScreen(
                place = place, onDismiss = { detailOpen = false },
                onEdit = { editingId = it.id; newLatitude = it.latitude; newLongitude = it.longitude; editorOpen = true },
                onDelete = { safely { repository.delete(it.id); detailOpen = false; selectedId = null; message("Luogo eliminato") } },
                onToggleFavorite = { safely { repository.toggleFavorite(place.id) } },
                onToggleVisited = { safely { repository.toggleVisited(place.id) } },
                onToggleTask = { safely { repository.toggleTask(place.id, it) } },
                onAddTask = { safely { repository.addTask(place.id, it) } },
                onDeleteTask = { safely { repository.deleteTask(place.id, it) } },
                onNavigate = { navigateToPlace(context, place) { message(it) } },
            )
        }
    }
    }

    if (searchOpen) SearchSheet(
        places = places, center = searchCenter, searcher = searchProvider ?: geocoder, onDismiss = { searchOpen = false },
        onResult = ::previewResult,
        onPickMap = { searchOpen = false; tabName = Tab.MAP.name; message("Tieni premuto un punto della mappa per aggiungerlo.") },
    )
    if (editorOpen) PlaceEditorSheet(
        initial = places.firstOrNull { it.id == editingId }, latitude = newLatitude, longitude = newLongitude,
        suggestedName = if (editingId == null) suggestedName else "", suggestedAddress = if (editingId == null) suggestedAddress else "",
        suggestedCity = if (editingId == null) suggestedCity else "",
        suggestedCategory = if (editingId == null) suggestedCategoryName?.let(PlaceCategory::valueOf) else null,
        onDismiss = { editorOpen = false },
        onSave = { place -> safely {
            if (editingId == null) repository.add(place) else repository.update(place)
            selectedId = place.id; editorOpen = false; detailOpen = editingId != null
            searchPreview = null
            categoryName = null; status = "Tutti"
            focus = MapFocus(place.latitude, place.longitude)
            message(if (editingId == null) "Un nuovo posto nella tua mappa ✨" else "Luogo aggiornato")
        } },
    )
    if (aboutOpen) AboutSheet(onDismiss = { aboutOpen = false }, onUpdates = if (updateManager == null) null else {
        {
            aboutOpen = false; updatesOpen = true
            if (updateState?.phase !in listOf(UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY)) updateManager.checkForUpdates()
        }
    })
    if (updatesOpen && updateManager != null && updateState != null) AppUpdatesSheet(
        state = updateState,
        onDismiss = { updatesOpen = false; updateManager.postpone() },
        onCheck = { updateManager.checkForUpdates() }, onDownload = { updateManager.downloadUpdate() },
        onCancelDownload = { updateManager.cancelDownload() },
        onOpenReleases = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateState.release?.pageUrl ?: "https://github.com/${it.mappinando.app.BuildConfig.GITHUB_REPOSITORY}/releases"))) }
                .onFailure { updateManager.installMessage("Non riesco ad aprire la pagina GitHub. Controlla di avere un browser installato.") }
        },
        onInstall = {
            if (context.packageManager.canRequestPackageInstalls()) updateManager.installReadyUpdate()
            else runCatching { installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                .onFailure { updateManager.installMessage("Apri le impostazioni Android e consenti le installazioni da Mappinando, poi riprova.") }
        },
    )
}

@Composable
private fun MapImmersiveMode(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        var current: Context? = context
        var activity: Activity? = null
        while (current != null && activity == null) {
            when (val candidate = current) {
                is Activity -> activity = candidate
                is ContextWrapper -> current = candidate.baseContext.takeUnless { it === candidate }
                else -> current = null
            }
        }
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private fun navigateToPlace(context: Context, place: Place, onError: (String) -> Unit) {
    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=17/${place.latitude}/${place.longitude}"))) }
            .onFailure { onError("Installa un’app di mappe per aprire le indicazioni.") }
    }
}

@Composable
private fun BrandHeader(onAbout: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Modifier.size(36.dp))
        Text("mappinando", fontSize = 22.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp).weight(1f))
        IconButton(onClick = onAbout) {
            Box(Modifier.size(37.dp).background(Color(0xFFEBEEE5), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Explore, "Informazioni su Mappinando", Modifier.size(21.dp), tint = Forest)
            }
        }
    }
}

@Composable
internal fun BrandMark(modifier: Modifier) {
    Image(painterResource(R.drawable.ic_launcher), contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp)))
}

@Composable
private fun SearchEntry(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).testTag("search-entry").padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(21.dp), tint = Forest)
        Text("Dove ti porta la curiosità?", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Icon(Icons.Outlined.NorthEast, null, Modifier.size(17.dp), tint = Muted)
    }
}

@Composable
internal fun CategoryFilters(selected: PlaceCategory?, onSelect: (PlaceCategory?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryChip("Tutti", Icons.Outlined.Widgets, selected == null) { onSelect(null) }
        PlaceCategory.entries.forEach { category ->
            CategoryChip(category.label, category.icon(), selected == category) { onSelect(if (selected == category) null else category) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Forest else Color.White, label = "Filtro categoria")
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
        shape = CircleShape, border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Line, selectedBorderColor = Forest),
        colors = FilterChipDefaults.filterChipColors(containerColor = color, selectedContainerColor = color, selectedLabelColor = Color.White, selectedLeadingIconColor = Lime, labelColor = Ink, iconColor = Muted),
        modifier = Modifier.heightIn(min = 42.dp),
    )
}

internal fun PlaceCategory.icon(): ImageVector = when (this) {
    PlaceCategory.RESTAURANTS -> Icons.Outlined.Restaurant
    PlaceCategory.BARS -> Icons.Outlined.LocalCafe
    PlaceCategory.VIEWS -> Icons.Outlined.WbTwilight
    PlaceCategory.CULTURE -> Icons.Outlined.AccountBalance
    PlaceCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    PlaceCategory.NATURE -> Icons.Outlined.Park
}

internal fun PlaceCategory.tint(): Color = when (this) {
    PlaceCategory.RESTAURANTS -> Color(0xFFBE765A)
    PlaceCategory.BARS -> Color(0xFFAD8640)
    PlaceCategory.VIEWS -> Color(0xFF9172A2)
    PlaceCategory.CULTURE -> Color(0xFF6589A2)
    PlaceCategory.SHOPPING -> Color(0xFFB87989)
    PlaceCategory.NATURE -> Color(0xFF6E9270)
}

@Composable
private fun CompactPlaceCard(place: Place, onClick: () -> Unit, onFavorite: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(21.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.width(290.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryArtwork(place.category, Modifier.size(62.dp).clip(RoundedCornerShape(15.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(place.category.label.uppercase(), color = place.category.tint(), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text(place.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(5.dp).background(if (place.visited) Forest else Color(0xFFCB9D4B), CircleShape))
                    Text(if (place.visited) "Già nel cuore" else "Da visitare", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(start = 5.dp))
                    if (place.tasks.isNotEmpty()) Text(" · ${place.tasks.count { !it.done }} cose da fare", fontSize = 10.sp, color = Muted)
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                Icon(if (place.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (place.favorite) "Rimuovi ${place.name} dai preferiti" else "Aggiungi ${place.name} ai preferiti", tint = if (place.favorite) Color(0xFFBD7765) else Muted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/** Small original landscape illustrations; no remote image assets or tracking. */
@Composable
internal fun CategoryArtwork(category: PlaceCategory, modifier: Modifier = Modifier) {
    val tint = category.tint()
    Box(modifier.background(tint.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(tint.copy(alpha = .18f), w * .35f, Offset(w * .9f, h * .1f))
            drawCircle(tint.copy(alpha = .13f), w * .4f, Offset(w * .03f, h * .98f))
            drawLine(tint.copy(alpha = .25f), Offset(w * .17f, h * .82f), Offset(w * .83f, h * .82f), 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        Icon(category.icon(), null, Modifier.size(30.dp), tint = tint)
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(78.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp), tint = Forest) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit, onUpdates: (() -> Unit)? = null) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { BrandMark(Modifier.size(60.dp)) }
            item { Text("Il tuo piccolo atlante\npersonale.", fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) }
            if (onUpdates != null) item {
                OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("about-updates")) {
                    Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(21.dp))
                    Text("Aggiornamenti dell’app", Modifier.padding(start = 9.dp))
                }
            }
            item { Text("Mappinando raccoglie i posti che ami e quelli che vuoi ancora scoprire. Salva un luogo, dagli una categoria e annota tutte le cose che ti va di fare.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp) }
            item {
                Surface(color = Lime.copy(alpha = .5f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Si comincia con un po’ d’ispirazione", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Al primo avvio trovi 6 luoghi di esempio a Milano. Puoi modificarli o eliminarli: la mappa diventa subito tua.", fontSize = 13.sp, lineHeight = 21.sp)
                    }
                }
            }
            item { Text("I tuoi dati", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            item { Text("I luoghi, le note e le attività sono salvati localmente. Non servono un account o l’accesso alla posizione. Cercando online, Photon riceve il testo, il filtro e il centro approssimativo della mappa. Per le ricerche per nome e indirizzo viene consultato anche il servizio Android, se disponibile. I servizi sono gratuiti e non richiedono chiavi API. Disinstallando l’app si eliminano i dati locali.", color = Muted, fontSize = 13.sp, lineHeight = 22.sp) }
            item { HorizontalDivider(color = Line) }
            item { Text("Mappe di OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors. Ricerca di attività: Photon, ospitato da komoot. Motore cartografico MapLibre Native.", color = Muted, fontSize = 11.sp, lineHeight = 18.sp) }
            item {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) } }) {
                    Text("Crediti e licenza della mappa")
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.padding(start = 8.dp).size(15.dp))
                }
                Text("Mappinando ${it.mappinando.app.BuildConfig.VERSION_NAME} · Fatto per andare a vedere.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}
