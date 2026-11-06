@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arcanatable

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.app.Activity
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    /** Current screen controller, also used by integration tests of the parked server transport. */
    internal var tableController: TableController? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                getSharedPreferences("arcana_crash", Context.MODE_PRIVATE).edit()
                    .putString("last_crash", Log.getStackTraceString(error).take(24_000))
                    .putString("last_crash_context", getSharedPreferences("arcana_options", Context.MODE_PRIVATE).getString("last_diagnostic_context", "No recent action context was recorded."))
                    .putString("last_crash_at", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()))
                    .commit()
            }
            previousHandler?.uncaughtException(thread, error)
        }
        setContent { TableScreen(onControllerReady = { tableController = it }) }
    }

    override fun onResume() { super.onResume(); appInForeground = true }
    override fun onPause() { appInForeground = false; super.onPause() }
    override fun onDestroy() { tableController = null; super.onDestroy() }
}

@Composable
fun TableScreen() = TableScreen(onControllerReady = {})

/** Lets integration tests start the retained server transport without enabling its parked menu. */
@Composable
internal fun TableScreen(onControllerReady: (TableController) -> Unit) {
    val context = LocalContext.current
    val optionPrefs = remember { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    var darkMode by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("dark_mode", true)) }
    var paletteName by rememberSaveable {
        mutableStateOf(optionPrefs.getString(TABLE_PALETTE_PREF, "Arcana Original") ?: "Arcana Original")
    }
    var customPalette by remember { mutableStateOf(readCustomTablePalette(optionPrefs)) }
    var customPaletteInitialized by remember {
        mutableStateOf(optionPrefs.getBoolean(TABLE_CUSTOM_READY_PREF, false))
    }
    val selectedPalette = if (paletteName == CUSTOM_TABLE_PALETTE) customPalette else tablePaletteByName(paletteName)
    // Keep the existing Light/Dark switch: a palette selection switches to that dark palette,
    // but the Light option can still temporarily override it without erasing the selection.
    val colors = if (darkMode) selectedPalette else lightTableColors
    val scheme = if (darkMode) darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.panel)
        else lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.panel)
    val cardZoom = remember { CardZoomController() }
    val paletteEditor = TablePaletteEditor(
        selectedName = paletteName,
        selectedColors = selectedPalette,
        customColors = customPalette,
        onSelect = { name ->
            if (name == CUSTOM_TABLE_PALETTE && !customPaletteInitialized) {
                // First use starts with the currently selected colors, not a random default.
                customPalette = selectedPalette
                customPaletteInitialized = true
                optionPrefs.edit().writeCustomTablePalette(customPalette).apply()
            }
            if (name == CUSTOM_TABLE_PALETTE || tablePalettePresets.any { it.name == name }) {
                paletteName = name
                darkMode = true
                optionPrefs.edit().putString(TABLE_PALETTE_PREF, name).putBoolean("dark_mode", true).apply()
            }
        },
        onCustomColor = { role, color ->
            val next = customPalette.withColorRole(role, color)
            customPalette = next
            customPaletteInitialized = true
            paletteName = CUSTOM_TABLE_PALETTE
            darkMode = true
            optionPrefs.edit().writeCustomTablePalette(next)
                .putString(TABLE_PALETTE_PREF, CUSTOM_TABLE_PALETTE)
                .putBoolean("dark_mode", true).apply()
        },
    )
    CompositionLocalProvider(
        LocalTableColors provides colors, LocalTablePaletteEditor provides paletteEditor, LocalCardZoom provides cardZoom
    ) {
        MaterialTheme(colorScheme = scheme) {
            TableScreenContent(darkMode, onToggleTheme = {
                darkMode = !darkMode
                optionPrefs.edit().putBoolean("dark_mode", darkMode).apply()
            }, onControllerReady = onControllerReady)
            CardZoomOverlay(cardZoom)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableScreenContent(darkMode: Boolean, onToggleTheme: () -> Unit, onControllerReady: (TableController) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardZoom = LocalCardZoom.current
    val t = remember { TableController(context, scope, cardZoom) }
    SideEffect { onControllerReady(t) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    t.configuration = configuration
    t.isLandscape = isLandscape
    t.layoutOrientationSuffix = if (isLandscape) "landscape" else "portrait"
    t.screenWidthDp = configuration.screenWidthDp
    t.density = LocalDensity.current
    t.haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    t.darkMode = darkMode
    t.onToggleTheme = onToggleTheme
    t.viewPagerState = rememberPagerState(pageCount = { t.players.size + 1 })
    with(t) {
        notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        backgroundAlertsEnabled = granted
        context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit().putBoolean("background_alerts", granted).apply()
        if (!granted) message = "Android notification permission is needed for background game alerts."
    }
        diagnosticExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            recordDiagnostic("Diagnostic export canceled")
        } else {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(diagnosticReport()) }
                    ?: error("Could not open the selected file.")
            }
            diagnosticExportSelection = null
            if (result.isSuccess) {
                recordDiagnostic("Diagnostic report exported")
                message = "Diagnostic log exported."
                lobbyNotice = "Diagnostic log exported."
            } else {
                recordDiagnostic("Diagnostic export failed")
                message = "Could not write the diagnostic log."
                lobbyNotice = "Could not write the diagnostic log."
            }
        }
    }
        filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                if (text.isNullOrBlank()) importError = "That file is empty or couldn't be read." else importText = text
            } catch (_: Exception) { importError = "Couldn't read the file. Choose a text or CSV deck export." }
        }
    }
        TableEffects(darkMode)
        SideEffect { TutorialActions.onGesture = { target, kind -> tutorialSignal(kind, target) } }
        DisposableEffect(t) {
            onDispose { TutorialActions.onGesture = null }
        }
        val studioRuntime = remember(layoutOrientationSuffix, nativeStudioRevision) {
            if (BuildConfig.DEBUG) StudioControlsRuntime(optionPrefs,layoutOrientationSuffix) else null
        }
        SideEffect {studioControlsRuntime=studioRuntime}
        CompositionLocalProvider(LocalStudioControls provides studioRuntime,
            LocalStudioScreen provides "Table",
            LocalTableColors provides (studioRuntime?.project()?.colors(LocalTableColors.current) ?: LocalTableColors.current)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            TableMainLayout()
            TableOverlays()
            TableOverlaysB()
            // The coach draws on whichever window is on top: here, or the dialog/sheet a step opened.
            val coachModel = tutorialLesson?.takeIf { !tutorialHubOpen && !(tutorialPracticeActive && tutorialLoading) }?.let { lesson ->
                TutorialCoachModel(lesson, tutorialStepIndex, tutorialGated && !tutorialStepSatisfied, onNext = { tutorialAdvance() }, onSkip = { stopTutorial() })
            }
            SideEffect { TutorialCoachState.model = coachModel }
            TutorialLayer(base = true)
            studioRuntime?.let {studio ->
                if(!studio.editing && (studio.active || studio.recoveryReasons().isNotEmpty()))androidx.compose.material3.TextButton(
                    onClick={studio.begin()},colors=androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor=darkTableColors.panel,contentColor=darkTableColors.text),modifier=Modifier.align(Alignment.BottomStart).navigationBarsPadding().testTag("studio-root-escape")
                ){androidx.compose.material3.Text(if(studio.recoveryReasons().isEmpty())"Arcana Studio" else "Studio restored a control · Review",color=darkTableColors.text)}
                var studioClassic by remember(studio) {mutableStateOf(false)}
                LaunchedEffect(studio.editing) {if(!studio.editing)studioClassic=false}
                val studioDestinations=mapOf(
                        "Home" to (true to {optionsOpen=false;showHand=false;homeOpen=true}),
                        "Setup" to (!gameInProgress to {homeOpen=true;playSheetOpen=true}),
                        "Table" to (gameInProgress to {homeOpen=false;optionsOpen=false;showHand=false;showOverview=true}),
                        "Player" to (gameInProgress to {homeOpen=false;optionsOpen=false;showHand=false;showOverview=false;selectedPlayer=localSeat.coerceIn(players.indices)}),
                        "Lobby" to (!gameInProgress to {wifiMultiConfigOpen=true}),
                        "Decks" to (true to {homeOpen=true;optionsOpen=false;deckPickerOpen=true}),
                        "Settings" to (true to {optionsOpen=true}),
                        "Stack" to (gameInProgress to {stackOpen=true}),
                        "Zones" to (gameInProgress to {zoneDialog=localSeat.coerceIn(players.indices) to "Graveyard"}),
                        "Combat" to ((argentumAttackPlan!=null || argentumBlockPlan!=null) to {homeOpen=false;showHand=false}),
                    )
                if(studio.editing && !studioClassic) StudioEditorShell(studio,studioDestinations,
                    onApplied={nativeStudioRevision++},
                    onClassic={studioClassic=true;studio.panelOpen=true},
                    onHand={studio.cancel();nativeStudioOpen=true})
                else {
                if(studio.editing && !studio.panelOpen) androidx.compose.material3.TextButton(
                    onClick={studio.panelOpen=true},modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                ) {androidx.compose.material3.Text("Arcana Studio · inspecting",color=Mint)}
                if(studio.panelOpen) StudioControlsPage(studio,
                    onApplied={nativeStudioRevision++},
                    onHand={studio.cancel();nativeStudioOpen=true},
                    destinations=studioDestinations)
                }
            }
            SnackbarHost(
                hostState = automaticActionSnackbar,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
                snackbar = { data -> Snackbar(snackbarData = data, containerColor = PanelLight, contentColor = SoftWhite) }
            )
        }
        }
    }
}
