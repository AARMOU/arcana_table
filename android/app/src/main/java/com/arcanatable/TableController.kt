@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

/**
 * State and game logic for the main table screen. It used to live inside one giant composable
 * (TableScreenContent); it was moved here so the screen's UI could be split into smaller files
 * and to stay clear of the JVM's 64 KB method limit.
 */
@Stable
internal class TableController(
    val context: Context,
    val scope: CoroutineScope,
    val cardZoom: CardZoomController,
) {
    // Set by TableScreenContent on every composition.
    var configuration: android.content.res.Configuration by mutableStateOf(android.content.res.Configuration(context.resources.configuration))
    var isLandscape by mutableStateOf(false)
    var layoutOrientationSuffix by mutableStateOf("portrait")
    var density: Density by mutableStateOf(Density(context))
    var screenWidthDp by mutableIntStateOf(0)
    var darkMode by mutableStateOf(true)
    var onToggleTheme: () -> Unit = {}
    lateinit var viewPagerState: PagerState
    lateinit var haptics: HapticFeedback
    lateinit var notificationPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>
    lateinit var diagnosticExportLauncher: ManagedActivityResultLauncher<String, Uri?>
    lateinit var filePicker: ManagedActivityResultLauncher<Array<String>, Uri?>

    inner class OrientationLayout(val layoutOrientationSuffix: String) {
        var cardSizeScale by run { mutableFloatStateOf(optionPrefs.getFloat("layout_card_size_$layoutOrientationSuffix", optionPrefs.getFloat("card_size_scale", 1f)).coerceIn(0.75f, 1.4f)) }
        var boardSpacingScale by run { mutableFloatStateOf(optionPrefs.getFloat("layout_board_spacing_$layoutOrientationSuffix", 1f).coerceIn(0.5f, 2f)) }
        var footerButtonScale by run { mutableFloatStateOf(optionPrefs.getFloat("layout_footer_button_scale_$layoutOrientationSuffix", 1f).coerceIn(0.78f, 1.3f)) }
        var handSheetHeightFraction by run {
            mutableFloatStateOf(optionPrefs.getFloat("layout_hand_sheet_height_$layoutOrientationSuffix", optionPrefs.getFloat("hand_sheet_height_fraction", 0.52f)).coerceIn(0.38f, 0.95f))
        }
        var playerSectionOrderRaw by run {
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
        var handArrangement by run { mutableStateOf(HandArrangement.parse(optionPrefs.getString("layout_hand_arrangement_$layoutOrientationSuffix", null))) }
        var handCardScale by run { mutableFloatStateOf(optionPrefs.getFloat("layout_hand_card_scale_$layoutOrientationSuffix", 1f).coerceIn(0.7f, 1.4f)) }
        var handActionOrderRaw by run { mutableStateOf(optionPrefs.getString("layout_hand_actions_$layoutOrientationSuffix", "DRAW|REVEAL|STACK|CAST_COMMANDER|COMBAT|NEXT_STEP|PASS_TURN|MORE") ?: "DRAW|REVEAL|STACK|CAST_COMMANDER|COMBAT|NEXT_STEP|PASS_TURN|MORE") }
        var quickActionOrderRaw by run {
            val key = "layout_quick_actions_$layoutOrientationSuffix"
            mutableStateOf(normalizeArgentumQuickActionOrder(optionPrefs.getString(key, null)).joinToString("|"))
        }
    }
    private var orientationLayoutCache: OrientationLayout? = null
    /** Layout settings are saved per orientation; reloaded from prefs when the orientation changes. */
    private val orientationLayout: OrientationLayout get() {
        val suffix = layoutOrientationSuffix
        orientationLayoutCache?.let { if (it.layoutOrientationSuffix == suffix) return it }
        return OrientationLayout(suffix).also { orientationLayoutCache = it }
    }
    var cardSizeScale: Float
        get() = orientationLayout.cardSizeScale
        set(value) { orientationLayout.cardSizeScale = value }
    var boardSpacingScale: Float
        get() = orientationLayout.boardSpacingScale
        set(value) { orientationLayout.boardSpacingScale = value }
    var footerButtonScale: Float
        get() = orientationLayout.footerButtonScale
        set(value) { orientationLayout.footerButtonScale = value }
    var handSheetHeightFraction: Float
        get() = orientationLayout.handSheetHeightFraction
        set(value) { orientationLayout.handSheetHeightFraction = value }
    var playerSectionOrderRaw: String
        get() = orientationLayout.playerSectionOrderRaw
        set(value) { orientationLayout.playerSectionOrderRaw = value }
    var handArrangement: HandArrangement
        get() = orientationLayout.handArrangement
        set(value) { orientationLayout.handArrangement = value }
    var handCardScale: Float
        get() = orientationLayout.handCardScale
        set(value) { orientationLayout.handCardScale = value }
    var handActionOrderRaw: String
        get() = orientationLayout.handActionOrderRaw
        set(value) { orientationLayout.handActionOrderRaw = value }
    var quickActionOrderRaw: String
        get() = orientationLayout.quickActionOrderRaw
        set(value) { orientationLayout.quickActionOrderRaw = value }

    val automaticActionSnackbar = run { SnackbarHostState() }
    val optionPrefs = run { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    var backgroundAlertsEnabled by run { mutableStateOf(optionPrefs.getBoolean("background_alerts", false)) }
    var autoReturnTableAfterPass by run { mutableStateOf(optionPrefs.getBoolean("auto_return_table", true)) }
    var autoDrawOnDrawStep by run { mutableStateOf(optionPrefs.getBoolean("auto_draw_step", true)) }
    var autoUntapOnUntapStep by run { mutableStateOf(optionPrefs.getBoolean("auto_untap_step", true)) }
    var openHandAfterDraw by run { mutableStateOf(optionPrefs.getBoolean("open_hand_after_draw", true)) }
    var reducedSwipeMotion by run { mutableStateOf(optionPrefs.getBoolean("reduced_swipe_motion", false)) }
    var showPlayedPopup by run { mutableStateOf(optionPrefs.getBoolean("adjust_play_popup", true)) }
    var showRecentPlays by run { mutableStateOf(optionPrefs.getBoolean("adjust_recent_plays", true)) }
    var showAbilityBanners by run { mutableStateOf(optionPrefs.getBoolean("adjust_ability_banners", true)) }
    var showAiRecap by run { mutableStateOf(optionPrefs.getBoolean("adjust_ai_recap", true)) }
    var showTurnBanner by run { mutableStateOf(optionPrefs.getBoolean("adjust_turn_banner", true)) }
    var buzzOnTurn by run { mutableStateOf(optionPrefs.getBoolean("adjust_turn_buzz", true)) }
    var stackSameCards by run { mutableStateOf(optionPrefs.getBoolean("adjust_stack_cards", true)) }
    var respondGlow by run { mutableStateOf(optionPrefs.getBoolean("adjust_respond_glow", true)) }
    var playedPopupSeconds by run { mutableFloatStateOf(optionPrefs.getFloat("adjust_notice_seconds", 4f).coerceIn(1f, 8f)) }
    var combatCardScale by run { mutableFloatStateOf(optionPrefs.getFloat("adjust_combat_card_scale", 1f).coerceIn(.65f, 1.35f)) }
    var showEngineStatus by run { mutableStateOf(optionPrefs.getBoolean("show_engine_status", true)) }
    val defaultQuickActionOrder get() = argentumQuickActionIds.joinToString("|")
    var nextId by run { mutableIntStateOf(20) }
    var players by run { mutableStateOf(loadSavedPlayers(context) ?: samplePlayers()) }
    var selectedPlayer by run { mutableIntStateOf(0) }
    var showOverview by run { mutableStateOf(true) }
    var showHand by run { mutableStateOf(false) }
    var importOpen by run { mutableStateOf(false) }
    var importText by run { mutableStateOf("") }
    var importSaveToLibrary by run { mutableStateOf(false) }
    var importSaveName by run { mutableStateOf("") }
    var savedDecks by run { mutableStateOf(loadSavedDecks(context)) }
    var activeDeckId by run { mutableStateOf(loadActiveDeck(context)) }
    var deckPickerOpen by run { mutableStateOf(false) }
    var playSheetOpen by run { mutableStateOf(false) }
    var offlineAiDeckId by run { mutableStateOf<String?>(null) }
    val premadeDecks = run { runCatching { context.assets.open("argentum/commander-decks.json").bufferedReader().use { it.readText() } }.getOrNull()?.let { runCatching { com.arcanatable.engine.OfflineGameSetup.parsePremades(it) }.getOrNull() }.orEmpty() }
    var savedDecksOpen by run { mutableStateOf(false) }
    var localDeckSyncConfirmed by run { mutableStateOf(false) }
    // Structured, redacted diagnostic log (DiagnosticLog.kt). Legacy free-text entries are migrated on load.
    val diagnosticLog = run { DiagnosticLog().apply { restore(optionPrefs.getString("diagnostic_events", "").orEmpty()) } }
    var diagnosticRevision by run { mutableIntStateOf(0) }
    var diagnosticViewerOpen by run { mutableStateOf(false) }
    var diagnosticExportSelection by run { mutableStateOf<List<DiagnosticEntry>?>(null) }
    var lastDiagnosticZoneSignature by run { mutableStateOf<String?>(null) }
    var lastDiagnosticZoneSnapshot by run { mutableStateOf<List<TablePlayer>?>(null) }
    var inboundSnapshotSequence by run { mutableIntStateOf(0) }
    var importing by run { mutableStateOf(false) }
    var argentumDeckLoading by run { mutableStateOf(false) }
    var argentumDeckPickerOpen by run { mutableStateOf(false) }
    var argentumDeckOptions by run { mutableStateOf<List<ArgentumExampleDeck>>(emptyList()) }
    var importError by run { mutableStateOf<String?>(null) }
    var importReviewOpen by run { mutableStateOf(false) }
    var importReviewMessage by run { mutableStateOf("") }
    var importReviewCardIds by run { mutableStateOf(emptySet<Int>()) }
    var importOverflowCardIds by run { mutableStateOf(emptySet<Int>()) }
    var importAutoFixInProgress by run { mutableStateOf(false) }
    var pendingAiPlayerIndex by run { mutableIntStateOf(-1) }
    var clearAllConfirmOpen by run { mutableStateOf(false) }
    var highlightedHandCardId by run { mutableStateOf<Int?>(null) }
    var tokenPickerOpen by run { mutableStateOf(false) }
    var tokenTarget by run { mutableIntStateOf(0) }
    var counterTarget by run { mutableStateOf<Pair<Int, Int>?>(null) }
    var preview by run { mutableStateOf<CardPreview?>(null) }
    var rulesOpen by run { mutableStateOf(false) }
    var battlefieldBounds by run { mutableStateOf<Rect?>(null) }
    var handSheetBounds by run { mutableStateOf<Rect?>(null) }
    var zoneDialog by run { mutableStateOf<Pair<Int, String>?>(null) }
    var message by run { mutableStateOf<String?>(null) }
    var cardActionTarget by run { mutableStateOf<Pair<Int, Int>?>(null) }
    var handActionCard by run { mutableStateOf<GameCard?>(null) }
    var activePlayer by run { mutableIntStateOf(0) }
    // Not saveable: after a relaunch/recreation the player chooses Resume (Argentum resumes on reconnect).
    var argentumEnabled by run { mutableStateOf(false) }
    /** True while the current Argentum game runs on the in-app engine (no server). */
    var argentumOffline by run { mutableStateOf(false) }
    var offlineStarting by run { mutableStateOf(false) }
    var gameTurnOrder by run { mutableStateOf(emptyList<Int>()) }
    var turnNumber by run { mutableIntStateOf(1) }
    val phaseSteps = run { listOf("UNTAP", "UPKEEP", "DRAW", "PRECOMBAT MAIN", "BEGIN COMBAT", "ATTACKERS", "BLOCKERS", "DAMAGE", "END COMBAT", "POSTCOMBAT MAIN", "END STEP", "CLEANUP") }
    var phaseIndex by run { mutableIntStateOf(0) }
    var elapsedGameSeconds by run { mutableLongStateOf(0L) }
    var elapsedTurnSeconds by run { mutableLongStateOf(0L) }
    var gameLog by run { mutableStateOf(listOf("Game session started")) }
    var logOpen by run { mutableStateOf(false) }
    var chatOpen by run { mutableStateOf(false) }
    var topMenuExpanded by run { mutableStateOf(false) }
    var topMenuSection by run { mutableStateOf<String?>(null) }
    var resetConfirmOpen by run { mutableStateOf(false) }
    var chatDraft by run { mutableStateOf("") }
    var chatMessages by run { mutableStateOf(emptyList<ChatMessage>()) }
    var unreadChatCount by run { mutableIntStateOf(0) }
    var triggerPrompts by run { mutableStateOf(emptyList<TriggerPrompt>()) }
    var stackActions by run { mutableStateOf(emptyList<StackAction>()) }
    var targetSelectionStackItemId by run { mutableStateOf<String?>(null) }
    var priorityPlayer by run { mutableIntStateOf(0) }
    var priorityPassed by run { mutableStateOf(emptySet<String>()) }
    var stackOpen by run { mutableStateOf(false) }
    var pendingStackOpen by run { mutableStateOf(false) }
    var stackCycleActive by run { mutableStateOf(false) }
    var trackerPlayer by run { mutableIntStateOf(-1) }
    var concedePlayer by run { mutableIntStateOf(-1) }
    var shuffleConfirmPlayer by run { mutableIntStateOf(-1) }
    var paymentCard by run { mutableStateOf<Pair<Int, GameCard>?>(null) }
    var landEntryPrompt by run { mutableStateOf<LandEntryPrompt?>(null) }
    var librarySearchPlayer by run { mutableIntStateOf(-1) }
    var manaPoolOpen by run { mutableStateOf(false) }
    var manaTarget by run { mutableIntStateOf(0) }
    var manaChoicePrompt by run { mutableStateOf<ManaChoicePrompt?>(null) }
    var creatureTypeChoicePrompt by run { mutableStateOf<CreatureTypeChoicePrompt?>(null) }
    var timerMenuOpen by run { mutableStateOf(false) }
    var optionsOpen by run { mutableStateOf(false) }
    var layoutLabOpen by run { mutableStateOf(false) }
    var changelogOpen by run { mutableStateOf(false) }
    var lobbyOpen by run { mutableStateOf(false) }
    var homeOpen by run { mutableStateOf(true) }
    var lobbyCreated by run { mutableStateOf(false) }
    var lobbyJoinMode by run { mutableStateOf(false) }
    var lobbyReadyPlayers by run { mutableStateOf(emptySet<Int>()) }
    var pendingReadyState by run { mutableStateOf<Boolean?>(null) }
    var lobbyTurnOrder by run { mutableStateOf<List<Int>?>(null) }
    var lobbyRoomCode by run { mutableStateOf("") }
    var lobbyPassword by run { mutableStateOf("") }
    var lobbyJoinCode by run { mutableStateOf("") }
    var lobbyJoinPassword by run { mutableStateOf("") }
    var lobbyHostInput by run { mutableStateOf("") }
    var lobbyInternetMode by run { mutableStateOf(false) }
    var lobbyCloudEndpoint by run {
        val savedEndpoint = optionPrefs.getString("cloud_room_endpoint", null)?.trim()
        mutableStateOf(savedEndpoint?.takeIf { it.isNotBlank() && !isLegacyExampleCloudEndpoint(it) } ?: DEFAULT_CLOUD_ROOM_ENDPOINT)
    }
    var lobbyPlayerName by run { mutableStateOf("PLAYER") }
    var lobbyHostAddress by run { mutableStateOf("") }
    var lobbyNotice by run { mutableStateOf<String?>(null) }
    var onlineStatus by run { mutableStateOf("Offline") }
    var roomConnected by run { mutableStateOf(false) }
    // Not saveable: after a relaunch/recreation the player chooses Resume (Argentum resumes on reconnect).
    var argentumResumeAvailable by run { mutableStateOf(optionPrefs.getBoolean("argentum_game_active", false)) }
    var argentumResumeRequested by run { mutableStateOf(false) }
    var argentumEndPreviousRequested by run { mutableStateOf(false) }
    var argentumLastStateJson by run { mutableStateOf<JSONObject?>(null) }
    var argentumAttackPlan by run { mutableStateOf<ArgentumAttackPlan?>(null) }
    var argentumBlockPlan by run { mutableStateOf<ArgentumBlockPlan?>(null) }
    var argentumDeferredDecisionId by run { mutableStateOf<String?>(null) }
    var argentumOrderSelection by run { mutableStateOf<List<String>>(emptyList()) }
    var argentumModeSelection by run { mutableStateOf<Set<Int>>(emptySet()) }
    var argentumAmounts by run { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var argentumDeckReview by run { mutableStateOf<ArgentumDeckReview?>(null) }
    var argentumComposer by run { mutableStateOf<ArgentumActionPlan?>(null) }
    /** Offline: card id whose real maximum X the engine is still working out for the open composer. */
    var argentumXChecking by run { mutableStateOf<String?>(null) }
    // Wi-Fi play (one phone hosts the engine, the other joins).
    var wifiServer: ArgentumHostLink? = null
    var onlineRoomCode by run { mutableStateOf<String?>(null) } // host: the code to share
    var onlineJoinOpen by run { mutableStateOf(false) }
    var onlineCodeInput by run { mutableStateOf("") }
    var wifiWaitingAddress by run { mutableStateOf<String?>(null) }
    var wifiJoinOpen by run { mutableStateOf(false) }
    var wifiSearching by run { mutableStateOf(false) }
    var wifiFound by run { mutableStateOf<List<ArgentumLan.FoundHost>>(emptyList()) }
    var wifiAddressInput by run { mutableStateOf("") }
    var wifiConnecting by run { mutableStateOf(false) }
    var wifiRole by run { mutableStateOf<String?>(null) }
    /** A big message that flashes over the table and fades ("Your go", "Your move"). */
    var flashMessage by run { mutableStateOf<FlashText?>(null) }
    /** Offline: the engine has been working on your last action for over 10 seconds. */
    var argentumSlow by run { mutableStateOf(false) }

    /** The name shown to a friend: your name, or your commander's first name instead of the default "PLAYER". */
    fun friendlyName(commander: String?): String = lobbyPlayerName.trim().takeIf { it.isNotBlank() && !it.equals("PLAYER", true) }
        ?: commander?.substringBefore(',')?.substringBefore(" of ")?.trim()?.takeIf { it.isNotBlank() } ?: "Friend" // "host" or "guest" while a Wi-Fi game is on
    var argentumActionCard by run { mutableStateOf<GameCard?>(null) }
    var argentumGameOverDismissed by run { mutableStateOf(false) }
    var argentumMulliganInfo by run { mutableStateOf<Triple<Int, Boolean, Int>?>(null) } // count, on the play, cards to bottom
    var argentumGameOverText by run { mutableStateOf<String?>(null) }
    var argentumWon by run { mutableStateOf<Boolean?>(null) }
    var argentumBudgetCounts by run { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var argentumReplacementPick by run { mutableStateOf(0 to 0) }
    val argentumReportedUnsupported = run { mutableSetOf<String>() }
    var argentumJoinDialogOpen by run { mutableStateOf(false) }
    var argentumJoinCode by run { mutableStateOf("") }
    var argentumVsAiLobby by run { mutableStateOf(true) }
    var argentumStatus by run { mutableStateOf(ArgentumConnectionStatus.DISCONNECTED) }
    var argentumPlayerId by run { mutableStateOf<String?>(null) }
    var argentumInbound by run { mutableStateOf<JSONObject?>(null) }
    var argentumSnapshot by run { mutableStateOf<ArgentumTableSnapshot?>(null) }
    var argentumPendingDecision by run { mutableStateOf<JSONObject?>(null) }
    var argentumDecisionSelection by run { mutableStateOf<Set<String>>(emptySet()) }
    var argentumDecisionTargets by run { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var argentumDecisionColors by run { mutableStateOf<Set<String>>(emptySet()) }
    var argentumLeaveConfirm by run { mutableStateOf(false) }
    var argentumDeckForLobby by run { mutableStateOf<Map<String, Int>?>(null) }
    var argentumDeckValidationRunning by run { mutableStateOf(false) }
    var argentumCommanderForLobby by run { mutableStateOf<String?>(null) }
    var argentumLobbySubmitted by run { mutableStateOf(false) }
    var argentumAwaitingUpdate by run { mutableStateOf(false) }
    var argentumLocalPlayersBackup by run { mutableStateOf<List<TablePlayer>?>(null) }
    var argentumStackUse by run { mutableStateOf<StackUse?>(null) }
    var argentumAutoYes by run { mutableStateOf(emptyMap<String, Int>()) }
    var playPopup by run { mutableStateOf<PlayedCard?>(null) }
    var playFeed by run { mutableStateOf(emptyList<PlayedCard>()) }
    var playFeedOpen by run { mutableStateOf(runCatching { context.getSharedPreferences("arcana-table-ui", Context.MODE_PRIVATE).getBoolean("playFeedOpen", true) }.getOrDefault(true)) }
    var playBanner by run { mutableStateOf<PlayedCard?>(null) }
    var aiRecap by run { mutableStateOf(-1 to TurnRecap()) }
    var recapText by run { mutableStateOf<String?>(null) }
    var argentumMulliganPrompt by run { mutableStateOf(false) }
    var argentumBottomCardIds by run { mutableStateOf<List<String>?>(null) }
    var argentumBottomCardCount by run { mutableIntStateOf(0) }
    var argentumBottomSelection by run { mutableStateOf<Set<String>>(emptySet()) }
    var argentumLastError by run { mutableStateOf<String?>(null) }
    var argentumActionChoices by run { mutableStateOf<List<JSONObject>>(emptyList()) }
    /** Argentum's `nextStopPoint` for this seat: where passing priority takes you ("To Combat"). */
    var argentumNextStop by run { mutableStateOf<String?>(null) }
    var argentumUndoAvailable by run { mutableStateOf(false) }
    /** Turn number when "Pass to my turn" was switched on; null when it is off. */
    var passToMyTurnFrom by run { mutableStateOf<Int?>(null) }
    var followActivePlayer by run { mutableStateOf(optionPrefs.getBoolean("follow_active_player", true)) }
    var quietOpponentTurns by run { mutableStateOf(optionPrefs.getBoolean("quiet_opponent_turns", false)) }
    var argentumPingResult by run { mutableStateOf<String?>(null) }
    var engineTrialResult by run { mutableStateOf<String?>(null) }
    var lastOpponentViewed by run { mutableIntStateOf(-1) }
    val argentumPrefs = run { context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE) }
    val argentumOrigin = run { ArgentumEndpoints.origin(argentumPrefs) }
    val argentumQueue = run { kotlinx.coroutines.channels.Channel<JSONObject>(kotlinx.coroutines.channels.Channel.UNLIMITED) }
    val argentumSocket = run {
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
    var connectionLost by run { mutableStateOf(false) }
    var connectionLostReason by run { mutableStateOf<String?>(null) }
    var reconnecting by run { mutableStateOf(false) }
    var reconnectJob by run { mutableStateOf<Job?>(null) }
    var localSeat by run { mutableIntStateOf(0) }
    var roomHost by run { mutableStateOf<LanRoomHost?>(null) }
    var roomClient by run { mutableStateOf<LanRoomClient?>(null) }
    var cloudRoomClient by run { mutableStateOf<CloudRoomClient?>(null) }
    var localPlayersBackup by run { mutableStateOf<List<TablePlayer>?>(null) }
    var lastRemoteSnapshot by run { mutableStateOf<String?>(null) }
    var gameStartedOnline by run { mutableStateOf(false) }
    var onlineStateReady by run { mutableStateOf(false) }
    var endProposalBy by run { mutableIntStateOf(-1) }
    var endVotes by run { mutableStateOf(emptyMap<Int, Boolean>()) }
    var gameEndedOnline by run { mutableStateOf(false) }
    var localGameStarted by run { mutableStateOf(false) }
    fun setBackgroundAlerts(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            backgroundAlertsEnabled = enabled
            context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit().putBoolean("background_alerts", enabled).apply()
        }
    }

    var pendingPlayerRemoval by run { mutableIntStateOf(-1) }
    var deckEditorOpen by run { mutableStateOf(false) }
    var combatDialogOpen by run { mutableStateOf(false) }
    var turnTimerRunning by run { mutableStateOf(false) }
    var gameTimerRunning by run { mutableStateOf(false) }
    var aiRoutineRunning by run { mutableStateOf(false) }
    var gameGeneration by run { mutableIntStateOf(0) }
    var turnTimerVisible by run { mutableStateOf(true) }
    var lastSwipeDistance by run { mutableFloatStateOf(0f) }
    val onlineMode get() = argentumEnabled || roomHost != null || roomClient != null || cloudRoomClient != null || connectionLost
    val gameInProgress get() = if (argentumEnabled) argentumSnapshot != null && argentumStatus == ArgentumConnectionStatus.CONNECTED && argentumSnapshot?.gameOver != true
        else if (onlineMode) gameStartedOnline && roomConnected && !gameEndedOnline else localGameStarted
    val visibleStackCount get() = if (argentumEnabled) argentumSnapshot?.targets?.values?.count { it.type == "Spell" } ?: 0 else stackActions.size
    val argentumLegalTypes get() = argentumSnapshot?.legalActions.orEmpty().mapNotNull { it.optJSONObject("action")?.optString("type") }.toSet()
    // Display ids of cards Argentum offers an action for (play, cast, activate) — drawn with a glow.
    val argentumPlayableIds: Set<Int> get() = argentumSnapshot?.let { snap ->
        val byServer = snap.serverCardIds.entries.associate { it.value to it.key }
        snap.legalActions.mapNotNull { info ->
            val action = info.optJSONObject("action") ?: return@mapNotNull null
            if (!info.optBoolean("isAffordable", true) || info.optBoolean("isManaAbility")) return@mapNotNull null
            listOf("cardId", "sourceId", "permanentId", "entityId").firstNotNullOfOrNull { key -> byServer[action.optString(key)] }
        }.toSet()
    }.orEmpty()
    // The one action Arcana's primary buttons perform in an Argentum game, named after what it does.
    val argentumPrimaryLabel: String? get() = when {
        !argentumEnabled || argentumSnapshot == null || gameEndedOnline -> null
        "DeclareAttackers" in argentumLegalTypes -> "DECLARE ATTACKERS"
        "DeclareBlockers" in argentumLegalTypes -> "DECLARE BLOCKERS"
        "PassPriority" in argentumLegalTypes -> ArgentumPassing.passLabel(argentumNextStop)
        else -> null
    }
    // You have priority on someone else's turn: the pass button lights up so "you can respond now"
    // never looks like "it's your turn".
    val argentumCanRespond get() = argentumEnabled && argentumPrimaryLabel != null && !argentumAwaitingUpdate &&
        argentumSnapshot?.let { it.activePlayer != localSeat && it.priorityPlayer == localSeat } == true
    val argentumTurnKey get() = argentumSnapshot?.takeIf { argentumEnabled }?.let { it.turnNumber to it.activePlayer }
    val argentumNeedsHandling: String? get() = argentumPendingDecision?.let { decision ->
        ArgentumDecisions.needsHandlingReason(decision)
            ?: if (decision.optString("id") == argentumDeferredDecisionId) "Argentum is waiting for your choice: ${decision.optString("prompt")}" else null
    }
    val canControlSelectedPlayer get() = (!onlineMode || selectedPlayer == localSeat) && !gameEndedOnline && players.getOrNull(selectedPlayer)?.isEliminated != true
    fun blockLocalRulesAction(action: String): Boolean {
        if (!argentumEnabled) return false
        message = "$action is controlled by Argentum in this game. Choose an action Argentum offers."
        return true
    }

    // Tell the player when Argentum is waiting on them: a light buzz in the app, an alert in the background.
    val argentumYourMove get() = argentumEnabled && !gameEndedOnline && !argentumAwaitingUpdate &&
        (argentumPrimaryLabel != null || argentumPendingDecision?.optString("type")?.let { it != "PlayCardDecision" } == true)
    // A banner (and a buzz on yours) when each turn starts.
    var turnBanner by run { mutableStateOf<TurnBannerState?>(null) }
    var observedChatCount by run { mutableIntStateOf(chatMessages.size) }

    val activity get() = context as? Activity

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
        scope.launch { automaticActionSnackbar.showSnackbar("$playerName · $action", duration = androidx.compose.material3.SnackbarDuration.Long) }
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
            scope.launch { automaticActionSnackbar.showSnackbar("$playerName · $notice", duration = androidx.compose.material3.SnackbarDuration.Long) }
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
            scope.launch { automaticActionSnackbar.showSnackbar("${players[playerIndex].name} · $notice", duration = androidx.compose.material3.SnackbarDuration.Long) }
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

    /**
     * Validates the local deck with Argentum and opens a lobby. When Argentum rejects cards, it
     * stops and shows a needs-handling review; substitutions happen only when the player chooses
     * [acceptSubstitutions], and only for this game (the saved deck is never edited here).
     */
    /** The deck you picked on the home screen (never the table, which a finished game leaves behind): card names and commander. */
    fun activeDeckCards(): Pair<List<String>, String?> {
        val seat = players.getOrNull(localSeat)
        val active = activeDeckId
        val premade = active?.takeIf { it.startsWith("builtin:") }?.removePrefix("builtin:")?.let { id -> premadeDecks.firstOrNull { it.id == id } }
        val saved = active?.let { a -> savedDecks.firstOrNull { it.name == a } }
        return when {
            premade != null -> premade.cards to premade.commander
            saved != null -> parseDeckList(saved.text).let { e -> e.flatMap { x -> List(x.count) { x.name } } to (saved.commander ?: e.firstOrNull { it.commander }?.name) }
            else -> seat?.takeIf { it.deckLoaded }?.let { (it.library + it.hand).filterNot { c -> c.isToken || c.name == "Hidden card" }.map { c -> c.name } }.orEmpty() to
                seat?.takeIf { it.deckLoaded }?.commanderName?.takeIf(String::isNotBlank)
        }
    }

    /** Loads the card library and builds an offline game's seats (on a large-stack thread). */
    suspend fun prepareOfflineSetup(names: List<String>, commander: String?, aiDeckId: String?): Pair<com.arcanatable.engine.OfflineSetup, com.wingedsheep.engine.registry.CardRegistry> =
        withContext(Dispatchers.IO) {
            var result: Pair<com.arcanatable.engine.OfflineSetup, com.wingedsheep.engine.registry.CardRegistry>? = null
            var failure: Throwable? = null
            val worker = Thread(null, {
                try {
                    val library = offlineLibrary()
                    val decks = runCatching { context.assets.open("argentum/commander-decks.json").bufferedReader().use { it.readText() } }.getOrNull()
                    result = com.arcanatable.engine.OfflineGameSetup.build(library, lobbyPlayerName.ifBlank { "You" }, names, commander, decks, aiDeckId = aiDeckId)
                } catch (t: Throwable) { failure = t }
            }, "argentum-offline-setup", 256L shl 20)
            worker.start(); worker.join()
            failure?.let { throw it }
            result!!
        }

    fun offlineLibrary() = com.arcanatable.engine.OfflineCardLibrary({ path -> runCatching { context.assets.open(path) }.getOrNull() })

    /**
     * Offline game: Argentum's engine and AI run inside the app (engine/LocalArgentumHost). Your
     * imported Commander deck plays one of Argentum's premade Commander decks when the full card
     * library ships in the app; otherwise a Portal duel. Same table, same messages, no server.
     */
    fun startOfflineGame() {
        if (offlineStarting || argentumEnabled) return
        offlineStarting = true
        argentumLastError = null
        scope.launch {
            try {
                val (names, commander) = activeDeckCards()
                val started = System.currentTimeMillis()
                val prepared = prepareOfflineSetup(names, commander, offlineAiDeckId)
                val (setup, registry) = prepared
                recordDiagnostic("Offline game prepared in ${System.currentTimeMillis() - started} ms: ${setup.you.commander ?: "Portal"} vs ${setup.aiDeckName}", DiagnosticCategory.ENGINE)
                setup.notes.forEach { recordDiagnostic("Offline: $it", DiagnosticCategory.ENGINE) }
                argentumSocket.localHostFactory = { emit -> com.arcanatable.engine.LocalArgentumHost(registry, setup.you, setup.ai, emit = emit).also { it.quietOpponentTurns = quietOpponentTurns } }
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

}
