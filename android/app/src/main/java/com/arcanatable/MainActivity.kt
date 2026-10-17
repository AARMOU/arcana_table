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
        setContent { TableScreen() }
    }

    override fun onResume() { super.onResume(); appInForeground = true }
    override fun onPause() { appInForeground = false; super.onPause() }
}

@Composable
fun TableScreen() {
    val context = LocalContext.current
    val optionPrefs = remember { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    var darkMode by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("dark_mode", true)) }
    val colors = if (darkMode) darkTableColors else lightTableColors
    val scheme = if (darkMode) darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.panel)
        else lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.panel)
    val cardZoom = remember { CardZoomController() }
    CompositionLocalProvider(LocalTableColors provides colors, LocalCardZoom provides cardZoom) {
        MaterialTheme(colorScheme = scheme) {
            TableScreenContent(darkMode, onToggleTheme = {
                darkMode = !darkMode
                optionPrefs.edit().putBoolean("dark_mode", darkMode).apply()
            })
            CardZoomOverlay(cardZoom)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableScreenContent(darkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val automaticActionSnackbar = remember { SnackbarHostState() }
    val optionPrefs = remember { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    var backgroundAlertsEnabled by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("background_alerts", false)) }
    var autoReturnTableAfterPass by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("auto_return_table", true)) }
    var autoDrawOnDrawStep by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("auto_draw_step", true)) }
    var autoUntapOnUntapStep by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("auto_untap_step", true)) }
    var openHandAfterDraw by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("open_hand_after_draw", true)) }
    var reducedSwipeMotion by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("reduced_swipe_motion", false)) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val layoutOrientationSuffix = if (isLandscape) "landscape" else "portrait"
    var cardSizeScale by rememberSaveable(layoutOrientationSuffix) { mutableFloatStateOf(optionPrefs.getFloat("layout_card_size_$layoutOrientationSuffix", optionPrefs.getFloat("card_size_scale", 1f)).coerceIn(0.75f, 1.4f)) }
    var boardSpacingScale by rememberSaveable(layoutOrientationSuffix) { mutableFloatStateOf(optionPrefs.getFloat("layout_board_spacing_$layoutOrientationSuffix", 1f).coerceIn(0.5f, 2f)) }
    var footerButtonScale by rememberSaveable(layoutOrientationSuffix) { mutableFloatStateOf(optionPrefs.getFloat("layout_footer_button_scale_$layoutOrientationSuffix", 1f).coerceIn(0.78f, 1.3f)) }
    var handSheetHeightFraction by rememberSaveable(layoutOrientationSuffix) {
        mutableFloatStateOf(optionPrefs.getFloat("layout_hand_sheet_height_$layoutOrientationSuffix", optionPrefs.getFloat("hand_sheet_height_fraction", 0.52f)).coerceIn(0.38f, 0.95f))
    }
    var playerSectionOrderRaw by rememberSaveable(layoutOrientationSuffix) {
        val key = "layout_player_sections_$layoutOrientationSuffix"
        val migrationKey = "layout_mana_section_migrated_$layoutOrientationSuffix"
        val defaultSections = "STATUS|MANA|COMMANDER|STEP|ZONES|REVEALED|BATTLEFIELD"
        val saved = optionPrefs.getString(key, null)
        val order = when {
            saved == null -> defaultSections
            optionPrefs.getBoolean(migrationKey, false) -> saved
            "MANA" in saved.split('|') -> saved
            else -> saved.split('|').toMutableList().apply {
                add((indexOf("STATUS") + 1).coerceAtLeast(0), "MANA")
            }.joinToString("|")
        }
        optionPrefs.edit().putBoolean(migrationKey, true).putString(key, order).apply()
        mutableStateOf(order)
    }
    var handArrangement by rememberSaveable(layoutOrientationSuffix) { mutableStateOf(HandArrangement.parse(optionPrefs.getString("layout_hand_arrangement_$layoutOrientationSuffix", null))) }
    var handCardScale by rememberSaveable(layoutOrientationSuffix) { mutableFloatStateOf(optionPrefs.getFloat("layout_hand_card_scale_$layoutOrientationSuffix", 1f).coerceIn(0.7f, 1.4f)) }
    var showEngineStatus by rememberSaveable { mutableStateOf(optionPrefs.getBoolean("show_engine_status", true)) }
    var handActionOrderRaw by rememberSaveable(layoutOrientationSuffix) { mutableStateOf(optionPrefs.getString("layout_hand_actions_$layoutOrientationSuffix", "DRAW|REVEAL|STACK|CAST_COMMANDER|COMBAT|NEXT_STEP|PASS_TURN|MORE") ?: "DRAW|REVEAL|STACK|CAST_COMMANDER|COMBAT|NEXT_STEP|PASS_TURN|MORE") }
    val defaultQuickActionOrder = "LIBRARY|HAND|GRAVEYARD|EXILE|REVEALED|MANA|DRAW|STACK|TOKEN|COMBAT|CAST_COMMANDER"
    var quickActionOrderRaw by rememberSaveable(layoutOrientationSuffix) {
        val key = "layout_quick_actions_$layoutOrientationSuffix"
        mutableStateOf(optionPrefs.getString(key, defaultQuickActionOrder) ?: defaultQuickActionOrder)
    }
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp
    var nextId by remember { mutableIntStateOf(20) }
    var players by remember { mutableStateOf(loadSavedPlayers(context) ?: samplePlayers()) }
    var selectedPlayer by remember { mutableIntStateOf(0) }
    var showOverview by remember { mutableStateOf(true) }
    var showHand by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importSaveToLibrary by rememberSaveable { mutableStateOf(false) }
    var importSaveName by rememberSaveable { mutableStateOf("") }
    var savedDecks by remember { mutableStateOf(loadSavedDecks(context)) }
    var activeDeckId by remember { mutableStateOf(loadActiveDeck(context)) }
    var deckPickerOpen by remember { mutableStateOf(false) }
    var playSheetOpen by remember { mutableStateOf(false) }
    var offlineAiDeckId by remember { mutableStateOf<String?>(null) }
    val premadeDecks = remember { runCatching { context.assets.open("argentum/commander-decks.json").bufferedReader().use { it.readText() } }.getOrNull()?.let { runCatching { com.arcanatable.engine.OfflineGameSetup.parsePremades(it) }.getOrNull() }.orEmpty() }
    var savedDecksOpen by remember { mutableStateOf(false) }
    var localDeckSyncConfirmed by remember { mutableStateOf(false) }
    // Structured, redacted diagnostic log (DiagnosticLog.kt). Legacy free-text entries are migrated on load.
    val diagnosticLog = remember { DiagnosticLog().apply { restore(optionPrefs.getString("diagnostic_events", "").orEmpty()) } }
    var diagnosticRevision by remember { mutableIntStateOf(0) }
    var diagnosticViewerOpen by remember { mutableStateOf(false) }
    var diagnosticExportSelection by remember { mutableStateOf<List<DiagnosticEntry>?>(null) }
    var lastDiagnosticZoneSignature by remember { mutableStateOf<String?>(null) }
    var lastDiagnosticZoneSnapshot by remember { mutableStateOf<List<TablePlayer>?>(null) }
    var inboundSnapshotSequence by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var argentumDeckLoading by remember { mutableStateOf(false) }
    var argentumDeckPickerOpen by remember { mutableStateOf(false) }
    var argentumDeckOptions by remember { mutableStateOf<List<ArgentumExampleDeck>>(emptyList()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importReviewOpen by remember { mutableStateOf(false) }
    var importReviewMessage by remember { mutableStateOf("") }
    var importReviewCardIds by remember { mutableStateOf(emptySet<Int>()) }
    var importOverflowCardIds by remember { mutableStateOf(emptySet<Int>()) }
    var importAutoFixInProgress by remember { mutableStateOf(false) }
    var pendingAiPlayerIndex by remember { mutableIntStateOf(-1) }
    var clearAllConfirmOpen by remember { mutableStateOf(false) }
    var highlightedHandCardId by remember { mutableStateOf<Int?>(null) }
    var tokenPickerOpen by remember { mutableStateOf(false) }
    var tokenTarget by remember { mutableIntStateOf(0) }
    var counterTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var preview by remember { mutableStateOf<CardPreview?>(null) }
    var rulesOpen by remember { mutableStateOf(false) }
    var battlefieldBounds by remember { mutableStateOf<Rect?>(null) }
    var handSheetBounds by remember { mutableStateOf<Rect?>(null) }
    var zoneDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var cardActionTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var handActionCard by remember { mutableStateOf<GameCard?>(null) }
    var activePlayer by remember { mutableIntStateOf(0) }
    val viewPagerState = rememberPagerState(pageCount = { players.size + 1 })
    // Not saveable: after a relaunch/recreation the player chooses Resume (Argentum resumes on reconnect).
    var argentumEnabled by remember { mutableStateOf(false) }
    /** True while the current Argentum game runs on the in-app engine (no server). */
    var argentumOffline by remember { mutableStateOf(false) }
    var offlineStarting by remember { mutableStateOf(false) }
    val cardZoom = LocalCardZoom.current
    SideEffect { cardZoom.enabled = argentumEnabled; if (!argentumEnabled) cardZoom.dismiss() }
    LaunchedEffect(showOverview, selectedPlayer, players.size, reducedSwipeMotion) {
        val targetPage = if (showOverview) 0 else (selectedPlayer + 1).coerceIn(0, players.size)
        if (viewPagerState.currentPage != targetPage) {
            if (reducedSwipeMotion) viewPagerState.scrollToPage(targetPage)
            else viewPagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(viewPagerState, players.size) {
        snapshotFlow { viewPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                showOverview = page == 0
                if (page in 1..players.size) selectedPlayer = page - 1
                if (page == 0 && !argentumEnabled) showHand = false
            }
    }
    var gameTurnOrder by remember { mutableStateOf(emptyList<Int>()) }
    var turnNumber by remember { mutableIntStateOf(1) }
    val phaseSteps = remember { listOf("UNTAP", "UPKEEP", "DRAW", "PRECOMBAT MAIN", "BEGIN COMBAT", "ATTACKERS", "BLOCKERS", "DAMAGE", "END COMBAT", "POSTCOMBAT MAIN", "END STEP", "CLEANUP") }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var elapsedGameSeconds by remember { mutableLongStateOf(0L) }
    var elapsedTurnSeconds by remember { mutableLongStateOf(0L) }
    var gameLog by remember { mutableStateOf(listOf("Game session started")) }
    var logOpen by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var topMenuSection by remember { mutableStateOf<String?>(null) }
    var resetConfirmOpen by remember { mutableStateOf(false) }
    var chatDraft by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var unreadChatCount by remember { mutableIntStateOf(0) }
    var triggerPrompts by remember { mutableStateOf(emptyList<TriggerPrompt>()) }
    var stackActions by remember { mutableStateOf(emptyList<StackAction>()) }
    var targetSelectionStackItemId by remember { mutableStateOf<String?>(null) }
    var priorityPlayer by remember { mutableIntStateOf(0) }
    var priorityPassed by remember { mutableStateOf(emptySet<String>()) }
    var stackOpen by remember { mutableStateOf(false) }
    var pendingStackOpen by remember { mutableStateOf(false) }
    var stackCycleActive by remember { mutableStateOf(false) }
    var trackerPlayer by remember { mutableIntStateOf(-1) }
    var concedePlayer by remember { mutableIntStateOf(-1) }
    var shuffleConfirmPlayer by remember { mutableIntStateOf(-1) }
    var paymentCard by remember { mutableStateOf<Pair<Int, GameCard>?>(null) }
    var landEntryPrompt by remember { mutableStateOf<LandEntryPrompt?>(null) }
    var librarySearchPlayer by remember { mutableIntStateOf(-1) }
    var manaPoolOpen by remember { mutableStateOf(false) }
    var manaTarget by remember { mutableIntStateOf(0) }
    var manaChoicePrompt by remember { mutableStateOf<ManaChoicePrompt?>(null) }
    var creatureTypeChoicePrompt by remember { mutableStateOf<CreatureTypeChoicePrompt?>(null) }
    var timerMenuOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var layoutLabOpen by remember { mutableStateOf(false) }
    var changelogOpen by remember { mutableStateOf(false) }
    var lobbyOpen by remember { mutableStateOf(false) }
    var homeOpen by rememberSaveable { mutableStateOf(true) }
    var lobbyCreated by remember { mutableStateOf(false) }
    var lobbyJoinMode by remember { mutableStateOf(false) }
    var lobbyReadyPlayers by remember { mutableStateOf(emptySet<Int>()) }
    var pendingReadyState by remember { mutableStateOf<Boolean?>(null) }
    var lobbyTurnOrder by remember { mutableStateOf<List<Int>?>(null) }
    var lobbyRoomCode by remember { mutableStateOf("") }
    var lobbyPassword by remember { mutableStateOf("") }
    var lobbyJoinCode by remember { mutableStateOf("") }
    var lobbyJoinPassword by remember { mutableStateOf("") }
    var lobbyHostInput by remember { mutableStateOf("") }
    var lobbyInternetMode by rememberSaveable { mutableStateOf(false) }
    var lobbyCloudEndpoint by rememberSaveable {
        val savedEndpoint = optionPrefs.getString("cloud_room_endpoint", null)?.trim()
        mutableStateOf(savedEndpoint?.takeIf { it.isNotBlank() && !isLegacyExampleCloudEndpoint(it) } ?: DEFAULT_CLOUD_ROOM_ENDPOINT)
    }
    LaunchedEffect(lobbyOpen, lobbyInternetMode) {
        if (lobbyOpen && lobbyInternetMode && (lobbyCloudEndpoint.isBlank() || isLegacyExampleCloudEndpoint(lobbyCloudEndpoint))) {
            lobbyCloudEndpoint = DEFAULT_CLOUD_ROOM_ENDPOINT
            optionPrefs.edit().putString("cloud_room_endpoint", DEFAULT_CLOUD_ROOM_ENDPOINT).apply()
        }
    }
    var lobbyPlayerName by remember { mutableStateOf("PLAYER") }
    var lobbyHostAddress by remember { mutableStateOf("") }
    var lobbyNotice by remember { mutableStateOf<String?>(null) }
    var onlineStatus by remember { mutableStateOf("Offline") }
    var roomConnected by remember { mutableStateOf(false) }
    // Not saveable: after a relaunch/recreation the player chooses Resume (Argentum resumes on reconnect).
    var argentumResumeAvailable by remember { mutableStateOf(optionPrefs.getBoolean("argentum_game_active", false)) }
    var argentumResumeRequested by remember { mutableStateOf(false) }
    var argentumEndPreviousRequested by remember { mutableStateOf(false) }
    var argentumLastStateJson by remember { mutableStateOf<JSONObject?>(null) }
    var argentumAttackPlan by remember { mutableStateOf<ArgentumAttackPlan?>(null) }
    var argentumBlockPlan by remember { mutableStateOf<ArgentumBlockPlan?>(null) }
    var argentumDeferredDecisionId by remember { mutableStateOf<String?>(null) }
    var argentumOrderSelection by remember { mutableStateOf<List<String>>(emptyList()) }
    var argentumModeSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var argentumAmounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var argentumDeckReview by remember { mutableStateOf<ArgentumDeckReview?>(null) }
    var argentumComposer by remember { mutableStateOf<ArgentumActionPlan?>(null) }
    var argentumActionCard by remember { mutableStateOf<GameCard?>(null) }
    var argentumGameOverDismissed by remember { mutableStateOf(false) }
    var argentumMulliganInfo by remember { mutableStateOf<Triple<Int, Boolean, Int>?>(null) } // count, on the play, cards to bottom
    var argentumGameOverText by remember { mutableStateOf<String?>(null) }
    var argentumWon by remember { mutableStateOf<Boolean?>(null) }
    var argentumBudgetCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var argentumReplacementPick by remember { mutableStateOf(0 to 0) }
    val argentumReportedUnsupported = remember { mutableSetOf<String>() }
    var argentumJoinDialogOpen by rememberSaveable { mutableStateOf(false) }
    var argentumJoinCode by rememberSaveable { mutableStateOf("") }
    var argentumVsAiLobby by rememberSaveable { mutableStateOf(true) }
    var argentumStatus by remember { mutableStateOf(ArgentumConnectionStatus.DISCONNECTED) }
    var argentumPlayerId by remember { mutableStateOf<String?>(null) }
    var argentumInbound by remember { mutableStateOf<JSONObject?>(null) }
    var argentumSnapshot by remember { mutableStateOf<ArgentumTableSnapshot?>(null) }
    var argentumPendingDecision by remember { mutableStateOf<JSONObject?>(null) }
    var argentumDecisionSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var argentumDecisionTargets by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var argentumDecisionColors by remember { mutableStateOf<Set<String>>(emptySet()) }
    var argentumLeaveConfirm by remember { mutableStateOf(false) }
    var argentumDeckForLobby by remember { mutableStateOf<Map<String, Int>?>(null) }
    var argentumDeckValidationRunning by remember { mutableStateOf(false) }
    var argentumCommanderForLobby by remember { mutableStateOf<String?>(null) }
    var argentumLobbySubmitted by remember { mutableStateOf(false) }
    var argentumAwaitingUpdate by remember { mutableStateOf(false) }
    var argentumLocalPlayersBackup by remember { mutableStateOf<List<TablePlayer>?>(null) }
    var argentumStackUse by remember { mutableStateOf<StackUse?>(null) }
    var argentumAutoYes by remember { mutableStateOf(emptyMap<String, Int>()) }
    var playPopup by remember { mutableStateOf<PlayedCard?>(null) }
    var playFeed by remember { mutableStateOf(emptyList<PlayedCard>()) }
    var playBanner by remember { mutableStateOf<PlayedCard?>(null) }
    var aiRecap by remember { mutableStateOf(-1 to TurnRecap()) }
    var recapText by remember { mutableStateOf<String?>(null) }
    var argentumMulliganPrompt by remember { mutableStateOf(false) }
    var argentumBottomCardIds by remember { mutableStateOf<List<String>?>(null) }
    var argentumBottomCardCount by remember { mutableIntStateOf(0) }
    var argentumBottomSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var argentumLastError by remember { mutableStateOf<String?>(null) }
    var argentumActionChoices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    /** Argentum's `nextStopPoint` for this seat: where passing priority takes you ("To Combat"). */
    var argentumNextStop by remember { mutableStateOf<String?>(null) }
    var argentumUndoAvailable by remember { mutableStateOf(false) }
    /** Turn number when "Pass to my turn" was switched on; null when it is off. */
    var passToMyTurnFrom by remember { mutableStateOf<Int?>(null) }
    var followActivePlayer by remember { mutableStateOf(optionPrefs.getBoolean("follow_active_player", true)) }
    var argentumPingResult by remember { mutableStateOf<String?>(null) }
    var engineTrialResult by remember { mutableStateOf<String?>(null) }
    var lastOpponentViewed by remember { mutableIntStateOf(-1) }
    val argentumPrefs = remember { context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE) }
    val argentumOrigin = remember { ArgentumEndpoints.origin(argentumPrefs) }
    val argentumQueue = remember { kotlinx.coroutines.channels.Channel<JSONObject>(kotlinx.coroutines.channels.Channel.UNLIMITED) }
    val argentumSocket = remember(context) {
        ArgentumGameSocket(
            preferences = argentumPrefs,
            url = ArgentumEndpoints.socketUrl(argentumOrigin),
            onStatus = { status -> argentumStatus = status; if (status != ArgentumConnectionStatus.CONNECTED) argentumAwaitingUpdate = false },
            onMessage = { argentumQueue.trySend(it) },
            onError = { argentumLastError = it; message = it; argentumAwaitingUpdate = false },
            // Reconnect on its own only while the player is looking at the app.
            canAutoReconnect = { appInForeground }
        )
    }
    var connectionLost by remember { mutableStateOf(false) }
    var connectionLostReason by remember { mutableStateOf<String?>(null) }
    var reconnecting by remember { mutableStateOf(false) }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    var localSeat by remember { mutableIntStateOf(0) }
    var roomHost by remember { mutableStateOf<LanRoomHost?>(null) }
    var roomClient by remember { mutableStateOf<LanRoomClient?>(null) }
    var cloudRoomClient by remember { mutableStateOf<CloudRoomClient?>(null) }
    var localPlayersBackup by remember { mutableStateOf<List<TablePlayer>?>(null) }
    var lastRemoteSnapshot by remember { mutableStateOf<String?>(null) }
    var gameStartedOnline by remember { mutableStateOf(false) }
    var onlineStateReady by remember { mutableStateOf(false) }
    var endProposalBy by remember { mutableIntStateOf(-1) }
    var endVotes by remember { mutableStateOf(emptyMap<Int, Boolean>()) }
    var gameEndedOnline by remember { mutableStateOf(false) }
    var localGameStarted by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        backgroundAlertsEnabled = granted
        context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit().putBoolean("background_alerts", granted).apply()
        if (!granted) message = "Android notification permission is needed for background game alerts."
    }
    fun setBackgroundAlerts(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            backgroundAlertsEnabled = enabled
            context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit().putBoolean("background_alerts", enabled).apply()
        }
    }

    fun recordDiagnostic(event: String, category: DiagnosticCategory? = null, private: Boolean = false) {
        // Own-card moves through hidden zones name cards the opponent cannot see; keep them out of exports.
        val isPrivate = private || (event.startsWith("Card move") && (" hand" in event || "library" in event))
        val entry = diagnosticLog.record(category ?: DiagnosticLog.categorize(event), event, isPrivate)
        diagnosticRevision++
        val timestamp = entry.timestamp()
        val safeEvent = if (isPrivate) "[private game detail]" else entry.message
        val contextLine = "$timestamp · $safeEvent · ${if (appInForeground) "foreground" else "background"} · ${if (argentumEnabled || roomHost != null || roomClient != null || cloudRoomClient != null) "online" else "offline"} · ${if (showOverview) "table" else "player view"} · P${(selectedPlayer + 1).coerceAtLeast(1)} · turn $turnNumber ${phaseSteps.getOrNull(phaseIndex).orEmpty()}"
        optionPrefs.edit().putString("diagnostic_events", diagnosticLog.serialize())
            .putString("last_diagnostic_context", contextLine).apply()
    }

    /** Persisted so a relaunch can offer (never force) a resume. */
    fun setArgentumGameActive(active: Boolean) {
        // Offline games live only in this app session; there is nothing on a server to resume.
        val resumable = active && !argentumOffline
        argentumResumeAvailable = resumable
        optionPrefs.edit().putBoolean("argentum_game_active", resumable).apply()
    }

    /** A card or player for the combat board, from Argentum's masked state. */
    fun argentumPiece(id: String): CombatPiece {
        val snap = argentumSnapshot
        val displayId = snap?.serverCardIds?.entries?.firstOrNull { it.value == id }?.key
        val card = displayId?.let { d -> snap.players.flatMap { it.battlefield }.firstOrNull { it.id == d } }
        val seat = players.indexOfFirst { it.id == id }
        val name = card?.name ?: if (seat >= 0 && seat == localSeat) "You" else players.getOrNull(seat)?.name ?: snap?.targets?.get(id)?.name ?: "Object ${id.takeLast(6)}"
        return CombatPiece(id, name, card, players.getOrNull(seat)?.life)
    }

    fun argentumName(id: String): String =
        argentumSnapshot?.targets?.get(id)?.name ?: players.firstOrNull { it.id == id }?.name ?: argentumPiece(id).name

    /** Opens Argentum's attack/block declaration when the server offers one. */
    fun openArgentumCombat(): Boolean {
        val actions = argentumSnapshot?.legalActions.orEmpty()
        actions.firstNotNullOfOrNull { ArgentumAttackPlan.from(it) }?.let { argentumAttackPlan = it; recordDiagnostic("Opened attack declaration (${it.options.size} possible attackers)", DiagnosticCategory.ACTION); return true }
        actions.firstNotNullOfOrNull { ArgentumBlockPlan.from(it, argentumLastStateJson) }?.let { argentumBlockPlan = it; recordDiagnostic("Opened block declaration (${it.blockers.size} possible blockers)", DiagnosticCategory.ACTION); return true }
        return false
    }

    fun reportArgentumUnsupported(key: String, detail: String) {
        if (argentumReportedUnsupported.add(key)) recordDiagnostic("Needs handling: $detail", DiagnosticCategory.UNSUPPORTED)
    }

    fun submitArgentumAction(action: JSONObject, epoch: String): Boolean {
        if (argentumAwaitingUpdate) return false
        val decision = argentumPendingDecision
        val type = action.optString("type")
        val outbound = if (decision?.optString("type") == "PlayCardDecision" && type in setOf("CastSpell", "PlayLand")) {
            JSONObject().put("type", "SubmitDecision").put("playerId", decision.optString("playerId"))
                .put("response", JSONObject().put("type", "PlayCardResponse").put("decisionId", decision.optString("id")).put("action", JSONObject(action.toString())))
        } else action
        val sent = argentumSocket.submitLegalAction(outbound, epoch)
        if (sent) argentumAwaitingUpdate = true
        argentumStackUse?.let { use -> if (sent && !use.started) argentumStackUse = use.copy(started = true, manaColor = use.manaColor ?: action.optString("manaColorChoice").takeIf { it.isNotBlank() && it != "null" }) }
        return sent
    }

    fun sendArgentumPriority() {
        if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous action."; return }
        // Never pass over an open question (Argentum rejects it): bring the prompt back instead.
        argentumPendingDecision?.takeIf { it.optString("type") != "PlayCardDecision" }?.let { decision ->
            if (argentumDeferredDecisionId == decision.optString("id")) argentumDeferredDecisionId = null
            message = "Answer the open prompt first."
            return
        }
        val snapshot = argentumSnapshot ?: return
        val pass = snapshot.legalActions.firstOrNull { it.optJSONObject("action")?.optString("type") == "PassPriority" }
        val action = pass?.optJSONObject("action")
        val epoch = pass?.optString("interactionEpoch")?.takeUnless { it.isNullOrBlank() || it == "null" }
            ?: snapshot.interactionEpoch
        if (action == null || epoch == null) {
            if (openArgentumCombat()) return
            message = "Argentum has not offered a pass-priority action right now."
            return
        }
        if (!submitArgentumAction(action, epoch)) message = "Could not send the Argentum action. Check your connection."
        else recordDiagnostic("Sent PassPriority", DiagnosticCategory.ACTION)
    }

    fun submitArgentumDecision(response: JSONObject) {
        if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous choice."; return }
        val pending = argentumPendingDecision ?: return
        val epoch = argentumSnapshot?.interactionEpoch ?: return
        val action = JSONObject().put("type", "SubmitDecision").put("playerId", pending.optString("playerId"))
            .put("response", JSONObject(response.toString()).put("decisionId", pending.optString("id")))
        if (argentumSocket.submitLegalAction(action, epoch)) {
            recordDiagnostic("Answered ${pending.optString("type")} with ${response.optString("type")}", DiagnosticCategory.ACTION)
            argentumAwaitingUpdate = true
            argentumDeferredDecisionId = null
            argentumPendingDecision = null
            argentumDecisionSelection = emptySet()
            argentumDecisionColors = emptySet()
            argentumDecisionTargets = emptyMap()
        } else message = "Argentum did not receive that choice. Check the connection and try again."
    }

    fun openArgentumActions(card: GameCard) {
        val snapshot = argentumSnapshot ?: return
        val serverId = snapshot.serverCardIds[card.id]
        if (serverId == null) { message = "This card is not part of Argentum's current state."; return }
        val choices = snapshot.legalActions.filter { info ->
            val action = info.optJSONObject("action") ?: return@filter false
            listOf("cardId", "sourceId", "permanentId", "entityId").any { key -> action.optString(key) == serverId }
        }
        argentumActionCard = card
        // Always open the sheet: it shows the card's text even when there is nothing to do with it now.
        argentumActionChoices = choices.ifEmpty { listOf(JSONObject().put("__none", true)) }
    }

    /**
     * Single entry point for submitting a server-offered legal action from any Arcana control.
     * Routes to combat, X or target pickers; refuses (with a needs-handling note) anything
     * Arcana cannot collect input for. Never invents an action.
     */
    fun chooseArgentumLegalAction(info: JSONObject) {
        val action = info.optJSONObject("action") ?: return
        val type = action.optString("type")
        if (type == "DeclareAttackers" || type == "DeclareBlockers") { if (!openArgentumCombat()) message = "Argentum's combat declaration is no longer available."; return }
        ArgentumActions.needsHandlingReason(info)?.let { reason ->
            reportArgentumUnsupported("action:${info.optString("description")}:$type", "${info.optString("description", type)} — $reason")
            message = "Needs handling: $reason Nothing was sent to Argentum."
            return
        }
        val plan = ArgentumActionPlan.from(info)
        if (plan.needsInput) {
            // A separate ChooseTargetsDecision from the server takes precedence over pre-chosen targets.
            if (argentumPendingDecision?.optString("type") == "ChooseTargetsDecision") return
            argentumComposer = plan
            return
        }
        val epoch = ArgentumActions.epochFor(info, argentumSnapshot?.interactionEpoch)
        if (epoch == null || !submitArgentumAction(action, epoch)) message = "This Argentum action is stale or the connection is unavailable. Refresh and try again."
        else recordDiagnostic("Sent ${type} (${info.optString("description")})", DiagnosticCategory.ACTION)
    }

    fun zoneCounts(player: TablePlayer): String = listOf(
        "library=${player.library.size}", "hand=${player.hand.size}", "battlefield=${player.battlefield.size}",
        "graveyard=${player.graveyard.size}", "exile=${player.exile.size}", "revealed=${player.revealed.size}"
    ).joinToString(", ")

    fun zoneIdentitySignature(seats: List<TablePlayer>): String = seats.joinToString("|") { player ->
        listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed)
            .joinToString("/") { zone -> zone.map { it.id }.sorted().joinToString(",") }
    }

    fun zoneCards(player: TablePlayer): List<Pair<String, GameCard>> = buildList {
        player.library.forEach { add("library" to it) }
        player.hand.forEach { add("hand" to it) }
        player.battlefield.forEach { add("battlefield" to it) }
        player.graveyard.forEach { add("graveyard" to it) }
        player.exile.forEach { add("exile" to it) }
        player.revealed.forEach { add("revealed" to it) }
    }

    fun zoneIsPublic(zone: String): Boolean = zone in setOf("battlefield", "graveyard", "exile", "revealed")

    fun logZoneSnapshotChange(seats: List<TablePlayer>, source: String) {
        val signature = zoneIdentitySignature(seats)
        if (signature == lastDiagnosticZoneSignature) return
        val previous = lastDiagnosticZoneSignature
        val previousSnapshot = lastDiagnosticZoneSnapshot
        lastDiagnosticZoneSignature = signature
        lastDiagnosticZoneSnapshot = seats
        if (previous == null) {
            recordDiagnostic("Zone baseline captured ($source): ${seats.mapIndexed { i, p -> "P${i + 1} ${zoneCounts(p)}" }.joinToString("; ")}")
            return
        }
        previousSnapshot?.let { oldSeats ->
            seats.forEachIndexed { index, player ->
                val oldPlayer = oldSeats.getOrNull(index) ?: return@forEachIndexed
                val oldCards = zoneCards(oldPlayer).groupBy { it.second.id }
                val newCards = zoneCards(player).groupBy { it.second.id }
                (oldCards.keys + newCards.keys).forEach { id ->
                    val before = oldCards[id]?.singleOrNull() ?: return@forEach
                    val after = newCards[id]?.singleOrNull() ?: return@forEach
                    if (before.first == after.first || (before.second.isFaceDown || after.second.isFaceDown)) return@forEach
                    val identityVisible = index == localSeat || zoneIsPublic(before.first) || zoneIsPublic(after.first) ||
                        (before.first == "hand" && oldPlayer.handRevealedToAll) || (after.first == "hand" && player.handRevealedToAll)
                    if (identityVisible) {
                        recordDiagnostic("Card move ($source): P${index + 1} ${after.second.name} #$id ${before.first} → ${after.first}")
                    }
                }
            }
        }
        val priorSeatSignatures = previous.split('|')
        val changed = seats.mapIndexedNotNull { index, player ->
            val currentSeat = listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed)
                .joinToString("/") { zone -> zone.map { it.id }.sorted().joinToString(",") }
            if (priorSeatSignatures.getOrNull(index) == currentSeat) null else {
                val allIds = listOf(player.library, player.hand, player.battlefield, player.graveyard, player.exile, player.revealed).flatten().map { it.id }
                val repeatedInstanceCount = allIds.groupingBy { it }.eachCount().count { it.value > 1 }
                "P${index + 1} ${zoneCounts(player)}, repeated instance IDs=$repeatedInstanceCount"
            }
        }
        if (changed.isNotEmpty()) recordDiagnostic("Zone state changed ($source): ${changed.joinToString("; ")}")
    }

    fun leaveOnlineRoom() {
        val ownPlayer = players.getOrNull(localSeat)?.copy(isOnline = false)
        roomClient?.close(); roomClient = null
        cloudRoomClient?.leave(); cloudRoomClient = null
        roomHost?.close(); roomHost = null
        val backup = localPlayersBackup
        val restoredOwn = when {
            ownPlayer == null -> backup?.firstOrNull()
            ownPlayer.deckLoaded || backup?.firstOrNull()?.deckLoaded != true -> ownPlayer
            else -> backup.first()
        }
        players = if (restoredOwn != null && !backup.isNullOrEmpty()) backup.mapIndexed { index, player -> if (index == 0) restoredOwn else player.copy(isOnline = false) }
            else if (restoredOwn != null) listOf(restoredOwn) else backup.orEmpty()
        localPlayersBackup = null
        roomConnected = false; lobbyCreated = false; onlineStatus = "Offline"; localSeat = 0
        onlineStateReady = false; gameStartedOnline = false; pendingReadyState = null
        selectedPlayer = 0
        lobbyOpen = false; homeOpen = true; showOverview = true
    }

    fun diagnosticReport(): String = buildString {
        appendLine("Arcana Table diagnostic report")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Role: ${if (argentumEnabled) "Argentum client" else if (lobbyCreated) "host" else if (roomClient != null || cloudRoomClient != null) "client" else "offline"}")
        appendLine("Connection: ${if (argentumEnabled) argentumStatus.name.lowercase() else onlineStatus}")
        appendLine("Sync revision: not provided by the current room protocol; local inbound snapshot counter=$inboundSnapshotSequence")
        appendLine("Local seat: ${localSeat + 1}; seats present: ${players.size}")
        appendLine("App state: ${if (appInForeground) "foreground" else "background"}; view=${if (showOverview) "table" else "player"}; selectedSeat=${selectedPlayer + 1}; activeSeat=${activePlayer + 1}; phase=${phaseSteps.getOrNull(phaseIndex).orEmpty()}")
        appendLine("Game started: ${gameStartedOnline || localGameStarted}")
        appendLine("Lobby readiness: ${lobbyReadyPlayers.sorted().joinToString { (it + 1).toString() }.ifBlank { "none" }}")
        appendLine("Deck status: ${players.mapIndexed { index, player -> "P${index + 1}=${if (player.deckLoaded) "loaded" else "missing"}" }.joinToString()}")
        context.getSharedPreferences("arcana_crash", Context.MODE_PRIVATE).getString("last_crash", null)?.let { crash ->
            appendLine()
            val crashPrefs = context.getSharedPreferences("arcana_crash", Context.MODE_PRIVATE)
            appendLine("Crash time: ${crashPrefs.getString("last_crash_at", "unknown")}")
            appendLine("Action context: ${DiagnosticRedactor.redact(crashPrefs.getString("last_crash_context", "unknown").orEmpty())}")
            appendLine("Last uncaught app crash:")
            appendLine(DiagnosticRedactor.redact(crash))
        }
        appendLine()
        appendLine("Zone totals include counts only. Opponents' hidden card identities are excluded; own-deck duplicate summaries may include card names.")
        appendLine("Event log (server addresses, IPs, credentials, tokens, IDs and room codes redacted; private card details omitted):")
        appendLine(diagnosticLog.exportText(diagnosticExportSelection ?: diagnosticLog.entries))
    }

    val diagnosticExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
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
    fun exportDiagnosticLog() {
        recordDiagnostic("Diagnostic export requested")
        val filename = "ArcanaTable-diagnostics-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.txt"
        diagnosticExportLauncher.launch(filename)
    }
    var pendingPlayerRemoval by remember { mutableIntStateOf(-1) }
    var deckEditorOpen by remember { mutableStateOf(false) }
    var combatDialogOpen by remember { mutableStateOf(false) }
    var turnTimerRunning by rememberSaveable { mutableStateOf(false) }
    var gameTimerRunning by rememberSaveable { mutableStateOf(false) }
    var aiRoutineRunning by remember { mutableStateOf(false) }
    var gameGeneration by remember { mutableIntStateOf(0) }
    var turnTimerVisible by rememberSaveable { mutableStateOf(true) }
    var lastSwipeDistance by remember { mutableFloatStateOf(0f) }
    val onlineMode = argentumEnabled || roomHost != null || roomClient != null || cloudRoomClient != null || connectionLost
    val gameInProgress = if (argentumEnabled) argentumSnapshot != null && argentumStatus == ArgentumConnectionStatus.CONNECTED && argentumSnapshot?.gameOver != true
        else if (onlineMode) gameStartedOnline && roomConnected && !gameEndedOnline else localGameStarted
    val visibleStackCount = if (argentumEnabled) argentumSnapshot?.targets?.values?.count { it.type == "Spell" } ?: 0 else stackActions.size
    val argentumLegalTypes = argentumSnapshot?.legalActions.orEmpty().mapNotNull { it.optJSONObject("action")?.optString("type") }.toSet()
    // Display ids of cards Argentum offers an action for (play, cast, activate) — drawn with a glow.
    val argentumPlayableIds: Set<Int> = argentumSnapshot?.let { snap ->
        val byServer = snap.serverCardIds.entries.associate { it.value to it.key }
        snap.legalActions.mapNotNull { info ->
            val action = info.optJSONObject("action") ?: return@mapNotNull null
            if (!info.optBoolean("isAffordable", true) || info.optBoolean("isManaAbility")) return@mapNotNull null
            listOf("cardId", "sourceId", "permanentId", "entityId").firstNotNullOfOrNull { key -> byServer[action.optString(key)] }
        }.toSet()
    }.orEmpty()
    // The one action Arcana's primary buttons perform in an Argentum game, named after what it does.
    val argentumPrimaryLabel: String? = when {
        !argentumEnabled || argentumSnapshot == null || gameEndedOnline -> null
        "DeclareAttackers" in argentumLegalTypes -> "DECLARE ATTACKERS"
        "DeclareBlockers" in argentumLegalTypes -> "DECLARE BLOCKERS"
        "PassPriority" in argentumLegalTypes -> ArgentumPassing.passLabel(argentumNextStop)
        else -> null
    }
    // You have priority on someone else's turn: the pass button lights up so "you can respond now"
    // never looks like "it's your turn".
    val argentumCanRespond = argentumEnabled && argentumPrimaryLabel != null && !argentumAwaitingUpdate &&
        argentumSnapshot?.let { it.activePlayer != localSeat && it.priorityPlayer == localSeat } == true
    fun argentumPrimary() {
        if ("DeclareAttackers" in argentumLegalTypes || "DeclareBlockers" in argentumLegalTypes) { if (!openArgentumCombat()) message = "Argentum's combat declaration is no longer available." }
        else sendArgentumPriority()
    }
    fun requestArgentumUndo() {
        if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous action."; return }
        if (!argentumUndoAvailable) { message = "Argentum has nothing to undo right now."; return }
        passToMyTurnFrom = null
        if (argentumSocket.requestUndo()) { argentumAwaitingUpdate = true; recordDiagnostic("Requested undo", DiagnosticCategory.ACTION) }
        else message = "Could not reach Argentum to undo. Check your connection."
    }
    fun togglePassToMyTurn() {
        if (passToMyTurnFrom != null) { passToMyTurnFrom = null; recordDiagnostic("Pass to my turn: stopped by player", DiagnosticCategory.ACTION); return }
        val snapshot = argentumSnapshot ?: return
        passToMyTurnFrom = snapshot.turnNumber
        recordDiagnostic("Pass to my turn: started on turn ${snapshot.turnNumber}", DiagnosticCategory.ACTION)
    }
    // "Always yes for this card this turn": answer the same prompt from the same card automatically.
    LaunchedEffect(argentumPendingDecision?.optString("id"), argentumAwaitingUpdate) {
        val decision = argentumPendingDecision ?: return@LaunchedEffect
        if (argentumAwaitingUpdate || decision.optString("type") != "YesNoDecision") return@LaunchedEffect
        val turn = argentumSnapshot?.turnNumber ?: return@LaunchedEffect
        if (argentumAutoYes[autoYesKey(decision)] != turn) return@LaunchedEffect
        delay(250)
        recordDiagnostic("Auto-answered yes: ${decision.optString("prompt")}", DiagnosticCategory.ACTION)
        submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true))
    }
    // "Use 5 / Use all": activate the same ability on one copy after another, from each new state.
    LaunchedEffect(argentumStackUse, argentumSnapshot, argentumAwaitingUpdate, argentumPendingDecision, argentumComposer) {
        val use = argentumStackUse ?: return@LaunchedEffect
        if (!use.started || argentumAwaitingUpdate || argentumComposer != null) return@LaunchedEffect
        if (argentumPendingDecision != null && argentumPendingDecision?.optString("type") != "PlayCardDecision") { argentumStackUse = null; return@LaunchedEffect }
        val snapshot = argentumSnapshot ?: return@LaunchedEffect
        delay(120)
        val action = StackUses.next(use, snapshot.legalActions) { id -> argentumPiece(id).name }
        val epoch = snapshot.interactionEpoch
        if (action == null || epoch == null || use.remaining <= 0) { argentumStackUse = null; return@LaunchedEffect }
        argentumStackUse = use.copy(remaining = use.remaining - 1)
        if (!submitArgentumAction(action, epoch)) argentumStackUse = null
    }
    // "Pass to my turn": press Argentum's own PassPriority until your next turn or until you're needed.
    LaunchedEffect(passToMyTurnFrom, argentumSnapshot, argentumPendingDecision, argentumAwaitingUpdate, argentumStatus) {
        val start = passToMyTurnFrom ?: return@LaunchedEffect
        val snapshot = argentumSnapshot ?: return@LaunchedEffect
        if (!argentumEnabled || argentumStatus != ArgentumConnectionStatus.CONNECTED) { passToMyTurnFrom = null; return@LaunchedEffect }
        delay(300) // let a burst of updates settle before answering
        val outcome = ArgentumPassing.next(
            startTurn = start, turnNumber = snapshot.turnNumber, activePlayer = snapshot.activePlayer, localSeat = localSeat,
            priorityPlayer = snapshot.priorityPlayer,
            legalActionTypes = snapshot.legalActions.mapNotNull { it.optJSONObject("action")?.optString("type") }.toSet(),
            hasPendingDecision = argentumPendingDecision != null,
            opponentStackItem = snapshot.stack.firstOrNull { !it.isYours }?.name,
            gameOver = gameEndedOnline || snapshot.gameOver, awaitingUpdate = argentumAwaitingUpdate,
        )
        when (outcome.step) {
            ArgentumPassing.Step.PASS -> sendArgentumPriority()
            ArgentumPassing.Step.WAIT -> Unit
            ArgentumPassing.Step.REACHED_YOUR_TURN -> {
                passToMyTurnFrom = null
                recordDiagnostic("Pass to my turn: reached your turn ${snapshot.turnNumber}", DiagnosticCategory.ACTION)
                android.widget.Toast.makeText(context, "Your turn.", android.widget.Toast.LENGTH_SHORT).show()
            }
            ArgentumPassing.Step.INTERRUPTED -> {
                passToMyTurnFrom = null
                recordDiagnostic("Pass to my turn: stopped (${outcome.reason})", DiagnosticCategory.ACTION)
                outcome.reason?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
    // Follow whose turn it is: on each new turn, show the active player's board. A tab the player
    // picks mid-turn sticks until the next turn change.
    LaunchedEffect(selectedPlayer, localSeat) { if (selectedPlayer != localSeat) lastOpponentViewed = selectedPlayer }
    val argentumTurnKey = argentumSnapshot?.takeIf { argentumEnabled }?.let { it.turnNumber to it.activePlayer }
    LaunchedEffect(argentumTurnKey) {
        val active = argentumTurnKey?.second ?: return@LaunchedEffect
        if (!followActivePlayer || active !in players.indices) return@LaunchedEffect
        selectedPlayer = active
        showOverview = false
    }
    val argentumNeedsHandling: String? = argentumPendingDecision?.let { decision ->
        ArgentumDecisions.needsHandlingReason(decision)
            ?: if (decision.optString("id") == argentumDeferredDecisionId) "Argentum is waiting for your choice: ${decision.optString("prompt")}" else null
    }
    val canControlSelectedPlayer = (!onlineMode || selectedPlayer == localSeat) && !gameEndedOnline && players.getOrNull(selectedPlayer)?.isEliminated != true
    fun blockLocalRulesAction(action: String): Boolean {
        if (!argentumEnabled) return false
        message = "$action is controlled by Argentum in this game. Choose an action Argentum offers."
        return true
    }

    LaunchedEffect(roomHost, roomClient, cloudRoomClient, argentumEnabled) {
        if (roomHost != null || roomClient != null || cloudRoomClient != null || argentumEnabled) LanConnectionService.start(context)
        else if (!argentumEnabled) LanConnectionService.stop(context)
    }
    LaunchedEffect(argentumEnabled) {
        if (argentumEnabled && argentumStatus == ArgentumConnectionStatus.DISCONNECTED) argentumSocket.connect(lobbyPlayerName)
        if (!argentumEnabled) argentumSocket.close()
    }
    LaunchedEffect(argentumStatus, argentumEnabled) {
        if (argentumEnabled) recordDiagnostic("Argentum connection status: ${argentumStatus.name.lowercase()}")
    }

    // Every server message is handled once, in order. (A single state variable conflated bursts:
    // e.g. gameStarted + mulliganDecision arriving in one frame lost the first message.)
    fun handleArgentumMessage(incoming: JSONObject) {
        argentumInbound = incoming
        if (argentumEndPreviousRequested && incoming.optString("type") in setOf("connected", "reconnected")) {
            argentumEndPreviousRequested = false
            if (incoming.optString("context") == "game") argentumSocket.concede()
            setArgentumGameActive(false)
            // Outside this effect: a new inbound message would cancel it before the close ran.
            scope.launch { delay(600); argentumSocket.close() }
            message = if (incoming.optString("context") == "game") "The previous Argentum game was conceded." else "The previous Argentum game had already ended."
            return
        }
        // Outside an Argentum session, never let pushed game messages take over the table.
        if (!argentumEnabled && incoming.optString("type") !in setOf("connected", "reconnected", "error", "sessionReplaced")) return
        when (incoming.optString("type")) {
            "connected", "reconnected" -> {
                argentumPlayerId = incoming.optString("playerId").takeUnless { it.isBlank() || it == "null" }
                val inGame = incoming.optString("context") == "game"
                recordDiagnostic("Argentum session ${incoming.optString("type")}${if (inGame) " to an active game" else ""}", DiagnosticCategory.CONNECTION)
                if (argentumResumeRequested) {
                    argentumResumeRequested = false
                    if (!inGame) {
                        // The player asked to resume, but Argentum no longer has that game.
                        setArgentumGameActive(false)
                        argentumSocket.close()
                        argentumEnabled = false
                        homeOpen = true
                        argentumLastError = "That Argentum game has ended or expired, so there is nothing to resume. Start a new game when you're ready."
                    }
                }
                // Argentum pushes state itself after a reconnect; this only covers a missed push.
                if (inGame) argentumSocket.requestResync()
            }
            "quickGameLobbyState" -> {
                val playersInLobby = incoming.optJSONArray("players") ?: JSONArray()
                localSeat = (0 until playersInLobby.length()).firstOrNull {
                    playersInLobby.optJSONObject(it)?.optString("playerId") == incoming.optString("youPlayerId")
                } ?: 0
                val cards = argentumDeckForLobby
                val commander = argentumCommanderForLobby
                if (cards != null && commander != null && !argentumLobbySubmitted && argentumStatus == ArgentumConnectionStatus.CONNECTED) {
                    argentumLobbySubmitted = true
                    if (!argentumSocket.submitCommanderDeck(cards, commander) || !argentumSocket.markReady()) {
                        argentumLobbySubmitted = false
                        argentumLastError = "Could not submit your deck to the Argentum lobby."
                    } else {
                        lobbyNotice = "Deck sent to Argentum. Waiting for the authoritative game to start."
                        recordDiagnostic("Argentum lobby received; compatible deck submitted and marked ready")
                    }
                }
            }
            "gameStarted" -> {
                val roster = incoming.optJSONArray("players") ?: JSONArray()
                localSeat = (0 until roster.length()).firstOrNull { roster.optJSONObject(it)?.optBoolean("isYou") == true } ?: 0
                gameStartedOnline = true
                homeOpen = false
                lobbyOpen = false
                gameTimerRunning = true
                turnTimerRunning = true
                setArgentumGameActive(true)
                argentumGameOverDismissed = false; argentumGameOverText = null; argentumWon = null
                recordDiagnostic("Argentum game started", DiagnosticCategory.ENGINE)
            }
            "stateUpdate" -> {
                val state = incoming.optJSONObject("state")
                if (state != null) {
                    argentumAwaitingUpdate = false
                    if (argentumLocalPlayersBackup == null) argentumLocalPlayersBackup = loadSavedPlayers(context) ?: players.toList()
                    val snapshot = ArgentumTableSnapshot.parse(state, incoming.optJSONArray("legalActions") ?: JSONArray(), incoming.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" })
                    val nextDecision = incoming.optJSONObject("pendingDecision")
                    argentumLastStateJson = state
                    if (nextDecision?.optString("id") != argentumPendingDecision?.optString("id")) {
                        argentumDecisionSelection = emptySet()
                        argentumDecisionColors = emptySet()
                        argentumDecisionTargets = emptyMap()
                        argentumModeSelection = emptySet()
                        argentumOrderSelection = nextDecision?.let(ArgentumDecisions::orderingItems).orEmpty()
                        argentumAmounts = when (nextDecision?.optString("type")) {
                            "AssignDamageDecision" -> ArgentumDecisions.defaultDamageAssignments(nextDecision)
                            "CombatResolutionDecision" -> ArgentumCombatResolution.from(nextDecision)?.defaults(nextDecision.optString("playerId")).orEmpty()
                            "SplitPilesDecision" -> ArgentumPileSplit(nextDecision).initial()
                            "DistributeDecision" -> nextDecision.optJSONArray("targets")?.let { t -> (0 until t.length()).associate { t.optString(it) to nextDecision.optInt("minPerTarget", 0) } }.orEmpty()
                            else -> emptyMap()
                        }
                        argentumDeferredDecisionId = null
                        argentumBudgetCounts = emptyMap()
                        argentumReplacementPick = nextDecision?.takeIf { it.optString("type") == "ChooseReplacementDecision" }
                            ?.let { ArgentumReplacementChoice(it) }?.let { c -> c.defaultFrom() to (c.allowedTo(c.defaultFrom()).firstOrNull() ?: 0) } ?: (0 to 0)
                        argentumAttackPlan = null
                        argentumBlockPlan = null
                        nextDecision?.let { decision ->
                            recordDiagnostic("Argentum decision ${decision.optString("type")}: ${decision.optString("prompt")}", DiagnosticCategory.ENGINE)
                            ArgentumDecisions.needsHandlingReason(decision)?.let { reportArgentumUnsupported("decision:${decision.optString("id")}", "${decision.optString("type")} — $it") }
                        }
                    }
                    argentumPendingDecision = nextDecision
                    argentumNextStop = incoming.optString("nextStopPoint").takeUnless { it.isBlank() || it == "null" }
                    argentumUndoAvailable = incoming.optBoolean("undoAvailable", false)
                    val firstSnapshot = argentumSnapshot == null
                    argentumSnapshot = snapshot
                    // Keep an open action sheet in step with the new state so it never offers stale actions.
                    if (argentumActionChoices.isNotEmpty()) {
                        val sheetCard = argentumActionCard?.let { old -> snapshot.players.flatMap { it.hand + it.battlefield + it.graveyard + it.exile + listOfNotNull(it.commanderCard) }.firstOrNull { it.id == old.id } }
                        if (argentumActionCard != null && sheetCard == null) { argentumActionChoices = emptyList(); argentumActionCard = null }
                        else if (sheetCard != null) openArgentumActions(sheetCard)
                        else argentumActionChoices = snapshot.legalActions.ifEmpty { listOf(JSONObject().put("__none", true)) }
                    }
                    players = snapshot.players
                    localSeat = snapshot.viewingPlayer
                    activePlayer = snapshot.activePlayer
                    if (firstSnapshot) selectedPlayer = snapshot.viewingPlayer
                    turnNumber = snapshot.turnNumber
                    phaseIndex = phaseSteps.indexOfFirst { it.equals(snapshot.stepName, true) }.takeIf { it >= 0 }
                        ?: phaseSteps.indexOfFirst { it.equals(snapshot.phaseName, true) }.takeIf { it >= 0 } ?: 3
                    gameEndedOnline = snapshot.gameOver
                    if (snapshot.gameOver) setArgentumGameActive(false) else if (!argentumResumeAvailable) setArgentumGameActive(true)
                    gameStartedOnline = true
                    homeOpen = false
                    lobbyOpen = false
                    gameTimerRunning = true
                    turnTimerRunning = true
                    val entries = incoming.optJSONArray("events")
                    if (entries != null && entries.length() > 0) {
                        val cardsJson = state.optJSONObject("cards")
                        val played = PlayFeedParser.parse(entries, cardsJson)
                        played.filter { it.kind != PlayedCard.Kind.ABILITY }.takeIf { it.isNotEmpty() }?.let { playFeed = (playFeed + it).takeLast(8) }
                        played.lastOrNull { it.kind != PlayedCard.Kind.ABILITY && !it.byYou }?.let { playPopup = it }
                        played.lastOrNull { it.kind == PlayedCard.Kind.ABILITY }?.let { playBanner = it }
                        if (snapshot.activePlayer != localSeat) {
                            val base = if (aiRecap.first == snapshot.turnNumber) aiRecap.second else TurnRecap()
                            aiRecap = snapshot.turnNumber to PlayFeedParser.recap(entries, cardsJson, argentumPlayerId, base)
                        }
                        val eventLines = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("description")?.takeIf(String::isNotBlank) }
                        if (eventLines.isNotEmpty()) gameLog = (gameLog + eventLines).takeLast(250)
                    }
                    recordDiagnostic("Argentum state v${incoming.optLong("stateVersion")} applied: ${snapshot.players.size} players, turn ${snapshot.turnNumber} ${snapshot.stepName}, ${snapshot.legalActions.size} legal actions", DiagnosticCategory.ENGINE)
                }
            }
            "stateDeltaUpdate" -> {
                // Arcana never guesses at a delta. Ask Argentum for a complete masked snapshot.
                argentumAwaitingUpdate = false
                if (!argentumSocket.requestResync()) argentumLastError = "Waiting to reconnect before syncing the game state."
            }
            "mulliganDecision" -> {
                argentumAwaitingUpdate = false
                argentumMulliganPrompt = true
                argentumMulliganInfo = Triple(incoming.optInt("mulliganCount"), incoming.optBoolean("isOnThePlay"), incoming.optInt("cardsToPutOnBottom"))
                val handIds = incoming.optJSONArray("hand") ?: JSONArray()
                val cardInfo = incoming.optJSONObject("cards") ?: JSONObject()
                val hand = (0 until handIds.length()).mapNotNull { index ->
                    val serverId = handIds.optString(index)
                    val detail = cardInfo.optJSONObject(serverId) ?: return@mapNotNull null
                    GameCard(
                        id = (serverId.hashCode() and 0x7fffffff).coerceAtLeast(1),
                        name = detail.optString("name", "Card"), type = detail.optString("typeLine").takeUnless { it.isBlank() || it == "null" } ?: "Card", color = Color(0xFF3C5A52),
                        imageUrl = detail.optString("imageUri").takeUnless { it.isBlank() || it == "null" },
                        manaCost = detail.optString("manaCost").takeUnless { it.isBlank() || it == "null" },
                        oracleText = detail.optString("oracleText").takeUnless { it.isBlank() || it == "null" },
                        power = detail.opt("power")?.toString()?.takeUnless { it == "null" },
                        toughness = detail.opt("toughness")?.toString()?.takeUnless { it == "null" }
                    )
                }
                if (hand.isNotEmpty()) players = players.mapIndexed { index, player ->
                    if (index == localSeat) player.copy(hand = hand) else player
                }
            }
            "chooseBottomCards" -> {
                argentumAwaitingUpdate = false
                val ids = incoming.optJSONArray("hand") ?: JSONArray()
                argentumBottomCardIds = (0 until ids.length()).map { ids.optString(it) }
                argentumBottomCardCount = incoming.optInt("cardsToPutOnBottom", 0)
                argentumBottomSelection = emptySet()
            }
            "mulliganComplete" -> { argentumMulliganPrompt = false; argentumAwaitingUpdate = false }
            "gameOver" -> {
                setArgentumGameActive(false)
                gameEndedOnline = true
                argentumAwaitingUpdate = false
                val winner = incoming.optString("winnerId").takeIf { it.isNotBlank() && it != "null" }
                val won = winner != null && winner == argentumPlayerId
                argentumWon = if (winner == null) null else won
                val reason = incoming.optString("reason").lowercase().replace('_', ' ')
                argentumGameOverText = incoming.optString("message").takeIf { it.isNotBlank() && it != "null" }
                    ?: listOfNotNull(if (winner == null) "No winner." else if (won) "You won." else "${players.firstOrNull { it.id == winner }?.name ?: "Your opponent"} won.", reason.takeIf { it.isNotBlank() }?.let { "Reason: $it." }).joinToString(" ")
                recordDiagnostic("Argentum game over (${incoming.optString("reason")})", DiagnosticCategory.ENGINE)
            }
            "sessionReplaced" -> recordDiagnostic("Argentum session replaced by another connection", DiagnosticCategory.CONNECTION)
            "error" -> {
                argentumLastError = incoming.optString("message", "Argentum returned an error."); message = argentumLastError; argentumAwaitingUpdate = false
                recordDiagnostic("Argentum error ${incoming.optString("code")}: ${argentumLastError}", DiagnosticCategory.ERROR)
            }
        }
    }
    LaunchedEffect(argentumQueue) {
        for (incoming in argentumQueue) {
            // A late update from a game you already left must not overwrite your table and deck.
            if (!argentumEnabled && incoming.optString("type") in setOf("stateUpdate", "stateDeltaUpdate", "gameOver", "mulliganDecision", "chooseBottomCards")) continue
            runCatching { handleArgentumMessage(incoming) }.onFailure { error ->
                recordDiagnostic("Could not apply Argentum ${incoming.optString("type")} message: ${error.javaClass.simpleName}", DiagnosticCategory.ERROR)
                argentumAwaitingUpdate = false
                argentumSocket.requestResync()
            }
        }
    }
    // Tell the player when Argentum is waiting on them: a light buzz in the app, an alert in the background.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val argentumYourMove = argentumEnabled && !gameEndedOnline && !argentumAwaitingUpdate &&
        (argentumPrimaryLabel != null || argentumPendingDecision?.optString("type")?.let { it != "PlayCardDecision" } == true)
    LaunchedEffect(argentumYourMove) {
        if (!argentumYourMove) return@LaunchedEffect
        delay(350) // ignore momentary priority during Argentum's own auto-passing
        if (appInForeground) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        else if (backgroundAlertsEnabled) postGameAlert(context, "Your move", argentumPendingDecision?.optString("prompt")?.takeIf { it.isNotBlank() } ?: "Argentum is waiting for you.")
    }
    // A banner (and a buzz on yours) when each turn starts.
    var turnBanner by remember { mutableStateOf<TurnBannerState?>(null) }
    LaunchedEffect(argentumTurnKey) {
        val (turn, active) = argentumTurnKey ?: return@LaunchedEffect
        val seat = players.getOrNull(active) ?: return@LaunchedEffect
        val isYou = active == localSeat
        turnBanner = TurnBannerState(TurnColors.label(isYou, seat.name, seat.isComputer), isYou, turn)
        if (isYou && aiRecap.first in 0 until turn && !aiRecap.second.isEmpty) {
            recapText = aiRecap.second.line(players.firstOrNull { it.isComputer }?.name ?: "Opponent")
            recordDiagnostic("Recap: ${recapText}", DiagnosticCategory.ENGINE)
        }
        if (isYou && appInForeground) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        delay(if (isYou) 1400 else 900)
        turnBanner = null
    }
    LaunchedEffect(playBanner?.key) { if (playBanner != null) { delay(2200); playBanner = null } }
    LaunchedEffect(recapText) { if (recapText != null) { delay(6500); recapText = null } }
    LaunchedEffect(argentumLastError) {
        // Socket-level problems (unreadable messages, failed connects) reach the searchable log too.
        argentumLastError?.let { recordDiagnostic("Argentum problem: $it", DiagnosticCategory.ERROR) }
    }
    DisposableEffect(argentumSocket) {
        onDispose { argentumSocket.close() }
    }
    LaunchedEffect(Unit) {
        // After a relaunch or recreation, surface the Resume / End choice instead of a stale table.
        if (argentumResumeAvailable && !argentumEnabled) {
            homeOpen = true
            recordDiagnostic("Previous Argentum game may still be active; offering Resume (no automatic reconnect)", DiagnosticCategory.CONNECTION)
        }
    }
    LaunchedEffect(argentumAwaitingUpdate) {
        if (argentumAwaitingUpdate) {
            delay(8_000)
            if (argentumAwaitingUpdate) {
                recordDiagnostic("Argentum did not confirm an action within 8 s; requested a full state refresh", DiagnosticCategory.CONNECTION)
                argentumAwaitingUpdate = false
                argentumSocket.requestResync()
                message = "Argentum has not confirmed that action yet. Arcana refreshed the table state."
            }
        }
    }

    LaunchedEffect(Unit) {
        recordDiagnostic("Application opened")
        while (true) {
            delay(1_000)
            if (roomClient == null && cloudRoomClient == null) {
                if (gameTimerRunning) elapsedGameSeconds++
                if (turnTimerRunning) elapsedTurnSeconds++
            }
        }
    }

    LaunchedEffect(players, onlineMode) { if (!onlineMode) savePlayers(context, players) }
    fun buildOnlineSnapshot(): String {
        val sharedOrder = if (gameStartedOnline) gameTurnOrder else lobbyTurnOrder.orEmpty()
        return OnlineGameState(players, activePlayer, turnNumber, phaseIndex, elapsedGameSeconds, elapsedTurnSeconds, sharedOrder, lobbyReadyPlayers, gameLog.takeLast(250), chatMessages.takeLast(200).map { OnlineChatMessage(it.author, it.text, it.timestamp) }, gameStartedOnline, endProposalBy, endVotes, gameEndedOnline, stackActions, priorityPlayer, priorityPassed, triggerPrompts).toJson()
    }
    fun publishOnlineSnapshot() {
        if ((roomHost != null || roomClient != null || cloudRoomClient != null) && roomConnected && onlineStateReady) {
            val snapshot = buildOnlineSnapshot()
            if (snapshot != lastRemoteSnapshot) {
                roomHost?.publish(snapshot)
                roomClient?.publish(snapshot)
                cloudRoomClient?.publish(snapshot)
            }
        }
    }
    LaunchedEffect(players, activePlayer, turnNumber, phaseIndex, gameTurnOrder, lobbyReadyPlayers, lobbyTurnOrder, gameLog, chatMessages, gameStartedOnline, endProposalBy, endVotes, gameEndedOnline, stackActions, priorityPlayer, priorityPassed, triggerPrompts, roomHost, roomClient, cloudRoomClient, roomConnected, onlineStateReady) {
        delay(180)
        publishOnlineSnapshot()
    }
    LaunchedEffect(elapsedGameSeconds, elapsedTurnSeconds, roomHost, roomClient, cloudRoomClient, roomConnected, onlineStateReady) {
        if (elapsedGameSeconds % 5L == 0L || elapsedTurnSeconds % 5L == 0L) publishOnlineSnapshot()
    }
    // Capture the instances that this effect owns. Reading the delegated Compose
    // state values inside onDispose would see the newly assigned room/client and
    // could close it immediately when the keys change during recomposition.
    val hostOwnedByEffect = roomHost
    val clientOwnedByEffect = roomClient
    val cloudClientOwnedByEffect = cloudRoomClient
    DisposableEffect(hostOwnedByEffect, clientOwnedByEffect, cloudClientOwnedByEffect) {
        onDispose {
            hostOwnedByEffect?.close()
            clientOwnedByEffect?.close()
            cloudClientOwnedByEffect?.close()
            LanConnectionService.stop(context)
        }
    }
    LaunchedEffect(players) { nextId = maxOf(nextId, (players.flatMap { it.library + it.hand + it.battlefield + it.graveyard + it.exile + it.revealed }.maxOfOrNull { it.id } ?: 0) + 1) }
    LaunchedEffect(stackActions, localSeat, onlineMode) {
        targetSelectionStackItemId = stackActions.firstOrNull {
            !it.targetsConfirmed && it.targetCount != null &&
                (!onlineMode || it.controllerId == players.getOrNull(localSeat)?.id)
        }?.id
    }
    var observedChatCount by remember { mutableIntStateOf(chatMessages.size) }
    LaunchedEffect(chatMessages.size, chatOpen) {
        if (chatMessages.size > observedChatCount && !chatOpen) unreadChatCount += chatMessages.size - observedChatCount
        observedChatCount = chatMessages.size
        if (chatOpen) unreadChatCount = 0
    }
    LaunchedEffect(gameStartedOnline) {
        if (gameStartedOnline) { homeOpen = false; showOverview = true; showHand = false; lobbyOpen = false }
    }
    LaunchedEffect(gameEndedOnline) {
        if (gameEndedOnline) { gameTimerRunning = false; turnTimerRunning = false; showOverview = true; showHand = false }
    }
    LaunchedEffect(pendingStackOpen, triggerPrompts.size, paymentCard, manaPoolOpen, targetSelectionStackItemId, creatureTypeChoicePrompt) {
        if (pendingStackOpen && triggerPrompts.isEmpty() && paymentCard == null && !manaPoolOpen && targetSelectionStackItemId == null && creatureTypeChoicePrompt == null) {
            delay(180)
            stackOpen = true
            pendingStackOpen = false
        }
    }
    LaunchedEffect(stackActions.isNotEmpty(), triggerPrompts.size, paymentCard, manaPoolOpen, manaChoicePrompt, creatureTypeChoicePrompt, pendingStackOpen) {
        if (stackActions.isNotEmpty()) {
            stackCycleActive = true
            return@LaunchedEffect
        }
        if (!stackCycleActive || triggerPrompts.isNotEmpty() || paymentCard != null || manaPoolOpen || manaChoicePrompt != null || creatureTypeChoicePrompt != null || pendingStackOpen) return@LaunchedEffect
        delay(250)
        if (stackActions.isEmpty() && triggerPrompts.isEmpty() && paymentCard == null && !manaPoolOpen && manaChoicePrompt == null && creatureTypeChoicePrompt == null && !pendingStackOpen) {
            stackOpen = false
            stackCycleActive = false
        }
    }

    val activity = context as? Activity
    SideEffect {
        val systemBar = if (darkMode) android.graphics.Color.rgb(16, 22, 21) else android.graphics.Color.rgb(242, 245, 241)
        activity?.window?.statusBarColor = systemBar
        activity?.window?.navigationBarColor = systemBar
    }

    fun updatePlayer(index: Int, transform: (TablePlayer) -> TablePlayer) {
        players = players.mapIndexed { i, p -> if (i == index) transform(p) else p }
        logZoneSnapshotChange(players, "local player update")
    }
    fun takeOpeningMulligan(index: Int) {
        val player = players.getOrNull(index) ?: return
        if (index != localSeat || !onlineMode || gameStartedOnline || !player.deckLoaded || player.openingHandKept || index in lobbyReadyPlayers || player.mulligansTaken >= 8) return
        val shuffled = (player.hand + player.library).shuffled()
        updatePlayer(index) { it.copy(
            hand = shuffled.take(7),
            library = shuffled.drop(7),
            mulligansTaken = player.mulligansTaken + 1,
            openingHandKept = false
        ) }
        publishOnlineSnapshot()
        lobbyNotice = if (player.mulligansTaken == 0) "Shuffled your hand into your library and dealt a new one. This multiplayer mulligan is free." else "Shuffled your hand into your library and dealt a new one."
        recordDiagnostic("Opening hand mulligan: seat ${index + 1}, total=${player.mulligansTaken + 1}")
    }
    fun keepOpeningHand(index: Int, bottomIds: List<Int>) {
        val player = players.getOrNull(index) ?: return
        if (index != localSeat || !onlineMode || gameStartedOnline || !player.deckLoaded || player.openingHandKept || index in lobbyReadyPlayers) return
        val requiredBottomCount = (player.mulligansTaken - 1).coerceAtLeast(0)
        val selectedIds = bottomIds.distinct()
        if (selectedIds.size != requiredBottomCount || selectedIds.any { id -> player.hand.none { it.id == id } }) return
        val bottomCards = player.hand.filter { it.id in selectedIds }
        updatePlayer(index) { current -> current.copy(
            hand = current.hand.filterNot { it.id in selectedIds },
            library = current.library + bottomCards,
            openingHandKept = true
        ) }
        publishOnlineSnapshot()
        lobbyNotice = if (requiredBottomCount == 0) "Opening hand kept. You can now ready up." else "Opening hand kept; $requiredBottomCount card(s) put on the bottom of your library."
        recordDiagnostic("Opening hand kept: seat ${index + 1}, mulligans=${player.mulligansTaken}, cards bottomed=$requiredBottomCount")
    }
    fun trackedMana(index: Int): Int = players.getOrNull(index)?.manaPool?.values?.sum() ?: 0
    fun changeMana(index: Int, color: String, delta: Int) {
        if (index !in players.indices || color !in listOf("W", "U", "B", "R", "G", "C")) return
        updatePlayer(index) { player ->
            val amount = ((player.manaPool[color] ?: 0) + delta).coerceIn(0, 99)
            player.copy(manaPool = player.manaPool.toMutableMap().apply {
                if (amount == 0) remove(color) else put(color, amount)
            })
        }
    }
    fun clearMana(index: Int) {
        if (players.getOrNull(index)?.manaPool?.isNotEmpty() == true) updatePlayer(index) { it.copy(manaPool = emptyMap()) }
    }
    fun spendGenericMana(index: Int, amount: Int): Boolean {
        if (index !in players.indices) return false
        val next = spendGenericMana(players[index].manaPool, amount) ?: return false
        updatePlayer(index) { it.copy(manaPool = next) }
        return true
    }
    fun updateCard(playerIndex: Int, cardId: Int, transform: (GameCard) -> GameCard) {
        updatePlayer(playerIndex) { p -> p.copy(battlefield = p.battlefield.map { if (it.id == cardId) transform(it) else it }) }
    }
    fun editDeckCard(playerIndex: Int, cardId: Int, newName: String?, remove: Boolean = false) {
        updatePlayer(playerIndex) { p ->
            fun change(cards: List<GameCard>) = cards.mapNotNull { card ->
                if (card.id != cardId) card else if (remove) null else card.copy(name = newName?.trim()?.takeIf { it.isNotEmpty() } ?: card.name)
            }
            val commander = p.commanderCard?.let { card ->
                if (card.id != cardId) card else if (remove) null else card.copy(name = newName?.trim()?.takeIf { it.isNotEmpty() } ?: card.name)
            }
            p.copy(
                library = change(p.library),
                hand = change(p.hand),
                battlefield = change(p.battlefield),
                graveyard = change(p.graveyard),
                exile = change(p.exile),
                revealed = change(p.revealed),
                commanderCard = commander,
                commanderName = commander?.name
            )
        }
    }
    fun nextTurnPlayer(currentIndex: Int): Int {
        return nextTurnIndex(currentIndex, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
    }
    fun completeCurrentPlayerTurn(currentIndex: Int) {
        val eligible = players.indices.filterNot { players[it].isEliminated }.toSet()
        if (isLastEligibleTurnInRound(currentIndex, gameTurnOrder, players.size, eligible)) turnNumber++
    }
    fun movePlayerCard(index: Int, direction: Int) {
        val target = (index + direction).coerceIn(0, players.lastIndex)
        if (target == index) return
        val reordered = players.toMutableList().also { list ->
            val item = list[index]; list[index] = list[target]; list[target] = item
        }
        players = reordered
        fun remap(value: Int) = when (value) { index -> target; target -> index; else -> value }
        selectedPlayer = remap(selectedPlayer)
        activePlayer = remap(activePlayer)
        priorityPlayer = remap(priorityPlayer)
        gameTurnOrder = gameTurnOrder.map(::remap)
        lobbyTurnOrder = lobbyTurnOrder?.map(::remap)
        lobbyReadyPlayers = lobbyReadyPlayers.map(::remap).toSet()
    }
    fun removeLobbyPlayer(index: Int) {
        if (!lobbyCreated || lobbyJoinMode || index <= 0 || index !in players.indices) return
        roomHost?.removeSeat(index)
        val removedName = players[index].name
        players = players.filterIndexed { playerIndex, _ -> playerIndex != index }
        fun remap(playerIndex: Int): Int = when {
            playerIndex == index -> 0
            playerIndex > index -> playerIndex - 1
            else -> playerIndex
        }
        selectedPlayer = remap(selectedPlayer).coerceIn(players.indices)
        activePlayer = remap(activePlayer).coerceIn(players.indices)
        priorityPlayer = remap(priorityPlayer).coerceIn(players.indices)
        gameTurnOrder = gameTurnOrder.filter { it != index }.map { if (it > index) it - 1 else it }
        lobbyReadyPlayers = lobbyReadyPlayers.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
        lobbyTurnOrder = lobbyTurnOrder?.filter { it != index }?.map { if (it > index) it - 1 else it }
        if (pendingAiPlayerIndex == index) pendingAiPlayerIndex = -1 else if (pendingAiPlayerIndex > index) pendingAiPlayerIndex--
        if (endProposalBy == index) { endProposalBy = -1; endVotes = emptyMap() }
        else if (endProposalBy > index) endProposalBy--
        endVotes = endVotes.filterKeys { it != index }.mapKeys { (seat, _) -> if (seat > index) seat - 1 else seat }
        triggerPrompts = triggerPrompts.filter { it.controllerIndex != index && it.eventPlayerIndex != index }.map {
            it.copy(controllerIndex = remap(it.controllerIndex), eventPlayerIndex = remap(it.eventPlayerIndex))
        }
        gameGeneration++
        lobbyNotice = "$removedName was removed from the lobby."
    }
    fun logAction(playerIndex: Int, action: String) {
        val playerName = players.getOrNull(playerIndex)?.name ?: "TABLE"
        val phase = phaseSteps.getOrNull(phaseIndex)?.let { " · $it" }.orEmpty()
        gameLog = (gameLog + "Turn $turnNumber$phase · $playerName $action").takeLast(250)
        if (backgroundAlertsEnabled && !appInForeground && onlineMode && playerIndex != localSeat && !action.startsWith("sent a chat")) {
            postGameAlert(context, "Game activity", "$playerName $action")
        }
    }
    fun announceAutomaticAction(playerIndex: Int, action: String, alreadyLogged: Boolean = false) {
        val playerName = players.getOrNull(playerIndex)?.name ?: "Player"
        if (!alreadyLogged) logAction(playerIndex, "$action automatically")
        scope.launch { automaticActionSnackbar.showSnackbar("$playerName · $action") }
    }
    fun tapPermanent(playerIndex: Int, card: GameCard) {
        if (blockLocalRulesAction("Tapping or untapping permanents")) return
        if (card.tapped) {
            updateCard(playerIndex, card.id) { it.copy(tapped = false) }
            logAction(playerIndex, "untapped ${card.name}")
            return
        }
        updateCard(playerIndex, card.id) { it.copy(tapped = true) }
        logAction(playerIndex, "tapped ${card.name}")
        val options = manaProductionOptions(card)
        if (options.size == 1) {
            val onlyOption = options.single()
            updatePlayer(playerIndex) { player ->
                player.copy(manaPool = player.manaPool.toMutableMap().apply {
                    onlyOption.pool.forEach { (color, count) -> put(color, ((get(color) ?: 0) + count).coerceAtMost(99)) }
                })
            }
            logAction(playerIndex, "added ${onlyOption.label} mana")
        } else if (options.size > 1) {
            manaChoicePrompt = ManaChoicePrompt(playerIndex, card.name, options)
        }
    }
    fun applyOnlineSnapshot(source: String) {
        val state = parseOnlineGameState(source) ?: return
        inboundSnapshotSequence += 1
        val firstSnapshotForConnection = !onlineStateReady
        val wasGameStarted = gameStartedOnline
        val oldActivePlayer = activePlayer
        val oldPriorityPlayer = priorityPlayer
        val oldStackIds = stackActions.map { it.id }.toSet()
        val oldGameLog = gameLog
        val previousReady = lobbyReadyPlayers
        val previousPlayers = players
        lastRemoteSnapshot = state.toJson()
        val receivedPlayers = state.players.mapIndexed { index, player ->
            if (roomConnected && index == localSeat) player.copy(isOnline = true) else player
        }.toMutableList()
        while (receivedPlayers.size <= localSeat && receivedPlayers.size < 4) receivedPlayers += TablePlayer(name = if (receivedPlayers.size == localSeat) lobbyPlayerName else "PLAYER ${receivedPlayers.size + 1}")
        // This device owns its assigned seat. Keep its current player data while
        // connected so an older server snapshot cannot undo a local card move
        // that is still waiting for its outbound snapshot to be published.
        // The first snapshot after connecting remains authoritative so a
        // reconnect can restore the room's saved state.
        if (!firstSnapshotForConnection) {
            previousPlayers.getOrNull(localSeat)?.let { localPlayer ->
                if (localSeat in receivedPlayers.indices) {
                    receivedPlayers[localSeat] = localPlayer.copy(isOnline = true)
                }
            }
        }
        players = receivedPlayers
        logZoneSnapshotChange(players, "online snapshot #$inboundSnapshotSequence")
        receivedPlayers.forEachIndexed { index, player ->
            val oldLoaded = previousPlayers.getOrNull(index)?.deckLoaded
            if (oldLoaded != null && oldLoaded != player.deckLoaded) recordDiagnostic("Lobby deck status updated: seat ${index + 1}, loaded=${player.deckLoaded}")
            val oldOnline = previousPlayers.getOrNull(index)?.isOnline
            if (!firstSnapshotForConnection && index != localSeat && oldOnline != null && oldOnline != player.isOnline) {
                val event = if (player.isOnline) "${player.name} reconnected" else "${player.name} lost connection"
                recordDiagnostic("Player connection changed: seat ${index + 1} ${if (player.isOnline) "online" else "offline"}")
                if (lobbyOpen) lobbyNotice = "$event."
                if (message == null) message = "$event."
                if (backgroundAlertsEnabled && !appInForeground) {
                    postGameAlert(context, if (player.isOnline) "Player reconnected" else "Player disconnected", event)
                }
            }
        }
        if (localSeat in players.indices) players = players.mapIndexed { index, player -> if (index == localSeat) player.copy(isOnline = true) else player }
        activePlayer = state.activePlayer.coerceIn(players.indices)
        turnNumber = state.turnNumber
        phaseIndex = state.phaseIndex.coerceAtMost(phaseSteps.lastIndex)
        elapsedGameSeconds = state.elapsedGameSeconds
        elapsedTurnSeconds = state.elapsedTurnSeconds
        gameTurnOrder = state.turnOrder
        lobbyTurnOrder = state.turnOrder.takeIf { it.isNotEmpty() && !state.gameStarted }
        lobbyReadyPlayers = state.readyPlayers
        ((previousReady union state.readyPlayers) - (previousReady intersect state.readyPlayers)).sorted().forEach { seat ->
            recordDiagnostic("Lobby readiness updated: seat ${seat + 1}, ready=${seat in state.readyPlayers}")
        }
        if (lobbyOpen && previousReady != state.readyPlayers) {
            val becameReady = (state.readyPlayers - previousReady).firstOrNull()
            val becameNotReady = (previousReady - state.readyPlayers).firstOrNull()
            val changedSeat = becameReady ?: becameNotReady
            if (changedSeat != null) {
                val name = state.players.getOrNull(changedSeat)?.name ?: "A player"
                lobbyNotice = if (becameReady != null) "$name is ready to start." else "$name is no longer ready."
            }
        }
        gameLog = state.gameLog
        if (backgroundAlertsEnabled && !appInForeground && wasGameStarted && state.gameStarted) {
            if (state.activePlayer == localSeat && oldActivePlayer != localSeat) postGameAlert(context, "Your turn", "It is your turn in Arcana Table.")
            val oldTail = oldGameLog.lastOrNull()
            val additions = if (oldTail == null) emptyList() else state.gameLog.dropWhile { it != oldTail }.drop(1)
            additions.takeLast(4).filterNot { it.contains("${players.getOrNull(localSeat)?.name.orEmpty()} ") }.forEach { entry ->
                postGameAlert(context, "Game play", entry.substringAfter("· ", entry))
            }
        }
        chatMessages = state.chat.map { ChatMessage(it.author, it.text, it.timestamp) }
        gameStartedOnline = state.gameStarted
        endProposalBy = state.endProposalBy
        endVotes = state.endVotes
        gameEndedOnline = state.gameEnded
        stackActions = state.stackActions
        targetSelectionStackItemId = state.stackActions.firstOrNull { !it.targetsConfirmed && it.targetCount != null && it.controllerId == players.getOrNull(localSeat)?.id }?.id
        triggerPrompts = state.triggerPrompts
        priorityPlayer = state.priorityPlayer.coerceIn(players.indices)
        priorityPassed = state.priorityPassed
        val stackItemAdded = state.stackActions.any { it.id !in oldStackIds }
        val priorityArrivedForLocalPlayer = wasGameStarted && oldPriorityPlayer != priorityPlayer && priorityPlayer == localSeat
        val joiningWithOpenStack = firstSnapshotForConnection && state.gameStarted && state.stackActions.isNotEmpty()
        if (state.gameStarted && state.stackActions.isNotEmpty() &&
            (stackItemAdded || priorityArrivedForLocalPlayer || joiningWithOpenStack)
        ) {
            pendingStackOpen = true
            if (backgroundAlertsEnabled && !appInForeground) {
                val title = if (priorityArrivedForLocalPlayer) "Your priority" else "Stack update"
                val detail = if (priorityArrivedForLocalPlayer) "It is your turn to respond on the stack." else "A spell or ability was added to the stack."
                postGameAlert(context, title, detail)
            }
            recordDiagnostic(if (priorityArrivedForLocalPlayer) "Stack priority arrived at local seat ${localSeat + 1}" else "Shared stack update received")
        }
        onlineStateReady = true
        // Server snapshots arrive for every remote action (and periodically for
        // timers). Preserve the local player's current view and open hand; only
        // select the local seat when first syncing or when the game starts.
        if (firstSnapshotForConnection || (!wasGameStarted && state.gameStarted)) {
            selectedPlayer = localSeat.coerceIn(players.indices)
            showOverview = true
            showHand = false
        } else {
            selectedPlayer = selectedPlayer.coerceIn(players.indices)
        }
    }
    fun makeHostTransferState(source: String, targetSeat: Int): String? {
        val state = parseOnlineGameState(source) ?: return null
        if (state.players.size < 2 || targetSeat !in 1 until state.players.size) return null
        fun shift(seat: Int): Int = when {
            seat == 0 || seat < 0 -> -1
            seat == targetSeat -> 0
            seat < targetSeat -> seat
            else -> seat - 1
        }
        val shiftedPlayers = listOf(state.players[targetSeat]) + state.players.indices
            .filter { it != 0 && it != targetSeat }.map { state.players[it] }
        val shiftedProposal = shift(state.endProposalBy)
        val shifted = state.copy(
            players = shiftedPlayers,
            activePlayer = shift(state.activePlayer).coerceAtLeast(0).coerceIn(shiftedPlayers.indices),
            priorityPlayer = shift(state.priorityPlayer).coerceAtLeast(0).coerceIn(shiftedPlayers.indices),
            turnOrder = state.turnOrder.map(::shift).filter { it >= 0 },
            readyPlayers = state.readyPlayers.map(::shift).filter { it >= 0 }.toSet(),
            endProposalBy = shiftedProposal,
            endVotes = state.endVotes.mapNotNull { (seat, vote) -> shift(seat).takeIf { it >= 0 }?.let { it to vote } }.toMap(),
            priorityPassed = state.priorityPassed.intersect(shiftedPlayers.map { it.id }.toSet()),
            triggerPrompts = state.triggerPrompts.mapNotNull { prompt ->
                val controller = shift(prompt.controllerIndex)
                val eventPlayer = shift(prompt.eventPlayerIndex)
                if (controller < 0 || eventPlayer < 0) null else prompt.copy(controllerIndex = controller, eventPlayerIndex = eventPlayer)
            }
        )
        return shifted.toJson()
    }
    fun startTransferredHost(source: String, targetSeat: Int, predecessor: LanRoomClient) {
        val shiftedSource = makeHostTransferState(source, targetSeat)
        if (shiftedSource == null) {
            lobbyNotice = "Host transfer needs another connected player. The room is still hosted here."
            return
        }
        val shifted = parseOnlineGameState(shiftedSource) ?: return
        roomHost?.close()
        lobbyCreated = false
        localSeat = 0
        roomConnected = true
        onlineStateReady = false
        applyOnlineSnapshot(shiftedSource)
        lobbyNotice = "Taking over the room…"
        onlineStatus = "Starting new room host"
        fun revertFailedTransfer(message: String) {
            roomHost?.close()
            roomHost = null
            lobbyCreated = false
            localSeat = targetSeat
            roomConnected = true
            onlineStateReady = true
            applyOnlineSnapshot(source)
            onlineStatus = "Connected to host"
            lobbyNotice = message
            recordDiagnostic("Host transfer could not start; returned to the original room")
        }
        val host = LanRoomHost(
            roomCode = lobbyRoomCode,
            password = lobbyPassword,
            onStarted = { address -> scope.launch(Dispatchers.Main) {
                lobbyHostAddress = address
                onlineStatus = if (address.isBlank()) "New host address unavailable" else "Room host transferred"
                onlineStateReady = true
                if (address.isBlank() || !predecessor.requestHostTransfer(address)) {
                    revertFailedTransfer("Could not start the new room host. You are still connected to the original host.")
                } else {
                    lobbyNotice = "Room transferred. Waiting for players to reconnect…"
                    recordDiagnostic("Successor started the new room host")
                }
            } },
            onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
            onPeerJoined = { seat, name -> scope.launch(Dispatchers.Main) {
                if (players.size <= seat) players = players + TablePlayer(name = name, isOnline = true)
                else players = players.mapIndexed { index, player -> if (index == seat) player.copy(name = name, isOnline = true) else player }
                recordDiagnostic("Player reconnected after host transfer: seat ${seat + 1}")
            } },
            onPeerLeft = { seat -> scope.launch(Dispatchers.Main) {
                if (seat in players.indices && seat > 0) removeLobbyPlayer(seat)
            } },
            onEvent = { event -> scope.launch(Dispatchers.Main) { recordDiagnostic(event) } },
            onError = { error -> scope.launch(Dispatchers.Main) {
                revertFailedTransfer("New host could not start the room: $error. You are still connected to the original host.")
                recordDiagnostic("Transferred room host error: $error")
            } },
            initialSnapshot = shiftedSource,
            onHostMigrationFinished = { scope.launch(Dispatchers.Main) { leaveOnlineRoom() } }
        )
        shifted.players.forEachIndexed { seat, player -> if (seat > 0 && player.isOnline) host.reserveSeat(seat) }
        roomHost = host
        host.start()
    }
    fun submitEndVote(approve: Boolean) {
        if (endProposalBy < 0 || localSeat !in players.indices) return
        val nextVotes = endVotes + (localSeat to approve)
        if (!approve) {
            endProposalBy = -1
            endVotes = emptyMap()
            logAction(localSeat, "voted to continue playing; end proposal declined")
            return
        }
        endVotes = nextVotes
        logAction(localSeat, "approved ending the game")
        if (players.indices.all { nextVotes[it] == true }) {
            gameEndedOnline = true
            logAction(localSeat, "all players agreed to end the game")
        }
    }
    fun untapPermanents(playerIndex: Int) {
        val battlefield = players.getOrNull(playerIndex)?.battlefield ?: return
        val tappedCards = battlefield.filter { it.tapped }
        if (tappedCards.isEmpty()) return
        val results = tappedCards.associate { it.id to resolveUntapStep(it.tapped, it.counters) }
        val untappedCount = results.values.count { !it.remainsTapped }
        val stunCount = results.values.count { it.removedStunCounter }
        updatePlayer(playerIndex) { player -> player.copy(battlefield = player.battlefield.map { card ->
            val result = results[card.id] ?: return@map card
            card.copy(tapped = result.remainsTapped, counters = result.counters)
        }) }
        if (untappedCount > 0) logAction(playerIndex, "untapped $untappedCount permanents")
        if (stunCount > 0) logAction(playerIndex, "removed a stun counter from $stunCount tapped permanent(s); they stayed tapped")
    }
    fun makeCard(name: String, type: String, imageUrl: String? = null, token: Boolean = false, oracleText: String? = null, manaValue: Double? = null, artCropUrl: String? = null, manaCost: String? = null, power: String? = null, toughness: String? = null, keywords: List<String> = emptyList()): GameCard {
        val palette = listOf(Color(0xFF547D9A), Color(0xFF9F8C63), Color(0xFF6C9A75), Color(0xFF756348), Color(0xFF8D8C9E))
        val id = nextId++
        return GameCard(id, name, type, palette[id % palette.size], imageUrl = imageUrl, isToken = token, oracleText = oracleText, manaValue = manaValue, artCropUrl = artCropUrl, manaCost = manaCost, power = power, toughness = toughness, keywords = keywords)
    }
    fun loadTestCardImages() {
        val names = players.flatMap { it.library + it.hand + it.battlefield + it.graveyard + it.exile + it.revealed }
            .filterNot { it.isToken }.map { it.name }.distinct()
        scope.launch {
            runCatching { Scryfall.lookup(names.map { DeckEntry(it, 1) }) }.onSuccess { found ->
                fun enrich(cards: List<GameCard>) = cards.map { card ->
                    val art = found.entries.firstOrNull { it.key.name.equals(card.name, true) }?.value
                    if (art == null) card else card.copy(imageUrl = art.imageUrl, artCropUrl = art.artCropUrl, oracleText = art.oracleText, manaValue = art.manaValue, manaCost = art.manaCost, power = art.power, toughness = art.toughness, keywords = art.keywords)
                }
                players = players.map { player -> player.copy(
                    library = enrich(player.library), hand = enrich(player.hand), battlefield = enrich(player.battlefield),
                    graveyard = enrich(player.graveyard), exile = enrich(player.exile), revealed = enrich(player.revealed)
                ) }
            }
        }
    }
    fun makeRandomDecks() {
        players = players.map { player ->
            val cards = List(100) { index ->
                val entry = debugCardPool.random()
                makeCard(entry.first, entry.second)
            }.shuffled()
            player.copy(deckLoaded = true, commanderName = null, commanderCard = null, hand = cards.take(7), library = cards.drop(7), battlefield = emptyList(), graveyard = emptyList(), exile = emptyList(), revealed = emptyList())
        }
        turnNumber = 1; phaseIndex = 0; activePlayer = 0; elapsedTurnSeconds = 0; showOverview = true; showHand = false
        logAction(0, "loaded randomized test decks")
        loadTestCardImages()
    }
    fun randomizeMidgame() {
        players = players.map { player ->
            val deck = List(75) { val entry = debugCardPool.random(); makeCard(entry.first, entry.second) }.shuffled()
            val handSize = (3..7).random(); val boardSize = (3..8).random(); val graveSize = (0..8).random()
            player.copy(deckLoaded = true, life = (12..48).random(), hand = deck.take(handSize), battlefield = deck.drop(handSize).take(boardSize).map { it.copy(tapped = listOf(true, false, false, false).random()) }, graveyard = deck.drop(handSize + boardSize).take(graveSize), library = deck.drop(handSize + boardSize + graveSize), exile = emptyList(), revealed = emptyList())
        }
        activePlayer = players.indices.random(); phaseIndex = (3..10).random(); turnNumber = (4..14).random(); elapsedTurnSeconds = (0..2100).random().toLong(); elapsedGameSeconds = (1200..9000).random().toLong(); showOverview = true; showHand = false
        logAction(activePlayer, "randomized a mid-game test state")
        loadTestCardImages()
    }

    fun queueKnownTriggers(event: String, eventPlayerIndex: Int, enteringCard: GameCard? = null) {
        if (eventPlayerIndex !in players.indices) return
        players.forEachIndexed { controllerIndex, player ->
            if (player.isEliminated) return@forEachIndexed
            player.battlefield.filterNot { it.isFaceDown }.forEach { card ->
                val knownKind = when {
                    event == "CAST" && controllerIndex != eventPlayerIndex && card.name.equals("Rhystic Study", ignoreCase = true) -> "RHYSTIC"
                    event == "DRAW" && controllerIndex != eventPlayerIndex && card.name.equals("Smothering Tithe", ignoreCase = true) -> "TITHE"
                    else -> null
                }
                if (knownKind != null) {
                    val prompt = TriggerPrompt(
                        cardName = card.name,
                        controllerIndex = controllerIndex,
                        eventPlayerIndex = eventPlayerIndex,
                        kind = knownKind,
                        triggerText = if (knownKind == "RHYSTIC") "Whenever an opponent casts a spell" else "Whenever an opponent draws a card",
                        effectText = if (knownKind == "RHYSTIC") "Draw a card unless that player pays {1}." else "Create a Treasure token unless that player pays {2}."
                    )
                    triggerPrompts = triggerPrompts + prompt
                    logAction(controllerIndex, "triggered ${card.name} · ${prompt.triggerText}")
                    return@forEach
                }
                parseRecognizedTriggeredAbilities(card.oracleText).filter { it.event == event }.forEach abilityLoop@{ ability ->
                    val isControllerEvent = controllerIndex == eventPlayerIndex
                    val actorMatches = when (ability.actorFilter) {
                        "YOU" -> isControllerEvent
                        "OPPONENT" -> !isControllerEvent
                        else -> true
                    }
                    if (!actorMatches || (ability.anotherOnly && enteringCard?.id == card.id)) return@abilityLoop
                    if (ability.controllerFilter == "under your control" && !isControllerEvent) return@abilityLoop
                    if (ability.controllerFilter == "under an opponent's control" && isControllerEvent) return@abilityLoop

                    val oldLifeTrigger = event == "CREATURE_ETB" && parseCreatureEntryLifeEffects(card.oracleText).isNotEmpty()
                    val simpleEffects = parseAutomaticResolutionEffects(ability.effectText)
                    if (oldLifeTrigger && !simpleEffects.requiresManualReview && simpleEffects.lifeGain > 0 &&
                        simpleEffects.lifeLoss == 0 && simpleEffects.cardsDrawn == 0 && simpleEffects.tokens.isEmpty()) return@abilityLoop

                    val targetEffect = parseTargetedAutomaticEffect(ability.effectText)
                    val triggerKind = when {
                        targetEffect != null -> "TARGET_EFFECT"
                        simpleEffects.requiresManualReview -> "MANUAL"
                        !simpleEffects.isEmpty -> "AUTO_EFFECT"
                        else -> "MANUAL"
                    }
                    val prompt = TriggerPrompt(
                        cardName = card.name,
                        controllerIndex = controllerIndex,
                        eventPlayerIndex = eventPlayerIndex,
                        kind = triggerKind,
                        triggerText = ability.conditionText,
                        effectText = targetEffect?.effectText ?: ability.effectText,
                        targetRule = targetEffect?.targetRule ?: parseTargetPlayerRule(ability.effectText)
                    )
                    if (parseTargetCountRange(ability.effectText) != null) {
                        val triggerStackItem = StackAction(
                            title = "${card.name}: ${ability.effectText}",
                            controllerId = player.id,
                            kind = "TRIGGER",
                            card = card,
                            targetText = ability.effectText
                        )
                        stackActions = stackActions + triggerStackItem
                        priorityPassed = emptySet()
                        pendingStackOpen = true
                        logAction(controllerIndex, "triggered ${card.name} · ${ability.conditionText}; choose its target")
                    } else {
                        triggerPrompts = triggerPrompts + prompt
                        logAction(controllerIndex, "triggered ${card.name} · ${ability.conditionText}")
                    }
                }
            }
        }
    }

    fun drawCard(playerIndex: Int, reason: String = "drew a card") {
        if (blockLocalRulesAction("Drawing cards")) return
        val player = players[playerIndex]
        if (player.library.isEmpty()) { message = "${player.name}'s library is empty."; return }
        val drawn = player.library.first()
        updatePlayer(playerIndex) { it.copy(library = it.library.drop(1), hand = it.hand + drawn) }
        highlightedHandCardId = drawn.id
        logAction(playerIndex, reason)
        queueKnownTriggers("DRAW", playerIndex)
    }

    fun applyCreatureEntryLifeEffects(eventPlayerIndex: Int, enteringCard: GameCard) {
        if (enteringCard.isFaceDown || !enteringCard.type.contains("creature", ignoreCase = true)) return
        players.forEachIndexed { controllerIndex, player ->
            player.battlefield.filterNot { it.isFaceDown }.forEach { source ->
                parseCreatureEntryLifeEffects(source.oracleText).forEach { effect ->
                    if (!effect.anotherOnly || source.id != enteringCard.id) {
                        val eventMatches = when (effect.controllerFilter) {
                            "under your control" -> controllerIndex == eventPlayerIndex
                            "under an opponent's control" -> controllerIndex != eventPlayerIndex
                            else -> true
                        }
                        if (eventMatches) {
                            updatePlayer(controllerIndex) { it.copy(life = it.life + effect.amount) }
                            announceAutomaticAction(controllerIndex, "gained ${effect.amount} life from ${source.name}")
                        }
                    }
                }
            }
        }
        queueKnownTriggers("CREATURE_ETB", eventPlayerIndex, enteringCard)
    }

    fun addTreasure(playerIndex: Int) {
        val token = makeCard("Treasure", "Artifact Token — Treasure", token = true)
        updatePlayer(playerIndex) { it.copy(battlefield = it.battlefield + token) }
        logAction(playerIndex, "created a Treasure token")
    }

    fun resetGame(startImmediately: Boolean = false, order: List<Int>? = null, preserveOpeningHands: Boolean = false) {
        if (blockLocalRulesAction("Resetting the game")) return
        players = players.map { player ->
            val deck = if (preserveOpeningHands) emptyList() else (player.library + player.hand + player.battlefield + player.graveyard + player.exile + player.revealed)
                .filterNot { it.isToken }
                .map { it.copy(tapped = false, counters = emptyMap(), isFaceDown = false) }
                .shuffled()
            val opening = if (preserveOpeningHands) player.hand else deck.take(7)
            player.copy(
                life = 40,
                playerCounters = emptyMap(),
                commanderDamageReceived = emptyMap(),
                commanderCastCount = 0,
                isEliminated = false,
                eliminationReason = null,
                hand = opening,
                library = if (preserveOpeningHands) player.library else deck.drop(opening.size),
                battlefield = emptyList(),
                graveyard = emptyList(),
                exile = emptyList(),
                revealed = emptyList(),
                commanderCard = player.commanderCard?.copy(tapped = false, counters = emptyMap(), isFaceDown = false)
            )
        }
        val validOrder = order?.takeIf { candidate -> candidate.size == players.size && candidate.toSet() == players.indices.toSet() }
            ?: players.indices.toList()
        players = players.map { it.copy(manaPool = emptyMap()) }
        gameTurnOrder = validOrder
        activePlayer = validOrder.firstOrNull() ?: 0
        selectedPlayer = activePlayer
        turnNumber = 1
        phaseIndex = 0
        elapsedGameSeconds = 0
        elapsedTurnSeconds = 0
        gameLog = listOf(if (startImmediately) "Game started" else "Game reset")
        chatMessages = emptyList()
        unreadChatCount = 0
        triggerPrompts = emptyList()
        stackActions = emptyList()
        creatureTypeChoicePrompt = null
        priorityPlayer = activePlayer
        priorityPassed = emptySet()
        endProposalBy = -1
        endVotes = emptyMap()
        gameEndedOnline = false
        localGameStarted = startImmediately
        gameStartedOnline = onlineMode && startImmediately
        gameTimerRunning = startImmediately
        turnTimerRunning = startImmediately
        showOverview = true
        showHand = false
        combatDialogOpen = false
        gameGeneration++
        resetConfirmOpen = false
        if (startImmediately) {
            homeOpen = false
            lobbyOpen = false
            showOverview = true
            showHand = false
        }
    }

    fun clearAllData() {
        roomClient?.close(); roomClient = null
        roomHost?.close(); roomHost = null
        cloudRoomClient?.leave(); cloudRoomClient = null
        context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        optionPrefs.edit().clear().putString("cloud_room_endpoint", DEFAULT_CLOUD_ROOM_ENDPOINT).apply()
        runCatching { java.io.File(context.filesDir, "magic-comprehensive-rules.txt").delete() }
        backgroundAlertsEnabled = false
        autoReturnTableAfterPass = true
        autoDrawOnDrawStep = true
        autoUntapOnUntapStep = true
        openHandAfterDraw = true
        reducedSwipeMotion = false
        cardSizeScale = 1f
        boardSpacingScale = 1f
        handSheetHeightFraction = 0.52f
        footerButtonScale = 1f
        layoutLabOpen = false
        lobbyInternetMode = false
        lobbyCloudEndpoint = DEFAULT_CLOUD_ROOM_ENDPOINT
        players = listOf(TablePlayer("YOU"))
        savedDecks = loadSavedDecks(context)
        selectedPlayer = 0; activePlayer = 0; gameTurnOrder = listOf(0)
        turnNumber = 1; phaseIndex = 0; elapsedGameSeconds = 0; elapsedTurnSeconds = 0
        gameLog = listOf("New game"); chatMessages = emptyList(); unreadChatCount = 0
        lobbyReadyPlayers = emptySet(); lobbyTurnOrder = null; lobbyRoomCode = ""; lobbyPassword = ""
        localPlayersBackup = null; roomConnected = false; onlineStateReady = false; lobbyCreated = false
        onlineStatus = "Offline"; localSeat = 0; gameStartedOnline = false; gameEndedOnline = false
        gameTimerRunning = false; turnTimerRunning = false; localGameStarted = false
        turnTimerVisible = true
        if (!darkMode) onToggleTheme()
        triggerPrompts = emptyList(); endProposalBy = -1; endVotes = emptyMap()
        stackActions = emptyList(); priorityPlayer = 0; trackerPlayer = -1; concedePlayer = -1; paymentCard = null; creatureTypeChoicePrompt = null
        priorityPassed = emptySet()
        importText = ""; importError = null; importReviewCardIds = emptySet(); importOverflowCardIds = emptySet()
        lobbyNotice = null; chatDraft = ""; preview = null; zoneDialog = null; handActionCard = null; cardActionTarget = null
        showOverview = true; showHand = false; homeOpen = true; clearAllConfirmOpen = false
        gameGeneration++
    }

    fun performHandAction(card: GameCard, action: String) {
        if (blockLocalRulesAction("Playing or moving cards")) { handActionCard = null; return }
        val owner = selectedPlayer
        if (owner !in players.indices || (onlineMode && owner != localSeat) || gameEndedOnline) {
            handActionCard = null
            return
        }
        when (action) {
            "INSPECT" -> { preview = CardPreview(owner, card, fromHand = true); handActionCard = null; return }
            "SET COMMANDER" -> {
                if (players[owner].commanderCard == null && players[owner].commanderName.isNullOrBlank()) {
                    updatePlayer(owner) { it.copy(commanderName = card.name, commanderCard = card, hand = it.hand.filterNot { held -> held.id == card.id }) }
                    logAction(owner, "set ${card.name} as commander")
                }
            }
            "COMMAND ZONE" -> {
                updatePlayer(owner) { it.copy(commanderName = card.name, commanderCard = card, hand = it.hand.filterNot { held -> held.id == card.id }) }
                logAction(owner, "moved ${card.name} to the command zone")
            }
            "PLAY", "PLAY FACE DOWN" -> {
                recordDiagnostic("Hand play selected; starting spell-cast flow")
                if (card.type.contains("Land", ignoreCase = true)) {
                    val condition = landEntryCondition(card)
                    if (shockLandChoiceRequired(card) || condition != null) {
                        handActionCard = null
                        landEntryPrompt = LandEntryPrompt(owner, card, shockLandChoiceRequired(card), condition.orEmpty())
                        return
                    }
                    val tapped = landAlwaysEntersTapped(card)
                    updatePlayer(owner) { it.copy(hand = it.hand.filterNot { held -> held.id == card.id }, battlefield = it.battlefield + card.copy(tapped = tapped)) }
                    logAction(owner, "played ${card.name}${if (tapped) " tapped" else ""}")
                    if (needsCreatureTypeChoice(card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(owner, card.id, card.name)
                } else {
                    val castRequest = owner to card.copy(isFaceDown = action == "PLAY FACE DOWN")
                    val actionDialogIsOpen = handActionCard?.id == card.id
                    handActionCard = null
                    if (actionDialogIsOpen) {
                        // Let Compose fully dismiss the hand-action window before opening
                        // the payment window. Some Android builds crash when two Dialog
                        // windows are replaced in the same frame.
                        scope.launch {
                            delay(120)
                            paymentCard = castRequest
                        }
                    } else {
                        paymentCard = castRequest
                    }
                    return
                }
            }
            "DISCARD" -> {
                updatePlayer(owner) { it.copy(hand = it.hand.filterNot { held -> held.id == card.id }, graveyard = it.graveyard + card) }
                logAction(owner, "discarded ${card.name}")
            }
            "REVEAL" -> {
                updatePlayer(owner) { it.copy(hand = it.hand.filterNot { held -> held.id == card.id }, revealed = it.revealed + card) }
                logAction(owner, "revealed ${card.name}")
            }
            "EXILE" -> {
                updatePlayer(owner) { it.copy(hand = it.hand.filterNot { held -> held.id == card.id }, exile = it.exile + card) }
                logAction(owner, "exiled ${card.name} from hand")
            }
        }
        handActionCard = null
    }

    fun advanceStep() {
        if (blockLocalRulesAction("Advancing the turn")) return
        if (activePlayer !in players.indices || players[activePlayer].isComputer) return
        players.indices.forEach(::clearMana)
        val progression = advanceTurnProgression(phaseIndex, phaseSteps.lastIndex, activePlayer, turnNumber, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
        phaseIndex = progression.phaseIndex
        if (progression.startedNewTurn) {
            activePlayer = progression.activePlayer
            priorityPlayer = progression.activePlayer
            priorityPassed = emptySet()
            turnNumber = progression.turnNumber
            elapsedTurnSeconds = 0
            if (autoUntapOnUntapStep && !players[activePlayer].isComputer) untapPermanents(activePlayer)
            logAction(activePlayer, "started turn $turnNumber")
        } else {
            logAction(activePlayer, "advanced to ${phaseSteps[phaseIndex]}")
            if (phaseSteps[phaseIndex] == "DRAW" && autoDrawOnDrawStep) {
                val nextCard = players[activePlayer].library.firstOrNull()
                if (nextCard != null) {
                    drawCard(activePlayer, "drew for turn")
                    selectedPlayer = activePlayer
                    if (openHandAfterDraw) {
                        showOverview = false
                        showHand = true
                    }
                }
            }
        }
    }

    fun castFromPayment(index: Int, card: GameCard, commander: Boolean = false, manualPayment: Boolean = false) {
        if (blockLocalRulesAction("Casting spells")) { paymentCard = null; handActionCard = null; return }
        if (index !in players.indices || (onlineMode && index != localSeat) || gameEndedOnline) {
            paymentCard = null
            handActionCard = null
            return
        }
        val current = players[index]
        recordDiagnostic("Spell-cast confirmation accepted; processing tracked or manual mana")
        val tax = if (commander) current.commanderCastCount * 2 else 0
        val remainingMana = if (manualPayment) current.manaPool else paySimpleManaCost(current.manaPool, card.manaCost, tax)
        if (remainingMana == null) {
            message = "Not enough tracked mana for ${card.name}${if (tax > 0) " and commander tax" else ""}."
            return
        }
        updatePlayer(index) { p -> p.copy(
            manaPool = if (manualPayment) p.manaPool else remainingMana,
            hand = if (commander) p.hand else p.hand.filterNot { it.id == card.id },
            commanderCastCount = if (commander) p.commanderCastCount + 1 else p.commanderCastCount
        ) }
        val title = if (card.isFaceDown) "Face-down spell" else card.name
        val typeText = card.type.lowercase()
        val isAura = Regex("\\baura\\b").containsMatchIn(typeText)
        val canTargetWhenCast = !card.isFaceDown && ("instant" in typeText || "sorcery" in typeText || isAura)
        val targetText = if (canTargetWhenCast) card.oracleText.orEmpty() else ""
        val targetCount = if (!canTargetWhenCast) null else if (isAura) TargetCountRange(1, 1) else parseTargetCountRange(targetText)
        val stackItem = StackAction(
            title = title,
            controllerId = current.id,
            kind = if (commander) "COMMANDER" else "SPELL",
            card = card,
            targetText = targetText,
            targetCount = targetCount,
            targetsConfirmed = targetCount == null
        )
        stackActions = stackActions + stackItem
        if (stackItem.targetCount != null) targetSelectionStackItemId = stackItem.id
        priorityPlayer = index
        priorityPassed = emptySet()
        val castDescription = if (commander) "cast ${card.name} from the command zone" else if (card.isFaceDown) "cast a face-down spell" else "cast ${card.name}"
        val listedCost = card.manaCost?.takeIf { it.isNotBlank() }
        val paymentDescription = when {
            manualPayment -> "payment handled manually${listedCost?.let { " · listed cost $it" }.orEmpty()}${if (tax > 0) " · commander tax {$tax}" else ""}"
            listedCost != null -> "paid $listedCost${if (tax > 0) " + commander tax {$tax}" else ""}"
            tax > 0 -> "paid commander tax {$tax}; other payment untracked"
            else -> "mana cost not listed"
        }
        logAction(index, "$castDescription · $paymentDescription")
        queueKnownTriggers("CAST", index)
        paymentCard = null
        handActionCard = null
        pendingStackOpen = true
        recordDiagnostic("Spell added to stack; waiting for payment and trigger dialogs to close")
    }

    fun eliminatePlayer(index: Int, reason: String) {
        if (blockLocalRulesAction("Eliminating or conceding a player")) return
        if (index !in players.indices || players[index].isEliminated) return
        val oldActive = activePlayer
        players = players.mapIndexed { i, p -> if (i == index) p.copy(isEliminated = true, eliminationReason = reason) else p }
        logAction(index, if (reason == "Conceded") "conceded and was eliminated" else "was eliminated · $reason")
        val survivors = players.indices.filterNot { players[it].isEliminated }.toSet()
        if (survivors.size <= 1) {
            gameEndedOnline = true
            localGameStarted = false
            gameTimerRunning = false
            turnTimerRunning = false
        } else if (oldActive == index) {
            completeCurrentPlayerTurn(index)
            activePlayer = nextTurnIndex(index, gameTurnOrder, players.size, survivors)
            phaseIndex = 0
            elapsedTurnSeconds = 0
        }
        if (priorityPlayer == index) priorityPlayer = survivors.firstOrNull() ?: 0
    }
    fun concede(index: Int) = eliminatePlayer(index, "Conceded")

    fun applyParsedAutomaticEffects(playerIndex: Int, sourceName: String, effects: AutomaticResolutionEffects) {
        if (blockLocalRulesAction("Resolving card effects")) return
        if (effects.requiresManualReview) {
            val notice = "$sourceName resolved; its effects need manual resolution."
            logAction(playerIndex, notice)
            val playerName = players[playerIndex].name
            scope.launch { automaticActionSnackbar.showSnackbar("$playerName · $notice") }
            return
        }
        if (effects.isEmpty) return
        if (effects.lifeGain > 0) {
            updatePlayer(playerIndex) { it.copy(life = it.life + effects.lifeGain) }
            announceAutomaticAction(playerIndex, "gained ${effects.lifeGain} life from $sourceName")
        }
        if (effects.lifeLoss > 0) {
            val nextLife = (players[playerIndex].life - effects.lifeLoss).coerceAtLeast(0)
            updatePlayer(playerIndex) { it.copy(life = nextLife) }
            announceAutomaticAction(playerIndex, "lost ${effects.lifeLoss} life from $sourceName")
            if (nextLife == 0) eliminatePlayer(playerIndex, "Life total reached 0")
        }
        repeat(effects.cardsDrawn) {
            if (players[playerIndex].library.isNotEmpty()) {
                drawCard(playerIndex, "automatically drew a card from $sourceName")
                announceAutomaticAction(playerIndex, "drew a card from $sourceName", alreadyLogged = true)
            }
        }
        effects.tokens.forEach { spec ->
            val generated = List(spec.count) {
                makeCard(
                    spec.name,
                    spec.type,
                    token = true,
                    power = spec.power,
                    toughness = spec.toughness
                )
            }
            updatePlayer(playerIndex) { it.copy(battlefield = it.battlefield + generated) }
            generated.forEach { applyCreatureEntryLifeEffects(playerIndex, it) }
            announceAutomaticAction(
                playerIndex,
                "created ${generated.size} ${spec.name} ${if (generated.size == 1) "token" else "tokens"} from $sourceName"
            )
        }
    }

    fun applyAutomaticResolutionEffects(playerIndex: Int, card: GameCard) {
        if (playerIndex !in players.indices || card.isFaceDown) return
        if (parseTargetCountRange(card.oracleText) != null) {
            val notice = "${card.name} resolved; resolve its targeted effect manually."
            logAction(playerIndex, notice)
            scope.launch { automaticActionSnackbar.showSnackbar("${players[playerIndex].name} · $notice") }
            return
        }
        applyParsedAutomaticEffects(playerIndex, card.name, parseAutomaticResolutionEffects(card.oracleText))
    }

    fun resolveTopOfStack() {
        if (blockLocalRulesAction("Resolving the stack")) return
        if (stackActions.lastOrNull()?.targetsConfirmed == false) {
            message = "Choose targets for the top stack item first."
            return
        }
        val eligibleIds = players.filterNot { it.isEliminated }.map { it.id }.toSet()
        if (eligibleIds.isNotEmpty() && !eligibleIds.all { it in priorityPassed }) {
            message = "Each remaining player must pass priority before the top item can resolve."
            return
        }
        val item = stackActions.lastOrNull() ?: return
        stackActions = stackActions.dropLast(1)
        val owner = players.indexOfFirst { it.id == item.controllerId }
        val selectedTargets = item.targets.takeIf { it.isNotEmpty() }?.joinToString { it.label }?.let { " · targets: $it" }.orEmpty()
        if (owner >= 0 && item.card != null && stackResolutionMovesCardToZone(item.kind)) {
            val card = item.card
            val isPermanent = listOf("creature", "artifact", "enchantment", "planeswalker", "battle", "land").any { card.type.contains(it, true) }
            updatePlayer(owner) { p ->
                if (isPermanent) p.copy(battlefield = p.battlefield + card)
                else p.copy(graveyard = p.graveyard + card)
            }
            logAction(owner, "resolved ${item.title}$selectedTargets")
            if (isPermanent && needsCreatureTypeChoice(card)) {
                creatureTypeChoicePrompt = CreatureTypeChoicePrompt(owner, card.id, card.name)
            }
            if (isPermanent && card.type.contains("creature", ignoreCase = true)) applyCreatureEntryLifeEffects(owner, card)
            if (!isPermanent && item.kind == "SPELL") applyAutomaticResolutionEffects(owner, card)
        } else if (owner >= 0 && item.card != null) {
            logAction(owner, "resolved ${item.title}$selectedTargets; source permanent stays on the battlefield")
        } else logAction(activePlayer, "resolved ${item.title}$selectedTargets")
        priorityPlayer = activePlayer
        priorityPassed = emptySet()
    }

    fun passTurn() {
        if (blockLocalRulesAction("Passing the turn")) return
        if (activePlayer !in players.indices || players[activePlayer].isComputer) return
        val passingPlayer = activePlayer
        val originalSelection = selectedPlayer
        val originallyOnTable = showOverview
        val startingPlayer = activePlayer
        var advances = 0
        while (activePlayer == startingPlayer && advances < phaseSteps.size) {
            advanceStep()
            advances++
        }
        showHand = false
        showOverview = autoReturnTableAfterPass || originallyOnTable
        selectedPlayer = originalSelection.coerceIn(players.indices)
        if (activePlayer != startingPlayer) {
            logAction(passingPlayer, "passed the turn to ${players[activePlayer].name}")
        }
    }

    fun resolveTrigger(prompt: TriggerPrompt, paid: Boolean, targetIndex: Int? = null, manual: Boolean = false) {
        if (blockLocalRulesAction("Resolving triggers")) return
        val payment = when (prompt.kind) { "RHYSTIC" -> 1; "TITHE" -> 2; else -> 0 }
        if (paid && payment > 0 && !spendGenericMana(prompt.eventPlayerIndex, payment)) return
        if (manual) {
            triggerPrompts = triggerPrompts.filterNot { it.id == prompt.id }
            val targetDetail = targetIndex?.let { " · targeted ${players.getOrNull(it)?.name ?: "player"}" }.orEmpty()
            logAction(prompt.controllerIndex, "resolved ${prompt.cardName} trigger manually$targetDetail · ${prompt.effectText}")
            return
        }
        if (prompt.kind == "TARGET_EFFECT") {
            val validTarget = targetIndex?.takeIf { target ->
                target in players.indices && !players[target].isEliminated &&
                    (prompt.targetRule != "OPPONENT" || target != prompt.controllerIndex)
            } ?: return
            triggerPrompts = triggerPrompts.filterNot { it.id == prompt.id }
            logAction(prompt.controllerIndex, "resolved ${prompt.cardName} trigger targeting ${players[validTarget].name}")
            applyParsedAutomaticEffects(validTarget, prompt.cardName, parseAutomaticResolutionEffects(prompt.effectText))
            return
        }
        triggerPrompts = triggerPrompts.filterNot { it.id == prompt.id }
        when (prompt.kind) {
            "RHYSTIC" -> if (paid) logAction(prompt.eventPlayerIndex, "paid {1} for Rhystic Study") else drawCard(prompt.controllerIndex, "drew a card from Rhystic Study")
            "TITHE" -> if (paid) logAction(prompt.eventPlayerIndex, "paid {2} for Smothering Tithe") else addTreasure(prompt.controllerIndex)
            "AUTO_EFFECT" -> applyParsedAutomaticEffects(prompt.controllerIndex, prompt.cardName, parseAutomaticResolutionEffects(prompt.effectText))
            "MANUAL" -> {
                val targetDetail = targetIndex?.let { " · targeted ${players.getOrNull(it)?.name ?: "player"}" }.orEmpty()
                logAction(prompt.controllerIndex, "resolved ${prompt.cardName} trigger manually$targetDetail · ${prompt.effectText}")
            }
        }
    }

    fun importDeck(text: String, saveAsMine: Boolean = true) {
        val entries = parseDeckList(text)
        if (entries.isEmpty()) { importError = "No card names found. Try a list such as ‘2 Sol Ring’."; return }
        importing = true
        importError = null
        scope.launch {
            try {
                val found = Scryfall.lookup(entries).toMutableMap()
                val autoFixedEntries = mutableSetOf<DeckEntry>()
                val autoFixedNames = mutableListOf<Pair<String, String>>()
                val unmatchedEntries = entries.filterNot { it in found }
                unmatchedEntries.groupBy { it.name.trim().lowercase(Locale.ROOT) }.values.forEach { sameNameEntries ->
                    val requestedName = sameNameEntries.first().name
                    val candidate = runCatching { Scryfall.fuzzyNamedCard(requestedName) }.getOrNull()?.takeIf {
                        cardNameSimilarity(requestedName, it.name) >= 0.78
                    } ?: return@forEach
                    sameNameEntries.forEach { entry ->
                        found[entry] = candidate
                        autoFixedEntries += entry
                    }
                    autoFixedNames += requestedName to candidate.name
                }
                var importedCommander: GameCard? = null
                val issueCardIds = linkedSetOf<Int>()
                val issueDescriptions = mutableListOf<String>()
                val deck = entries.flatMap { entry ->
                    val match = found[entry]
                    val printingRequested = !entry.setCode.isNullOrBlank() && !entry.collectorNumber.isNullOrBlank()
                    val nameConflict = match != null && !match.name.equals(entry.name, ignoreCase = true) && entry !in autoFixedEntries
                    val issue = when {
                        nameConflict -> "Requested ${entry.name}, but ${entry.setCode?.uppercase()} #${entry.collectorNumber} resolves to ${match?.name}. You’ll need to resolve it manually in the editor."
                        match == null && printingRequested -> "Could not verify ${entry.name} in ${entry.setCode?.uppercase()} #${entry.collectorNumber}. You’ll need to resolve it manually in the editor."
                        match == null -> "Could not match ‘${entry.name}’ to a Magic card. You’ll need to resolve it manually in the editor."
                        else -> null
                    }
                    if (issue != null) issueDescriptions += issue
                    // Keep the requested card name when a printing identifier resolves to a
                    // different card. The review search must be for what the user imported.
                    val verifiedMatch = match?.takeUnless { nameConflict }
                    val copies = List(entry.count) {
                        makeCard(verifiedMatch?.name ?: entry.name, verifiedMatch?.type ?: "NEEDS REVIEW", verifiedMatch?.imageUrl, oracleText = verifiedMatch?.oracleText, manaValue = verifiedMatch?.manaValue, artCropUrl = verifiedMatch?.artCropUrl, manaCost = verifiedMatch?.manaCost, power = verifiedMatch?.power, toughness = verifiedMatch?.toughness, keywords = verifiedMatch?.keywords.orEmpty())
                            .copy(importIssue = issue)
                            .also { if (issue != null) issueCardIds += it.id }
                    }
                    if (entry.commander && importedCommander == null) {
                        importedCommander = copies.firstOrNull()
                        copies.drop(1)
                    } else copies
                }.shuffled()
                if (deck.isEmpty()) error("No cards found in that list.")
                val allowedMainDeckSize = if (importedCommander != null) 99 else 100
                val overflowIds = deck.drop(allowedMainDeckSize).map { it.id }.toSet()
                val importTraceId = java.util.UUID.randomUUID().toString().take(8)
                val repeated = repeatedDeckEntries(entries)
                val duplicateDetail = if (roomConnected && selectedPlayer != localSeat) {
                    "${repeated.size} repeated card name(s) withheld for a remote seat"
                } else {
                    repeated.joinToString { "${it.name} ×${it.quantity}" }.ifBlank { "none" }
                }
                recordDiagnostic("Deck import [$importTraceId]: target seat ${selectedPlayer + 1}, parsed entries=${entries.size}, requested main-deck copies=${entries.filterNot { it.commander }.sumOf { it.count }}, commander entries=${entries.count { it.commander }}, built main-deck copies=${deck.size}, duplicate quantities=$duplicateDetail, unresolved entries=${issueDescriptions.size}, over-limit copies=${overflowIds.size}")
                val opening = deck.take(7)
                if (onlineMode && selectedPlayer == localSeat && localSeat in lobbyReadyPlayers) {
                    lobbyReadyPlayers = lobbyReadyPlayers - localSeat
                    roomClient?.publishReady(false)
                    cloudRoomClient?.publishReady(false)
                }
                updatePlayer(selectedPlayer) { it.copy(deckLoaded = true, mulligansTaken = 0, openingHandKept = it.isComputer, commanderName = importedCommander?.name, commanderCard = importedCommander, library = deck.drop(opening.size), hand = opening, battlefield = emptyList(), graveyard = emptyList(), exile = emptyList(), revealed = emptyList()) }
                val importedTarget = players.getOrNull(selectedPlayer)
                if (lobbyCreated && !gameStartedOnline && importedTarget?.isComputer == true) {
                    lobbyReadyPlayers = lobbyReadyPlayers + selectedPlayer
                }
                // Your own deck always goes into your deck list and becomes the deck you play.
                if (importSaveToLibrary || (saveAsMine && selectedPlayer == localSeat)) {
                    val savedName = importSaveName.trim().ifBlank { importedCommander?.name ?: importedTarget?.name ?: "Saved deck" }
                    val saved = SavedDeck(savedName, text.trim(), importedCommander?.name)
                    if (selectedPlayer == localSeat) { activeDeckId = savedName; saveActiveDeck(context, savedName) }
                    savedDecks = listOf(saved) + savedDecks.filterNot { it.name.equals(savedName, ignoreCase = true) }
                    saveSavedDecks(context, savedDecks)
                    importSaveToLibrary = false
                    importSaveName = ""
                }
                if (roomClient != null || cloudRoomClient != null) {
                    // The room host can import a deck for an AI seat. Publish that
                    // whole host-owned state, but only send a deck-status packet
                    // when importing for this device's own seat. The Worker assigns
                    // deck-status to the connected socket's seat, so sending the AI
                    // name here would rename the host to e.g. "COMPUTER 4".
                    publishOnlineSnapshot()
                    if (selectedPlayer == localSeat) {
                        val importedPlayer = players.getOrNull(localSeat)
                        localDeckSyncConfirmed = false
                        recordDiagnostic("Deck status sent to host: loaded=true, seat ${localSeat + 1}")
                        val sent = roomClient?.publishDeckStatus(true, importedPlayer?.name ?: lobbyPlayerName, importedPlayer?.commanderName)
                            ?: cloudRoomClient?.publishDeckStatus(true, importedPlayer?.name ?: lobbyPlayerName, importedPlayer?.commanderName)
                            ?: false
                        if (!sent) {
                            lobbyNotice = "Your deck loaded on this phone, but its status could not be sent to the host. Check the connection and retry."
                            recordDiagnostic("Deck status send failed: seat ${localSeat + 1}")
                        } else {
                            scope.launch {
                                delay(5_000)
                                if (!localDeckSyncConfirmed && (roomClient != null || cloudRoomClient != null) && selectedPlayer == localSeat) {
                                    lobbyNotice = "Your deck is on this phone, but the host has not confirmed receiving it. Check both phones are on the same build and retry."
                                    recordDiagnostic("Deck confirmation timed out: seat ${localSeat + 1}")
                                }
                            }
                        }
                    } else {
                        recordDiagnostic("Host published deck for computer seat ${selectedPlayer + 1}")
                    }
                }
                pendingAiPlayerIndex = -1
                logAction(selectedPlayer, "imported a ${deck.size}-card deck")
                showOverview = false
                showHand = false
                importOpen = false
                if (issueDescriptions.isNotEmpty() || overflowIds.isNotEmpty()) {
                    importReviewCardIds = issueCardIds + overflowIds
                    importOverflowCardIds = overflowIds
                    importReviewMessage = buildString {
                        if (autoFixedNames.isNotEmpty()) {
                            append("Automatically corrected ${autoFixedNames.size} card ${if (autoFixedNames.size == 1) "name" else "names"}: ")
                            append(autoFixedNames.joinToString { "${it.first} → ${it.second}" })
                            append(". ")
                        }
                        if (issueDescriptions.isNotEmpty()) {
                            append("Import check found ${issueDescriptions.size} card ${if (issueDescriptions.size == 1) "name" else "names"} that need manual review. ")
                        } else {
                            append("Card name check passed. ")
                        }
                        append("Imported ${deck.size} main-deck cards${if (importedCommander != null) " plus ${importedCommander!!.name} as commander" else ""}.")
                        if (overflowIds.isNotEmpty()) append(" This is ${overflowIds.size} card(s) over the ${if (importedCommander != null) "99-card Commander deck" else "100-card deck"} limit.")
                        if (issueDescriptions.isNotEmpty()) {
                            append("\n\n${issueDescriptions.size} entr${if (issueDescriptions.size == 1) "y needs" else "ies need"} review:\n")
                            append(issueDescriptions.take(5).joinToString("\n"))
                            if (issueDescriptions.size > 5) append("\n…and ${issueDescriptions.size - 5} more.")
                        }
                    }
                    importReviewOpen = true
                } else {
                    message = buildString {
                        append("Import check passed: all ${entries.size} card names matched. ")
                        if (autoFixedNames.isNotEmpty()) append("Automatically corrected ${autoFixedNames.size} ${if (autoFixedNames.size == 1) "card name" else "card names"}. ")
                        append("Imported ${deck.size} cards. Your opening hand is ready.")
                    }
                }
                if (onlineMode && !gameStartedOnline) { showOverview = true; showHand = false; lobbyOpen = true }
            } catch (e: Exception) {
                importError = e.message ?: "Could not load the deck. Check your internet connection and try again."
            } finally { importing = false }
        }
    }

    fun addAiOpponent() {
        topMenuExpanded = false
        if (onlineMode && (!lobbyCreated || lobbyJoinMode || gameStartedOnline)) {
            message = "Only the host can add an AI player before the game starts."
            return
        }
        if (players.size >= 4) {
            message = "This game already has the maximum of four players."
            return
        }
        players = players + TablePlayer("COMPUTER ${players.size + 1}", isComputer = true)
        pendingAiPlayerIndex = players.lastIndex
        recordDiagnostic("AI seat added: seat ${pendingAiPlayerIndex + 1}; awaiting deck import")
        if (lobbyCreated) roomHost?.reserveSeat(pendingAiPlayerIndex)
        selectedPlayer = players.lastIndex
        gameTurnOrder = (gameTurnOrder + selectedPlayer).distinct()
        // Keep the table visible behind the import dialog so the add-AI action
        // has an immediate, obvious result. The player view opens after import.
        showOverview = true
        showHand = false
        importText = ""
        importError = null
        importOpen = true
        message = null
    }

    fun cancelDeckImport() {
        importOpen = false
        val index = pendingAiPlayerIndex
        if (index in players.indices && players[index].isComputer && !players[index].deckLoaded) {
            if (lobbyCreated) {
                removeLobbyPlayer(index)
            } else {
                players = players.filterIndexed { playerIndex, _ -> playerIndex != index }
                if (players.isNotEmpty()) {
                    selectedPlayer = if (selectedPlayer >= index) (selectedPlayer - 1).coerceIn(players.indices) else selectedPlayer.coerceIn(players.indices)
                    activePlayer = if (activePlayer >= index) (activePlayer - 1).coerceIn(players.indices) else activePlayer.coerceIn(players.indices)
                    gameTurnOrder = gameTurnOrder.filter { it != index }.map { if (it > index) it - 1 else it }
                }
            }
        }
        pendingAiPlayerIndex = -1
        importSaveToLibrary = false
    }

    fun openLobby(joining: Boolean) {
        topMenuExpanded = false
        lobbyOpen = true
        lobbyCreated = roomHost != null || (cloudRoomClient != null && localSeat == 0)
        lobbyJoinMode = joining && roomHost == null && roomClient == null && cloudRoomClient == null
        lobbyNotice = null
        lobbyTurnOrder = null
    }

    /**
     * Validates the local deck with Argentum and opens a lobby. When Argentum rejects cards, it
     * stops and shows a needs-handling review; substitutions happen only when the player chooses
     * [acceptSubstitutions], and only for this game (the saved deck is never edited here).
     */
    /**
     * Offline game: Argentum's engine and AI run inside the app (engine/LocalArgentumHost). Your
     * imported Commander deck plays one of Argentum's premade Commander decks when the full card
     * library ships in the app; otherwise a Portal duel. Same table, same messages, no server.
     */
    fun startOfflineGame() {
        if (offlineStarting || argentumEnabled) return
        val seat = players.getOrNull(localSeat)
        offlineStarting = true
        argentumLastError = null
        scope.launch {
            try {
                // The deck you picked on the home screen (never the table, which a finished game leaves behind).
                val active = activeDeckId
                val premade = active?.takeIf { it.startsWith("builtin:") }?.removePrefix("builtin:")?.let { id -> premadeDecks.firstOrNull { it.id == id } }
                val saved = active?.let { a -> savedDecks.firstOrNull { it.name == a } }
                val (names, commander) = when {
                    premade != null -> premade.cards to premade.commander
                    saved != null -> parseDeckList(saved.text).let { e -> e.flatMap { x -> List(x.count) { x.name } } to (saved.commander ?: e.firstOrNull { it.commander }?.name) }
                    else -> seat?.takeIf { it.deckLoaded }?.let { (it.library + it.hand).filterNot { c -> c.isToken || c.name == "Hidden card" }.map { c -> c.name } }.orEmpty() to
                        seat?.takeIf { it.deckLoaded }?.commanderName?.takeIf(String::isNotBlank)
                }
                val aiDeckId = offlineAiDeckId
                val started = System.currentTimeMillis()
                val prepared = withContext(Dispatchers.IO) {
                    var result: Pair<com.arcanatable.engine.OfflineSetup, com.wingedsheep.engine.registry.CardRegistry>? = null
                    var failure: Throwable? = null
                    val worker = Thread(null, {
                        try {
                            val library = com.arcanatable.engine.OfflineCardLibrary({ path -> runCatching { context.assets.open(path) }.getOrNull() })
                            val decks = runCatching { context.assets.open("argentum/commander-decks.json").bufferedReader().use { it.readText() } }.getOrNull()
                            result = com.arcanatable.engine.OfflineGameSetup.build(library, lobbyPlayerName.ifBlank { "You" }, names, commander, decks, aiDeckId = aiDeckId)
                        } catch (t: Throwable) { failure = t }
                    }, "argentum-offline-setup", 256L shl 20)
                    worker.start(); worker.join()
                    failure?.let { throw it }
                    result!!
                }
                val (setup, registry) = prepared
                recordDiagnostic("Offline game prepared in ${System.currentTimeMillis() - started} ms: ${setup.you.commander ?: "Portal"} vs ${setup.aiDeckName}", DiagnosticCategory.ENGINE)
                setup.notes.forEach { recordDiagnostic("Offline: $it", DiagnosticCategory.ENGINE) }
                argentumSocket.localHostFactory = { emit -> com.arcanatable.engine.LocalArgentumHost(registry, setup.you, setup.ai, emit = emit) }
                argentumOffline = true
                argentumDeckForLobby = null
                argentumCommanderForLobby = null
                argentumVsAiLobby = true
                argentumLobbySubmitted = true
                argentumLocalPlayersBackup = players.toList()
                argentumEnabled = true
                homeOpen = false
                lobbyOpen = false
                argentumSocket.connect(lobbyPlayerName)
                var waitCount = 0
                while (argentumStatus != ArgentumConnectionStatus.CONNECTED && waitCount < 20) { delay(100); waitCount++ }
                argentumSocket.createCommanderPracticeLobby(vsAi = true)
                if (setup.notes.isNotEmpty()) message = setup.notes.joinToString("\n\n") + "\n\nOpponent: ${setup.aiDeckName}."
            } catch (error: Throwable) {
                argentumSocket.localHostFactory = null
                argentumOffline = false
                argentumLastError = "Couldn't start the offline game: ${error.javaClass.simpleName}: ${error.message?.take(200)}"
                message = argentumLastError
                recordDiagnostic("Offline game failed to start: ${error.javaClass.simpleName}: ${error.message}", DiagnosticCategory.ERROR)
            } finally {
                offlineStarting = false
            }
        }
    }

    fun startArgentumPractice(vsAi: Boolean = true, joinLobbyCode: String? = null, acceptSubstitutions: Boolean = false) {
        if (argentumDeckValidationRunning) return
        if (joinLobbyCode != null && joinLobbyCode.isBlank()) {
            argentumLastError = "Enter the Argentum room code to join."
            return
        }
        val seat = players.getOrNull(localSeat) ?: run { message = "Import a deck before starting an Argentum game."; return }
        if (!seat.deckLoaded) {
            selectedPlayer = localSeat
            importError = null
            importOpen = true
            message = "Import your deck, then choose Play with Argentum."
            return
        }
        val commander = seat.commanderName?.takeIf(String::isNotBlank)
        if (commander == null) {
            message = "Argentum Commander games need a commander. Set one in the deck import or editor."
            return
        }
        scope.launch {
            argentumDeckValidationRunning = true
            argentumLastError = null
            val submitted = argentumCommanderLibraryDeck(
                (seat.library + seat.hand)
                    .filterNot { it.isToken || it.name == "Hidden card" }
                    .map { it.name },
                commander
            )
            if (submitted.isEmpty()) { argentumLastError = "Your imported deck has no cards to submit."; argentumDeckValidationRunning = false; return@launch }
            try {
                val api = ArgentumDeckApi(argentumOrigin)
                var commanderForGame: String = requireNotNull(commander)
                var replacementCommander = false
                var validation = withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
                if (validation.unsupportedCards.isNotEmpty() && !acceptSubstitutions) {
                    val rejected = validation.unsupportedCards.sortedBy { it.lowercase() }
                    rejected.forEach { reportArgentumUnsupported("card:$it", "Argentum cannot run card $it") }
                    recordDiagnostic("Deck check: ${rejected.size} card(s) need handling; waiting for the player's choice", DiagnosticCategory.UNSUPPORTED)
                    argentumDeckReview = ArgentumDeckReview(
                        unsupported = rejected,
                        commanderUnsupported = rejected.any { it.equals(commander, ignoreCase = true) },
                        commander = commander,
                        vsAi = vsAi,
                        joinCode = joinLobbyCode,
                    )
                    return@launch
                }
                val unsupported = linkedSetOf<String>()
                val replacementBasics = mutableListOf<String>()
                var checks = 0
                while (validation.unsupportedCards.isNotEmpty() && checks++ < 100) {
                    val newlyUnsupported = validation.unsupportedCards
                    unsupported += newlyUnsupported
                    val commanderUnsupported = newlyUnsupported.any { it.equals(commander, ignoreCase = true) }
                    val priorSize = submitted.size
                    submitted.keys.filter { name -> newlyUnsupported.any { it.equals(name, ignoreCase = true) } }.forEach(submitted::remove)
                    if (commanderUnsupported) {
                        val candidates = withContext(Dispatchers.IO) {
                            api.compatibleCommanderOptions((seat.library + seat.hand)
                                .filterNot { it.isToken || it.name == "Hidden card" || newlyUnsupported.any { name -> name.equals(it.name, true) } }
                                .map { it.name })
                        }
                        var replacement: Pair<String, Pair<Map<String, Int>, List<String>>>? = null
                        for ((candidate, basics) in candidates) {
                            val candidateDeck = submitted.toMutableMap()
                            // The replacement commander moves from the 99-card library into
                            // the command zone, so remove one copy before filling the library.
                            val candidateCount = candidateDeck.entries.firstOrNull {
                                it.key.equals(candidate, ignoreCase = true)
                            }
                            if (candidateCount != null) {
                                if (candidateCount.value <= 1) candidateDeck.remove(candidateCount.key)
                                else candidateDeck[candidateCount.key] = candidateCount.value - 1
                            }
                            if (basics.isEmpty()) continue
                            val candidateBasics = mutableListOf<String>()
                            val missing = (99 - candidateDeck.values.sum()).coerceAtLeast(0)
                            repeat(missing) { offset ->
                                val basic = basics[offset % basics.size]
                                candidateDeck[basic] = (candidateDeck[basic] ?: 0) + 1
                                candidateBasics += basic
                            }
                            val candidateValidation = withContext(Dispatchers.IO) {
                                api.validate(candidateDeck, candidate, "COMMANDER")
                            }
                            if (candidateValidation.valid) {
                                replacement = candidate to (candidateDeck to candidateBasics)
                                validation = candidateValidation
                                break
                            }
                        }
                        if (replacement == null) {
                            argentumLastError = "Argentum does not support commander $commander, and this deck has no compatible commander. Choose a supported commander and try again."
                            return@launch
                        }
                        val selectedReplacement = replacement
                        commanderForGame = selectedReplacement.first
                        submitted.clear()
                        submitted.putAll(selectedReplacement.second.first)
                        replacementBasics += selectedReplacement.second.second
                        replacementCommander = true
                        break
                    }
                    if (submitted.size == priorSize) break
                    validation = withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
                }
                if (unsupported.isNotEmpty() && !validation.valid) {
                    val missing = (99 - submitted.values.sum()).coerceAtLeast(0)
                    if (missing > 0) {
                        val basics = withContext(Dispatchers.IO) { api.supportedBasicLands(commanderForGame) }
                        if (basics.isEmpty()) {
                            argentumLastError = "Argentum rejected ${unsupported.joinToString()}; no supported basic lands were found to complete the deck."
                            return@launch
                        }
                        repeat(missing) { offset ->
                            val basic = basics[offset % basics.size]
                            submitted[basic] = (submitted[basic] ?: 0) + 1
                            replacementBasics += basic
                        }
                        validation = withContext(Dispatchers.IO) { api.validate(submitted, commanderForGame, "COMMANDER") }
                    }
                }
                if (unsupported.isNotEmpty()) {
                    recordDiagnostic("Removed ${unsupported.size} Argentum-unsupported card name(s) and added ${replacementBasics.size} supported basic land(s)")
                }
                if (!validation.valid) {
                    val reasons = validation.errors.joinToString("\n") { it.message }.ifBlank { "Argentum rejected this Commander deck (${validation.totalCards} cards)." }
                    argentumLastError = (if (unsupported.isEmpty()) "" else "Unsupported cards: ${unsupported.joinToString()}. They were left out and replaced with ${replacementBasics.size} supported basic lands.\n") + reasons
                    return@launch
                }
                if (unsupported.isNotEmpty()) {
                    // Substitutions apply to this Argentum game only; the saved Arcana deck is untouched.
                    message = "For this Argentum game, ${unsupported.size} unsupported card(s) were left out${if (replacementCommander) " and $commanderForGame is commander" else ""}; ${replacementBasics.size} supported basic land(s) fill the gap. Your saved deck is unchanged."
                    recordDiagnostic("Player chose to play without ${unsupported.size} unsupported card(s); ${replacementBasics.size} basic land(s) added for this game only", DiagnosticCategory.UNSUPPORTED)
                }
                argentumDeckForLobby = submitted
                argentumCommanderForLobby = commanderForGame
                argentumVsAiLobby = vsAi
                argentumLobbySubmitted = false
                argentumLocalPlayersBackup = players.toList()
                argentumEnabled = true
                homeOpen = false
                lobbyOpen = false
                argentumSocket.connect(lobbyPlayerName)
                var waitCount = 0
                while (argentumStatus != ArgentumConnectionStatus.CONNECTED && waitCount < 30) {
                    delay(400)
                    waitCount++
                }
                if (argentumStatus != ArgentumConnectionStatus.CONNECTED) {
                    argentumLastError = "Could not connect to Argentum. The app will keep retrying in the background."
                    return@launch
                }
                val lobbySent = if (joinLobbyCode != null) {
                    argentumSocket.joinQuickGameLobby(joinLobbyCode.trim())
                } else {
                    argentumSocket.createCommanderPracticeLobby(vsAi = vsAi)
                }
                if (!lobbySent) {
                    argentumLastError = if (joinLobbyCode != null) "Connected to Argentum, but could not join that room. Check the code and try again."
                    else "Connected to Argentum, but could not create the room."
                } else {
                    recordDiagnostic("Argentum Commander ${if (joinLobbyCode != null) "room join" else if (vsAi) "practice lobby" else "room"} requested with ${validation.totalCards} supported cards")
                }
            } catch (error: Exception) {
                argentumLastError = when (error) {
                    is java.net.SocketTimeoutException -> "Argentum took too long to answer. Check your connection and try again."
                    is java.net.UnknownHostException, is java.net.ConnectException, is javax.net.ssl.SSLException, is java.io.IOException ->
                        "Couldn't reach the Argentum server. Check your internet connection and try again. Your deck hasn't been changed."
                    else -> error.message ?: "Could not validate this deck with Argentum."
                }
                recordDiagnostic("Argentum deck validation failed (${error.javaClass.simpleName}): ${error.message.orEmpty()}", DiagnosticCategory.ERROR)
            } finally {
                argentumDeckValidationRunning = false
            }
        }
    }

    suspend fun runComputerTurn(playerIndex: Int) {
        if (playerIndex !in players.indices || !players[playerIndex].isComputer || !players[playerIndex].deckLoaded) return
        aiRoutineRunning = true
        try {
            delay(450)
            clearMana(playerIndex)
            untapPermanents(playerIndex)
            phaseIndex = 2
            if (players[playerIndex].library.isNotEmpty()) drawCard(playerIndex, "drew for turn")
            delay(450)

            phaseIndex = 3
            val land = players[playerIndex].hand.firstOrNull { it.type.contains("land", true) }
            if (land != null) {
                val entersTapped = landAlwaysEntersTapped(land) || landEntryCondition(land) != null
                updatePlayer(playerIndex) { player -> player.copy(hand = player.hand.filterNot { it.id == land.id }, battlefield = player.battlefield + land.copy(tapped = entersTapped)) }
                logAction(playerIndex, "played ${land.name} as a land${if (entersTapped) " tapped" else ""}")
                if (needsCreatureTypeChoice(land)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(playerIndex, land.id, land.name)
                delay(350)
            }

            while (true) {
                val player = players[playerIndex]
                val choice = player.hand.asSequence()
                    .filterNot { it.type.contains("land", true) }
                    .mapNotNull { card -> planAiMana(player, card)?.let { card to it } }
                    .maxByOrNull { (card, _) -> aiCardPriority(card) } ?: break
                val spell = choice.first
                val plan = choice.second
                val permanent = Regex("creature|artifact|enchantment|planeswalker|battle", RegexOption.IGNORE_CASE).containsMatchIn(spell.type)
                updatePlayer(playerIndex) { current ->
                    current.copy(
                        hand = current.hand.filterNot { it.id == spell.id },
                        manaPool = plan.remainingPool,
                        battlefield = current.battlefield.map { if (it.id in plan.landIds) it.copy(tapped = true) else it } + if (permanent) listOf(spell) else emptyList(),
                        graveyard = if (permanent) current.graveyard else current.graveyard + spell
                    )
                }
                logAction(playerIndex, "cast ${spell.name} using tracked colored mana · cost ${spell.manaCost ?: "unlisted"}")
                if (!permanent) logAction(playerIndex, "resolved ${spell.name} to graveyard")
                if (permanent && needsCreatureTypeChoice(spell)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(playerIndex, spell.id, spell.name)
                if (permanent && spell.type.contains("creature", ignoreCase = true)) applyCreatureEntryLifeEffects(playerIndex, spell)
                if (!permanent) applyAutomaticResolutionEffects(playerIndex, spell)
                queueKnownTriggers("CAST", playerIndex)
                delay(500)
            }

            phaseIndex = 6
            logAction(playerIndex, "skipped combat and passed")
            delay(450)
            clearMana(playerIndex)
            phaseIndex = 0
            completeCurrentPlayerTurn(playerIndex)
            activePlayer = nextTurnPlayer(playerIndex)
            elapsedTurnSeconds = 0
            if (!players[activePlayer].isComputer) untapPermanents(activePlayer)
            logAction(activePlayer, "started turn $turnNumber")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recordDiagnostic("AI turn recovered for seat ${playerIndex + 1}: ${error.message ?: error::class.java.simpleName}")
            // Keep a malformed card or unexpected rules edge case from trapping
            // the whole table on an AI turn. The human can inspect the diagnostic.
            if (activePlayer == playerIndex && !gameEndedOnline && players.getOrNull(playerIndex)?.isEliminated == false) {
                clearMana(playerIndex)
                phaseIndex = 0
                completeCurrentPlayerTurn(playerIndex)
                activePlayer = nextTurnPlayer(playerIndex)
                elapsedTurnSeconds = 0
                if (!players[activePlayer].isComputer) untapPermanents(activePlayer)
                logAction(activePlayer, "started turn $turnNumber after AI recovery")
            }
        } finally {
            aiRoutineRunning = false
        }
    }

    LaunchedEffect(stackActions, priorityPlayer, priorityPassed, players, gameGeneration, onlineMode, argentumEnabled) {
        val current = players.getOrNull(priorityPlayer)
        if (!argentumEnabled && (!onlineMode || localSeat == 0) && stackActions.isNotEmpty() && current?.isComputer == true && !current.isEliminated) {
            delay(350)
            val passed = priorityPassed + current.id
            priorityPassed = passed
            val eligibleIds = players.filterNot { it.isEliminated }.map { it.id }.toSet()
            if (eligibleIds.isNotEmpty() && eligibleIds.all { it in passed }) {
                resolveTopOfStack()
            } else {
                priorityPlayer = nextTurnIndex(priorityPlayer, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
            }
        }
    }

    LaunchedEffect(activePlayer, turnNumber, players.getOrNull(activePlayer)?.deckLoaded, gameGeneration, argentumEnabled) {
        if (!argentumEnabled && (!onlineMode || localSeat == 0) && !aiRoutineRunning && players.getOrNull(activePlayer)?.let { it.isComputer && it.deckLoaded } == true) {
            runComputerTurn(activePlayer)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                if (text.isNullOrBlank()) importError = "That file is empty or couldn't be read." else importText = text
            } catch (_: Exception) { importError = "Couldn't read the file. Choose a text or CSV deck export." }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
    Column(
        Modifier.fillMaxSize().background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 10.dp, top = 8.dp, end = 10.dp)
    ) {
        // Argentum game: one strip for connection, phase and timer (life is on the player tabs).
        val compactArgentumTable = argentumEnabled && argentumSnapshot != null
        // The colour of whoever's turn it is: gold for you, blue for an opponent.
        val turnAccent = argentumSnapshot?.let { TurnColors.forSeat(it.activePlayer == localSeat) } ?: Mint
        // Player tabs (seat tabs with life in Argentum games). In landscape they share the top strip.
        val seatTabs: @Composable (Modifier) -> Unit = { rowModifier ->
            Row(rowModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier) { ViewChip("TABLE", selected = showOverview, compact = compactArgentumTable, onClick = { run { showOverview = true; showHand = false } }) }
                players.forEachIndexed { index, player ->
                    Box(Modifier.testTag(if (onlineMode && index == localSeat) "player-tab-you" else "player-tab-$index")) {
                        val snap = argentumSnapshot
                        if (compactArgentumTable && snap != null) ArgentumSeatTab(
                            seat = ArgentumSeatChip(player.name, player.life, isYou = index == localSeat, isActive = index == snap.activePlayer, hasPriority = index == snap.priorityPlayer),
                            turnLabel = if (index == snap.activePlayer) TurnColors.label(index == localSeat, player.name, player.isComputer) else null,
                            artUrl = player.commanderCard?.let { it.artCropUrl ?: it.imageUrl }, artLabel = player.commanderCard?.name,
                            selected = !showOverview && selectedPlayer == index,
                            onClick = { run { selectedPlayer = index; showOverview = false } }
                        ) else PlayerViewTab(
                            name = if (onlineMode && index == localSeat) "YOU" else player.name,
                            commander = player.commanderCard,
                            selected = !showOverview && selectedPlayer == index,
                            onClick = { run { selectedPlayer = index; showOverview = false; showHand = false } }
                        )
                    }
                }
            }
        }
        val tabsInTopBar = compactArgentumTable && isLandscape
        val sideBySideBoards = compactArgentumTable && !isLandscape && screenWidthDp >= 600 && configuration.screenHeightDp >= 600 && players.size >= 2 && localSeat in players.indices
        Row(Modifier.fillMaxWidth().heightIn(min = if (compactArgentumTable) 44.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compactArgentumTable) {
                val dotColor = when { argentumStatus != ArgentumConnectionStatus.CONNECTED -> Color(0xFFE9978B); argentumAwaitingUpdate -> CounterGold; else -> Mint }
                Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor).semantics { contentDescription = "Argentum ${argentumStatus.name.lowercase()}" })
                Spacer(Modifier.width(8.dp))
                if (tabsInTopBar) {
                    PhaseTracker(argentumSnapshot?.stepKey.orEmpty(), Modifier.width(320.dp), accent = turnAccent)
                    Spacer(Modifier.width(8.dp))
                    seatTabs(Modifier.weight(1f))
                } else PhaseTracker(argentumSnapshot?.stepKey.orEmpty(), Modifier.weight(1f), accent = turnAccent)
                Spacer(Modifier.width(6.dp))
            } else Column {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                if (!argentumEnabled) {
                    Text("VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                    val inProgress = if (onlineMode) gameStartedOnline && !gameEndedOnline else localGameStarted
                    Text(if (inProgress) "● GAME IN PROGRESS" else "● GAME NOT STARTED", color = if (inProgress) Mint else CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                } else Text("ARGENTUM ENGINE", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            if (!compactArgentumTable) Spacer(Modifier.weight(1f))
            Box(Modifier) {
                Text(if (compactArgentumTable) "T${turnNumber}${if (turnTimerVisible) " · " + formatClock(elapsedTurnSeconds) else ""}" else "GAME ${formatClock(elapsedGameSeconds)} · TURN ${if (turnTimerVisible) formatClock(elapsedTurnSeconds) else "HIDDEN"}", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically).combinedClickable(enabled = !onlineMode || localSeat == 0, onClick = { run { timerMenuOpen = true } }, onLongClick = { turnTimerVisible = !turnTimerVisible }))
                DropdownMenu(expanded = timerMenuOpen, onDismissRequest = { timerMenuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (gameTimerRunning) "Stop session timer" else "Start session timer") }, onClick = { gameTimerRunning = !gameTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset session timer") }, onClick = { elapsedGameSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerRunning) "Stop turn timer" else "Start turn timer") }, onClick = { turnTimerRunning = !turnTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset turn timer") }, onClick = { elapsedTurnSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerVisible) "Hide turn timer display" else "Show turn timer display") }, onClick = { turnTimerVisible = !turnTimerVisible; timerMenuOpen = false })
                }
            }
            // Argentum does not relay Arcana chat, so the chat entry point is hidden during Argentum games.
            if (!argentumEnabled) Box(Modifier) {
                IconButton(onClick = { run { chatOpen = true; unreadChatCount = 0 } }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Chat, "Open chat${if (unreadChatCount > 0) ", $unreadChatCount unread" else ""}", tint = if (unreadChatCount > 0) CounterGold else SoftWhite)
                }
                if (unreadChatCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(9.dp).clip(CircleShape).background(Color(0xFFE35656)))
            }
            Box(Modifier) {
                IconButton(onClick = { run { topMenuSection = null; topMenuExpanded = true } }, modifier = Modifier.size(46.dp)) { Icon(Icons.Filled.MoreVert, "App menu", tint = SoftWhite) }
                DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false; topMenuSection = null }) {
                    if (topMenuSection == null) {
                        DropdownMenuItem(text = { Text("Decks") }, modifier = Modifier, onClick = { run { topMenuSection = "decks" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Game tools") }, modifier = Modifier, onClick = { run { topMenuSection = "tools" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Room & game") }, modifier = Modifier, onClick = { run { topMenuSection = "game" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("What's new") }, onClick = { topMenuExpanded = false; changelogOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Adjust UI") }, onClick = { topMenuExpanded = false; topMenuSection = null; layoutLabOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Options") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; optionsOpen = true } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                    } else {
                        DropdownMenuItem(text = { Text("‹  MAIN MENU") }, modifier = Modifier, onClick = { run { topMenuSection = null } })
                        when (topMenuSection) {
                            "decks" -> {
                                if (!argentumEnabled) DropdownMenuItem(text = { Text(if (players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) "Replace imported deck" else "Import deck") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } })
                                if (!argentumEnabled && players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) DropdownMenuItem(text = { Text("Edit imported deck") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; deckEditorOpen = true } })
                                DropdownMenuItem(text = { Text("Saved decks · ${savedDecks.size}") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; savedDecksOpen = true } })
                            }
                            "tools" -> {
                                if (!onlineMode) DropdownMenuItem(text = { Text("Add AI opponent · ${players.size}/4") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; addAiOpponent() } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Add token") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; tokenTarget = selectedPlayer; tokenPickerOpen = true } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Mana pool · ${trackedMana(selectedPlayer)}") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; manaTarget = selectedPlayer; manaPoolOpen = true } })
                                if (!argentumEnabled && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Search / browse library") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; librarySearchPlayer = selectedPlayer } })
                                if (argentumEnabled && gameInProgress) DropdownMenuItem(text = { Text("Argentum legal actions · ${ArgentumActions.visibleChoices(argentumSnapshot?.legalActions.orEmpty()).size}") }, onClick = { topMenuExpanded = false; topMenuSection = null; run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } })
                                if (gameInProgress) DropdownMenuItem(text = { Text("Stack and priority · $visibleStackCount") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; stackOpen = true } })
                                DropdownMenuItem(text = { Text("Game log") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; logOpen = true } })
                                DropdownMenuItem(text = { Text("MTG rules") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; rulesOpen = true } })
                            }
                            "game" -> {
                                DropdownMenuItem(text = { Text("Room home") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; homeOpen = true; showOverview = true; showHand = false } })
                                if (!onlineMode && !localGameStarted) DropdownMenuItem(text = { Text("Start Argentum game") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; startArgentumPractice(vsAi = true) } })
                                if (!argentumEnabled) DropdownMenuItem(text = { Text("Argentum room") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; startArgentumPractice(vsAi = false) } })
                                if (onlineMode && gameStartedOnline && !gameEndedOnline) DropdownMenuItem(text = { Text("Propose ending the game") }, modifier = Modifier, onClick = { run {
                                    topMenuExpanded = false; topMenuSection = null
                                    if (endProposalBy < 0) { endProposalBy = localSeat; endVotes = mapOf(localSeat to true); logAction(localSeat, "proposed ending the game") }
                                } })
                                if (onlineMode) DropdownMenuItem(text = { Text("Leave game…") }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; if (argentumEnabled) argentumLeaveConfirm = true else lobbyOpen = true } })
                                if (!onlineMode || localSeat == 0 && gameEndedOnline) DropdownMenuItem(text = { Text("Reset game", color = Color(0xFFE9978B)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; resetConfirmOpen = true } })
                                if (!onlineMode) DropdownMenuItem(text = { Text("Reset and clear all data", color = Color(0xFFE9978B)) }, modifier = Modifier, onClick = { run { topMenuExpanded = false; topMenuSection = null; clearAllConfirmOpen = true } })
                            }
                        }
                    }
                }
            }
        }

        // Compact table: the strip appears only for problems, a waiting confirmation, game over or a non-empty stack.
        val stripNeeded = argentumStatus != ArgentumConnectionStatus.CONNECTED || argentumNeedsHandling != null || argentumAwaitingUpdate || gameEndedOnline ||
            argentumSnapshot == null || argentumSnapshot?.stack?.isNotEmpty() == true
        if (argentumEnabled && stripNeeded) ArgentumStatusStrip(
            model = ArgentumStatusModel(
                connection = argentumStatus,
                gameActive = argentumSnapshot != null && !gameEndedOnline,
                gameOver = gameEndedOnline,
                awaitingUpdate = argentumAwaitingUpdate,
                turnLabel = argentumSnapshot?.let { "Turn ${it.turnNumber} · ${it.stepName.lowercase().replaceFirstChar(Char::uppercase)} · ${if (it.activePlayer == localSeat) "your turn" else "${players.getOrNull(it.activePlayer)?.name ?: "opponent"}'s turn"}" },
                priorityLabel = argentumPrimaryLabel,
                needsHandling = argentumNeedsHandling,
                stepKey = argentumSnapshot?.stepKey.orEmpty(),
                seats = argentumSnapshot?.let { snap -> snap.players.mapIndexed { i, p ->
                    ArgentumSeatChip(p.name, p.life, isYou = i == snap.viewingPlayer, isActive = i == snap.activePlayer, hasPriority = i == snap.priorityPlayer)
                } }.orEmpty(),
                stack = argentumSnapshot?.stack.orEmpty(),
                feed = argentumSnapshot?.recentLog.orEmpty(),
            ),
            onOpenLog = { logOpen = true },
            onResume = { recordDiagnostic("Player chose Resume", DiagnosticCategory.CONNECTION); argentumSocket.retryNow() },
            onReconnect = { recordDiagnostic("Player chose Reconnect", DiagnosticCategory.CONNECTION); argentumSocket.retryNow() },
            onPrimary = { argentumPrimary() },
            onNeedsHandling = { argentumDeferredDecisionId = null },
            onLeave = { argentumLeaveConfirm = true },
            compact = true,
        )
        if (!tabsInTopBar) Row(Modifier.fillMaxWidth().padding(vertical = if (compactArgentumTable) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
            seatTabs(Modifier.weight(1f))
        }

        HorizontalPager(
            state = viewPagerState,
            modifier = Modifier.weight(1f),
            // Player boards contain horizontal card and zone lists. Keep
            // gestures inside those lists; use the player tabs to switch boards.
            userScrollEnabled = showOverview,
            verticalAlignment = Alignment.Top,
            key = { page -> if (page == 0) "table" else players.getOrNull(page - 1)?.id ?: page }
        ) { page ->
        // Pager pages need their own Column scope: the overview grid and player
        // board use weight to fill the page beneath the player tabs.
        Column(Modifier.fillMaxSize()) {
        val showingView = page - 1
        if (showingView < 0) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FULL TABLE · ${players.size}/4 PLAYERS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            }
            val compactOverview = screenWidthDp < 900 && isLandscape && players.size >= 3
            val overviewColumns = when {
                screenWidthDp >= 1800 -> 4
                screenWidthDp >= 1300 -> minOf(3, players.size)
                screenWidthDp >= 700 -> 2
                else -> 1
            }
            val overviewGridState = rememberLazyGridState()
            LaunchedEffect(activePlayer, players.size) {
                if (activePlayer in players.indices) {
                    if (reducedSwipeMotion) overviewGridState.scrollToItem(activePlayer)
                    else overviewGridState.animateScrollToItem(activePlayer)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(overviewColumns.coerceAtLeast(1)),
                state = overviewGridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(players.size) { index ->
                    val player = players[index]
                    PlayerOverview(
                        player.copy(name = if (onlineMode && index == localSeat) "YOU" else player.name), index,
                        Modifier.fillMaxWidth().then(if (index == selectedPlayer) Modifier else Modifier),
                        compact = compactOverview,
                        isActivePlayer = index == activePlayer,
                        turnNumber = turnNumber,
                        phaseName = if (index == activePlayer) phaseSteps[phaseIndex] else "WAITING",
                        turnTime = if (index == activePlayer) elapsedTurnSeconds else null,
                        showTurnTimer = turnTimerVisible,
                        onToggleTimer = { if (!onlineMode || localSeat == 0) turnTimerVisible = !turnTimerVisible },
                        onMoveUp = { if (!onlineMode) movePlayerCard(index, -1) }, onMoveDown = { if (!onlineMode) movePlayerCard(index, 1) },
                        canReorder = !onlineMode,
                        onOpen = { run { selectedPlayer = index; showOverview = false } },
                        onZone = { zoneDialog = index to it },
                        onPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = gameInProgress && (!onlineMode || index == localSeat) && !gameEndedOnline) },
                        onPublicPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = false) }
                    )
                }
            }
        } else {
            // One board, or on a big screen (unfolded Fold) an opponent's board beside yours.
            @Composable
            fun BoardFor(boardIndex: Int, boardModifier: Modifier) {
                val boardCanControl = gameInProgress && !argentumEnabled && (!onlineMode || boardIndex == localSeat) && !gameEndedOnline && !players[boardIndex].isEliminated
                PlayerBoard(
                    player = players[boardIndex].copy(name = if (onlineMode && boardIndex == localSeat) "YOU" else players[boardIndex].name), playerIndex = boardIndex, isLandscape = isLandscape, cardSizeScale = cardSizeScale,
                    boardSpacingScale = boardSpacingScale,
                    playerSectionOrder = playerSectionOrderRaw.split('|').filter(String::isNotBlank),
                    quickActionOrder = quickActionOrderRaw.split('|').filter(String::isNotBlank),
                    gameInProgress = gameInProgress,
                    stackCount = visibleStackCount,
                    commanderUnavailable = players[boardIndex].commanderCard?.let { commander ->
                        players[boardIndex].library.any { it.id == commander.id } || players[boardIndex].hand.any { it.id == commander.id } || players[boardIndex].battlefield.any { it.id == commander.id } || players[boardIndex].graveyard.any { it.id == commander.id } || players[boardIndex].exile.any { it.id == commander.id } || players[boardIndex].revealed.any { it.id == commander.id } || stackActions.any { it.card?.id == commander.id }
                    } ?: false,
                    canControl = boardCanControl,
                    isActivePlayer = boardIndex == activePlayer, turnNumber = turnNumber, phaseName = phaseSteps[phaseIndex],
                    modifier = boardModifier
                        .turnHighlight(argentumEnabled && argentumSnapshot != null, boardIndex == argentumSnapshot?.activePlayer, boardIndex == localSeat)
                        .onGloballyPositioned { if (boardIndex == localSeat || !sideBySideBoards) battlefieldBounds = it.boundsInRoot() },
                    onLife = { delta -> run { if (boardCanControl) { val nextLife = (players[boardIndex].life + delta).coerceAtLeast(0); updatePlayer(boardIndex) { it.copy(life = nextLife) }; logAction(boardIndex, if (delta > 0) "gained life" else "lost life"); if (nextLife == 0) eliminatePlayer(boardIndex, "Life total reached 0") } } },
                    onDraw = { run { if (boardCanControl) { drawCard(boardIndex); showHand = true } } },
                    onShowHand = { run { if (boardCanControl || (argentumEnabled && boardIndex == localSeat)) showHand = true } },
                    onShowHandFromZone = { run { if (boardCanControl || (argentumEnabled && boardIndex == localSeat)) showHand = true } },
                    engineControlled = argentumEnabled && boardIndex == localSeat,
                    engineGame = argentumEnabled,
                    undoAvailable = argentumEnabled && boardIndex == localSeat && argentumUndoAvailable && !argentumAwaitingUpdate,
                    passToMyTurnActive = passToMyTurnFrom != null,
                    onPassToMyTurn = { run { togglePassToMyTurn() } },
                    onUndo = { run { requestArgentumUndo() } },
                    playableIds = if (boardIndex == localSeat) argentumPlayableIds else emptySet(),
                    onToken = { run { if (boardCanControl) { tokenTarget = boardIndex; tokenPickerOpen = true } } },
                    onManageMana = { run { if (boardCanControl) { manaTarget = boardIndex; manaPoolOpen = true } } },
                    onZone = { zone -> run { zoneDialog = boardIndex to zone } },
                    onPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = boardCanControl) },
                    onPublicPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = false) },
                    onTapCard = { id -> run {
                        val card = players.getOrNull(boardIndex)?.battlefield?.firstOrNull { it.id == id }
                        if (argentumEnabled && card != null && boardIndex == localSeat) openArgentumActions(card)
                        else if (boardCanControl) card?.let { tapPermanent(boardIndex, it) }
                    } },
                    onCardAction = { card, action ->
                        if (argentumEnabled && action != "INSPECT" && boardIndex == localSeat) openArgentumActions(card)
                        else if (boardCanControl || action == "INSPECT") when (action) {
                            "COUNTER" -> counterTarget = boardIndex to card.id
                            "TOKEN" -> { tokenTarget = boardIndex; tokenPickerOpen = true }
                            "TAP" -> tapPermanent(boardIndex, card)
                            "FACE UP" -> { updateCard(boardIndex, card.id) { it.copy(isFaceDown = false) }; logAction(boardIndex, "turned a face-down card face up") }
                            "SET COMMANDER" -> if (players[boardIndex].commanderCard == null && players[boardIndex].commanderName.isNullOrBlank()) { updatePlayer(boardIndex) { it.copy(commanderName = card.name, commanderCard = card) }; logAction(boardIndex, "marked ${card.name} as commander") }
                            "COMMAND ZONE" -> if (players[boardIndex].commanderCard?.id == card.id) {
                                updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, commanderCard = card, commanderName = card.name) }
                                logAction(boardIndex, "moved ${card.name} to the command zone")
                            } else message = "Only your designated commander can move to the command zone."
                            "GRAVEYARD" -> { updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, graveyard = p.graveyard + card) }; logAction(boardIndex, "moved ${card.name} to graveyard") }
                            "EXILE" -> { updatePlayer(boardIndex) { p -> p.copy(battlefield = p.battlefield.filterNot { it.id == card.id }, exile = p.exile + card) }; logAction(boardIndex, "exiled ${card.name}") }
                            "INSPECT" -> preview = CardPreview(boardIndex, card, fromHand = false)
                            else -> if (action.startsWith("ABILITY:")) {
                                val abilityText = action.removePrefix("ABILITY:")
                                val kind = if (card.type.contains("planeswalker", ignoreCase = true)) "LOYALTY ABILITY" else "ACTIVATED ABILITY"
                                val stackItem = StackAction(title = "${card.name}: $abilityText", controllerId = players[boardIndex].id, kind = kind, card = card, targetText = abilityText)
                                stackActions = stackActions + stackItem
                                if (stackItem.targetCount != null) targetSelectionStackItemId = stackItem.id
                                priorityPassed = emptySet()
                                priorityPlayer = boardIndex
                                stackOpen = stackItem.targetCount == null
                                logAction(boardIndex, "activated an ability of ${card.name}; resolve its effect manually")
                            }
                        }
                    },
                    onNextStep = { run { if (argentumEnabled && boardIndex == localSeat) sendArgentumPriority() else if (boardCanControl && boardIndex == activePlayer) advanceStep() } }, onOpenLog = { logOpen = true }, onOpenChat = { chatOpen = true },
                    onCombat = { run { if (argentumEnabled && boardIndex == localSeat) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else if (boardCanControl && boardIndex == activePlayer) combatDialogOpen = true } },
                    onTrackers = { if (boardCanControl) trackerPlayer = boardIndex },
                    onSearchLibrary = { run { if (boardCanControl) librarySearchPlayer = boardIndex } },
                    onStack = { run { stackOpen = true } },
                    onOpenMore = { run {} },
            onCastCommander = { run {
                        if (boardCanControl) {
                            val commander = players[boardIndex].commanderCard
                            if (commander == null) message = "Set a commander from your deck or battlefield first."
                            else paymentCard = boardIndex to commander
                        } else if (argentumEnabled && boardIndex == localSeat) players[boardIndex].commanderCard?.let(::openArgentumActions)
                    } },
                    onConcede = { if (boardCanControl) concedePlayer = boardIndex },
                    onReveal = {
                        if (boardCanControl) {
                            updatePlayer(boardIndex) { p ->
                                if (p.library.isEmpty()) p else p.copy(library = p.library.drop(1), revealed = p.revealed + p.library.first())
                            }
                            logAction(boardIndex, "revealed the top card")
                        }
                    },
                    onShuffle = { if (boardCanControl) { updatePlayer(boardIndex) { it.copy(library = it.library.shuffled()) }; logAction(boardIndex, "shuffled their library") } }
                )
            }
            val shownIndex = showingView.coerceIn(players.indices)
            if (sideBySideBoards) {
                val leftIndex = if (shownIndex != localSeat) shownIndex
                    else lastOpponentViewed.takeIf { it in players.indices && it != localSeat } ?: players.indices.first { it != localSeat }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardFor(leftIndex, Modifier.weight(1f).fillMaxHeight())
                    BoardFor(localSeat, Modifier.weight(1f).fillMaxHeight())
                }
            } else BoardFor(shownIndex, Modifier.weight(1f).fillMaxWidth())
        }
        }
        }

        if (showOverview) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (argentumEnabled && gameInProgress) HandPeekBar(
                    players.getOrNull(localSeat)?.hand?.size ?: 0,
                    modifier = Modifier.weight(1f).testTag("hand-peek-table"),
                    onClick = { run { showHand = true } }
                ) else Text("Tap a player to inspect their board", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (argentumEnabled) {
                    if (passToMyTurnFrom != null) HeaderButton("AUTO-PASSING · STOP", onClick = { run { togglePassToMyTurn() } }, modifier = Modifier)
                    else if (argentumPrimaryLabel != null) PassButton(argentumPrimaryLabel, onClick = { run { argentumPrimary() } }, enabled = !argentumAwaitingUpdate, canRespond = argentumCanRespond, modifier = Modifier)
                    else Text(when { gameEndedOnline -> "GAME OVER"; argentumSnapshot == null -> "WAITING FOR ARGENTUM"; argentumPendingDecision != null -> "YOUR CHOICE IS NEEDED"; argentumAwaitingUpdate -> "WAITING FOR ARGENTUM"; else -> "OPPONENT HAS PRIORITY" }, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) HeaderButton("NEXT · ${phaseSteps[phaseIndex]}", onClick = { run { if (argentumEnabled) sendArgentumPriority() else advanceStep() } }, enabled = !argentumAwaitingUpdate, modifier = Modifier)
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) {
                    Spacer(Modifier.width(5.dp))
                    HeaderButton("PASS TURN", onClick = { run { if (argentumEnabled) sendArgentumPriority() else passTurn() } }, enabled = !argentumAwaitingUpdate, modifier = Modifier)
                } else if (gameInProgress) Text(if (players[activePlayer].deckLoaded) "COMPUTER TURN" else "AI NEEDS DECK", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                else Text("START GAME TO PLAY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                }
                if (!players[selectedPlayer].deckLoaded && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) {
                    Spacer(Modifier.width(6.dp))
                    HeaderButton("IMPORT DECK", onClick = { run { if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } }, modifier = Modifier)
                }
            }
        } else {
            val canPassTurnFromBoard = gameInProgress && !gameEndedOnline && selectedPlayer == activePlayer && !players[selectedPlayer].isComputer && !players[selectedPlayer].isEliminated && (!onlineMode || localSeat == activePlayer) && !argentumAwaitingUpdate
            val footerControlHeight = (54.dp * footerButtonScale).coerceIn(44.dp, 76.dp)
            val handControlHeight = (64.dp * footerButtonScale).coerceIn(50.dp, 84.dp)
            // Pass · Hand · Table share one baseline and height; the hand drawer is the one larger
            // control (a little taller and wider). The pass button works whichever board you're
            // looking at, and lights up when you can respond on someone else's turn.
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).testTag("bottom-bar"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                PassButton(
                    if (argentumEnabled && passToMyTurnFrom != null) "AUTO-PASSING · STOP" else if (argentumEnabled) argentumPrimaryLabel ?: "WAITING" else "PASS TURN",
                    onClick = { run { if (argentumEnabled && passToMyTurnFrom != null) togglePassToMyTurn() else if (argentumEnabled) argentumPrimary() else passTurn() } },
                    enabled = if (argentumEnabled && passToMyTurnFrom != null) true else if (argentumEnabled) argentumPrimaryLabel != null && !argentumAwaitingUpdate else canPassTurnFromBoard,
                    canRespond = argentumCanRespond,
                    modifier = Modifier.weight(1f).height(footerControlHeight)
                )
                Box(Modifier.weight(1.3f)) {
                    HandPeekBar(
                        players[if (argentumEnabled) localSeat.coerceIn(players.indices) else selectedPlayer].hand.size,
                        modifier = Modifier.fillMaxWidth().height(handControlHeight).testTag("hand-peek"),
                        onClick = { run { if (canControlSelectedPlayer || argentumEnabled) showHand = true } }
                    )
                }
                // Same box style as the pass button, so both fill their full height and line up.
                PassButton(
                    if (argentumEnabled) "TABLE" else "BACK TO TABLE", onClick = { run { showOverview = true; showHand = false } },
                    enabled = true, canRespond = false,
                    modifier = Modifier.weight(1f).height(footerControlHeight)
                )
            }
        }
    }

        @Composable
        fun TableOverlays() {
            Box(Modifier.fillMaxSize()) {
        if (argentumEnabled && !homeOpen) TurnBanner(turnBanner)
        if (argentumEnabled && !homeOpen) PlayFeedOverlay(playPopup, playFeed, playBanner, recapText,
            onDismissPopup = { playPopup = null }, onPreview = { playPopup = it })
        if (homeOpen) {
            val deckChoices = remember(savedDecks, premadeDecks) {
                savedDecks.map { DeckChoice(it.name, it.name, it.commander ?: parseDeckList(it.text).firstOrNull { e -> e.commander }?.name, builtIn = false) } +
                    premadeDecks.map { DeckChoice("builtin:${it.id}", it.name, it.commander, builtIn = true) }
            }
            val seat = players.getOrNull(localSeat)
            val activeDeck = deckChoices.firstOrNull { it.id == activeDeckId }
                ?: seat?.takeIf { it.deckLoaded }?.let { DeckChoice("table", it.commanderName ?: "Your table deck", it.commanderName, builtIn = false) }
            val art = activeDeck?.commander?.let { name ->
                seat?.commanderCard?.takeIf { it.name.equals(name, true) }?.let { it.artCropUrl ?: it.imageUrl }
                    ?: "https://api.scryfall.com/cards/named?exact=${java.net.URLEncoder.encode(name, "UTF-8")}&format=image&version=art_crop"
            }
            ArcanaHomeScreen(
                wide = screenWidthDp >= 700,
                deck = activeDeck, commanderArt = art,
                argentumActive = argentumEnabled,
                working = argentumDeckValidationRunning || offlineStarting,
                workingLabel = if (offlineStarting) "PREPARING OFFLINE GAME…" else "CHECKING DECK WITH ARGENTUM…",
                resumeAvailable = argentumResumeAvailable && !argentumEnabled,
                onResume = {
                    recordDiagnostic("Player chose to resume the previous Argentum game", DiagnosticCategory.CONNECTION)
                    argentumResumeRequested = true
                    argentumLocalPlayersBackup = players.toList()
                    argentumEnabled = true
                    homeOpen = false
                    argentumSocket.retryNow()
                },
                onDismissResume = {
                    recordDiagnostic("Player chose to end the previous Argentum game", DiagnosticCategory.CONNECTION)
                    argentumEndPreviousRequested = true
                    argentumSocket.connect(lobbyPlayerName)
                },
                onPlay = { playSheetOpen = true },
                onChangeDeck = { deckPickerOpen = true },
                onImport = { selectedPlayer = localSeat; importError = null; importOpen = true },
                onSettings = { optionsOpen = true },
                onReturnToTable = { run { homeOpen = false } },
            )
            if (playSheetOpen) PlaySheet(
                deckName = activeDeck?.name,
                aiDecks = premadeDecks.map { DeckChoice(it.id, it.name, it.commander, builtIn = true) },
                aiDeckId = offlineAiDeckId,
                onAiDeck = { offlineAiDeckId = it },
                onOffline = { playSheetOpen = false; run { startOfflineGame() } },
                onOnline = { playSheetOpen = false; run { startArgentumPractice(vsAi = true) } },
                onCreateRoom = { playSheetOpen = false; run { startArgentumPractice(vsAi = false) } },
                onJoinRoom = { playSheetOpen = false; run { argentumLastError = null; argentumJoinCode = ""; argentumJoinDialogOpen = true } },
                onDismiss = { playSheetOpen = false },
            )
            if (deckPickerOpen) DeckPickerSheet(deckChoices, activeDeckId,
                onPick = { choice ->
                    deckPickerOpen = false
                    activeDeckId = choice.id; saveActiveDeck(context, choice.id)
                    recordDiagnostic("Deck chosen: ${choice.name}", DiagnosticCategory.ACTION)
                    // Also put it on your table (card art, online play). Offline games read the choice directly.
                    val text = savedDecks.firstOrNull { it.name == choice.id }?.text
                        ?: premadeDecks.firstOrNull { "builtin:${it.id}" == choice.id }?.let { d -> "Commander\n1 ${d.commander}\nDeck\n" + d.cards.filterNot { it == d.commander }.groupingBy { it }.eachCount().entries.joinToString("\n") { (n, c) -> "$c $n" } }
                    if (text != null) { selectedPlayer = localSeat; importDeck(text, saveAsMine = false) }
                },
                onImport = { deckPickerOpen = false; selectedPlayer = localSeat; importError = null; importOpen = true },
                onDismiss = { deckPickerOpen = false })
        }

    if (argentumEnabled && argentumSnapshot == null && !homeOpen) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text("ARGENTUM GAME", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
                Text(
                    when {
                        argentumStatus == ArgentumConnectionStatus.CONNECTING -> "Connecting to Argentum…"
                        argentumStatus == ArgentumConnectionStatus.REPLACED -> "This Argentum session was opened on another device."
                        argentumStatus == ArgentumConnectionStatus.DISCONNECTED -> "Connection lost. Arcana will retry automatically."
                        argentumStatus == ArgentumConnectionStatus.PAUSED -> "Paused while Arcana was in the background. Tap Reconnect when you're ready."
                        argentumInbound?.optString("type") == "quickGameLobbyState" -> if (argentumVsAiLobby) "Your practice table is ready when Argentum finishes preparing it." else "Your room is open. Share its code so other players can join."
                        else -> if (argentumVsAiLobby) "Connected. Creating your practice table…" else "Connected. Creating your Argentum room…"
                    },
                    color = SoftWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)
                )
                val lobbyState = argentumInbound?.takeIf { it.optString("type") == "quickGameLobbyState" }
                val lobbyId = lobbyState?.optString("lobbyId")
                if (!lobbyId.isNullOrBlank()) {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(18.dp)) {
                        Text("ARGENTUM ROOM CODE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(lobbyId, color = CounterGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (lobbyState.optBoolean("vsAi", argentumVsAiLobby).not()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Argentum room code", lobbyId))
                                    message = "Argentum room code copied."
                                },
                                modifier = Modifier.padding(top = 10.dp).heightIn(min = 44.dp)
                            ) { Text("COPY ROOM CODE") }
                        }
                    }
                }
                val lobbyPlayers = lobbyState?.optJSONArray("players")
                if (lobbyPlayers != null) {
                    Text("PLAYERS · ${lobbyPlayers.length()}/${if (lobbyState?.optBoolean("vsAi", argentumVsAiLobby) == true) 2 else 4}", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
                    for (index in 0 until lobbyPlayers.length()) {
                        val lobbyPlayer = lobbyPlayers.optJSONObject(index) ?: continue
                        val status = when {
                            lobbyPlayer.optBoolean("isAi") && lobbyPlayer.optBoolean("ready") -> "ARGENTUM AI · READY"
                            lobbyPlayer.optBoolean("ready") -> "DECK READY"
                            lobbyPlayer.optBoolean("deckSelected") -> "DECK SELECTED"
                            else -> "CHOOSING A DECK"
                        }
                        Text("${lobbyPlayer.optString("playerName", "PLAYER")} · $status · ${lobbyPlayer.optInt("deckCardCount")} cards", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Text(
                    "Your Arcana table and card presentation stay here. Argentum validates the deck and controls every game action.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp)
                )
                if (argentumLastError != null) Text(argentumLastError!!, color = Color(0xFFFFB4A8), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { argentumSocket.retryNow() }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("RECONNECT") }
                    OutlinedButton(onClick = {
                        argentumSocket.leaveQuickGameLobby()
                        argentumSocket.close()
                        argentumEnabled = false
                        setArgentumGameActive(false)
                        argentumSnapshot = null
                        argentumPendingDecision = null
                        argentumLocalPlayersBackup?.let { players = it; argentumLocalPlayersBackup = null }
                        argentumDeckForLobby = null
                        argentumCommanderForLobby = null
                        argentumLobbySubmitted = false
                        gameStartedOnline = false
                        lobbyOpen = false
                        gameEndedOnline = false
                        gameTimerRunning = false
                        turnTimerRunning = false
                        homeOpen = true
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("LEAVE") }
                }
            }
        }
    }

    if (argentumJoinDialogOpen) AlertDialog(
        onDismissRequest = { argentumJoinDialogOpen = false },
        title = { Text("Join Argentum room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the room code shared by the host. Arcana will check your deck with Argentum before joining.")
                OutlinedTextField(
                    value = argentumJoinCode,
                    onValueChange = { argentumJoinCode = it.trim() },
                    label = { Text("Room code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = argentumJoinCode.isNotBlank() && !argentumDeckValidationRunning,
                onClick = {
                    val code = argentumJoinCode
                    argentumJoinDialogOpen = false
                    startArgentumPractice(vsAi = false, joinLobbyCode = code)
                }
            ) { Text(if (argentumDeckValidationRunning) "CHECKING…" else "CHECK DECK & JOIN") }
        },
        dismissButton = { TextButton(onClick = { argentumJoinDialogOpen = false }) { Text("CANCEL") } }
    )

    if (argentumLastError != null && argentumSnapshot == null && homeOpen) AlertDialog(
        onDismissRequest = { argentumLastError = null },
        title = { Text("Argentum deck check") },
        text = { Text(argentumLastError.orEmpty()) },
        confirmButton = { TextButton(onClick = { argentumLastError = null }) { Text("OK") } }
    )

    /** Ends this Argentum session on the client and restores the local table. */
    fun closeArgentumSession(concede: Boolean) {
        if (concede) { if (argentumSnapshot == null) argentumSocket.leaveQuickGameLobby() else if (!gameEndedOnline) argentumSocket.concede() }
        argentumSocket.close()
        argentumEnabled = false
        argentumOffline = false
        setArgentumGameActive(false)
        argentumSnapshot = null
        argentumPendingDecision = null
        argentumActionChoices = emptyList(); argentumActionCard = null; argentumComposer = null
        argentumAttackPlan = null; argentumBlockPlan = null
        playPopup = null; playFeed = emptyList(); playBanner = null; recapText = null; aiRecap = -1 to TurnRecap(); argentumStackUse = null; argentumAutoYes = emptyMap()
        argentumLocalPlayersBackup?.let { players = it; argentumLocalPlayersBackup = null }
        argentumDeckForLobby = null
        argentumCommanderForLobby = null
        argentumLobbySubmitted = false
        gameStartedOnline = false
        gameEndedOnline = false
        gameTimerRunning = false
        turnTimerRunning = false
        homeOpen = true
        showOverview = true
        showHand = false
    }

    // Game over: result, then play again / look at the final board / go home.
    if (argentumEnabled && gameEndedOnline && !argentumGameOverDismissed) {
        val won = argentumWon ?: argentumSnapshot?.winnerName?.let { it == players.getOrNull(localSeat)?.name }
        AlertDialog(
            onDismissRequest = { argentumGameOverDismissed = true },
            title = { Text(when (won) { true -> "Victory"; false -> "Defeat"; null -> "Game over" }, fontWeight = FontWeight.Black, color = when (won) { true -> Mint; false -> Color(0xFFFFB4A8); null -> SoftWhite }) },
            text = {
                Column {
                    Text(argentumGameOverText ?: argentumSnapshot?.winnerName?.let { "$it won." } ?: "The game has ended.", color = SoftWhite)
                    Text("Turn ${argentumSnapshot?.turnNumber ?: turnNumber} · ${formatClock(elapsedGameSeconds)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = {
                recordDiagnostic("Player chose Play again", DiagnosticCategory.ACTION)
                val wasOffline = argentumOffline
                closeArgentumSession(concede = false)
                if (wasOffline) startOfflineGame() else startArgentumPractice(vsAi = true)
            }) { Text("PLAY AGAIN") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { argentumGameOverDismissed = true }) { Text("VIEW BOARD") }
                    TextButton(onClick = { closeArgentumSession(concede = false) }) { Text("HOME") }
                }
            }
        )
    }

    if (argentumLeaveConfirm) AlertDialog(
        onDismissRequest = { argentumLeaveConfirm = false },
        title = { Text("Leave Argentum game?") },
        text = { Text(if (argentumSnapshot == null) "This will leave the Argentum room." else "Leaving a live game concedes the match to Argentum.") },
        confirmButton = { TextButton(onClick = {
            closeArgentumSession(concede = true)
            argentumLeaveConfirm = false
        }) { Text("LEAVE GAME") } },
        dismissButton = { TextButton(onClick = { argentumLeaveConfirm = false }) { Text("CANCEL") } }
    )

    if (argentumMulliganPrompt && argentumSnapshot == null) ArgentumOpeningHandScreen(
        cards = players.getOrNull(localSeat)?.hand.orEmpty(),
        title = "Opening hand",
        subtitle = argentumMulliganInfo?.let { (count, onPlay, _) ->
            listOfNotNull(if (onPlay) "You're on the play" else "You're on the draw", if (count > 0) "Mulligan $count · you'll put $count on the bottom after keeping" else null).joinToString(" · ")
        } ?: "Argentum dealt your opening seven.",
        selectable = false, selection = emptySet(), onToggle = {},
        primaryLabel = "KEEP ${players.getOrNull(localSeat)?.hand?.size ?: 7}", primaryEnabled = !argentumAwaitingUpdate,
        onPrimary = { if (argentumSocket.keepHand()) { argentumMulliganPrompt = false; argentumAwaitingUpdate = true; recordDiagnostic("Kept opening hand", DiagnosticCategory.ACTION) } },
        secondaryLabel = "MULLIGAN", secondaryEnabled = !argentumAwaitingUpdate,
        onSecondary = { if (argentumSocket.mulligan()) { argentumAwaitingUpdate = true; recordDiagnostic("Took a mulligan", DiagnosticCategory.ACTION) } },
    )

    argentumBottomCardIds?.let { ids ->
        val idFor = { serverId: String -> (serverId.hashCode() and 0x7fffffff).coerceAtLeast(1) }
        val hand = players.getOrNull(localSeat)?.hand.orEmpty()
        ArgentumOpeningHandScreen(
            cards = ids.mapNotNull { id -> hand.firstOrNull { it.id == idFor(id) } },
            title = "Put ${argentumBottomCardCount} on the bottom",
            subtitle = "Tap ${if (argentumBottomCardCount == 1) "the card" else "$argentumBottomCardCount cards"} to put on the bottom of your library.",
            selectable = true,
            selection = argentumBottomSelection.map(idFor).toSet(),
            onToggle = { card ->
                val serverId = ids.firstOrNull { idFor(it) == card.id } ?: return@ArgentumOpeningHandScreen
                argentumBottomSelection = if (serverId in argentumBottomSelection) argentumBottomSelection - serverId
                    else if (argentumBottomSelection.size < argentumBottomCardCount) argentumBottomSelection + serverId else argentumBottomSelection
            },
            primaryLabel = "CONFIRM ${argentumBottomSelection.size}/$argentumBottomCardCount",
            primaryEnabled = argentumBottomSelection.size == argentumBottomCardCount && !argentumAwaitingUpdate,
            onPrimary = { if (argentumSocket.chooseBottomCards(argentumBottomSelection.toList())) { argentumBottomCardIds = null; argentumAwaitingUpdate = true } },
            secondaryLabel = null, secondaryEnabled = false, onSecondary = {},
        )
    }

    if (argentumActionChoices.isNotEmpty()) ArgentumActionSheet(
        card = argentumActionCard,
        choices = ArgentumActions.visibleChoices(argentumActionChoices),
        hidManaAbilities = argentumActionChoices.any { ArgentumActions.isManaAbility(it) && !ArgentumActions.isSacrificeMana(it) },
        busy = argentumAwaitingUpdate,
        stackCount = { info -> StackUses.count(info, argentumSnapshot?.legalActions.orEmpty()) { id -> argentumPiece(id).name } },
        onChooseMany = { info, count ->
            val source = info.optJSONObject("action")?.optString("sourceId").orEmpty()
            argentumStackUse = StackUse(argentumPiece(source).name, info.optString("description"), remaining = count - 1)
            argentumActionChoices = emptyList(); argentumActionCard = null; chooseArgentumLegalAction(info)
        },
        onChoose = { info -> argentumActionChoices = emptyList(); argentumActionCard = null; argentumStackUse = null; chooseArgentumLegalAction(info) },
        onDismiss = { argentumActionChoices = emptyList(); argentumActionCard = null }
    )

    // Combat damage is split on the combat board rather than in a dialog.
    argentumPendingDecision?.takeIf { it.optString("type") == "CombatResolutionDecision" && it.optString("id") != argentumDeferredDecisionId }?.let { decision ->
        ArgentumCombatResolution.from(decision)?.let { board ->
            ArgentumDamageBoard(board, decision.optString("playerId"), ::argentumPiece, argentumAmounts, busy = argentumAwaitingUpdate,
                onChange = { argentumAmounts = it },
                onConfirm = { submitArgentumDecision(board.response(decision.optString("playerId"), argentumAmounts)) },
                onDismiss = { argentumDeferredDecisionId = decision.optString("id") })
        }
    }
    argentumPendingDecision?.takeUnless { it.optString("type") == "PlayCardDecision" || it.optString("id") == argentumDeferredDecisionId ||
        (it.optString("type") == "CombatResolutionDecision" && ArgentumCombatResolution.from(it) != null) }?.let { decision ->
        val decisionId = decision.optString("id")
        val kind = decision.optString("type")
        val prompt = decision.optString("prompt", "Argentum needs a choice")
        AlertDialog(
            onDismissRequest = {},
            title = { Text(prompt) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    when (kind) {
                        "YesNoDecision" -> Text(decision.optString("hint").takeIf(String::isNotBlank).orEmpty(), color = Muted)
                        "ChooseOptionDecision" -> (decision.optJSONArray("options") ?: JSONArray()).let { options ->
                            for (index in 0 until options.length()) {
                                val option = options.optString(index)
                                TextButton(onClick = {
                                    submitArgentumDecision(JSONObject().put("type", "OptionChosenResponse").put("optionIndex", index))
                                }, modifier = Modifier.fillMaxWidth()) { Text(option, color = Mint) }
                            }
                        }
                        "ChooseNumberDecision" -> {
                            val min = decision.optInt("minValue", 0)
                            val max = decision.optInt("maxValue", min)
                            var chosenNumber by remember(decision.optString("id")) { mutableIntStateOf(min) }
                            NumberPicker(chosenNumber, min..maxOf(min, max), onChange = { chosenNumber = it })
                            TextButton(onClick = {
                                submitArgentumDecision(JSONObject().put("type", "NumberChosenResponse").put("number", chosenNumber))
                            }, modifier = Modifier.fillMaxWidth()) { Text("CHOOSE $chosenNumber", color = Mint, fontWeight = FontWeight.Black) }
                        }
                        "ChooseColorDecision" -> {
                            val colors = decision.optJSONArray("availableColors") ?: JSONArray()
                            for (index in 0 until colors.length()) {
                                val color = colors.optString(index)
                                val multi = decision.optInt("maxColors", 1) > 1
                                Row(Modifier.fillMaxWidth().clickable {
                                    argentumDecisionColors = if (multi && color in argentumDecisionColors) argentumDecisionColors - color
                                        else if (multi) argentumDecisionColors + color else setOf(color)
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (multi) Checkbox(checked = color in argentumDecisionColors, onCheckedChange = { checked ->
                                        argentumDecisionColors = if (checked) argentumDecisionColors + color else argentumDecisionColors - color
                                    }) else RadioButton(selected = color in argentumDecisionColors, onClick = { argentumDecisionColors = setOf(color) })
                                    Text(color, color = SoftWhite)
                                }
                            }
                        }
                        "SelectCardsDecision" -> {
                            val options = decision.optJSONArray("options") ?: JSONArray()
                            val limits = decision.optJSONObject("selectionLimits")
                            val cardInfo = decision.optJSONObject("cardInfo")
                            for (index in 0 until options.length()) {
                                val id = options.optString(index)
                                val target = argentumSnapshot?.targets?.get(id)
                                val name = target?.name ?: cardInfo?.optJSONObject(id)?.optString("name") ?: id.takeLast(6)
                                val cap = limits?.optInt(id, 1) ?: 1
                                Row(Modifier.fillMaxWidth().clickable {
                                    argentumDecisionSelection = if (id in argentumDecisionSelection) argentumDecisionSelection - id
                                        else if (argentumDecisionSelection.size < decision.optInt("maxSelections", 1) && (argentumDecisionSelection.count { limits?.optInt(it, 1) == cap } < cap)) argentumDecisionSelection + id
                                        else argentumDecisionSelection
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = id in argentumDecisionSelection, onCheckedChange = { checked ->
                                        argentumDecisionSelection = if (checked) argentumDecisionSelection + id else argentumDecisionSelection - id
                                    })
                                    Text(name, color = SoftWhite, fontSize = 12.sp)
                                }
                            }
                            Text("Choose ${decision.optInt("minSelections", 0)}–${decision.optInt("maxSelections", 1)} card(s).", color = Muted, fontSize = 11.sp)
                        }
                        "ChooseTargetsDecision" -> {
                            val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                            val legalTargets = decision.optJSONObject("legalTargets")
                            for (requirementPosition in 0 until requirements.length()) {
                                val requirementValue = requirements.opt(requirementPosition)
                                val requirement = requirementValue as? JSONObject ?: continue
                                val requirementIndex = requirement.optInt("index", requirementPosition)
                                val chosen = argentumDecisionTargets[requirementIndex].orEmpty()
                                val maxTargets = requirement.optInt("maxTargets", 1)
                                Text(requirement.optString("description", "Choose target(s)"), color = Mint, fontSize = 12.sp)
                                val prefixPools = requirement.optJSONObject("legalTargetsByPrefix")
                                val prefixLength = requirement.optInt("prefixLength", requirementIndex)
                                val prefixKey = (0 until prefixLength).joinToString(",") { priorIndex ->
                                    argentumDecisionTargets[priorIndex].orEmpty().joinToString(",")
                                }
                                val ids = prefixPools?.optJSONArray(prefixKey)
                                    ?: legalTargets?.optJSONArray(requirementIndex.toString())
                                    ?: JSONArray()
                                val selectionGroups = requirement.optJSONArray("selectionGroups")
                                val currentSet = chosen.toSet()
                                for (index in 0 until ids.length()) {
                                    val id = ids.optString(index)
                                    val groupAllows = selectionGroups == null || selectionGroups.length() == 0 ||
                                        (0 until selectionGroups.length()).any { groupIndex ->
                                            val group = selectionGroups.optJSONArray(groupIndex) ?: return@any false
                                            (0 until group.length()).map { group.optString(it) }.containsAll(currentSet + id)
                                        }
                                    val checked = id in chosen
                                    val enabled = checked || (chosen.size < maxTargets && groupAllows)
                                    val label = argentumSnapshot?.targets?.get(id)?.name ?: id.takeLast(6)
                                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                                        argentumDecisionTargets = argentumDecisionTargets.toMutableMap().apply {
                                            put(requirementIndex, if (checked) chosen - id else chosen + id)
                                        }
                                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = checked, enabled = enabled, onCheckedChange = { checkedNow ->
                                            argentumDecisionTargets = argentumDecisionTargets.toMutableMap().apply {
                                                put(requirementIndex, if (checkedNow) chosen + id else chosen - id)
                                            }
                                        })
                                        Text(label, color = if (enabled) SoftWhite else Muted, fontSize = 12.sp)
                                    }
                                }
                                Text("Choose ${requirement.optInt("minTargets", 0)}–$maxTargets target(s).", color = Muted, fontSize = 11.sp)
                            }
                        }
                        "ChooseModeDecision" -> {
                            val modes = decision.optJSONArray("modes") ?: JSONArray()
                            val maxModes = decision.optInt("maxModes", 1)
                            for (position in 0 until modes.length()) {
                                val mode = modes.optJSONObject(position) ?: continue
                                val index = mode.optInt("index", position)
                                val available = mode.optBoolean("available", true)
                                val on = index in argentumModeSelection
                                Row(Modifier.fillMaxWidth().clickable(enabled = available) {
                                    argentumModeSelection = if (on) argentumModeSelection - index else if (maxModes == 1) setOf(index) else if (argentumModeSelection.size < maxModes) argentumModeSelection + index else argentumModeSelection
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (maxModes == 1) RadioButton(selected = on, enabled = available, onClick = { argentumModeSelection = setOf(index) })
                                    else Checkbox(checked = on, enabled = available, onCheckedChange = { argentumModeSelection = if (it) argentumModeSelection + index else argentumModeSelection - index })
                                    Text(mode.optString("text", "Mode ${index + 1}"), color = if (available) SoftWhite else Muted, fontSize = 12.sp)
                                }
                            }
                            Text("Choose ${decision.optInt("minModes", 1)}–$maxModes mode(s).", color = Muted, fontSize = 11.sp)
                        }
                        "SearchLibraryDecision" -> {
                            val options = decision.optJSONArray("options") ?: JSONArray()
                            Text(decision.optString("filterDescription"), color = Muted, fontSize = 11.sp)
                            if (options.length() == 0) Text("No matching cards. Confirm to finish the search.", color = Muted, fontSize = 12.sp)
                            for (index in 0 until options.length()) {
                                val id = options.optString(index)
                                val on = id in argentumDecisionSelection
                                Row(Modifier.fillMaxWidth().clickable {
                                    argentumDecisionSelection = if (on) argentumDecisionSelection - id else if (argentumDecisionSelection.size < decision.optInt("maxSelections", 1)) argentumDecisionSelection + id else argentumDecisionSelection
                                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = on, onCheckedChange = null)
                                    Text(ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name }, color = SoftWhite, fontSize = 12.sp)
                                }
                            }
                            Text("Choose ${decision.optInt("minSelections", 0)}–${decision.optInt("maxSelections", 1)} card(s).", color = Muted, fontSize = 11.sp)
                        }
                        "OrderObjectsDecision", "ReorderLibraryDecision" -> ArgentumOrderEditor(
                            items = argentumOrderSelection,
                            nameOf = { id -> ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name } },
                            firstLabel = decision.optString("firstLabel").takeIf { it.isNotBlank() && it != "null" },
                            lastLabel = decision.optString("lastLabel").takeIf { it.isNotBlank() && it != "null" },
                            onChange = { argentumOrderSelection = it }
                        )
                        "SelectManaSourcesDecision" -> Text("Pay ${decision.optString("requiredCost")}. Argentum can choose the mana sources for you.", color = SoftWhite, fontSize = 12.sp)
                        "AssignDamageDecision" -> {
                            val ids = (decision.optJSONArray("orderedTargets") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } } +
                                listOfNotNull(decision.optString("defenderId").takeIf { it.isNotBlank() && it != "null" })
                            val minimums = decision.optJSONObject("minimumAssignments")
                            Text("${argentumName(decision.optString("attackerId"))} assigns ${decision.optInt("availablePower")} damage" + (if (decision.optBoolean("hasTrample")) " (trample)" else "") + (if (decision.optBoolean("hasDeathtouch")) " (deathtouch)" else "") + ".", color = Muted, fontSize = 11.sp)
                            ArgentumAmountEditor(ids.distinct(), ::argentumName, argentumAmounts, decision.optInt("availablePower"),
                                minEach = ids.associateWith { minimums?.optInt(it, 0) ?: 0 }, maxEach = emptyMap(), onChange = { argentumAmounts = it })
                        }
                        "SplitPilesDecision" -> ArgentumPileEditor(ArgentumPileSplit(decision), { id -> ArgentumDecisions.cardName(decision, id) { argentumSnapshot?.targets?.get(it)?.name } }, argentumAmounts) { argentumAmounts = it }
                        "ChooseReplacementDecision" -> ArgentumReplacementEditor(ArgentumReplacementChoice(decision), argentumReplacementPick.first, argentumReplacementPick.second) { f, t -> argentumReplacementPick = f to t }
                        "BudgetModalDecision" -> ArgentumBudgetEditor(ArgentumBudgetModes(decision), argentumBudgetCounts) { argentumBudgetCounts = it }
                        "CombatResolutionDecision" -> ArgentumCombatResolution.from(decision)?.let { board ->
                            ArgentumCombatDamageEditor(board, decision.optString("playerId"), argentumAmounts, onChange = { argentumAmounts = it })
                        }
                        "DistributeDecision" -> {
                            val ids = (decision.optJSONArray("targets") ?: JSONArray()).let { t -> (0 until t.length()).map { t.optString(it) } }
                            val caps = decision.optJSONObject("maxPerTarget")
                            ArgentumAmountEditor(ids, ::argentumName, argentumAmounts, decision.optInt("totalAmount"),
                                minEach = ids.associateWith { decision.optInt("minPerTarget", 0) },
                                maxEach = ids.associateWith { caps?.optInt(it, decision.optInt("totalAmount")) ?: decision.optInt("totalAmount") },
                                onChange = { argentumAmounts = it })
                        }
                        else -> {
                            val reason = ArgentumDecisions.needsHandlingReason(decision) ?: "Arcana cannot present this choice."
                            Text("NEEDS HANDLING", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(reason, color = SoftWhite, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            Text("Nothing has been chosen for you. You can " + (if (ArgentumDecisions.canCancel(decision)) "cancel this if Argentum allows it, " else "") +
                                "refresh in case Argentum sends a different prompt, or hide this to inspect the table with Arcana's viewing tools (the game waits for this choice). The event is in the diagnostic log.",
                                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                when (kind) {
                    "YesNoDecision" -> Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true)) }) { Text(decision.optString("yesText", "YES")) }
                        TextButton(onClick = {
                            argentumAutoYes = argentumAutoYes + (autoYesKey(decision) to (argentumSnapshot?.turnNumber ?: 0))
                            submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true))
                        }) { Text("${decision.optString("yesText", "YES").uppercase()} · ALWAYS THIS TURN", fontSize = 12.sp) }
                    }
                    "BatchYesNoDecision" -> Row {
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", true).put("applyToAll", false)) }) { Text("${decision.optString("yesText", "YES")} · ONCE") }
                        TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", true).put("applyToAll", true)) }) { Text("${decision.optString("yesText", "YES")} · ALL") }
                    }
                    "ChooseColorDecision" -> TextButton(enabled = argentumDecisionColors.isNotEmpty() && argentumDecisionColors.size <= decision.optInt("maxColors", 1), onClick = {
                        val colors = argentumDecisionColors.toList()
                        submitArgentumDecision(JSONObject().put("type", "ColorChosenResponse").put("color", colors.first()).put("colors", JSONArray(colors)))
                    }) { Text("CONFIRM") }
                    "SelectCardsDecision" -> TextButton(enabled = argentumDecisionSelection.size in decision.optInt("minSelections", 0)..decision.optInt("maxSelections", 1), onClick = {
                        submitArgentumDecision(JSONObject().put("type", "CardsSelectedResponse").put("selectedCards", JSONArray(argentumDecisionSelection.toList())))
                    }) { Text("CONFIRM") }
                    "ChooseTargetsDecision" -> TextButton(enabled = run {
                        val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                        (0 until requirements.length()).all { position ->
                            val requirement = requirements.optJSONObject(position) ?: return@all false
                            val index = requirement.optInt("index", position)
                            val count = argentumDecisionTargets[index].orEmpty().size
                            count in requirement.optInt("minTargets", 0)..requirement.optInt("maxTargets", 1)
                        }
                    }, onClick = {
                        val requirements = decision.optJSONArray("targetRequirements") ?: JSONArray()
                        val targets = JSONObject()
                        for (position in 0 until requirements.length()) {
                            val requirement = requirements.optJSONObject(position) ?: continue
                            val index = requirement.optInt("index", position)
                            targets.put(index.toString(), JSONArray(argentumDecisionTargets[index].orEmpty()))
                        }
                        submitArgentumDecision(JSONObject().put("type", "TargetsResponse").put("selectedTargets", targets))
                        argentumDecisionTargets = emptyMap()
                    }) { Text("CONFIRM TARGET") }
                    "ChooseModeDecision" -> TextButton(enabled = argentumModeSelection.size in decision.optInt("minModes", 1)..decision.optInt("maxModes", 1), onClick = {
                        submitArgentumDecision(ArgentumDecisions.modesResponse(argentumModeSelection.sorted()))
                    }) { Text("CONFIRM") }
                    "SearchLibraryDecision" -> TextButton(enabled = argentumDecisionSelection.size in decision.optInt("minSelections", 0)..decision.optInt("maxSelections", 1), onClick = {
                        submitArgentumDecision(ArgentumDecisions.cardsSelectedResponse(argentumDecisionSelection))
                    }) { Text("CONFIRM") }
                    "OrderObjectsDecision", "ReorderLibraryDecision" -> TextButton(enabled = argentumOrderSelection.isNotEmpty(), onClick = {
                        submitArgentumDecision(ArgentumDecisions.orderedResponse(argentumOrderSelection))
                    }) { Text("CONFIRM ORDER") }
                    "SelectManaSourcesDecision" -> TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.autoPayManaResponse()) }) { Text("AUTO-PAY") }
                    "AssignDamageDecision" -> TextButton(enabled = ArgentumDecisions.damageAssignmentComplete(decision, argentumAmounts), onClick = {
                        submitArgentumDecision(ArgentumDecisions.damageAssignmentResponse(argentumAmounts.filterValues { it > 0 }))
                    }) { Text("ASSIGN") }
                    "SplitPilesDecision" -> ArgentumPileSplit(decision).let { split ->
                        TextButton(enabled = split.problem(argentumAmounts) == null, onClick = { submitArgentumDecision(split.response(argentumAmounts)) }) { Text("CONFIRM PILES") }
                    }
                    "ChooseReplacementDecision" -> TextButton(onClick = {
                        submitArgentumDecision(ArgentumReplacementChoice(decision).response(argentumReplacementPick.first, argentumReplacementPick.second))
                    }) { Text("CONFIRM") }
                    "BudgetModalDecision" -> ArgentumBudgetModes(decision).let { budget ->
                        TextButton(enabled = budget.spent(argentumBudgetCounts) <= budget.budget, onClick = { submitArgentumDecision(budget.response(argentumBudgetCounts)) }) { Text("CONFIRM") }
                    }
                    "CombatResolutionDecision" -> {
                        val board = ArgentumCombatResolution.from(decision)
                        TextButton(enabled = board != null && board.problem(argentumAmounts) == null, onClick = {
                            board?.let { submitArgentumDecision(it.response(decision.optString("playerId"), argentumAmounts)) }
                        }) { Text("CONFIRM DAMAGE") }
                    }
                    "DistributeDecision" -> TextButton(enabled = ArgentumDecisions.distributionComplete(decision, argentumAmounts), onClick = {
                        submitArgentumDecision(ArgentumDecisions.distributionResponse(argentumAmounts))
                    }) { Text("CONFIRM") }
                    else -> Row {
                        if (ArgentumDecisions.canCancel(decision)) TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.cancelResponse()) }) { Text("CANCEL CHOICE") }
                        TextButton(onClick = { recordDiagnostic("Player requested refresh for unhandled ${kind}", DiagnosticCategory.UNSUPPORTED); argentumSocket.requestResync() }) { Text("REFRESH") }
                    }
                }
            },
            dismissButton = {
                if (kind == "YesNoDecision") TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", false)) }) { Text(decision.optString("noText", "NO")) }
                else if (kind == "BatchYesNoDecision") Row {
                    TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", false).put("applyToAll", false)) }) { Text("${decision.optString("noText", "NO")} · ONCE") }
                    TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "BatchYesNoResponse").put("choice", false).put("applyToAll", true)) }) { Text("${decision.optString("noText", "NO")} · ALL") }
                }
                else if (kind == "SelectCardsDecision" && decision.optInt("minSelections", 0) == 0) TextButton(onClick = {
                    submitArgentumDecision(JSONObject().put("type", "CardsSelectedResponse").put("selectedCards", JSONArray()))
                }) { Text(decision.optString("declineLabel", "SELECT NONE")) }
                else if (kind == "SelectManaSourcesDecision" && decision.optBoolean("canDecline")) TextButton(onClick = { submitArgentumDecision(ArgentumDecisions.declineManaResponse()) }) { Text("DECLINE") }
                // Every decision can be set aside to look at the board; the status strip brings it back.
                if (kind !in setOf("YesNoDecision", "BatchYesNoDecision")) TextButton(onClick = { argentumDeferredDecisionId = decisionId }) { Text("VIEW TABLE") }
            }
        )
    }

    argentumAttackPlan?.let { plan ->
        ArgentumAttackBoard(plan, ::argentumPiece, busy = argentumAwaitingUpdate,
            onConfirm = { action ->
                val epoch = ArgentumActions.epochFor(plan.legalAction, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    recordDiagnostic("Declared ${action.optJSONObject("attackers")?.length() ?: 0} attacker(s)", DiagnosticCategory.ACTION)
                    argentumAttackPlan = null
                } else message = "Could not send the attack declaration. Refresh and try again."
            },
            onDismiss = { argentumAttackPlan = null })
    }
    argentumBlockPlan?.let { plan ->
        ArgentumBlockBoard(plan, ::argentumPiece, busy = argentumAwaitingUpdate,
            onConfirm = { action ->
                val epoch = ArgentumActions.epochFor(plan.legalAction, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    recordDiagnostic("Declared ${action.optJSONObject("blockers")?.length() ?: 0} blocker(s)", DiagnosticCategory.ACTION)
                    argentumBlockPlan = null
                } else message = "Could not send the block declaration. Refresh and try again."
            },
            onDismiss = { argentumBlockPlan = null })
    }
    argentumComposer?.let { plan ->
        ArgentumActionComposer(plan, ::argentumName, busy = argentumAwaitingUpdate,
            onConfirm = { picks, x, distribution, color ->
                val refs = argentumSnapshot?.targets.orEmpty()
                val action = plan.build(picks, x, distribution, color) { id -> refs[id]?.let(ArgentumActionPlan::chosenTarget) }
                val epoch = ArgentumActions.epochFor(plan.info, argentumSnapshot?.interactionEpoch)
                if (epoch != null && submitArgentumAction(action, epoch)) {
                    recordDiagnostic("Sent ${action.optString("type")} (${plan.description}) with ${plan.chosenTargets(picks).size} target(s)${x?.let { ", X=$it" }.orEmpty()}", DiagnosticCategory.ACTION)
                    argentumComposer = null
                } else message = "Could not send that to Argentum. Refresh and try again."
            },
            onDismiss = { argentumComposer = null; argentumStackUse = null })
    }
    argentumDeckReview?.let { review ->
        ArgentumDeckReviewDialog(review,
            onPlayWithout = {
                argentumDeckReview = null
                recordDiagnostic("Player chose to play on Argentum without ${review.unsupported.size} unsupported card(s)", DiagnosticCategory.UNSUPPORTED)
                startArgentumPractice(vsAi = review.vsAi, joinLobbyCode = review.joinCode, acceptSubstitutions = true)
            },
            onSandbox = {
                argentumDeckReview = null
                recordDiagnostic("Player chose the Arcana sandbox for a deck with ${review.unsupported.size} unsupported card(s)", DiagnosticCategory.UNSUPPORTED)
                resetGame(startImmediately = true)
                homeOpen = false
                showOverview = true
                message = "Arcana sandbox: Argentum is not running this game. Use the table tools to resolve cards yourself."
            },
            onEditDeck = { argentumDeckReview = null; selectedPlayer = localSeat; deckEditorOpen = true },
            onCancel = { argentumDeckReview = null })
    }
    if (diagnosticViewerOpen) DiagnosticLogDialog(diagnosticLog, diagnosticRevision,
        onExport = { selection -> diagnosticExportSelection = selection; exportDiagnosticLog() },
        onClear = { diagnosticLog.clear(); diagnosticRevision++; optionPrefs.edit().putString("diagnostic_events", "").apply(); recordDiagnostic("Diagnostic log cleared", DiagnosticCategory.APP) },
        onDismiss = { diagnosticViewerOpen = false })

    if (changelogOpen) ChangelogDialog(onDismiss = { changelogOpen = false })

    // The guide is layered over the real screen at the end of this composition.
    // Argentum games: the hand drawer always shows YOUR hand and opens from any table screen.
    val handSeat = if (argentumEnabled) localSeat.coerceIn(players.indices) else selectedPlayer
    val handTrayVisible = showHand && gameInProgress && (if (argentumEnabled) players.getOrNull(handSeat) != null else !showOverview && canControlSelectedPlayer)
    // While a combat board or another Argentum choice is open, the hand is for looking only.
    val handViewOnly = argentumEnabled && (argentumPendingDecision != null || argentumAttackPlan != null || argentumBlockPlan != null || argentumComposer != null)

    AnimatedVisibility(
        visible = handTrayVisible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showHand = false }
        ) {
            AnimatedVisibility(
                visible = handTrayVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                // Never shorter than the tray chrome (~172dp) plus one readable card row (~110dp),
                // which matters on short landscape screens.
                val effectiveHandFraction = handSheetHeightFraction.coerceAtLeast((282f / configuration.screenHeightDp.coerceAtLeast(1)).coerceIn(0.38f, 0.95f))
                Column(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(effectiveHandFraction)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Panel)
                        .navigationBarsPadding()
                        .onGloballyPositioned { handSheetBounds = it.boundsInRoot() }
                        .clickable { }
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(36.dp)
                            .pointerInput(configuration.screenHeightDp) {
                                var dragDistance = 0f
                                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
                                val dismissDistancePx = with(density) { 120.dp.toPx() }
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragDistance += amount
                                        handSheetHeightFraction = (handSheetHeightFraction - amount / screenHeightPx).coerceIn(0.38f, 0.95f)
                                    },
                                    onDragEnd = {
                                        optionPrefs.edit().putFloat("hand_sheet_height_fraction", handSheetHeightFraction).putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", handSheetHeightFraction).apply()
                                        if (dragDistance > dismissDistancePx && handSheetHeightFraction <= 0.40f) showHand = false
                                        dragDistance = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BottomSheetDefaults.DragHandle(color = Muted)
                    }
                    Text("DRAG UP TO EXPAND · DRAG DOWN TO SHRINK", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
                    HandSheet(
                        cards = players[handSeat].hand, isLandscape = isLandscape,
                        availableHeight = LocalConfiguration.current.screenHeightDp.dp * effectiveHandFraction,
                        stackCount = visibleStackCount,
                        highlightedCardId = highlightedHandCardId,
                        actionOrder = handActionOrderRaw.split('|').filter(String::isNotBlank),
                        handRevealed = players[handSeat].handRevealedToAll,
                        canAdvance = canControlSelectedPlayer && selectedPlayer == activePlayer && !players[activePlayer].isComputer,
                        canControl = canControlSelectedPlayer || argentumEnabled,
                        commanderCanCast = players[handSeat].commanderCard?.let { commander ->
                            val owner = players[handSeat]
                            (canControlSelectedPlayer || argentumEnabled) && !owner.isEliminated && !owner.library.any { it.id == commander.id } && !owner.hand.any { it.id == commander.id } && !owner.battlefield.any { it.id == commander.id } && !owner.graveyard.any { it.id == commander.id } && !owner.exile.any { it.id == commander.id } && !owner.revealed.any { it.id == commander.id } && stackActions.none { it.card?.id == commander.id }
                        } == true,
                        onToggleReveal = { revealed -> if (!argentumEnabled) {
                            updatePlayer(selectedPlayer) { it.copy(handRevealedToAll = revealed) }
                            logAction(selectedPlayer, if (revealed) "revealed their hand to everyone" else "hid their hand")
                        } },
                        onClose = { showHand = false },
                        onDraw = { if (!argentumEnabled) { drawCard(selectedPlayer); showHand = true } },
                        onNextStep = { if (argentumEnabled) argentumPrimary() else advanceStep() },
                        onPassTurn = { if (argentumEnabled) argentumPrimary() else passTurn() },
                        argentumMode = argentumEnabled,
                        argentumPrimaryLabel = argentumPrimaryLabel?.takeIf { !argentumAwaitingUpdate && passToMyTurnFrom == null },
                        onUndo = if (argentumEnabled && argentumUndoAvailable && !argentumAwaitingUpdate) ({ requestArgentumUndo() }) else null,
                        legalActionCount = ArgentumActions.visibleChoices(argentumSnapshot?.legalActions.orEmpty()).size,
                        arrangement = handArrangement,
                        cardScale = handCardScale,
                        playableIds = argentumPlayableIds,
                        onStack = { run { if (argentumEnabled) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else stackOpen = true } },
                        onCastCommander = {
                            val commander = players.getOrNull(handSeat)?.commanderCard
                            if (argentumEnabled && commander != null) openArgentumActions(commander)
                            else if (commander == null) message = "Set a commander from your deck or battlefield first."
                            else paymentCard = selectedPlayer to commander
                        },
                        onCombat = { run { if (argentumEnabled) run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } else if (canControlSelectedPlayer && selectedPlayer == activePlayer) combatDialogOpen = true } },
                        onMoreAction = { action ->
                            if (argentumEnabled) { if (action == "LOG") logOpen = true else run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } }
                            else if (action in setOf("LOG", "CHAT") || canControlSelectedPlayer) when (action) {
                                "TOKEN" -> { tokenTarget = selectedPlayer; tokenPickerOpen = true }
                                "REVEAL" -> {
                                    val player = players[selectedPlayer]
                                    if (player.library.isNotEmpty()) {
                                        updatePlayer(selectedPlayer) { it.copy(library = it.library.drop(1), revealed = it.revealed + player.library.first()) }
                                        logAction(selectedPlayer, "revealed the top card")
                                    }
                                }
                                "SHUFFLE" -> shuffleConfirmPlayer = selectedPlayer
                                "LOG" -> logOpen = true
                                "TRACKERS" -> trackerPlayer = selectedPlayer
                                "SEARCH" -> librarySearchPlayer = selectedPlayer
                                "CONCEDE" -> concedePlayer = selectedPlayer
                                "CHAT" -> chatOpen = true
                                "GRAVEYARD" -> zoneDialog = selectedPlayer to "Graveyard"
                                "EXILE" -> zoneDialog = selectedPlayer to "Exile"
                                "REVEALED" -> zoneDialog = selectedPlayer to "Revealed"
                            }
                        },
                        onSelect = { card -> run { if (handViewOnly) cardZoom.hold(card).also { cardZoom.release(false) } else if (argentumEnabled) openArgentumActions(card) else handActionCard = card } },
                        onDragDrop = { card, point ->
                            if (argentumEnabled) {
                                // A drag is only a visual gesture in Arcana. Ask Argentum
                                // for the card's legal actions instead of simulating a cast.
                                openArgentumActions(card)
                            } else {
                                val validTarget = battlefieldBounds?.contains(point) == true && handSheetBounds?.contains(point) != true
                                if (validTarget && canControlSelectedPlayer) {
                                    showHand = false
                                    performHandAction(card, "PLAY")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (deckEditorOpen) DeckEditorDialog(
        player = players[selectedPlayer],
        highlightedCardIds = importReviewCardIds,
        onRename = { card, name -> editDeckCard(selectedPlayer, card.id, name) },
        onRemove = { card -> editDeckCard(selectedPlayer, card.id, null, remove = true) },
        onAdd = { result -> updatePlayer(selectedPlayer) { p -> p.copy(library = p.library + makeCard(result.name, result.type, result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords)) } },
        onReplace = { card, result ->
            updatePlayer(selectedPlayer) { p ->
                fun replace(cards: List<GameCard>) = cards.map { current ->
                    if (current.id == card.id) current.copy(name = result.name, type = result.type, imageUrl = result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, importIssue = null, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords) else current
                }
                val commander = p.commanderCard?.let { current ->
                    if (current.id == card.id) current.copy(name = result.name, type = result.type, imageUrl = result.imageUrl, oracleText = result.oracleText, manaValue = result.manaValue, importIssue = null, artCropUrl = result.artCropUrl, manaCost = result.manaCost, power = result.power, toughness = result.toughness, keywords = result.keywords) else current
                }
                p.copy(library = replace(p.library), hand = replace(p.hand), commanderCard = commander, commanderName = commander?.name ?: p.commanderName)
            }
            importReviewCardIds = importReviewCardIds - card.id
        },
        onDismiss = { deckEditorOpen = false }
    )
    if (combatDialogOpen && !showOverview) CombatDialog(
        players = players, attackerIndex = activePlayer,
        onDismiss = { combatDialogOpen = false },
        onLog = { logAction(activePlayer, it) },
        onCombatDamage = { sourceCard, defender, amount ->
            if (amount > 0 && defender in players.indices) {
                val source = players[activePlayer]
                val isCommander = source.commanderCard?.id == sourceCard.id
                val sourceId = source.id
                val newCommanderDamage = (players[defender].commanderDamageReceived[sourceId] ?: 0) + if (isCommander) amount else 0
                val newLife = (players[defender].life - amount).coerceAtLeast(0)
                updatePlayer(defender) { p -> p.copy(
                    life = newLife,
                    commanderDamageReceived = if (isCommander) p.commanderDamageReceived + (sourceId to newCommanderDamage) else p.commanderDamageReceived
                ) }
                logAction(activePlayer, "dealt $amount combat damage to ${players.getOrNull(defender)?.name ?: "player"}")
                if (newLife == 0) eliminatePlayer(defender, "Life total reached 0")
                if (isCommander && newCommanderDamage >= 21) eliminatePlayer(defender, "21 commander damage from ${source.name}")
            }
        }
    )

            }
        }
        // TableOverlays is split in two so each half stays under the JVM's 64 KB method limit.
        @Composable
        fun TableOverlaysB() {
            Box(Modifier.fillMaxSize()) {
    fun makeRoomClient(hostAddress: String, code: String, password: String, preferredSeat: Int? = null): LanRoomClient {
        return LanRoomClient(
            onSeatAssigned = { seat -> scope.launch(Dispatchers.Main) {
                val previousSeat = localSeat
                localSeat = seat.coerceAtLeast(0)
                if (roomConnected) {
                    if (selectedPlayer == previousSeat) selectedPlayer = localSeat
                } else {
                    selectedPlayer = localSeat
                    lobbyCreated = false
                    lobbyJoinMode = false
                    lobbyRoomCode = code
                    lobbyPassword = password
                    roomConnected = true
                        connectionLost = false
                        connectionLostReason = null
                    onlineStatus = "Connected to host"
                    recordDiagnostic("Connected to host and assigned seat ${localSeat + 1}")
                    lobbyNotice = "Connected. Import your deck, choose Keep Hand or Mulligan, then mark yourself ready."
                }
            } },
            onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
            onReadyConfirmed = { seat, ready, accepted -> scope.launch(Dispatchers.Main) {
                recordDiagnostic("Host confirmed readiness: seat ${seat + 1}, ready=$ready, accepted=$accepted")
                if (seat == localSeat) {
                    if (accepted) lobbyReadyPlayers = if (ready) lobbyReadyPlayers + seat else lobbyReadyPlayers - seat
                    pendingReadyState = null
                    lobbyNotice = when {
                        !accepted -> "The host has not received your opening-hand choice yet. Wait a moment, then try readying up again."
                        ready -> "The host confirmed you are ready."
                        else -> "The host confirmed you are not ready."
                    }
                }
            } },
            onDeckStatusConfirmed = { seat, loaded -> scope.launch(Dispatchers.Main) {
                recordDiagnostic("Host confirmed deck status: seat ${seat + 1}, loaded=$loaded")
                if (seat == localSeat) {
                    localDeckSyncConfirmed = loaded
                    lobbyNotice = if (loaded) "The host confirmed your deck is loaded. Choose Keep Hand or Mulligan before readying up." else "The host confirmed your deck was removed."
                }
            } },
            onDisconnected = { reason -> scope.launch(Dispatchers.Main) {
                val interruptedSession = roomConnected || onlineStateReady || gameStartedOnline
                roomConnected = false
                onlineStatus = "Disconnected"
                pendingReadyState = null
                localDeckSyncConfirmed = false
                recordDiagnostic("Disconnected from host: $reason")
                lobbyNotice = reason
                if (interruptedSession) {
                    connectionLost = true
                    if (!reconnecting) connectionLostReason = reason
                    if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Connection interrupted", "Connection to the game host was lost. Open Arcana Table to reconnect.")
                }
                roomClient?.close()
                roomClient = null
            } },
            onHostTransfer = { source, targetSeat -> scope.launch(Dispatchers.Main) {
                val predecessor = roomClient ?: return@launch
                recordDiagnostic("Received host transfer offer")
                startTransferredHost(source, targetSeat, predecessor)
            } },
            onRedirect = { address, seat -> scope.launch(Dispatchers.Main) {
                val oldClient = roomClient
                oldClient?.close()
                lobbyHostInput = address
                roomConnected = false
                onlineStateReady = false
                onlineStatus = "Reconnecting to new host"
                lobbyNotice = "Host changed. Reconnecting to the room…"
                recordDiagnostic("Room redirected to the new host")
                val redirected = makeRoomClient(address, lobbyRoomCode, lobbyPassword, seat)
                roomClient = redirected
                redirected.connect(address, lobbyRoomCode, lobbyPassword, lobbyPlayerName, seat)
            } },
            onMigrationAccepted = { scope.launch(Dispatchers.Main) {
                if (lobbyCreated && localSeat == 0) {
                    roomClient?.close()
                    roomClient = null
                    roomConnected = true
                    onlineStateReady = true
                    onlineStatus = "Room host transferred"
                    lobbyNotice = "The room is yours. Other players are reconnecting."
                    recordDiagnostic("Host transfer confirmed by previous host")
                }
            } }
        )
    }

    fun makeCloudRoomClient(code: String, password: String, creating: Boolean, preferredSeat: Int? = null): CloudRoomClient {
        return CloudRoomClient(
            onSeatAssigned = { seat -> scope.launch(Dispatchers.Main) {
                localSeat = seat
                selectedPlayer = seat
                lobbyCreated = creating
                lobbyJoinMode = false
                lobbyRoomCode = code
                lobbyPassword = password
                roomConnected = true
                onlineStateReady = true
                connectionLost = false
                connectionLostReason = null
                onlineStatus = "Connected to internet room"
                lobbyNotice = if (creating) "Internet room is open. Share the room code and password." else "Connected. Import your deck, choose Keep Hand or Mulligan, then mark yourself ready."
                recordDiagnostic("Internet room connected; assigned seat ${seat + 1}")
            } },
            onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
            onReadyConfirmed = { seat, ready, accepted -> scope.launch(Dispatchers.Main) {
                recordDiagnostic("Internet room confirmed readiness: seat ${seat + 1}, ready=$ready, accepted=$accepted")
                if (seat == localSeat) {
                    if (accepted) lobbyReadyPlayers = if (ready) lobbyReadyPlayers + seat else lobbyReadyPlayers - seat
                    pendingReadyState = null
                    lobbyNotice = when {
                        !accepted -> "The room has not received your opening-hand choice yet. Wait a moment, then try readying up again."
                        ready -> "The room confirmed you are ready."
                        else -> "The room confirmed you are not ready."
                    }
                }
            } },
            onDeckStatusConfirmed = { seat, loaded -> scope.launch(Dispatchers.Main) {
                recordDiagnostic("Internet room confirmed deck status: seat ${seat + 1}, loaded=$loaded")
                if (seat == localSeat) {
                    localDeckSyncConfirmed = loaded
                    lobbyNotice = if (loaded) "The room confirmed your deck is loaded. Choose Keep Hand or Mulligan before readying up." else "The room confirmed your deck was removed."
                }
            } },
            onDisconnected = { reason -> scope.launch(Dispatchers.Main) {
                val hadConnection = roomConnected || onlineStateReady || gameStartedOnline
                roomConnected = false
                onlineStatus = "Disconnected from internet room"
                pendingReadyState = null
                localDeckSyncConfirmed = false
                lobbyNotice = reason
                recordDiagnostic("Internet room disconnected: $reason")
                if (hadConnection) {
                    connectionLost = true
                    connectionLostReason = reason
                    if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Connection interrupted", "Connection to the internet room was lost. Open Arcana Table to reconnect.")
                } else if (creating && !reconnecting) {
                    lobbyCreated = false
                }
                if (cloudRoomClient != null) cloudRoomClient = null
            } }
        )
    }

    fun startInternetRoom() {
        if (lobbyCloudEndpoint.isBlank()) {
            lobbyNotice = "Enter your deployed Cloudflare Worker URL first."
            return
        }
        lobbyCloudEndpoint = lobbyCloudEndpoint.trim().removeSuffix("/")
        optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply()
        lobbyRoomCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        roomClient?.close(); roomClient = null
        roomHost?.close(); roomHost = null
        cloudRoomClient?.close()
        localPlayersBackup = players
        players = listOf(players.first().copy(name = lobbyPlayerName.trim().ifBlank { "PLAYER" }, isOnline = true))
        selectedPlayer = 0
        activePlayer = 0
        localSeat = 0
        lobbyCreated = false
        roomConnected = false
        gameStartedOnline = false
        endProposalBy = -1
        endVotes = emptyMap()
        gameEndedOnline = false
        onlineStateReady = false
        lobbyReadyPlayers = emptySet()
        pendingReadyState = null
        lobbyTurnOrder = null
        lobbyHostAddress = ""
        onlineStatus = "Connecting"
        lobbyNotice = "Opening a secure internet room…"
        val client = makeCloudRoomClient(lobbyRoomCode, lobbyPassword, creating = true)
        cloudRoomClient = client
        client.connect(lobbyCloudEndpoint, lobbyRoomCode, lobbyPassword, lobbyPlayerName, create = true, initialSnapshot = buildOnlineSnapshot())
        scope.launch {
            delay(15_000)
            if (cloudRoomClient === client && !roomConnected) {
                client.close()
                cloudRoomClient = null
                lobbyCreated = false
                onlineStatus = "Connection timed out"
                lobbyNotice = "The room service did not answer in time. Check the Worker URL and internet connection, then try again."
            }
        }
    }

    fun reconnectToRoom() {
        if (reconnecting) return
        if ((!lobbyInternetMode && lobbyHostInput.isBlank()) || (lobbyInternetMode && lobbyCloudEndpoint.isBlank()) || lobbyRoomCode.isBlank() || lobbyPassword.isBlank()) {
            connectionLostReason = "The saved room details are incomplete. Rejoin from the lobby."
            return
        }
        reconnecting = true
        connectionLostReason = null
        lobbyOpen = true
        lobbyJoinMode = true
        lobbyNotice = "Trying to reconnect to the game…"
        onlineStatus = "Reconnecting"
        roomConnected = false
        onlineStateReady = false
        reconnectJob = scope.launch {
            var recovered = false
            if (!lobbyInternetMode) {
                // Give the host's heartbeat timeout a moment to reserve this seat.
                delay(3_200)
            }
            repeat(4) { attempt ->
                if (!reconnecting) return@launch
                if (lobbyInternetMode) {
                    val reconnectClient = makeCloudRoomClient(lobbyRoomCode, lobbyPassword, creating = lobbyCreated, preferredSeat = localSeat)
                    cloudRoomClient = reconnectClient
                    val name = players.getOrNull(localSeat)?.name?.takeIf { it.isNotBlank() && it != "YOU" } ?: lobbyPlayerName
                    reconnectClient.connect(lobbyCloudEndpoint, lobbyRoomCode, lobbyPassword, name, create = false, preferredSeat = localSeat)
                    recovered = withTimeoutOrNull(12_000L) {
                        while (!roomConnected) delay(120)
                        true
                    } ?: false
                    if (recovered) {
                        reconnecting = false
                        connectionLost = false
                        connectionLostReason = null
                        lobbyNotice = "Reconnected to the internet room."
                        recordDiagnostic("Reconnected to the internet room")
                        return@launch
                    }
                    reconnectClient.close()
                    if (cloudRoomClient === reconnectClient) cloudRoomClient = null
                    if (attempt < 3) delay(900L * (attempt + 1))
                    return@repeat
                }
                val client = makeRoomClient(lobbyHostInput, lobbyRoomCode, lobbyPassword, localSeat)
                roomClient = client
                val name = players.getOrNull(localSeat)?.name?.takeIf { it.isNotBlank() && it != "YOU" } ?: lobbyPlayerName
                client.connect(lobbyHostInput, lobbyRoomCode, lobbyPassword, name, localSeat)
                recovered = withTimeoutOrNull(9_000L) {
                    while (!roomConnected) delay(120)
                    true
                } ?: false
                if (recovered) {
                    reconnecting = false
                    connectionLost = false
                    connectionLostReason = null
                    lobbyNotice = "Reconnected to the room."
                    recordDiagnostic("Reconnected to the game room")
                    return@launch
                }
                client.close()
                if (roomClient === client) roomClient = null
                if (attempt < 3) delay(900L * (attempt + 1))
            }
            reconnecting = false
            if (!recovered) {
                connectionLost = true
                onlineStatus = "Disconnected"
                connectionLostReason = "Couldn't reconnect after four attempts. Check that the host is still in the room and both phones are on the same Wi-Fi, then try again."
                lobbyNotice = connectionLostReason
                recordDiagnostic("Automatic reconnect failed after four attempts")
            }
        }
    }

    fun cancelRoomJoin() {
        if (onlineStatus != "Connecting") return
        roomClient?.close()
        roomClient = null
        cloudRoomClient?.close()
        cloudRoomClient = null
        roomConnected = false
        onlineStateReady = false
        onlineStatus = "Offline"
        lobbyNotice = "Join canceled. You can change the details and try again."
    }

    if (lobbyOpen) LobbyDialog(
        players = players,
        roomCreated = lobbyCreated || roomConnected,
        isHost = lobbyCreated,
        gameStarted = gameStartedOnline,
        gameEnded = gameEndedOnline,
        joining = lobbyJoinMode,
        roomCode = lobbyRoomCode,
        password = lobbyPassword,
        joinCode = lobbyJoinCode,
        joinPassword = lobbyJoinPassword,
        hostInput = lobbyHostInput,
        internetMode = lobbyInternetMode,
        cloudEndpoint = lobbyCloudEndpoint,
        playerName = lobbyPlayerName,
        hostAddress = lobbyHostAddress,
        localSeat = localSeat,
        onlineStatus = onlineStatus,
        readyPlayers = lobbyReadyPlayers,
        pendingReady = pendingReadyState,
        deckSyncConfirmed = localDeckSyncConfirmed,
        onTakeMulligan = { takeOpeningMulligan(localSeat) },
        onKeepOpeningHand = { bottomIds -> keepOpeningHand(localSeat, bottomIds) },
        turnOrder = lobbyTurnOrder,
        notice = lobbyNotice,
        onRemovePlayer = ::removeLobbyPlayer,
        onExportDiagnostics = { run { exportDiagnosticLog() } },
        onDismiss = { run { cancelRoomJoin(); lobbyOpen = false } },
        onCancelJoin = { run { cancelRoomJoin() } },
        onSelectJoin = { run { lobbyJoinMode = true; lobbyNotice = null } },
        onSelectCreate = { run { lobbyJoinMode = false; lobbyNotice = null } },
        onSelectNetwork = { internet -> run {
            lobbyInternetMode = internet
            if (internet && (lobbyCloudEndpoint.isBlank() || isLegacyExampleCloudEndpoint(lobbyCloudEndpoint))) {
                lobbyCloudEndpoint = DEFAULT_CLOUD_ROOM_ENDPOINT
                optionPrefs.edit().putString("cloud_room_endpoint", DEFAULT_CLOUD_ROOM_ENDPOINT).apply()
            }
            lobbyNotice = null
        } },
        onCloudEndpoint = { lobbyCloudEndpoint = it.trim(); optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply() },
        onPassword = { lobbyPassword = it },
        onJoinCode = { lobbyJoinCode = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
        onJoinPassword = { lobbyJoinPassword = it },
        onHostInput = { lobbyHostInput = it.trim() },
        onPlayerName = { lobbyPlayerName = it.take(24) },
        onCreate = { run { if (lobbyInternetMode) startInternetRoom() else {
            recordDiagnostic("Lobby creation requested")
            lobbyRoomCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
            roomClient?.close(); roomClient = null
            roomHost?.close()
            localPlayersBackup = players
            players = listOf(players.first().copy(name = lobbyPlayerName.trim().ifBlank { "PLAYER" }, isOnline = true))
            selectedPlayer = 0
            activePlayer = 0
            localSeat = 0
            lobbyCreated = true
            roomConnected = false
            gameStartedOnline = false
            endProposalBy = -1
            endVotes = emptyMap()
            gameEndedOnline = false
            onlineStateReady = false
            lobbyReadyPlayers = emptySet()
            pendingReadyState = null
            lobbyTurnOrder = null
            lobbyHostAddress = ""
            onlineStatus = "Starting room host"
            val host = LanRoomHost(
                roomCode = lobbyRoomCode,
                password = lobbyPassword,
                onStarted = { address -> scope.launch(Dispatchers.Main) {
                    lobbyHostAddress = address
                    onlineStatus = if (address.isBlank()) "Room is open · local IP not found" else "Room is open"
                    roomConnected = true
                    onlineStateReady = true
                    recordDiagnostic(if (address.isBlank()) "Lobby host started without a detected local IP" else "Lobby host started with a detected local IP")
                    if (address.isBlank()) lobbyNotice = "The room server is open, but this phone's Wi-Fi IP could not be detected. Check Wi-Fi or hotspot settings before inviting players."
                } },
                onSnapshot = { snapshot -> scope.launch(Dispatchers.Main) { applyOnlineSnapshot(snapshot) } },
                onPeerJoined = { seat, name -> scope.launch(Dispatchers.Main) {
                    if (players.size <= seat) players = players + TablePlayer(name = name, isOnline = true)
                    else players = players.mapIndexed { index, player -> if (index == seat) player.copy(name = name, isOnline = true) else player }
                    recordDiagnostic("Remote player joined: seat ${seat + 1}")
                    lobbyNotice = "$name joined the room."
                    if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Player connected", "$name joined the game room.")
                } },
                onPeerLeft = { seat -> scope.launch(Dispatchers.Main) {
                    if (seat in players.indices && seat > 0) {
                        val departed = players[seat].name
                        roomHost?.reserveSeat(seat)
                        players = players.mapIndexed { index, player -> if (index == seat) player.copy(isOnline = false) else player }
                        lobbyReadyPlayers = lobbyReadyPlayers - seat
                        recordDiagnostic("Remote player disconnected; reserved seat ${seat + 1} for reconnect")
                        lobbyNotice = "$departed lost connection. Their seat is reserved; they can try to reconnect."
                        if (message == null) message = "$departed lost connection. Their seat is reserved while they reconnect."
                        if (backgroundAlertsEnabled && !appInForeground) postGameAlert(context, "Player disconnected", "$departed lost connection. Their seat is reserved for reconnect.")
                    }
                } },
                onEvent = { event -> scope.launch(Dispatchers.Main) { recordDiagnostic(event) } },
                onError = { error -> scope.launch(Dispatchers.Main) {
                    recordDiagnostic("Room host error: $error")
                    lobbyHostAddress = ""
                    roomConnected = false
                    onlineStateReady = false
                    onlineStatus = "Host failed to start"
                    lobbyNotice = "Couldn't start the room host: $error. Close this lobby and try creating it again."
                    roomHost?.close()
                    roomHost = null
                    lobbyCreated = false
                } },
                onHostMigrationFinished = { scope.launch(Dispatchers.Main) {
                    recordDiagnostic("Previous host left after successful handoff")
                    leaveOnlineRoom()
                } }
            )
            roomHost = host
            host.start()
            scope.launch {
                delay(10_000)
                if (roomHost === host && onlineStatus == "Starting room host") {
                    recordDiagnostic("Lobby host startup timed out")
                    host.close()
                    roomHost = null
                    lobbyCreated = false
                    roomConnected = false
                    onlineStateReady = false
                    lobbyHostAddress = ""
                    onlineStatus = "Host startup timed out"
                    lobbyNotice = "The room didn't start. Check that no other Arcana Table room is open, then try again."
                }
            }
        } } },
        onJoin = { run { if (lobbyInternetMode) {
            if (lobbyCloudEndpoint.isBlank()) {
                lobbyNotice = "Enter your deployed Cloudflare Worker URL first."
            } else {
                lobbyCloudEndpoint = lobbyCloudEndpoint.trim().removeSuffix("/")
                optionPrefs.edit().putString("cloud_room_endpoint", lobbyCloudEndpoint).apply()
                roomHost?.close(); roomHost = null
                roomClient?.close(); roomClient = null
                cloudRoomClient?.close()
                localPlayersBackup = players
                roomConnected = false
                onlineStateReady = false
                localDeckSyncConfirmed = false
                pendingReadyState = null
                lobbyCreated = false
                lobbyRoomCode = lobbyJoinCode
                lobbyPassword = lobbyJoinPassword
                recordDiagnostic("Internet lobby join requested")
                lobbyNotice = "Connecting securely to the internet room…"
                onlineStatus = "Connecting"
                val client = makeCloudRoomClient(lobbyJoinCode, lobbyJoinPassword, creating = false)
                cloudRoomClient = client
                client.connect(lobbyCloudEndpoint, lobbyJoinCode, lobbyJoinPassword, lobbyPlayerName, create = false)
                scope.launch {
                    delay(15_000)
                    if (cloudRoomClient === client && !roomConnected) {
                        client.close()
                        cloudRoomClient = null
                        onlineStatus = "Connection timed out"
                        lobbyNotice = "The room service did not answer in time. Check the Worker URL and internet connection, then try again."
                    }
                }
            }
        } else {
            roomHost?.close(); roomHost = null
            cloudRoomClient?.close(); cloudRoomClient = null
            roomClient?.close()
            localPlayersBackup = players
            roomConnected = false
            onlineStateReady = false
            localDeckSyncConfirmed = false
            pendingReadyState = null
            recordDiagnostic("Lobby join requested")
            lobbyNotice = "Connecting to $lobbyHostInput…"
            onlineStatus = "Connecting"
            val client = makeRoomClient(lobbyHostInput, lobbyJoinCode, lobbyJoinPassword)
            roomClient = client
            client.connect(lobbyHostInput, lobbyJoinCode, lobbyJoinPassword, lobbyPlayerName)
        } } },
        onToggleReady = { index -> run {
            val isHostAi = lobbyCreated && index in players.indices && players[index].isComputer
            if (index != localSeat && !isHostAi) lobbyNotice = "Each player marks their own seat ready on their device."
            else if (!players[index].deckLoaded) lobbyNotice = "${players[index].name} needs an imported deck before becoming ready."
            else if (!players[index].openingHandKept && !players[index].isComputer) lobbyNotice = "Choose Keep Hand or Mulligan before marking ready."
            else {
                val isBecomingReady = index !in lobbyReadyPlayers
                if ((roomClient != null || cloudRoomClient != null) && index == localSeat) {
                    pendingReadyState = isBecomingReady
                    recordDiagnostic("Ready status sent: seat ${localSeat + 1}, ready=$isBecomingReady")
                    val sent = roomClient?.publishReady(isBecomingReady) ?: cloudRoomClient?.publishReady(isBecomingReady) ?: false
                    if (!sent) {
                        pendingReadyState = null
                        lobbyNotice = "Your ready status could not be sent. Check the connection and retry."
                        recordDiagnostic("Ready status send failed: seat ${localSeat + 1}")
                    } else lobbyNotice = if (isBecomingReady) "Sending ready status to the host…" else "Sending your not-ready status to the host…"
                    scope.launch {
                        delay(5_000)
                        if (pendingReadyState == isBecomingReady) {
                            pendingReadyState = null
                            lobbyNotice = "The host has not confirmed that readiness change. Reopen the lobby or try again."
                            recordDiagnostic("Ready confirmation timed out: seat ${localSeat + 1}, requested=$isBecomingReady")
                        }
                    }
                } else {
                    lobbyReadyPlayers = if (isBecomingReady) lobbyReadyPlayers + index else lobbyReadyPlayers - index
                    recordDiagnostic("Ready status changed locally: seat ${index + 1}, ready=$isBecomingReady")
                    lobbyNotice = if (isBecomingReady) "${players[index].name} is ready to start." else "${players[index].name} is not ready yet."
                }
            }
        } },
        onImportDeck = { index -> run {
            selectedPlayer = index
            lobbyOpen = false
            importError = null
            importOpen = true
        } },
        onAddAi = { run {
            lobbyOpen = false
            addAiOpponent()
        } },
        onRandomizeTurnOrder = { run {
            val randomized = players.indices.shuffled()
            lobbyTurnOrder = randomized
            lobbyNotice = "${players[randomized.first()].name} will take the first turn."
        } },
        onStartGame = { run {
            val order = lobbyTurnOrder ?: players.indices.toList()
            recordDiagnostic("Host started game with ${players.size} occupied seat(s)")
            resetGame(startImmediately = true, order = order, preserveOpeningHands = true)
            endProposalBy = -1
            endVotes = emptyMap()
            logAction(activePlayer, "started the game and goes first")
        } },
        onLeave = { run {
            if (cloudRoomClient != null) {
                lobbyNotice = "Leaving the room. The internet room stays available for the other players."
                recordDiagnostic("Player left the internet room")
                leaveOnlineRoom()
            } else if (lobbyCreated && gameStartedOnline && !gameEndedOnline) {
                val successorSeat = roomHost?.beginHostTransfer()
                if (successorSeat == null) {
                    lobbyNotice = "The room needs another connected player before you can hand off hosting. The game is still running here."
                    recordDiagnostic("Host leave blocked because no successor could accept the room")
                } else {
                    lobbyNotice = "Handing the room to ${players.getOrNull(successorSeat)?.name ?: "the next player"}… Keep this screen open until the transfer finishes."
                    onlineStatus = "Transferring host"
                    recordDiagnostic("Host initiated transfer to seat ${successorSeat + 1}")
                }
            } else {
                leaveOnlineRoom()
            }
        } },
    )

    handActionCard?.let { card -> HandCardActionsDialog(
        card, hasCommander = players.getOrNull(selectedPlayer)?.let { it.commanderCard != null || !it.commanderName.isNullOrBlank() } == true,
        isCommanderCard = players.getOrNull(selectedPlayer)?.commanderCard?.id == card.id,
        onDismiss = { handActionCard = null }, onAction = { action -> run { performHandAction(card, action) } }
    ) }

    if (connectionLostReason != null || reconnecting) AlertDialog(
        onDismissRequest = { if (!reconnecting) connectionLostReason = null },
        title = { Text(if (reconnecting) "Reconnecting…" else "Connection interrupted") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reconnecting) CircularProgressIndicator(color = Mint, strokeWidth = 2.dp)
                Text(connectionLostReason ?: "Arcana Table is trying to restore your room connection.", color = Muted)
                if (reconnecting) Text("The host keeps your seat reserved while you reconnect.", color = Mint, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { reconnectToRoom() }, enabled = !reconnecting) {
                Text(if (reconnecting) "TRYING…" else "AUTO RECONNECT", color = if (reconnecting) Muted else Mint)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                reconnectJob?.cancel()
                reconnectJob = null
                reconnecting = false
                roomClient?.close()
                roomClient = null
                if (connectionLost) {
                    roomConnected = false
                    onlineStatus = "Disconnected"
                }
                connectionLostReason = null
            }) { Text(if (reconnecting) "CANCEL" else "LATER", color = Muted) }
        },
        containerColor = Panel,
        titleContentColor = SoftWhite,
        textContentColor = Muted
    )

    if (importOpen) {
        Dialog(onDismissRequest = { if (!importing) cancelDeckImport() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val maxImportDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
            Surface(Modifier.fillMaxWidth(0.96f).heightIn(max = maxImportDialogHeight), shape = RoundedCornerShape(18.dp), color = Panel) {
                Box(Modifier.fillMaxWidth().heightIn(max = maxImportDialogHeight)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${if (players[selectedPlayer].deckLoaded) "Replace deck for" else "Import deck for"} ${players[selectedPlayer].name}", color = SoftWhite, fontSize = 19.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { run { if (!importing) cancelDeckImport() } }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Cancel deck import", tint = Muted) }
                        }
                        Text("Paste a deck list or choose a .txt/.csv file. Import replaces this player's deck.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = { run { savedDecksOpen = true } }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("SAVED DECKS · ${savedDecks.size}", color = Mint) }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { run { filePicker.launch(arrayOf("text/*", "application/*")) } }, enabled = !importing && !argentumDeckLoading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CHOOSE FILE", fontSize = 9.sp) }
                            OutlinedButton(
                                onClick = { run {
                                    argentumDeckLoading = true
                                    importError = null
                                    scope.launch {
                                        try {
                                            val decks = withContext(Dispatchers.IO) { ArgentumDeckApi(argentumOrigin).commanderExamples() }
                                            argentumDeckOptions = decks
                                            if (decks.isEmpty()) importError = "Argentum did not return any Commander examples that pass its current deck validator."
                                            else argentumDeckPickerOpen = true
                                        } catch (error: Exception) {
                                            importError = error.message ?: "Could not load Argentum's premade decks. Check your connection and try again."
                                        } finally { argentumDeckLoading = false }
                                    }
                                } },
                                enabled = !importing && !argentumDeckLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text(if (argentumDeckLoading) "LOADING…" else "ARGENTUM DECKS", fontSize = 9.sp, color = Mint) }
                        }
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp).padding(top = 6.dp),
                            placeholder = { Text("1 Sol Ring\n36 Island\n...") },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !importing && !argentumDeckLoading
                        )
                        if (importText.isNotBlank()) {
                            val importedCardCount = remember(importText) { parseDeckList(importText).sumOf { it.count } }
                            Text("${importedCardCount} cards in list · scroll inside the box to review", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importSaveToLibrary, onCheckedChange = { importSaveToLibrary = it }, enabled = !importing)
                            Text("Save this imported deck for later", color = SoftWhite, fontSize = 11.sp)
                        }
                        if (importSaveToLibrary) OutlinedTextField(importSaveName, { importSaveName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Saved deck name (optional)") }, enabled = !importing)
                        importError?.let { Text(it, color = Color(0xFFFF9C91), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                        if (importing) Text("Matching card names and trying automatic fixes…", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { run { if (!importing) cancelDeckImport() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                            Button(onClick = { run { importDeck(importText) } }, enabled = !importing && !argentumDeckLoading, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (importing || argentumDeckLoading) "LOADING" else "IMPORT") }
                        }
                    }
                }
            }
        }
    }

    if (argentumDeckPickerOpen) Dialog(onDismissRequest = { argentumDeckPickerOpen = false }) {
        Surface(
            Modifier.fillMaxWidth(0.96f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f),
            shape = RoundedCornerShape(18.dp), color = Panel
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ARGENTUM COMMANDER DECKS", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("These lists come from Argentum and passed its live Commander deck validator.", color = Muted, fontSize = 12.sp)
                argentumDeckOptions.forEach { deck ->
                    Surface(
                        Modifier.fillMaxWidth().clickable {
                            importText = deck.asImportText()
                            importError = null
                            message = "${deck.name} loaded from Argentum. Review the 100-card list, then tap Import."
                            argentumDeckPickerOpen = false
                        },
                        shape = RoundedCornerShape(12.dp), color = PanelLight, border = BorderStroke(1.dp, Muted.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(deck.name, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Commander · ${deck.commander} · 100 cards", color = SoftWhite, fontSize = 11.sp)
                            if (deck.description.isNotBlank()) Text(deck.description, color = Muted, fontSize = 11.sp)
                            deck.note?.let { Text(it, color = Color(0xFFFFD271), fontSize = 10.sp) }
                        }
                    }
                }
                TextButton(onClick = { argentumDeckPickerOpen = false }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { Text("CLOSE", color = Muted) }
            }
        }
    }

    if (savedDecksOpen) SavedDecksDialog(
        decks = savedDecks,
        onLoad = { deck -> run {
            if (onlineMode) selectedPlayer = localSeat
            importText = deck.text
            importError = null
            importOpen = true
            savedDecksOpen = false
        } },
        onDelete = { deck -> run {
            savedDecks = savedDecks.filterNot { it.name == deck.name && it.text == deck.text }
            saveSavedDecks(context, savedDecks)
        } },
        onDismiss = { run { savedDecksOpen = false } }
    )

    if (importReviewOpen) {
        val reviewPlayerIndex = selectedPlayer
        val reviewPlayer = players.getOrNull(reviewPlayerIndex)
        val cardsNeedingReview = reviewPlayer?.let { player ->
            (player.library + player.hand + listOfNotNull(player.commanderCard))
                .filter { it.id in importReviewCardIds && it.importIssue != null }
                .distinctBy { it.id }
        }.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!importAutoFixInProgress) importReviewOpen = false },
            title = { Text("Review imported cards") },
            text = { Text(importReviewMessage, color = Muted, fontSize = 12.sp) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        if (cardsNeedingReview.isNotEmpty()) TextButton(
                            enabled = !importAutoFixInProgress,
                            onClick = {
                                val groupedCards = cardsNeedingReview.groupBy { it.name }
                                importAutoFixInProgress = true
                                importReviewMessage = "Checking ${groupedCards.size} card ${if (groupedCards.size == 1) "name" else "names"} for close matches…"
                                scope.launch {
                                    val fixedIds = mutableSetOf<Int>()
                                    val matchedNames = mutableListOf<Pair<String, String>>()
                                    val unresolvedNames = mutableListOf<String>()
                                    var lookupFailures = 0
                                    for ((name, copies) in groupedCards) {
                                        val lookup = runCatching { Scryfall.fuzzyNamedCard(name) }
                                        val candidate = lookup.getOrNull()?.takeIf {
                                            cardNameSimilarity(name, it.name) >= 0.78
                                        }
                                        if (candidate == null) {
                                            unresolvedNames += name
                                            if (lookup.isFailure) lookupFailures++
                                            continue
                                        }
                                        val ids = copies.mapTo(mutableSetOf()) { it.id }
                                        updatePlayer(reviewPlayerIndex) { player ->
                                            fun fix(card: GameCard) = if (card.id in ids) card.copy(
                                                name = candidate.name,
                                                type = candidate.type,
                                                imageUrl = candidate.imageUrl,
                                                oracleText = candidate.oracleText,
                                                manaValue = candidate.manaValue,
                                                importIssue = null,
                                                artCropUrl = candidate.artCropUrl,
                                                manaCost = candidate.manaCost,
                                                power = candidate.power,
                                                toughness = candidate.toughness,
                                                keywords = candidate.keywords
                                            ) else card
                                            val commander = player.commanderCard?.let(::fix)
                                            val commanderWasFixed = player.commanderCard?.id?.let(ids::contains) == true
                                            player.copy(
                                                library = player.library.map(::fix),
                                                hand = player.hand.map(::fix),
                                                commanderCard = commander,
                                                commanderName = if (commanderWasFixed) commander?.name else player.commanderName
                                            )
                                        }
                                        fixedIds += ids
                                        matchedNames += name to candidate.name
                                    }
                                    importReviewCardIds = importReviewCardIds - fixedIds
                                    val allReviewCardsFixed = unresolvedNames.isEmpty() && lookupFailures == 0
                                    if (allReviewCardsFixed && importOverflowCardIds.isEmpty()) {
                                        importReviewCardIds = emptySet()
                                        importReviewOpen = false
                                        message = "Auto Fix succeeded. All cards needing review were matched."
                                    } else {
                                        importReviewMessage = buildString {
                                            if (matchedNames.isNotEmpty()) {
                                                append("Auto Fix matched ${matchedNames.size} card ${if (matchedNames.size == 1) "name" else "names"} and updated ${fixedIds.size} flagged ${if (fixedIds.size == 1) "card" else "cards"}.")
                                            } else if (lookupFailures == 0) {
                                                append("Auto Fix couldn’t identify the cards.")
                                            }
                                            if (unresolvedNames.isNotEmpty()) {
                                                append("\n\nYou’ll need to resolve these manually: ")
                                                append(unresolvedNames.take(5).joinToString(", "))
                                                if (unresolvedNames.size > 5) append(" and ${unresolvedNames.size - 5} more")
                                                append(".")
                                            }
                                            if (lookupFailures > 0) append("\n\nCouldn’t check $lookupFailures ${if (lookupFailures == 1) "name" else "names"}. Check your connection and try Auto Fix again.")
                                            if (importOverflowCardIds.isNotEmpty()) append("\n\n${importOverflowCardIds.size} over-limit ${if (importOverflowCardIds.size == 1) "card remains" else "cards remain"}.")
                                            if (unresolvedNames.isNotEmpty() || lookupFailures > 0) append("\n\nOpen Editor to resolve the remaining cards manually.")
                                        }
                                    }
                                    importAutoFixInProgress = false
                                }
                            }
                        ) { Text(if (importAutoFixInProgress) "FIXING…" else "AUTO FIX", color = Mint) }
                        if (importOverflowCardIds.isNotEmpty()) TextButton(
                            enabled = !importAutoFixInProgress,
                            onClick = {
                                val overflow = importOverflowCardIds
                                updatePlayer(reviewPlayerIndex) { p -> p.copy(hand = p.hand.filterNot { it.id in overflow }, library = p.library.filterNot { it.id in overflow }) }
                                importReviewCardIds = importReviewCardIds - overflow
                                importOverflowCardIds = emptySet()
                                importReviewMessage = "Removed ${overflow.size} card(s) over the deck limit."
                            }
                        ) { Text("TRIM EXCESS", color = Mint) }
                    }
                    if (cardsNeedingReview.isNotEmpty()) TextButton(onClick = { if (!importAutoFixInProgress) { importReviewOpen = false; deckEditorOpen = true } }, enabled = !importAutoFixInProgress) {
                        Text("OPEN EDITOR", color = Mint)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { if (!importAutoFixInProgress) importReviewOpen = false }, enabled = !importAutoFixInProgress) { Text("LATER") } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    if (tokenPickerOpen) TokenPickerDialog(
        onDismiss = { tokenPickerOpen = false },
        onAdd = { token ->
            val created = makeCard(token.name, token.type, token.imageUrl, token = true)
            updatePlayer(tokenTarget) { it.copy(battlefield = it.battlefield + created) }
            logAction(tokenTarget, "created a ${token.name} token")
            applyCreatureEntryLifeEffects(tokenTarget, created)
            tokenPickerOpen = false
        }
    )

    counterTarget?.let { (playerIndex, cardId) ->
        val card = players[playerIndex].battlefield.firstOrNull { it.id == cardId }
        if (card != null) CounterPickerDialog(card, onDismiss = { counterTarget = null }, onAdd = { counter ->
            updateCard(playerIndex, cardId) { current -> current.copy(counters = current.counters + (counter to ((current.counters[counter] ?: 0) + 1))) }
            logAction(playerIndex, "put a $counter counter on ${card.name}")
            counterTarget = null
        })
    }

    preview?.let { item ->
        CardPreviewPopup(item = item, onDismiss = { preview = null })
    }

    zoneDialog?.let { (index, zone) ->
        val cards = when (zone) {
            "Graveyard" -> players[index].graveyard
            "Exile" -> players[index].exile
            else -> players[index].revealed
        }
        CardZoneDialog(
            zone, players[index].name, cards, onDismiss = { zoneDialog = null },
            onPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = false) },
            // Argentum owns every zone change in its games; Arcana only shows them.
            canMoveCards = !argentumEnabled && zone in listOf("Graveyard", "Exile") && (!onlineMode || index == localSeat && !gameEndedOnline),
            onMoveCard = { card, destination ->
                val canControlZone = !onlineMode || index == localSeat && !gameEndedOnline
                if (canControlZone && destination == "Command Zone" && card.id == players[index].commanderCard?.id) {
                    updatePlayer(index) { p -> p.copy(graveyard = p.graveyard.filterNot { it.id == card.id }, exile = p.exile.filterNot { it.id == card.id }, revealed = p.revealed.filterNot { it.id == card.id }, commanderCard = card, commanderName = card.name) }
                    logAction(index, "moved ${card.name} to the command zone")
                } else if (canControlZone) {
                    updatePlayer(index) { p ->
                        val sourceCards = when (zone) {
                            "Graveyard" -> p.graveyard
                            "Exile" -> p.exile
                            else -> p.revealed
                        }
                        if (sourceCards.none { it.id == card.id }) p else {
                            val withoutCard = when (zone) {
                                "Graveyard" -> p.copy(graveyard = p.graveyard.filterNot { it.id == card.id })
                                "Exile" -> p.copy(exile = p.exile.filterNot { it.id == card.id })
                                else -> p.copy(revealed = p.revealed.filterNot { it.id == card.id })
                            }
                            when (destination) {
                                "Hand" -> withoutCard.copy(hand = withoutCard.hand + card)
                                "Battlefield" -> withoutCard.copy(battlefield = withoutCard.battlefield + card.copy(tapped = false))
                                "Library (top)" -> withoutCard.copy(library = listOf(card) + withoutCard.library)
                                "Library (bottom)" -> withoutCard.copy(library = withoutCard.library + card)
                                "Graveyard" -> withoutCard.copy(graveyard = withoutCard.graveyard + card)
                                "Exile" -> withoutCard.copy(exile = withoutCard.exile + card)
                                "Revealed" -> withoutCard.copy(revealed = withoutCard.revealed + card)
                                else -> p
                            }
                        }
                    }
                    val destinationLabel = if (destination == "Library (top)") "the top of the library" else if (destination == "Library (bottom)") "the bottom of the library" else destination.lowercase()
                    logAction(index, "moved ${card.name} from ${zone.lowercase()} to $destinationLabel")
                    if (destination == "Battlefield" && needsCreatureTypeChoice(card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(index, card.id, card.name)
                    if (destination == "Battlefield") applyCreatureEntryLifeEffects(index, card)
                }
            },
            commanderId = players[index].commanderCard?.id,
            canMoveCommander = !onlineMode || index == localSeat && !gameEndedOnline
        )
    }

    if (message != null) AlertDialog(onDismissRequest = { message = null }, title = { Text("Arcana Table") }, text = { Text(message!!) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted)

    if (logOpen) GameLogDialog(
        entries = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { it.description } else gameLog,
        ownership = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { if (it.isTurnMarker) null else it.isYours } else null,
        turnMarkers = if (argentumEnabled && argentumSnapshot != null) argentumSnapshot!!.fullLog.map { it.isTurnMarker } else null,
        previewableCards = players.flatMapIndexed { index, player ->
            (player.battlefield + player.graveyard + player.exile + player.revealed.filterNot { it.isFaceDown } + listOfNotNull(player.commanderCard))
                .filterNot { it.isFaceDown }.map { index to it }
        },
        onPreviewCard = { owner, card -> preview = CardPreview(owner, card, fromHand = false, canManipulate = false) },
        onDismiss = { run { logOpen = false } }
    )
    if (rulesOpen) RulesLookupDialog(onDismiss = { rulesOpen = false })
    AnimatedVisibility(
        visible = chatOpen,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable { chatOpen = false }, contentAlignment = Alignment.CenterEnd) {
            LocalChatPanel(
                players = players, activePlayer = if (onlineMode) localSeat else activePlayer, messages = chatMessages, draft = chatDraft,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f).clickable(enabled = false) {},
                onDraft = { chatDraft = it }, onDismiss = { run { chatOpen = false } }, onSend = { run {
            val text = chatDraft.trim()
            if (text.isNotEmpty()) {
                val speaker = if (onlineMode) localSeat else activePlayer
                chatMessages = chatMessages + ChatMessage(players[speaker.coerceIn(players.indices)].name, text, formatClock(elapsedGameSeconds))
                logAction(speaker, "sent a chat message")
                chatDraft = ""
            }
                } }
            )
        }
    }

    if (optionsOpen) OptionsPage(
        darkMode = darkMode, timerVisible = turnTimerVisible,
        backgroundAlertsEnabled = backgroundAlertsEnabled,
        followActivePlayer = followActivePlayer,
        connectionLabel = when {
            argentumEnabled && argentumOffline -> "Offline game · Argentum running on this phone"
            argentumEnabled -> "Argentum · ${argentumStatus.name.lowercase().replaceFirstChar(Char::uppercase)}"
            else -> "Argentum · not in a game"
        },
        pingResult = argentumPingResult,
        versionLabel = "${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
        diagnosticCount = diagnosticLog.entries.size,
        onTestConnection = {
            argentumPingResult = "Testing…"
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { ArgentumDeckApi(argentumOrigin).ping() } }
                argentumPingResult = result.fold({ "Server reachable · $it ms" }, { "Can't reach Argentum: ${it.message ?: "network error"}" })
                recordDiagnostic("Connection test: ${argentumPingResult}", DiagnosticCategory.CONNECTION)
            }
        },
        onReconnect = if (argentumEnabled && argentumStatus != ArgentumConnectionStatus.CONNECTED && argentumStatus != ArgentumConnectionStatus.REPLACED) ({
            recordDiagnostic("Player chose Reconnect (Options)", DiagnosticCategory.CONNECTION); argentumSocket.retryNow()
        }) else null,
        onViewDiagnostics = { optionsOpen = false; diagnosticViewerOpen = true },
        onExportDiagnostics = { exportDiagnosticLog() },
        onWhatsNew = { optionsOpen = false; changelogOpen = true },
        engineTrialResult = engineTrialResult,
        onEngineSelfTest = {
            engineTrialResult = "Running an AI-vs-AI game on this phone…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    // The engine and AI recurse deeply; give them a large stack like a server thread.
                    var outcome: com.arcanatable.engine.EmbeddedEngineTrial.Result? = null
                    val worker = Thread(null, {
                        outcome = com.arcanatable.engine.EmbeddedEngineTrial.run({ context.assets.open("argentum/por-cards.jsonl") })
                    }, "argentum-engine-trial", 256L shl 20)
                    worker.start(); worker.join()
                    outcome
                }
                engineTrialResult = result?.summary() ?: "Engine trial did not return a result."
                recordDiagnostic(engineTrialResult!!, DiagnosticCategory.ENGINE)
            }
        },
        onFollowActivePlayer = { value -> run { followActivePlayer = value; optionPrefs.edit().putBoolean("follow_active_player", value).apply() } },
        reducedSwipeMotion = reducedSwipeMotion, cardSizeScale = cardSizeScale,
        onOpenLayoutLab = { run { optionsOpen = false; layoutLabOpen = true } },
        onToggleTheme = { run { onToggleTheme() } },
        onToggleTimerVisibility = { run { turnTimerVisible = !turnTimerVisible } },
        onToggleBackgroundAlerts = { enabled -> run { setBackgroundAlerts(enabled) } },
        onReducedMotion = { value -> run { reducedSwipeMotion = value; optionPrefs.edit().putBoolean("reduced_swipe_motion", value).apply() } },
        onCardSizeScale = { value -> run { cardSizeScale = value; optionPrefs.edit().putFloat("card_size_scale", value).putFloat("layout_card_size_$layoutOrientationSuffix", value).apply() } },
        onClearAllData = { run { optionsOpen = false; clearAllConfirmOpen = true } },
        onDismiss = { run { optionsOpen = false } }
    )
    if (layoutLabOpen) LayoutLabPage(
        orientationName = if (isLandscape) "LANDSCAPE" else "PORTRAIT",
        cardSizeScale = cardSizeScale,
        boardSpacingScale = boardSpacingScale,
        handSheetHeightFraction = handSheetHeightFraction,
        footerButtonScale = footerButtonScale,
        playerSectionOrder = playerSectionOrderRaw.split('|').filter(String::isNotBlank),
        quickActionOrder = quickActionOrderRaw.split('|').filter(String::isNotBlank),
        handActionOrder = handActionOrderRaw.split('|').filter(String::isNotBlank),
        handArrangement = handArrangement,
        handCardScale = handCardScale,
        showEngineStatus = showEngineStatus,
        onHandArrangement = { value -> handArrangement = value; optionPrefs.edit().putString("layout_hand_arrangement_$layoutOrientationSuffix", value.name).apply() },
        onHandCardScale = { value -> handCardScale = value.coerceIn(0.7f, 1.4f); optionPrefs.edit().putFloat("layout_hand_card_scale_$layoutOrientationSuffix", handCardScale).apply() },
        onShowEngineStatus = { value -> showEngineStatus = value; optionPrefs.edit().putBoolean("show_engine_status", value).apply() },
        onCardSizeScale = { value -> cardSizeScale = value.coerceIn(0.75f, 1.4f); optionPrefs.edit().putFloat("card_size_scale", cardSizeScale).putFloat("layout_card_size_$layoutOrientationSuffix", cardSizeScale).apply() },
        onBoardSpacingScale = { value -> boardSpacingScale = value.coerceIn(0.5f, 2f); optionPrefs.edit().putFloat("layout_board_spacing_$layoutOrientationSuffix", boardSpacingScale).apply() },
        onHandSheetHeightFraction = { value -> handSheetHeightFraction = value.coerceIn(0.38f, 0.95f); optionPrefs.edit().putFloat("hand_sheet_height_fraction", handSheetHeightFraction).putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", handSheetHeightFraction).apply() },
        onFooterButtonScale = { value -> footerButtonScale = value.coerceIn(0.78f, 1.3f); optionPrefs.edit().putFloat("layout_footer_button_scale_$layoutOrientationSuffix", footerButtonScale).apply() },
        onPlayerSectionOrder = { order -> playerSectionOrderRaw = order.joinToString("|"); optionPrefs.edit().putString("layout_player_sections_$layoutOrientationSuffix", playerSectionOrderRaw).apply() },
        onQuickActionOrder = { order -> quickActionOrderRaw = order.joinToString("|"); optionPrefs.edit().putString("layout_quick_actions_$layoutOrientationSuffix", quickActionOrderRaw).apply() },
        onHandActionOrder = { order -> handActionOrderRaw = order.joinToString("|"); optionPrefs.edit().putString("layout_hand_actions_$layoutOrientationSuffix", handActionOrderRaw).apply() },
        onReset = {
            cardSizeScale = 1f; boardSpacingScale = 1f
            handSheetHeightFraction = 0.52f; footerButtonScale = 1f
            playerSectionOrderRaw = "STATUS|MANA|COMMANDER|STEP|ZONES|REVEALED|BATTLEFIELD"
            handActionOrderRaw = "DRAW|REVEAL|STACK|CAST_COMMANDER|COMBAT|NEXT_STEP|PASS_TURN|MORE"
            quickActionOrderRaw = defaultQuickActionOrder
            handArrangement = HandArrangement.AUTO_FIT; handCardScale = 1f; showEngineStatus = true
            optionPrefs.edit()
                .putString("layout_hand_arrangement_$layoutOrientationSuffix", handArrangement.name)
                .putFloat("layout_hand_card_scale_$layoutOrientationSuffix", handCardScale)
                .putBoolean("show_engine_status", showEngineStatus)
                .putFloat("card_size_scale", cardSizeScale)
                .putFloat("layout_card_size_$layoutOrientationSuffix", cardSizeScale)
                .putFloat("layout_board_spacing_$layoutOrientationSuffix", boardSpacingScale)
                .putFloat("hand_sheet_height_fraction", handSheetHeightFraction)
                .putFloat("layout_hand_sheet_height_$layoutOrientationSuffix", handSheetHeightFraction)
                .putFloat("layout_footer_button_scale_$layoutOrientationSuffix", footerButtonScale)
                .putString("layout_player_sections_$layoutOrientationSuffix", playerSectionOrderRaw)
                .putString("layout_hand_actions_$layoutOrientationSuffix", handActionOrderRaw)
                .putString("layout_quick_actions_$layoutOrientationSuffix", quickActionOrderRaw)
                .apply()
        },
        onDismiss = { layoutLabOpen = false; optionsOpen = true }
    )

    if (resetConfirmOpen) AlertDialog(
        onDismissRequest = { resetConfirmOpen = false },
        title = { Text("Reset this game?") },
        text = { Text("This will shuffle each player's non-token cards into a new library, deal a new opening hand, reset life and timers, and clear the current board and game log. Player names, decks, and commanders will stay.") },
        confirmButton = { TextButton(onClick = { resetGame() }) { Text("RESET GAME", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { resetConfirmOpen = false }) { Text("CANCEL") } }
    )

    if (onlineMode && endProposalBy >= 0 && localSeat !in endVotes && !gameEndedOnline) {
        val proposer = players.getOrNull(endProposalBy)?.name ?: "A player"
        AlertDialog(
            onDismissRequest = { submitEndVote(false) },
            title = { Text("End this game?") },
            text = { Text("$proposer proposes ending the game. Everyone must agree. If you decline, the game continues.") },
            confirmButton = { TextButton(onClick = { submitEndVote(true) }) { Text("AGREE TO END", color = Mint) } },
            dismissButton = { TextButton(onClick = { submitEndVote(false) }) { Text("KEEP PLAYING", color = SoftWhite) } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }
    if (onlineMode && gameEndedOnline && !argentumEnabled) AlertDialog(
        onDismissRequest = {},
        title = { Text("Game ended by unanimous vote") },
        text = { Text("Every player agreed to end the game. The table is now read-only.") },
        confirmButton = { TextButton(onClick = { homeOpen = true; showOverview = true; lobbyOpen = true }) { Text("ROOM LOBBY", color = Mint) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (clearAllConfirmOpen) AlertDialog(
        onDismissRequest = { clearAllConfirmOpen = false },
        title = { Text("Clear all app data?") },
        text = { Text("This clears the current game, room, chat, logs, and game settings. The Internet room URL resets to your default. Saved decks are kept.") },
        confirmButton = { TextButton(onClick = { clearAllData() }) { Text("CLEAR EVERYTHING", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { clearAllConfirmOpen = false }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (manaPoolOpen) ManaPoolDialog(
        player = players.getOrNull(manaTarget) ?: players.first(),
        onChange = { color, delta -> changeMana(manaTarget, color, delta) },
        onClear = { clearMana(manaTarget) },
        onDismiss = { manaPoolOpen = false }
    )

    landEntryPrompt?.let { prompt ->
        val life = players.getOrNull(prompt.playerIndex)?.life ?: 0
        fun placeLand(enterTapped: Boolean, payTwoLife: Boolean = false) {
            val playerIndex = prompt.playerIndex
            if (playerIndex in players.indices) {
                updatePlayer(playerIndex) { player ->
                    player.copy(
                        life = if (payTwoLife) player.life - 2 else player.life,
                        hand = player.hand.filterNot { it.id == prompt.card.id },
                        battlefield = player.battlefield + prompt.card.copy(tapped = enterTapped)
                    )
                }
                logAction(playerIndex, "played ${prompt.card.name}${if (payTwoLife) " and paid 2 life" else if (enterTapped) " tapped" else " untapped"}")
                if (needsCreatureTypeChoice(prompt.card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(playerIndex, prompt.card.id, prompt.card.name)
            }
            landEntryPrompt = null
        }
        AlertDialog(
            onDismissRequest = { landEntryPrompt = null },
            title = { Text(if (prompt.isShockLand) "Pay 2 life for ${prompt.card.name}?" else "How should ${prompt.card.name} enter?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prompt.conditionText.isNotBlank()) Text(prompt.conditionText, color = Muted)
                    if (prompt.isShockLand) Text("Pay 2 life to have it enter untapped, or let it enter tapped.", color = SoftWhite)
                    else Text("Choose the entry state. Check the condition above; Arcana will track the tapped state you choose.", color = SoftWhite)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (prompt.isShockLand) TextButton(onClick = { placeLand(enterTapped = false, payTwoLife = true) }, enabled = life >= 2) {
                        Text("PAY 2 LIFE · ENTER UNTAPPED", color = if (life >= 2) Mint else Muted)
                    } else TextButton(onClick = { placeLand(enterTapped = false) }) {
                        Text("ENTER UNTAPPED", color = Mint)
                    }
                    TextButton(onClick = { placeLand(enterTapped = true) }) { Text("ENTER TAPPED", color = Mint) }
                }
            },
            dismissButton = { TextButton(onClick = { landEntryPrompt = null }) { Text("CANCEL", color = Muted) } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    paymentCard?.let { (owner, card) ->
        val commanderCast = players.getOrNull(owner)?.commanderCard?.id == card.id
        val tax = if (commanderCast) (players.getOrNull(owner)?.commanderCastCount ?: 0) * 2 else 0
        val payable = paySimpleManaCost(players.getOrNull(owner)?.manaPool.orEmpty(), card.manaCost, tax)
        val costKnown = card.manaCost != null
        AlertDialog(
            onDismissRequest = { paymentCard = null; handActionCard = null },
            title = { Text(if (commanderCast) "Cast commander?" else "Cast ${if (card.isFaceDown) "face down" else card.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mana cost:")
                    if (card.manaCost.isNullOrBlank()) Text("unknown") else ManaCostPips(card.manaCost.orEmpty())
                    if (tax > 0) Text("+ $tax commander tax")
                }
                    Text("Tracked pool: ${players.getOrNull(owner)?.manaPool?.entries?.joinToString { (color, amount) -> "${color} $amount" }?.ifBlank { "empty" } ?: "empty"}", color = Muted)
                    if (!costKnown) Text("No parsed mana cost is available. Use manual payment after checking the card.", color = CounterGold)
                    else if (!supportsSimpleManaCost(card.manaCost)) Text("This cost uses hybrid, phyrexian, X, or other special symbols. Review and pay it manually.", color = CounterGold)
                    else if (payable == null) Text("The tracked pool does not have the required colored and generic mana.", color = CounterGold)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { castFromPayment(owner, card, commanderCast) }, enabled = payable != null && costKnown) { Text("PAY FROM MANA POOL", color = if (payable != null && costKnown) Mint else Muted) }
                    TextButton(onClick = { castFromPayment(owner, card, commanderCast, manualPayment = true) }) { Text("I PAID MANUALLY", color = CounterGold) }
                }
            },
            dismissButton = { TextButton(onClick = { paymentCard = null; handActionCard = null }) { Text("CANCEL") } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = SoftWhite
        )
    }

    if (stackOpen && argentumEnabled) AlertDialog(
        onDismissRequest = { stackOpen = false },
        title = { Text("Argentum stack and priority") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val stackItems = argentumSnapshot?.targets?.values.orEmpty().filter { it.type == "Spell" }
                Text(if (stackItems.isEmpty()) "The Argentum stack is empty." else "ON THE STACK · ${stackItems.size}", color = Mint, fontWeight = FontWeight.Bold)
                stackItems.forEach { item ->
                    Text(item.name, color = SoftWhite, fontSize = 13.sp)
                }
                Text("Argentum controls priority and resolves the stack. Arcana displays the server state and offers only server-provided actions.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { stackOpen = false; run { argentumActionCard = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty().ifEmpty { listOf(JSONObject().put("__none", true)) } } }, enabled = argentumSnapshot?.legalActions?.isNotEmpty() == true) {
                    Text("VIEW LEGAL ACTIONS", color = Mint)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { stackOpen = false; sendArgentumPriority() }, enabled = !argentumAwaitingUpdate && argentumSnapshot?.legalActions?.any { it.optJSONObject("action")?.optString("type") == "PassPriority" } == true) {
                Text("PASS PRIORITY", color = Mint)
            }
        },
        dismissButton = { TextButton(onClick = { stackOpen = false }) { Text("CLOSE") } }
    ) else if (stackOpen) StackPriorityDialog(
        players = players, stack = stackActions, priorityPlayer = priorityPlayer, passedCount = priorityPassed.size,
        eligibleCount = players.count { !it.isEliminated }, canResolve = stackActions.isNotEmpty() && stackActions.none { !it.targetsConfirmed } && players.filterNot { it.isEliminated }.all { it.id in priorityPassed },
        canAct = !gameEndedOnline && stackActions.none { !it.targetsConfirmed } && players.getOrNull(priorityPlayer)?.isEliminated == false && (if (onlineMode) priorityPlayer == localSeat else priorityPlayer == selectedPlayer),
        onPreviewCard = { action, card ->
            val ownerIndex = players.indexOfFirst { it.id == action.controllerId }.coerceAtLeast(0)
            preview = CardPreview(ownerIndex, card, fromHand = false, canManipulate = false)
        },
        onStackItemTap = { run {} },
        onCastFromHand = { run {
            selectedPlayer = if (onlineMode) localSeat else priorityPlayer
            showOverview = false
            showHand = true
            stackOpen = false
        } },
        onAddTrigger = { title -> run { stackActions = stackActions + StackAction(title = title, controllerId = players.getOrNull(priorityPlayer)?.id.orEmpty(), kind = "TRIGGER"); priorityPassed = emptySet() } },
        onPassPriority = { run {
            val currentId = players.getOrNull(priorityPlayer)?.id
            if (currentId != null) priorityPassed = priorityPassed + currentId
            priorityPlayer = nextTurnIndex(priorityPlayer, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
        } },
        onResolve = { run { resolveTopOfStack() } },
        onDismiss = { run { stackOpen = false } }
    )

    val targetSelectionItem = stackActions.firstOrNull { it.id == targetSelectionStackItemId }
    targetSelectionItem?.let { item ->
        val controllerIndex = players.indexOfFirst { it.id == item.controllerId }.coerceAtLeast(0)
        TargetSelectionDialog(
            stackItem = item,
            candidates = buildTargetCandidates(players, stackActions, item.id, players.getOrNull(controllerIndex)?.id.orEmpty()),
            onConfirm = { selectedTargets ->
                stackActions = stackActions.map { current -> if (current.id == item.id) current.copy(targets = selectedTargets, targetsConfirmed = true) else current }
                logAction(controllerIndex, "chose ${selectedTargets.size} target${if (selectedTargets.size == 1) "" else "s"} for ${item.title}")
                targetSelectionStackItemId = null
                if (stackActions.none { !it.targetsConfirmed && it.targetCount != null }) {
                    stackOpen = true
                    pendingStackOpen = false
                }
            }
        )
    }

    if (trackerPlayer in players.indices) PlayerTrackerDialog(
        player = players[trackerPlayer], players = players, playerIndex = trackerPlayer,
        onUpdate = { updated ->
            val target = trackerPlayer
            updatePlayer(target) { updated }
            if (!players[target].isEliminated && (updated.playerCounters["Poison"] ?: 0) >= 10) eliminatePlayer(target, "10 poison counters")
        },
        onDismiss = { trackerPlayer = -1 }
    )

    if (concedePlayer in players.indices) AlertDialog(
        onDismissRequest = { concedePlayer = -1 },
        title = { Text("Concede and leave the game?") },
        text = { Text("${players[concedePlayer].name} will be marked eliminated and cannot take further game actions. This does not close the room for other players.") },
        confirmButton = { TextButton(onClick = { concede(concedePlayer); concedePlayer = -1 }) { Text("CONCEDE", color = Color(0xFFE9978B)) } },
        dismissButton = { TextButton(onClick = { concedePlayer = -1 }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (shuffleConfirmPlayer in players.indices) AlertDialog(
        onDismissRequest = { shuffleConfirmPlayer = -1 },
        title = { Text("Shuffle library?") },
        text = { Text("This will randomize ${players[shuffleConfirmPlayer].name}’s library order.") },
        confirmButton = {
            TextButton(onClick = {
                val target = shuffleConfirmPlayer
                shuffleConfirmPlayer = -1
                updatePlayer(target) { it.copy(library = it.library.shuffled()) }
                logAction(target, "shuffled their library")
            }) { Text("SHUFFLE") }
        },
        dismissButton = { TextButton(onClick = { shuffleConfirmPlayer = -1 }) { Text("CANCEL") } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (librarySearchPlayer in players.indices) {
        val owner = librarySearchPlayer
        LibrarySearchDialog(
            player = players[owner],
            onPreview = { preview = CardPreview(owner, it, fromHand = false, canManipulate = false) },
            onTakeToHand = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, hand = p.hand + card) }; logAction(owner, "found ${card.name} and put it into hand") },
            onPutBattlefield = { card ->
                updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, battlefield = p.battlefield + card) }
                logAction(owner, "found ${card.name} and put it onto the battlefield")
                if (needsCreatureTypeChoice(card)) creatureTypeChoicePrompt = CreatureTypeChoicePrompt(owner, card.id, card.name)
                applyCreatureEntryLifeEffects(owner, card)
            },
            onPutGraveyard = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, graveyard = p.graveyard + card) }; logAction(owner, "found ${card.name} and put it into the graveyard") },
            onPutExile = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id }, exile = p.exile + card) }; logAction(owner, "found ${card.name} and exiled it") },
            onMoveToTop = { card -> updatePlayer(owner) { p -> p.copy(library = listOf(card) + p.library.filterNot { it.id == card.id }) }; logAction(owner, "put ${card.name} on top of the library") },
            onMoveToBottom = { card -> updatePlayer(owner) { p -> p.copy(library = p.library.filterNot { it.id == card.id } + card) }; logAction(owner, "put ${card.name} on the bottom of the library") },
            onShuffle = { updatePlayer(owner) { it.copy(library = it.library.shuffled()) }; logAction(owner, "shuffled their library") },
            onDismiss = { librarySearchPlayer = -1 }
        )
    }

    manaChoicePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { manaChoicePrompt = null },
            title = { Text("Choose mana · ${prompt.cardName}") },
            text = { Text("Which mana did this permanent produce?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    prompt.options.forEach { option ->
                        TextButton(onClick = {
                            updatePlayer(prompt.playerIndex) { player ->
                                player.copy(manaPool = player.manaPool.toMutableMap().apply {
                                    option.pool.forEach { (color, count) -> put(color, ((get(color) ?: 0) + count).coerceAtMost(99)) }
                                })
                            }
                            logAction(prompt.playerIndex, "added ${option.label} mana")
                            manaChoicePrompt = null
                        }, modifier = Modifier.heightIn(min = 44.dp)) {
                            ManaSymbolText(option.label, color = Mint, fontSize = 14.sp)
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { manaChoicePrompt = null }) { Text("CANCEL", color = Muted) } },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }

    val visibleTriggerPrompt = if (onlineMode) triggerPrompts.firstOrNull {
        it.controllerIndex == localSeat || (roomHost != null && players.getOrNull(it.controllerIndex)?.isComputer == true)
    } else triggerPrompts.firstOrNull()
    creatureTypeChoicePrompt?.let { prompt ->
        CreatureTypeChoiceDialog(
            cardName = prompt.cardName,
            onConfirm = { chosenType ->
                updatePlayer(prompt.playerIndex) { player ->
                    player.copy(battlefield = player.battlefield.map { card ->
                        if (card.id == prompt.cardId) card.copy(chosenCreatureType = chosenType) else card
                    })
                }
                logAction(prompt.playerIndex, "chose $chosenType for ${prompt.cardName}")
                creatureTypeChoicePrompt = null
            }
        )
    }

    if (!manaPoolOpen && manaChoicePrompt == null && creatureTypeChoicePrompt == null) visibleTriggerPrompt?.let { prompt ->
        val payment = when (prompt.kind) { "RHYSTIC" -> 1; "TITHE" -> 2; else -> 0 }
        val canPay = payment == 0 || trackedMana(prompt.eventPlayerIndex) >= payment
        val targetCandidates = players.indices.filter { target ->
            !players[target].isEliminated && (prompt.targetRule != "OPPONENT" || target != prompt.controllerIndex)
        }
        var selectedTriggerTarget by remember(prompt.cardName, prompt.controllerIndex, prompt.eventPlayerIndex, prompt.kind, prompt.effectText) { mutableIntStateOf(-1) }
        val promptText = when (prompt.kind) {
            "RHYSTIC" -> "${players[prompt.eventPlayerIndex].name} cast a spell. Pay {1} for ${prompt.cardName}, or let ${players[prompt.controllerIndex].name} draw a card?"
            "TITHE" -> "${players[prompt.eventPlayerIndex].name} drew a card. Pay {2} for ${prompt.cardName}, or create a Treasure for ${players[prompt.controllerIndex].name}?"
            "AUTO_EFFECT" -> "${prompt.cardName} triggered: ${prompt.triggerText}. The app can apply this effect: ${prompt.effectText}"
            "TARGET_EFFECT" -> "${prompt.cardName} triggered: ${prompt.triggerText}. Choose a target for: ${prompt.effectText}"
            else -> "${prompt.cardName} triggered: ${prompt.triggerText}. Choose any modes, targets, and other decisions on the card, resolve it manually, then confirm. Effect: ${prompt.effectText}"
        }
        AlertDialog(
            onDismissRequest = { if (prompt.kind == "RHYSTIC" || prompt.kind == "TITHE") resolveTrigger(prompt, paid = false) },
            title = { Text(if (prompt.kind == "RHYSTIC" || prompt.kind == "TITHE") "Triggered choice · ${prompt.cardName}" else "Triggered ability · ${prompt.cardName}") },
            text = { Column {
                Text(promptText, color = SoftWhite)
                if (prompt.targetRule != null) {
                    Text("TARGET", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    targetCandidates.forEach { target ->
                        TextButton(onClick = { selectedTriggerTarget = target }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) {
                            Text("${if (selectedTriggerTarget == target) "✓ " else ""}${players[target].name}", color = if (selectedTriggerTarget == target) Mint else SoftWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (payment > 0) {
                    Text("Tracked mana for ${players.getOrNull(prompt.eventPlayerIndex)?.name ?: "player"}: ${trackedMana(prompt.eventPlayerIndex)}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    if (!canPay) Text("Paying is unavailable until enough mana is tracked.", color = CounterGold, fontSize = 10.sp)
                    TextButton(onClick = { manaTarget = prompt.eventPlayerIndex; manaPoolOpen = true }, modifier = Modifier.heightIn(min = 42.dp)) { Text("UPDATE MANA POOL", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            } },
            confirmButton = {
                val enabled = when (prompt.kind) {
                    "RHYSTIC", "TITHE" -> canPay
                    "TARGET_EFFECT" -> selectedTriggerTarget in targetCandidates
                    "MANUAL" -> prompt.targetRule == null || selectedTriggerTarget in targetCandidates
                    else -> true
                }
                TextButton(onClick = { resolveTrigger(prompt, paid = prompt.kind == "RHYSTIC" || prompt.kind == "TITHE", targetIndex = selectedTriggerTarget.takeIf { it >= 0 }) }, enabled = enabled) {
                    Text(when (prompt.kind) {
                        "RHYSTIC" -> "PAY {1}"
                        "TITHE" -> "PAY {2}"
                        "AUTO_EFFECT" -> "RESOLVE EFFECT"
                        "TARGET_EFFECT" -> "APPLY TO TARGET"
                        else -> "DONE · MANUAL RESOLUTION"
                    }, color = if (enabled) Mint else Muted)
                }
            },
            dismissButton = {
                when (prompt.kind) {
                    "RHYSTIC" -> TextButton(onClick = { resolveTrigger(prompt, paid = false) }) { Text("DRAW CARD") }
                    "TITHE" -> TextButton(onClick = { resolveTrigger(prompt, paid = false) }) { Text("CREATE TREASURE") }
                    "AUTO_EFFECT", "TARGET_EFFECT" -> TextButton(onClick = { resolveTrigger(prompt, paid = false, targetIndex = selectedTriggerTarget.takeIf { it >= 0 }, manual = true) }) { Text("RESOLVE MANUALLY", color = Muted) }
                }
            },
            containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
        )
    }
            }
        }
        TableOverlays()
        TableOverlaysB()
        SnackbarHost(
            hostState = automaticActionSnackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            snackbar = { data -> Snackbar(snackbarData = data, containerColor = PanelLight, contentColor = SoftWhite) }
        )
}
}
