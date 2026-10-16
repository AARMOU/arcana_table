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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

private data class TableColors(val background: Color, val panel: Color, val raised: Color, val accent: Color, val text: Color, val muted: Color, val counter: Color)
private val darkTableColors = TableColors(Color(0xFF101615), Color(0xFF192220), Color(0xFF26332F), Color(0xFF89E3B0), Color(0xFFEAF1ED), Color(0xFFA4B1AB), Color(0xFFFFD166))
private val lightTableColors = TableColors(Color(0xFFF2F5F1), Color(0xFFFFFFFF), Color(0xFFE4EBE6), Color(0xFF196E4A), Color(0xFF17231D), Color(0xFF5E6C64), Color(0xFF8D6500))
private val LocalTableColors = staticCompositionLocalOf { darkTableColors }
private var appInForeground by mutableStateOf(true)
private const val GAME_ALERT_CHANNEL = "arcana_game_alerts"
private const val DEFAULT_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.arcanamtg.workers.dev"
private const val LEGACY_EXAMPLE_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.example.workers.dev"

private fun isLegacyExampleCloudEndpoint(endpoint: String): Boolean =
    endpoint.trim().trimEnd('/').equals(LEGACY_EXAMPLE_CLOUD_ROOM_ENDPOINT, ignoreCase = true)

private fun postGameAlert(context: Context, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(GAME_ALERT_CHANNEL, "Game activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Turn and play updates while Arcana Table is in the background."
        })
    }
    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = Notification.Builder(context, GAME_ALERT_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text.take(180))
        .setCategory(Notification.CATEGORY_EVENT)
        .setAutoCancel(true)
        .setContentIntent(openApp)
        .build()
    manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
}
private val Ink: Color @Composable get() = LocalTableColors.current.background
private val Panel: Color @Composable get() = LocalTableColors.current.panel
private val PanelLight: Color @Composable get() = LocalTableColors.current.raised
private val Mint: Color @Composable get() = LocalTableColors.current.accent
private val SoftWhite: Color @Composable get() = LocalTableColors.current.text
private val Muted: Color @Composable get() = LocalTableColors.current.muted
private val CounterGold: Color @Composable get() = LocalTableColors.current.counter

private data class TutorialStop(
    val title: String,
    val explanation: String,
    val target: String,
    val view: String,
    val executeAction: Boolean = true
)
private val tutorialStops = listOf(
    TutorialStop("WELCOME TO ARCANA TABLE!", "Welcome to Arcana Table! Please follow this brief tutorial to show you everything Arcana has to offer!", "", "HOME", executeAction = false),
    TutorialStop("PLAY MAGIC TOGETHER", "Arcana Table lets you play Magic: The Gathering with up to four people, anywhere in the world!", "", "HOME", executeAction = false),
    TutorialStop("CREATE ROOM", "Create a new room. You choose the connection type, invite players, load decks, and start the game from the lobby.", "home-create", "HOME"),

    TutorialStop("CREATE ROOM", "Choose this to host a room.", "lobby-create", "LOBBY"),
    TutorialStop("JOIN ROOM", "Choose this to enter a room.", "lobby-join", "LOBBY"),
    TutorialStop("LOCAL WI-FI", "Play with devices on your Wi-Fi.", "lobby-local", "LOBBY"),
    TutorialStop("INTERNET", "Play with people on different networks using the secure room service.", "lobby-internet", "LOBBY"),
    TutorialStop("ROOM SERVICE ADDRESS", "The Arcana room address is already filled in. You do not need to tap or change it. Continue past this step. Only edit it if you run your own room server and know its address.", "", "LOBBY", executeAction = false),
    TutorialStop("CREATE OR JOIN", "Join Game needs a room code and password. Tap Next to continue without connecting.", "lobby-submit", "LOBBY", executeAction = false),
    TutorialStop("CANCEL JOIN", "Stop the connection attempt.", "lobby-cancel", "LOBBY", executeAction = false),
    TutorialStop("ROOM CODE", "Share this code and password so friends can join. Use Share Room to send the invite in one message.", "lobby-copy", "LOBBY", executeAction = false),
    TutorialStop("SHARE ROOM", "Send the room code, password, and connection address with your phone's share menu.", "lobby-share", "LOBBY", executeAction = false),
    TutorialStop("COPY ADDRESS", "Copy the host address for local players.", "lobby-copy-ip", "LOBBY", executeAction = false),
    TutorialStop("IMPORT DECK", "Load a deck for this seat.", "lobby-import", "LOBBY"),
    TutorialStop("READY UP", "Tell the host you are ready.", "lobby-ready", "LOBBY", executeAction = false),
    TutorialStop("ADD AI OPPONENT", "Add a computer player.", "lobby-ai", "LOBBY", executeAction = false),
    TutorialStop("RANDOMIZE ORDER", "Choose who takes the first turn.", "lobby-order", "LOBBY", executeAction = false),
    TutorialStop("EXPORT DIAGNOSTIC LOG", "Save connection details for troubleshooting.", "lobby-diagnostics", "LOBBY", executeAction = false),
    TutorialStop("START GAME", "Begin once every player is ready.", "lobby-start", "LOBBY", executeAction = false),
    TutorialStop("CLOSE ROOM", "Leave or close this room.", "lobby-leave", "LOBBY", executeAction = false),
    TutorialStop("CLOSE LOBBY", "Return to the home screen.", "lobby-close", "LOBBY"),

    TutorialStop("JOIN ROOM", "Join a friend's room. It opens this same lobby screen with Join Room already selected.", "home-join", "HOME"),
    TutorialStop("JOIN IS SELECTED", "This is the same lobby as Create Room. The Join Room choice is selected for you, so enter the host's room details.", "lobby-join", "LOBBY", executeAction = false),
    TutorialStop("RETURN HOME", "Close the lobby to continue the guide.", "lobby-close", "LOBBY"),
    TutorialStop("CONTINUE TO TABLE", "Open the game table on this device. The Table view shows everyone; the player view gives you more controls.", "home-continue", "HOME"),

    TutorialStop("SESSION TIMER", "Tap the timer to open timer controls.", "timer", "TABLE"),
    TutorialStop("CHAT", "Open messages for your room.", "chat-button", "TABLE"),
    TutorialStop("APP MENU", "Open decks, game tools, room controls, and options.", "main-menu", "TABLE"),
    TutorialStop("TABLE VIEW", "See every player at once.", "table-tab", "TABLE"),
    TutorialStop("PLAYER VIEW", "Open this player’s board.", "player-tab", "TABLE"),
    TutorialStop("PLAYER BOARD", "Tap a player panel to inspect their board.", "player-card", "TABLE"),
    TutorialStop("NEXT STEP", "Move to the next phase.", "table-next", "TABLE", executeAction = false),
    TutorialStop("PASS TURN", "Finish your turn and pass play.", "table-pass", "TABLE", executeAction = false),
    TutorialStop("IMPORT DECK", "Load a deck for the selected player.", "table-import", "TABLE"),

    TutorialStop("PLAYER STATUS", "See whose turn it is and the current phase.", "player-status", "PLAYER"),
    TutorialStop("SESSION TIMER", "Tap the timer to open timer controls.", "timer", "PLAYER"),
    TutorialStop("CHAT", "Open room messages.", "chat-button", "PLAYER"),
    TutorialStop("APP MENU", "Open decks, game tools, room controls, and options.", "main-menu", "PLAYER"),
    TutorialStop("LOWER LIFE", "Subtract one life.", "life-down", "PLAYER", executeAction = false),
    TutorialStop("RAISE LIFE", "Add one life.", "life-up", "PLAYER", executeAction = false),
    TutorialStop("MANA POOL", "Tap the MANA status to track or spend mana.", "mana-status", "PLAYER"),
    TutorialStop("CAST COMMANDER", "Cast your commander from the command zone.", "commander-cast", "PLAYER", executeAction = false),
    TutorialStop("NEXT STEP", "Move to the next phase.", "next-step", "PLAYER", executeAction = false),
    TutorialStop("PASS TURN", "Finish your turn and pass play.", "pass-turn", "PLAYER", executeAction = false),
    TutorialStop("LIBRARY", "Search or browse this library.", "zone-library", "PLAYER"),
    TutorialStop("HAND", "Open this player’s hand.", "hand-button", "PLAYER"),
    TutorialStop("HAND ZONE", "Open the hand from this zone button.", "zone-hand", "PLAYER"),
    TutorialStop("GRAVEYARD", "View cards in the graveyard.", "zone-graveyard", "PLAYER"),
    TutorialStop("EXILE", "View exiled cards.", "zone-exile", "PLAYER"),
    TutorialStop("REVEALED", "View cards revealed to the table.", "zone-revealed", "PLAYER"),
    TutorialStop("DRAW", "Draw one card.", "draw-action", "PLAYER", executeAction = false),
    TutorialStop("COMBAT", "Set attackers, blockers, and damage.", "combat-action", "PLAYER", executeAction = false),
    TutorialStop("MORE", "Open more player and game tools.", "more-actions", "PLAYER"),
    TutorialStop("ADD TOKEN", "Open MORE, then choose Add token.", "token-action", "PLAYER", executeAction = false),
    TutorialStop("STACK AND PRIORITY", "Open spells and abilities waiting to resolve.", "stack-open", "PLAYER"),
    TutorialStop("BACK TO TABLE", "Return to the full table.", "player-back-table", "PLAYER"),
    TutorialStop("TAP OR INSPECT A CARD", "Tap to tap or untap; long-press for details.", "battlefield", "PLAYER", executeAction = false),

    TutorialStop("CARD DETAILS", "Tap a card to see its actions; long-press for full text.", "hand-card", "HAND"),
    TutorialStop("PLAY CARD ACTIONS", "Choose what to do with this card.", "hand-actions", "HAND", executeAction = false),

    TutorialStop("THEME", "Switch between light and dark.", "options-theme", "OPTIONS", executeAction = false),
    TutorialStop("REDUCE SWIPE ANIMATION", "Use shorter screen transitions.", "options-motion", "OPTIONS", executeAction = false),
    TutorialStop("CARD SIZE", "Choose how large cards appear.", "options-card-size", "OPTIONS", executeAction = false),
    TutorialStop("RETURN TO TABLE", "Show the table after passing your turn.", "options-return-table", "OPTIONS", executeAction = false),
    TutorialStop("OPEN HAND AFTER DRAW", "Show your hand after drawing.", "options-open-hand", "OPTIONS", executeAction = false),
    TutorialStop("DRAW AUTOMATICALLY", "Draw when the draw step begins.", "options-auto-draw", "OPTIONS", executeAction = false),
    TutorialStop("UNTAP AUTOMATICALLY", "Untap your permanents at turn start.", "options-auto-untap", "OPTIONS", executeAction = false),
    TutorialStop("TURN TIMER DISPLAY", "Show or hide the turn timer.", "options-timer", "OPTIONS", executeAction = false),
    TutorialStop("BACKGROUND ALERTS", "Get a notification when the game changes.", "options-alerts", "OPTIONS", executeAction = false),
    TutorialStop("CLEAR ALL DATA", "Reset game settings; your decks and guide progress stay.", "clear-data", "OPTIONS", executeAction = false),
    TutorialStop("DEVELOPER OPTIONS", "Open test tools.", "options-developer", "OPTIONS", executeAction = false),
    TutorialStop("CLOSE OPTIONS", "Return to the table.", "options-close", "OPTIONS"),

    TutorialStop("CAST FROM HAND", "Open your hand to choose a spell.", "stack-cast", "STACK"),
    TutorialStop("PUT ON STACK", "Add a manual ability or effect.", "stack-add", "STACK", executeAction = false),
    TutorialStop("PASS PRIORITY", "Let the next player respond. The AI passes automatically when it has no response.", "stack-pass", "STACK", executeAction = false),
    TutorialStop("RESOLVE TOP", "Resolve the top item after everyone passes. The newest item resolves first.", "stack-resolve", "STACK", executeAction = false),
    TutorialStop("STACK ITEM", "Tap an item to inspect it. If the AI has no response, it passes so the stack can resolve.", "stack-item", "STACK"),
    TutorialStop("CLOSE STACK", "Close the stack view.", "stack-close", "STACK"),

    TutorialStop("SEND MESSAGE", "Send a message to the table.", "chat-send", "CHAT", executeAction = false),
    TutorialStop("CHAT CLOSE", "Close the chat panel.", "chat-close", "CHAT"),

    TutorialStop("CLOSE GAME LOG", "Return to the table.", "log-close", "GAME_LOG"),

    TutorialStop("DECKS MENU", "Open deck import, editing, and saved decks.", "menu-decks", "MENU_MAIN"),
    TutorialStop("GAME TOOLS MENU", "Open tracking tools, the stack, log, and rules.", "menu-tools", "MENU_MAIN"),
    TutorialStop("ROOM AND GAME MENU", "Open room, game, and reset controls.", "menu-game", "MENU_MAIN"),
    TutorialStop("OPTIONS MENU", "Open preferences and Clear All Data.", "menu-options", "MENU_MAIN"),
    TutorialStop("IMPORT DECK MENU", "Choose or paste a deck list.", "menu-import", "MENU_DECKS"),
    TutorialStop("EDIT DECK MENU", "Change the selected deck.", "menu-edit", "MENU_DECKS"),
    TutorialStop("SAVED DECKS MENU", "Load or remove a saved deck.", "menu-saved", "MENU_DECKS"),
    TutorialStop("ADD AI", "Add a computer seat to a local game.", "menu-add-ai", "MENU_TOOLS", executeAction = false),
    TutorialStop("ADD TOKEN", "Open the token picker.", "menu-add-token", "MENU_TOOLS", executeAction = false),
    TutorialStop("MANA POOL", "Track colored mana for this player.", "menu-mana", "MENU_TOOLS", executeAction = false),
    TutorialStop("SEARCH LIBRARY", "Search or browse cards in the library.", "menu-search", "MENU_TOOLS", executeAction = false),
    TutorialStop("STACK MENU", "Open spells and abilities awaiting responses.", "menu-stack", "MENU_TOOLS"),
    TutorialStop("GAME LOG MENU", "Review the recorded game actions.", "menu-log", "MENU_TOOLS"),
    TutorialStop("RULES MENU", "Search the Magic rules reference.", "menu-rules", "MENU_TOOLS"),
    TutorialStop("EXPORT DIAGNOSTICS", "Save a connection log for troubleshooting.", "menu-export", "MENU_TOOLS", executeAction = false),
    TutorialStop("ROOM HOME MENU", "Return to the home screen.", "menu-home", "MENU_GAME"),
    TutorialStop("START GAME MENU", "Begin a new local game.", "menu-start-game", "MENU_GAME", executeAction = false),
    TutorialStop("GAME LOBBY MENU", "Open room and player controls.", "menu-lobby", "MENU_GAME"),
    TutorialStop("END GAME MENU", "Ask all players to agree to end the game.", "menu-end", "MENU_GAME", executeAction = false),
    TutorialStop("LEAVE GAME MENU", "Open the lobby to leave or hand off the room.", "menu-leave", "MENU_GAME"),
    TutorialStop("RESET GAME MENU", "Start a fresh game with these decks.", "menu-reset", "MENU_GAME", executeAction = false),
    TutorialStop("CLEAR GAME DATA MENU", "Open the confirmation before clearing data.", "menu-clear", "MENU_GAME", executeAction = false),

    TutorialStop("CHOOSE FILE", "Pick a deck list file.", "deck-choose-file", "DECK_IMPORT", executeAction = false),
    TutorialStop("RANDOM DECK", "Load an example deck list.", "deck-random", "DECK_IMPORT", executeAction = false),
    TutorialStop("LOAD SAVED DECK", "Open your saved deck list.", "deck-load-saved", "DECK_IMPORT"),
    TutorialStop("IMPORT DECK", "Replace this player’s deck.", "deck-import", "DECK_IMPORT", executeAction = false),
    TutorialStop("CANCEL IMPORT", "Close without replacing this deck.", "deck-cancel", "DECK_IMPORT", executeAction = false),
    TutorialStop("LOAD A SAVED DECK", "Put this deck into the import box.", "saved-load", "SAVED_DECKS", executeAction = false),
    TutorialStop("DELETE A SAVED DECK", "Remove this saved deck from the device.", "saved-delete", "SAVED_DECKS", executeAction = false),
    TutorialStop("CLOSE SAVED DECKS", "Return to deck import.", "saved-close", "SAVED_DECKS")
)

data class GameCard(
    val id: Int,
    val name: String,
    val type: String,
    val color: Color,
    val tapped: Boolean = false,
    val imageUrl: String? = null,
    val isToken: Boolean = false,
    val counters: Map<String, Int> = emptyMap(),
    val isFaceDown: Boolean = false,
    val oracleText: String? = null,
    val manaValue: Double? = null,
    val importIssue: String? = null,
    val artCropUrl: String? = null,
    val manaCost: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val keywords: List<String> = emptyList(),
    val chosenCreatureType: String? = null,
    val isCommander: Boolean = false
)

private data class LandEntryPrompt(val playerIndex: Int, val card: GameCard, val isShockLand: Boolean, val conditionText: String)

private fun shockLandChoiceRequired(card: GameCard): Boolean {
    val text = card.oracleText.orEmpty().lowercase()
    return card.type.contains("land", ignoreCase = true) &&
        ("pay 2 life" in text || "pay 2 life" in card.name.lowercase()) &&
        ("enters tapped" in text || "enters the battlefield tapped" in text)
}

private fun landEntryCondition(card: GameCard): String? {
    if (!card.type.contains("land", ignoreCase = true)) return null
    val text = card.oracleText.orEmpty()
    val relevant = text.lines().firstOrNull { line ->
        val lower = line.lowercase()
        ("enters tapped" in lower || "enters the battlefield tapped" in lower) &&
            ("unless" in lower || "if" in lower || "pay" in lower)
    }
    return relevant?.trim()?.takeIf(String::isNotBlank)
}

private fun landAlwaysEntersTapped(card: GameCard): Boolean {
    val text = card.oracleText.orEmpty().lowercase()
    return card.type.contains("land", ignoreCase = true) &&
        ("enters tapped" in text || "enters the battlefield tapped" in text) &&
        landEntryCondition(card) == null
}

private data class ManaChoicePrompt(
    val playerIndex: Int,
    val cardName: String,
    val options: List<ManaProductionOption>
)

private data class CreatureTypeChoicePrompt(val playerIndex: Int, val cardId: Int, val cardName: String)

internal fun needsCreatureTypeChoice(card: GameCard): Boolean =
    !card.isFaceDown && Regex("\\bas this enters(?: the battlefield)?, choose a creature type\\b", RegexOption.IGNORE_CASE)
        .containsMatchIn(card.oracleText.orEmpty())

private data class AiManaPlan(val landIds: Set<Int>, val remainingPool: Map<String, Int>)

private fun planAiMana(player: TablePlayer, card: GameCard): AiManaPlan? {
    val cost = card.manaCost ?: return if (card.manaValue == 0.0) AiManaPlan(emptySet(), player.manaPool) else null
    val symbols = simpleManaSymbols(cost) ?: return null
    val required = symbols.filter { it in setOf("W", "U", "B", "R", "G", "C") }.groupingBy { it }.eachCount()
    val generic = symbols.mapNotNull { it.toIntOrNull() }.sum()
    val available = player.battlefield.filter { it.type.contains("land", true) && !it.tapped }.toMutableList()
    val pool = player.manaPool.toMutableMap()
    val usedLandIds = linkedSetOf<Int>()
    fun addOption(land: GameCard, option: ManaProductionOption) {
        option.pool.forEach { (color, count) -> pool[color] = ((pool[color] ?: 0) + count).coerceAtMost(99) }
        available.removeAll { it.id == land.id }
        usedLandIds += land.id
    }
    for (color in listOf("W", "U", "B", "R", "G", "C")) {
        while ((pool[color] ?: 0) < (required[color] ?: 0)) {
            val choice = available.asSequence().flatMap { land ->
                manaProductionOptions(land).asSequence().filter { (it.pool[color] ?: 0) > 0 }.map { land to it }
            }.maxByOrNull { (_, option) -> (option.pool[color] ?: 0) * 10 - (option.pool.values.sum() - (option.pool[color] ?: 0)) } ?: return null
            addOption(choice.first, choice.second)
        }
    }
    val totalRequired = required.values.sum() + generic
    while (pool.values.sum() < totalRequired) {
        val choice = available.asSequence().flatMap { land -> manaProductionOptions(land).asSequence().map { land to it } }
            .maxByOrNull { (_, option) -> option.pool.values.sum() } ?: return null
        addOption(choice.first, choice.second)
    }
    val remaining = paySimpleManaCost(pool, cost) ?: return null
    return AiManaPlan(usedLandIds, remaining)
}

private fun aiCardPriority(card: GameCard): Double {
    val text = card.oracleText.orEmpty().lowercase()
    val type = card.type.lowercase()
    var score = (card.manaValue ?: 0.0).coerceAtMost(8.0) * 0.25
    if ("creature" in type) score += 2.3
    if ("artifact" in type || "enchantment" in type || "planeswalker" in type || "battle" in type) score += 1.1
    if ("draw" in text && "card" in text) score += 1.8
    if ("search your library" in text && ("land" in text || "mana" in text)) score += 1.6
    if (listOf("destroy target", "exile target", "counter target").any { it in text }) score += 1.4
    if ("whenever" in text || "at the beginning" in text) score += 0.6
    if ("instant" in type || "sorcery" in type) score -= 0.5
    return score
}

private fun manaProductionOptions(card: GameCard): List<ManaProductionOption> {
    return manaProductionPools(card.name, card.type, card.oracleText).map(::ManaProductionOption)
}

data class TablePlayer(
    val name: String,
    val id: String = java.util.UUID.randomUUID().toString(),
    val isOnline: Boolean = false,
    val commanderName: String? = null,
    val commanderCard: GameCard? = null,
    val isComputer: Boolean = false,
    val deckLoaded: Boolean = false,
    val mulligansTaken: Int = 0,
    val openingHandKept: Boolean = false,
    val life: Int = 40,
    val manaPool: Map<String, Int> = emptyMap(),
    val library: List<GameCard> = emptyList(),
    val hand: List<GameCard> = emptyList(),
    val battlefield: List<GameCard> = emptyList(),
    val graveyard: List<GameCard> = emptyList(),
    val exile: List<GameCard> = emptyList(),
    val revealed: List<GameCard> = emptyList(),
    val handRevealedToAll: Boolean = false,
    val playerCounters: Map<String, Int> = emptyMap(),
    val commanderDamageReceived: Map<String, Int> = emptyMap(),
    val commanderCastCount: Int = 0,
    val isEliminated: Boolean = false,
    val eliminationReason: String? = null
)

data class StackAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val controllerId: String,
    val kind: String = "SPELL",
    val card: GameCard? = null,
    val targetText: String = card?.oracleText.orEmpty(),
    val targetCount: TargetCountRange? = parseTargetCountRange(targetText),
    val targets: List<StackTarget> = emptyList(),
    val targetsConfirmed: Boolean = targetCount == null
)

private data class SavedDeck(val name: String, val text: String)

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
    CompositionLocalProvider(LocalTableColors provides colors) {
        MaterialTheme(colorScheme = scheme) { TableScreenContent(darkMode, onToggleTheme = {
            darkMode = !darkMode
            optionPrefs.edit().putBoolean("dark_mode", darkMode).apply()
        }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableScreenContent(darkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val automaticActionSnackbar = remember { SnackbarHostState() }
    val optionPrefs = remember { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    val tutorialPrefs = remember { context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE) }
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
    var savedDecksOpen by remember { mutableStateOf(false) }
    var localDeckSyncConfirmed by remember { mutableStateOf(false) }
    val diagnosticEvents = remember {
        mutableStateListOf<String>().apply {
            addAll(optionPrefs.getString("diagnostic_events", "").orEmpty().lineSequence().filter(String::isNotBlank).toList().takeLast(500))
        }
    }
    var diagnosticEventSequence by remember { mutableIntStateOf(diagnosticEvents.size) }
    var lastDiagnosticZoneSignature by remember { mutableStateOf<String?>(null) }
    var lastDiagnosticZoneSnapshot by remember { mutableStateOf<List<TablePlayer>?>(null) }
    var inboundSnapshotSequence by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var randomDeckLoading by remember { mutableStateOf(false) }
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
                if (page == 0) showHand = false
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
    var developerOptionsOpen by remember { mutableStateOf(false) }
    var tutorialOpen by remember { mutableStateOf(false) }
    var tutorialIndex by remember { mutableIntStateOf(0) }
    val tutorialParentIndices = remember { mutableStateListOf<Int>() }
    val tutorialBounds = remember { mutableStateMapOf<String, Rect>() }
    val previouslySeenTutorialScreens = remember {
        val current = tutorialPrefs.getStringSet("first_visit_guides_v2", null)
        val legacy = optionPrefs.getStringSet("first_visit_guides_v2", null)
        val seen = current ?: legacy
        if (current == null && legacy != null) {
            tutorialPrefs.edit().putStringSet("first_visit_guides_v2", legacy.toSet()).apply()
            optionPrefs.edit().remove("first_visit_guides_v2").apply()
        }
        seen.orEmpty().toSet()
    }
    var tutorialSeenScreens by remember { mutableStateOf(previouslySeenTutorialScreens) }
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
    var argentumEnabled by rememberSaveable { mutableStateOf(false) }
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
    var argentumMulliganPrompt by remember { mutableStateOf(false) }
    var argentumBottomCardIds by remember { mutableStateOf<List<String>?>(null) }
    var argentumBottomCardCount by remember { mutableIntStateOf(0) }
    var argentumBottomSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var argentumLastError by remember { mutableStateOf<String?>(null) }
    var argentumActionChoices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var argentumTargetAction by remember { mutableStateOf<JSONObject?>(null) }
    var argentumTargetChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var argentumSelectedTarget by remember { mutableStateOf<String?>(null) }
    val argentumSocket = remember(context) {
        ArgentumGameSocket(
            preferences = context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE),
            onStatus = { status -> argentumStatus = status; if (status != ArgentumConnectionStatus.CONNECTED) argentumAwaitingUpdate = false },
            onMessage = { argentumInbound = it },
            onError = { argentumLastError = it; message = it; argentumAwaitingUpdate = false }
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

    fun recordDiagnostic(event: String) {
        val safeEvent = event
            .replace(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}"), "<ip>")
            .replace(Regex("(?i)(password\\s*[:=]\\s*)\\S+"), "$1<redacted>")
            .replace(Regex("(?i)((?:room|join)\\s*code\\s*[:=]\\s*)[a-z0-9-]+"), "$1<redacted>")
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        diagnosticEventSequence += 1
        diagnosticEvents.add("#${diagnosticEventSequence.toString().padStart(5, '0')}  $timestamp  $safeEvent")
        while (diagnosticEvents.size > 500) diagnosticEvents.removeAt(0)
        val contextLine = "$timestamp · $safeEvent · ${if (appInForeground) "foreground" else "background"} · ${if (argentumEnabled || roomHost != null || roomClient != null || cloudRoomClient != null) "online" else "offline"} · ${if (showOverview) "table" else "player view"} · P${(selectedPlayer + 1).coerceAtLeast(1)} · turn $turnNumber ${phaseSteps.getOrNull(phaseIndex).orEmpty()}"
        optionPrefs.edit().putString("diagnostic_events", diagnosticEvents.joinToString("\n"))
            .putString("last_diagnostic_context", contextLine).apply()
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
        return sent
    }

    fun sendArgentumPriority() {
        if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous action."; return }
        val snapshot = argentumSnapshot ?: return
        val pass = snapshot.legalActions.firstOrNull { it.optJSONObject("action")?.optString("type") == "PassPriority" }
        val action = pass?.optJSONObject("action")
        val epoch = pass?.optString("interactionEpoch")?.takeUnless { it.isNullOrBlank() || it == "null" }
            ?: snapshot.interactionEpoch
        if (action == null || epoch == null) {
            message = "Argentum has not offered a pass-priority action right now."
            return
        }
        if (!submitArgentumAction(action, epoch)) message = "Could not send the Argentum action. Check your connection."
    }

    fun submitArgentumDecision(response: JSONObject) {
        if (argentumAwaitingUpdate) { message = "Waiting for Argentum to confirm the previous choice."; return }
        val pending = argentumPendingDecision ?: return
        val epoch = argentumSnapshot?.interactionEpoch ?: return
        val action = JSONObject().put("type", "SubmitDecision").put("playerId", pending.optString("playerId"))
            .put("response", JSONObject(response.toString()).put("decisionId", pending.optString("id")))
        if (argentumSocket.submitLegalAction(action, epoch)) {
            argentumAwaitingUpdate = true
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
        if (choices.isEmpty()) message = "Argentum has no legal action for ${card.name} right now."
        else argentumActionChoices = choices
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
            appendLine("Action context: ${crashPrefs.getString("last_crash_context", "unknown")}")
            appendLine("Last uncaught app crash:")
            appendLine(crash)
        }
        appendLine()
        appendLine("Zone totals include counts only. Opponents' hidden card identities are excluded; own-deck duplicate summaries may include card names.")
        appendLine("Recent event log (passwords, IP addresses, and room codes redacted):")
        diagnosticEvents.forEach { appendLine(it) }
    }

    val diagnosticExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            recordDiagnostic("Diagnostic export canceled")
        } else {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(diagnosticReport()) }
                    ?: error("Could not open the selected file.")
            }
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

    LaunchedEffect(argentumInbound) {
        val incoming = argentumInbound ?: return@LaunchedEffect
        when (incoming.optString("type")) {
            "connected", "reconnected" -> {
                argentumPlayerId = incoming.optString("playerId").takeUnless { it.isBlank() || it == "null" }
                if (incoming.optString("context") == "game") argentumSocket.requestResync()
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
                recordDiagnostic("Argentum game started")
            }
            "stateUpdate" -> {
                val state = incoming.optJSONObject("state")
                if (state != null) {
                    argentumAwaitingUpdate = false
                    if (argentumLocalPlayersBackup == null) argentumLocalPlayersBackup = loadSavedPlayers(context) ?: players.toList()
                    val snapshot = ArgentumTableSnapshot.parse(state, incoming.optJSONArray("legalActions") ?: JSONArray(), incoming.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" })
                    val nextDecision = incoming.optJSONObject("pendingDecision")
                    if (nextDecision?.optString("id") != argentumPendingDecision?.optString("id")) {
                        argentumDecisionSelection = emptySet()
                        argentumDecisionColors = emptySet()
                        argentumDecisionTargets = emptyMap()
                    }
                    argentumPendingDecision = nextDecision
                    val firstSnapshot = argentumSnapshot == null
                    argentumSnapshot = snapshot
                    players = snapshot.players
                    localSeat = snapshot.viewingPlayer
                    activePlayer = snapshot.activePlayer
                    if (firstSnapshot) selectedPlayer = snapshot.viewingPlayer
                    turnNumber = snapshot.turnNumber
                    phaseIndex = phaseSteps.indexOfFirst { it.equals(snapshot.stepName, true) }.takeIf { it >= 0 }
                        ?: phaseSteps.indexOfFirst { it.equals(snapshot.phaseName, true) }.takeIf { it >= 0 } ?: 3
                    gameEndedOnline = snapshot.gameOver
                    gameStartedOnline = true
                    homeOpen = false
                    lobbyOpen = false
                    gameTimerRunning = true
                    turnTimerRunning = true
                    val entries = incoming.optJSONArray("events")
                    if (entries != null && entries.length() > 0) {
                        val eventLines = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("description")?.takeIf(String::isNotBlank) }
                        if (eventLines.isNotEmpty()) gameLog = (gameLog + eventLines).takeLast(250)
                    }
                    recordDiagnostic("Argentum authoritative state applied: ${snapshot.players.size} players, turn ${snapshot.turnNumber}, ${snapshot.legalActions.size} legal actions")
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
                val handIds = incoming.optJSONArray("hand") ?: JSONArray()
                val cardInfo = incoming.optJSONObject("cards") ?: JSONObject()
                val hand = (0 until handIds.length()).mapNotNull { index ->
                    val serverId = handIds.optString(index)
                    val detail = cardInfo.optJSONObject(serverId) ?: return@mapNotNull null
                    GameCard(
                        id = (serverId.hashCode() and 0x7fffffff).coerceAtLeast(1),
                        name = detail.optString("name", "Card"), type = "CARD", color = Color(0xFFB8C4BE),
                        imageUrl = detail.optString("imageUri").takeUnless { it.isBlank() || it == "null" }
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
            "error" -> { argentumLastError = incoming.optString("message", "Argentum returned an error."); message = argentumLastError; argentumAwaitingUpdate = false }
        }
    }
    LaunchedEffect(argentumAwaitingUpdate) {
        if (argentumAwaitingUpdate) {
            delay(8_000)
            if (argentumAwaitingUpdate) {
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
        if (tutorialOpen) {
            tutorialStops.getOrNull(tutorialIndex)?.let { step ->
                tutorialSeenScreens = tutorialSeenScreens + step.view
                tutorialPrefs.edit().putStringSet("first_visit_guides_v2", tutorialSeenScreens.toSet()).apply()
            }
            tutorialOpen = false
        }
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

    fun importDeck(text: String) {
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
                if (importSaveToLibrary) {
                    val savedName = importSaveName.trim().ifBlank { importedCommander?.name ?: importedTarget?.name ?: "Saved deck" }
                    val saved = SavedDeck(savedName, importText.trim())
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

    fun changeTutorialStep(index: Int) {
        tutorialIndex = index.coerceIn(tutorialStops.indices)
        tutorialBounds.clear()
    }

    fun currentTutorialView(): String = when {
        topMenuExpanded -> when (topMenuSection) {
            "decks" -> "MENU_DECKS"
            "tools" -> "MENU_TOOLS"
            "game" -> "MENU_GAME"
            else -> "MENU_MAIN"
        }
        lobbyOpen -> "LOBBY"
        optionsOpen || developerOptionsOpen -> "OPTIONS"
        stackOpen -> "STACK"
        savedDecksOpen -> "SAVED_DECKS"
        importOpen -> "DECK_IMPORT"
        chatOpen -> "CHAT"
        logOpen -> "GAME_LOG"
        homeOpen -> "HOME"
        showHand -> "HAND"
        showOverview -> "TABLE"
        else -> "PLAYER"
    }

    fun showTutorialView(view: String) {
        topMenuExpanded = false
        topMenuSection = null
        lobbyOpen = false
        optionsOpen = false
        developerOptionsOpen = false
        stackOpen = false
        chatOpen = false
        logOpen = false
        importOpen = false
        savedDecksOpen = false
        deckEditorOpen = false
        homeOpen = false
        showOverview = true
        showHand = false
        when (view) {
            "HOME" -> homeOpen = true
            "LOBBY" -> lobbyOpen = true
            "TABLE" -> Unit
            "PLAYER" -> showOverview = false
            "HAND" -> { showOverview = false; showHand = true }
            "OPTIONS" -> optionsOpen = true
            "STACK" -> stackOpen = true
            "CHAT" -> chatOpen = true
            "GAME_LOG" -> logOpen = true
            "DECK_IMPORT" -> importOpen = true
            "SAVED_DECKS" -> savedDecksOpen = true
            "MENU_MAIN" -> topMenuExpanded = true
            "MENU_DECKS" -> { topMenuExpanded = true; topMenuSection = "decks" }
            "MENU_TOOLS" -> { topMenuExpanded = true; topMenuSection = "tools" }
            "MENU_GAME" -> { topMenuExpanded = true; topMenuSection = "game" }
        }
    }

    fun goBackTutorial() {
        val previous = tutorialIndex - 1
        if (previous >= 0) {
            changeTutorialStep(previous)
            showTutorialView(tutorialStops[previous].view)
        }
    }

    fun startTutorial() {
        tutorialOpen = true
        tutorialIndex = 0
        tutorialParentIndices.clear()
        tutorialBounds.clear()
        topMenuExpanded = false
        topMenuSection = null
        lobbyOpen = false
        optionsOpen = false
        developerOptionsOpen = false
        stackOpen = false
        chatOpen = false
        logOpen = false
        importOpen = false
        savedDecksOpen = false
        deckEditorOpen = false
        handActionCard = null
        showHand = false
        showOverview = true
        homeOpen = true
    }

    fun markTutorialSeen(view: String) {
        tutorialSeenScreens = tutorialSeenScreens + view
        tutorialPrefs.edit().putStringSet("first_visit_guides_v2", tutorialSeenScreens.toSet()).apply()
    }

    fun guidedAction(target: String, action: () -> Unit) {
        val step = tutorialStops.getOrNull(tutorialIndex)
        if (tutorialOpen && step?.target == target) {
            markTutorialSeen(step.view)
            if (step.executeAction) action()
            val resultingView = currentTutorialView()
            val openedAnotherView = resultingView != step.view
            val isClosingView = target in setOf("lobby-close", "stack-close", "saved-close", "log-close", "chat-close", "options-close", "deck-cancel")
            val nextIndex = when {
                !openedAnotherView -> tutorialIndex + 1
                isClosingView && tutorialParentIndices.isNotEmpty() -> {
                    val parentIndex = tutorialParentIndices.removeAt(tutorialParentIndices.lastIndex)
                    (tutorialIndex + 1 until tutorialStops.size).firstOrNull { tutorialStops[it].view == resultingView }
                        ?: (parentIndex + 1).takeIf { it < tutorialStops.size }
                        ?: tutorialStops.size
                }
                openedAnotherView -> {
                    tutorialParentIndices.add(tutorialIndex)
                    (tutorialIndex + 1 until tutorialStops.size).firstOrNull { tutorialStops[it].view == resultingView }
                        ?: tutorialStops.indexOfFirst { it.view == resultingView }.takeIf { it >= 0 }
                        ?: tutorialIndex + 1
                }
                else -> tutorialIndex + 1
            }
            if (nextIndex < tutorialStops.size) {
                changeTutorialStep(nextIndex)
                val nextStep = tutorialStops[nextIndex]
                if (currentTutorialView() != nextStep.view) showTutorialView(nextStep.view)
            } else tutorialOpen = false
        } else {
            action()
            if (tutorialOpen && target == "menu-back") tutorialOpen = false
        }
    }

    fun advanceTutorial() {
        val step = tutorialStops.getOrNull(tutorialIndex) ?: return
        markTutorialSeen(step.view)
        val next = tutorialIndex + 1
        if (next < tutorialStops.size) {
            changeTutorialStep(next)
            showTutorialView(tutorialStops[next].view)
        } else tutorialOpen = false
    }

    fun openLobby(joining: Boolean) {
        topMenuExpanded = false
        lobbyOpen = true
        lobbyCreated = roomHost != null || (cloudRoomClient != null && localSeat == 0)
        lobbyJoinMode = joining && roomHost == null && roomClient == null && cloudRoomClient == null
        lobbyNotice = null
        lobbyTurnOrder = null
    }

    fun startArgentumPractice(vsAi: Boolean = true, joinLobbyCode: String? = null) {
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
            val submitted = (seat.library + seat.hand)
                .filterNot { it.isToken || it.name == "Hidden card" }
                .groupingBy { it.name.trim() }.eachCount().toMutableMap()
            submitted[commander] = (submitted[commander] ?: 0) + 1
            if (submitted.isEmpty()) { argentumLastError = "Your imported deck has no cards to submit."; argentumDeckValidationRunning = false; return@launch }
            try {
                val api = ArgentumDeckApi()
                var validation = withContext(Dispatchers.IO) { api.validate(submitted, commander, "COMMANDER") }
                val unsupported = linkedSetOf<String>()
                val replacementBasics = mutableListOf<String>()
                var checks = 0
                while (validation.unsupportedCards.isNotEmpty() && checks++ < 100) {
                    val newlyUnsupported = validation.unsupportedCards
                    unsupported += newlyUnsupported
                    val commanderUnsupported = newlyUnsupported.any { it.equals(commander, ignoreCase = true) }
                    if (commanderUnsupported) {
                        argentumLastError = "Argentum does not support your commander: $commander. Choose a supported commander to continue."
                        return@launch
                    }
                    val priorSize = submitted.size
                    submitted.keys.filter { name -> newlyUnsupported.any { it.equals(name, ignoreCase = true) } }.forEach(submitted::remove)
                    if (submitted.size == priorSize) break
                    validation = withContext(Dispatchers.IO) { api.validate(submitted, commander, "COMMANDER") }
                }
                if (unsupported.isNotEmpty() && !validation.valid) {
                    val missing = (100 - submitted.values.sum()).coerceAtLeast(0)
                    if (missing > 0) {
                        val basics = withContext(Dispatchers.IO) { api.supportedBasicLands(commander) }
                        if (basics.isEmpty()) {
                            argentumLastError = "Argentum rejected ${unsupported.joinToString()}; no supported basic lands were found to complete the deck."
                            return@launch
                        }
                        repeat(missing) { offset ->
                            val basic = basics[offset % basics.size]
                            submitted[basic] = (submitted[basic] ?: 0) + 1
                            replacementBasics += basic
                        }
                        validation = withContext(Dispatchers.IO) { api.validate(submitted, commander, "COMMANDER") }
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
                    val addedCards = replacementBasics.map { name ->
                        val type = "Basic Land — $name"
                        makeCard(name, type)
                    }
                    players = players.mapIndexed { index, player ->
                        if (index != localSeat) player else player.copy(
                            library = player.library.filterNot { card -> unsupported.any { it.equals(card.name, true) } } + addedCards,
                            hand = player.hand.filterNot { card -> unsupported.any { it.equals(card.name, true) } }
                        )
                    }
                    message = "Argentum removed ${unsupported.size} unsupported card name(s) and filled the deck with ${replacementBasics.size} supported basic lands."
                }
                argentumDeckForLobby = submitted
                argentumCommanderForLobby = commander
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
                argentumLastError = error.message ?: "Could not validate this deck with Argentum."
                recordDiagnostic("Argentum deck validation failed: ${argentumLastError}")
            } finally {
                argentumDeckValidationRunning = false
            }
        }
    }

    fun tutorialAnchor(key: String): Modifier = Modifier.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        if (tutorialBounds[key] != rect) tutorialBounds[key] = rect
    }

    val firstVisitScreen = when {
        topMenuExpanded -> when (topMenuSection) {
            "decks" -> "MENU_DECKS"
            "tools" -> "MENU_TOOLS"
            "game" -> "MENU_GAME"
            else -> "MENU_MAIN"
        }
        lobbyOpen -> "LOBBY"
        optionsOpen || developerOptionsOpen -> "OPTIONS"
        stackOpen -> "STACK"
        savedDecksOpen -> "SAVED_DECKS"
        importOpen -> "DECK_IMPORT"
        chatOpen -> "CHAT"
        logOpen -> "GAME_LOG"
        homeOpen -> "HOME"
        showHand -> "HAND"
        showOverview -> "TABLE"
        else -> "PLAYER"
    }
    LaunchedEffect(firstVisitScreen, tutorialOpen, tutorialSeenScreens) {
        // Screen visits never start the guide automatically. It is always opt-in.
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
            .then(if (tutorialOpen) Modifier.blur(4.dp) else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 10.dp, top = 8.dp, end = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                val inProgress = if (onlineMode) gameStartedOnline && !gameEndedOnline else localGameStarted
                Text(if (inProgress) "● GAME IN PROGRESS" else "● GAME NOT STARTED", color = if (inProgress) Mint else CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                if (argentumEnabled) Text("ARGENTUM · ${argentumStatus.name}", color = if (argentumStatus == ArgentumConnectionStatus.CONNECTED) Mint else CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.then(tutorialAnchor("timer"))) {
                Text("GAME ${formatClock(elapsedGameSeconds)} · TURN ${if (turnTimerVisible) formatClock(elapsedTurnSeconds) else "HIDDEN"}", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically).combinedClickable(enabled = !onlineMode || localSeat == 0, onClick = { guidedAction("timer") { timerMenuOpen = true } }, onLongClick = { turnTimerVisible = !turnTimerVisible }))
                DropdownMenu(expanded = timerMenuOpen, onDismissRequest = { timerMenuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (gameTimerRunning) "Stop session timer" else "Start session timer") }, onClick = { gameTimerRunning = !gameTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset session timer") }, onClick = { elapsedGameSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerRunning) "Stop turn timer" else "Start turn timer") }, onClick = { turnTimerRunning = !turnTimerRunning; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text("Reset turn timer") }, onClick = { elapsedTurnSeconds = 0; timerMenuOpen = false })
                    DropdownMenuItem(text = { Text(if (turnTimerVisible) "Hide turn timer display" else "Show turn timer display") }, onClick = { turnTimerVisible = !turnTimerVisible; timerMenuOpen = false })
                }
            }
            Box(Modifier.then(tutorialAnchor("chat-button"))) {
                IconButton(onClick = { guidedAction("chat-button") { chatOpen = true; unreadChatCount = 0 } }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Chat, "Open chat${if (unreadChatCount > 0) ", $unreadChatCount unread" else ""}", tint = if (unreadChatCount > 0) CounterGold else SoftWhite)
                }
                if (unreadChatCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(9.dp).clip(CircleShape).background(Color(0xFFE35656)))
            }
            Box(Modifier.then(tutorialAnchor("main-menu"))) {
                IconButton(onClick = { guidedAction("main-menu") { topMenuSection = null; topMenuExpanded = true } }, modifier = Modifier.size(46.dp)) { Icon(Icons.Filled.MoreVert, "App menu", tint = SoftWhite) }
                DropdownMenu(expanded = topMenuExpanded, onDismissRequest = { topMenuExpanded = false; topMenuSection = null }) {
                    if (topMenuSection == null) {
                        DropdownMenuItem(text = { Text("Full visual guide") }, onClick = { startTutorial() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Decks") }, modifier = tutorialAnchor("menu-decks"), onClick = { guidedAction("menu-decks") { topMenuSection = "decks" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Game tools") }, modifier = tutorialAnchor("menu-tools"), onClick = { guidedAction("menu-tools") { topMenuSection = "tools" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Room & game") }, modifier = tutorialAnchor("menu-game"), onClick = { guidedAction("menu-game") { topMenuSection = "game" } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("What's new") }, onClick = { topMenuExpanded = false; changelogOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Adjust UI") }, onClick = { topMenuExpanded = false; topMenuSection = null; layoutLabOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                        DropdownMenuItem(text = { Text("Options") }, modifier = tutorialAnchor("menu-options"), onClick = { guidedAction("menu-options") { topMenuExpanded = false; optionsOpen = true } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp))
                    } else {
                        DropdownMenuItem(text = { Text("‹  MAIN MENU") }, modifier = tutorialAnchor("menu-back"), onClick = { guidedAction("menu-back") { topMenuSection = null } })
                        when (topMenuSection) {
                            "decks" -> {
                                if (!argentumEnabled) DropdownMenuItem(text = { Text(if (players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) "Replace imported deck" else "Import deck") }, modifier = tutorialAnchor("menu-import"), onClick = { guidedAction("menu-import") { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } })
                                if (!argentumEnabled && players[if (onlineMode) localSeat else selectedPlayer].deckLoaded) DropdownMenuItem(text = { Text("Edit imported deck") }, modifier = tutorialAnchor("menu-edit"), onClick = { guidedAction("menu-edit") { topMenuExpanded = false; topMenuSection = null; if (onlineMode) selectedPlayer = localSeat; deckEditorOpen = true } })
                                DropdownMenuItem(text = { Text("Saved decks · ${savedDecks.size}") }, modifier = tutorialAnchor("menu-saved"), onClick = { guidedAction("menu-saved") { topMenuExpanded = false; topMenuSection = null; savedDecksOpen = true } })
                            }
                            "tools" -> {
                                if (!onlineMode) DropdownMenuItem(text = { Text("Add AI opponent · ${players.size}/4") }, modifier = tutorialAnchor("menu-add-ai"), onClick = { guidedAction("menu-add-ai") { topMenuExpanded = false; topMenuSection = null; addAiOpponent() } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Add token") }, modifier = tutorialAnchor("menu-add-token"), onClick = { guidedAction("menu-add-token") { topMenuExpanded = false; topMenuSection = null; tokenTarget = selectedPlayer; tokenPickerOpen = true } })
                                if (!argentumEnabled && gameInProgress && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Mana pool · ${trackedMana(selectedPlayer)}") }, modifier = tutorialAnchor("menu-mana"), onClick = { guidedAction("menu-mana") { topMenuExpanded = false; topMenuSection = null; manaTarget = selectedPlayer; manaPoolOpen = true } })
                                if (!argentumEnabled && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) DropdownMenuItem(text = { Text("Search / browse library") }, modifier = tutorialAnchor("menu-search"), onClick = { guidedAction("menu-search") { topMenuExpanded = false; topMenuSection = null; librarySearchPlayer = selectedPlayer } })
                                if (argentumEnabled && gameInProgress) DropdownMenuItem(text = { Text("Argentum legal actions · ${argentumSnapshot?.legalActions?.size ?: 0}") }, onClick = { topMenuExpanded = false; topMenuSection = null; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty() })
                                if (gameInProgress) DropdownMenuItem(text = { Text("Stack and priority · $visibleStackCount") }, modifier = tutorialAnchor("menu-stack"), onClick = { guidedAction("menu-stack") { topMenuExpanded = false; topMenuSection = null; stackOpen = true } })
                                DropdownMenuItem(text = { Text("Game log") }, modifier = tutorialAnchor("menu-log"), onClick = { guidedAction("menu-log") { topMenuExpanded = false; topMenuSection = null; logOpen = true } })
                                DropdownMenuItem(text = { Text("MTG rules") }, modifier = tutorialAnchor("menu-rules"), onClick = { guidedAction("menu-rules") { topMenuExpanded = false; topMenuSection = null; rulesOpen = true } })
                                DropdownMenuItem(text = { Text("Export diagnostic log") }, modifier = tutorialAnchor("menu-export"), onClick = { guidedAction("menu-export") { topMenuExpanded = false; topMenuSection = null; exportDiagnosticLog() } })
                            }
                            "game" -> {
                                DropdownMenuItem(text = { Text("Room home") }, modifier = tutorialAnchor("menu-home"), onClick = { guidedAction("menu-home") { topMenuExpanded = false; topMenuSection = null; homeOpen = true; showOverview = true; showHand = false } })
                                if (!onlineMode && !localGameStarted) DropdownMenuItem(text = { Text("Start Argentum game") }, modifier = tutorialAnchor("menu-start-game"), onClick = { guidedAction("menu-start-game") { topMenuExpanded = false; topMenuSection = null; startArgentumPractice(vsAi = true) } })
                                DropdownMenuItem(text = { Text("Argentum room") }, modifier = tutorialAnchor("menu-lobby"), onClick = { guidedAction("menu-lobby") { topMenuSection = null; startArgentumPractice(vsAi = false) } })
                                if (onlineMode && gameStartedOnline && !gameEndedOnline) DropdownMenuItem(text = { Text("Propose ending the game") }, modifier = tutorialAnchor("menu-end"), onClick = { guidedAction("menu-end") {
                                    topMenuExpanded = false; topMenuSection = null
                                    if (endProposalBy < 0) { endProposalBy = localSeat; endVotes = mapOf(localSeat to true); logAction(localSeat, "proposed ending the game") }
                                } })
                                if (onlineMode) DropdownMenuItem(text = { Text("Leave game…") }, modifier = tutorialAnchor("menu-leave"), onClick = { guidedAction("menu-leave") { topMenuExpanded = false; topMenuSection = null; if (argentumEnabled) argentumLeaveConfirm = true else lobbyOpen = true } })
                                if (!onlineMode || localSeat == 0 && gameEndedOnline) DropdownMenuItem(text = { Text("Reset game", color = Color(0xFFE9978B)) }, modifier = tutorialAnchor("menu-reset"), onClick = { guidedAction("menu-reset") { topMenuExpanded = false; topMenuSection = null; resetConfirmOpen = true } })
                                if (!onlineMode) DropdownMenuItem(text = { Text("Reset and clear all data", color = Color(0xFFE9978B)) }, modifier = tutorialAnchor("menu-clear"), onClick = { guidedAction("menu-clear") { topMenuExpanded = false; topMenuSection = null; clearAllConfirmOpen = true } })
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.then(tutorialAnchor("table-tab"))) { ViewChip("TABLE", selected = showOverview, onClick = { guidedAction("table-tab") { showOverview = true; showHand = false } }) }
                players.forEachIndexed { index, player ->
                    Box(Modifier.then(tutorialAnchor("player-tab"))) {
                        PlayerViewTab(
                            name = if (onlineMode && index == localSeat) "YOU" else player.name,
                            commander = player.commanderCard,
                            selected = !showOverview && selectedPlayer == index,
                            onClick = { guidedAction("player-tab") { selectedPlayer = index; showOverview = false; showHand = false } }
                        )
                    }
                }
            }
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
                        Modifier.fillMaxWidth().then(if (index == selectedPlayer) tutorialAnchor("player-card") else Modifier),
                        tutorialAnchor = ::tutorialAnchor,
                        compact = compactOverview,
                        isActivePlayer = index == activePlayer,
                        turnNumber = turnNumber,
                        phaseName = if (index == activePlayer) phaseSteps[phaseIndex] else "WAITING",
                        turnTime = if (index == activePlayer) elapsedTurnSeconds else null,
                        showTurnTimer = turnTimerVisible,
                        onToggleTimer = { if (!onlineMode || localSeat == 0) turnTimerVisible = !turnTimerVisible },
                        onMoveUp = { if (!onlineMode) movePlayerCard(index, -1) }, onMoveDown = { if (!onlineMode) movePlayerCard(index, 1) },
                        onOpen = { guidedAction("player-card") { selectedPlayer = index; showOverview = false } },
                        onZone = { zoneDialog = index to it },
                        onPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = gameInProgress && (!onlineMode || index == localSeat) && !gameEndedOnline) },
                        onPublicPreview = { preview = CardPreview(index, it, fromHand = false, canManipulate = false) }
                    )
                }
            }
        } else {
            val boardIndex = showingView.coerceIn(players.indices)
            val boardCanControl = gameInProgress && !argentumEnabled && (!onlineMode || boardIndex == localSeat) && !gameEndedOnline && !players[boardIndex].isEliminated
            PlayerBoard(
                player = players[boardIndex].copy(name = if (onlineMode && boardIndex == localSeat) "YOU" else players[boardIndex].name), playerIndex = boardIndex, isLandscape = isLandscape, cardSizeScale = cardSizeScale,
                boardSpacingScale = boardSpacingScale,
                playerSectionOrder = playerSectionOrderRaw.split('|').filter(String::isNotBlank),
                quickActionOrder = quickActionOrderRaw.split('|').filter(String::isNotBlank),
                gameInProgress = gameInProgress,
                tutorialAnchor = ::tutorialAnchor,
                stackCount = visibleStackCount,
                commanderUnavailable = players[boardIndex].commanderCard?.let { commander ->
                    players[boardIndex].library.any { it.id == commander.id } || players[boardIndex].hand.any { it.id == commander.id } || players[boardIndex].battlefield.any { it.id == commander.id } || players[boardIndex].graveyard.any { it.id == commander.id } || players[boardIndex].exile.any { it.id == commander.id } || players[boardIndex].revealed.any { it.id == commander.id } || stackActions.any { it.card?.id == commander.id }
                } ?: false,
                canControl = boardCanControl,
                isActivePlayer = boardIndex == activePlayer, turnNumber = turnNumber, phaseName = phaseSteps[phaseIndex],
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { battlefieldBounds = it.boundsInRoot() },
                onLife = { delta -> guidedAction(if (delta > 0) "life-up" else "life-down") { if (boardCanControl) { val nextLife = (players[boardIndex].life + delta).coerceAtLeast(0); updatePlayer(boardIndex) { it.copy(life = nextLife) }; logAction(boardIndex, if (delta > 0) "gained life" else "lost life"); if (nextLife == 0) eliminatePlayer(boardIndex, "Life total reached 0") } } },
                onDraw = { guidedAction("draw-action") { if (boardCanControl) { drawCard(boardIndex); showHand = true } } },
                onShowHand = { guidedAction("hand-button") { if (boardCanControl) showHand = true } },
                onShowHandFromZone = { guidedAction("zone-hand") { if (boardCanControl) showHand = true } },
                onToken = { guidedAction("token-action") { if (boardCanControl) { tokenTarget = boardIndex; tokenPickerOpen = true } } },
                onManageMana = { guidedAction("mana-status") { if (boardCanControl) { manaTarget = boardIndex; manaPoolOpen = true } } },
                onZone = { zone -> guidedAction("zone-${zone.lowercase()}") { zoneDialog = boardIndex to zone } },
                onPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = boardCanControl) },
                onPublicPreview = { preview = CardPreview(boardIndex, it, fromHand = false, canManipulate = false) },
                onTapCard = { id -> guidedAction("battlefield") {
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
                onNextStep = { guidedAction("next-step") { if (argentumEnabled && boardIndex == localSeat) sendArgentumPriority() else if (boardCanControl && boardIndex == activePlayer) advanceStep() } }, onOpenLog = { logOpen = true }, onOpenChat = { chatOpen = true },
                onCombat = { guidedAction("combat-action") { if (argentumEnabled && boardIndex == localSeat) argentumActionChoices = argentumSnapshot?.legalActions.orEmpty() else if (boardCanControl && boardIndex == activePlayer) combatDialogOpen = true } },
                onTrackers = { if (boardCanControl) trackerPlayer = boardIndex },
                onSearchLibrary = { guidedAction("zone-library") { if (boardCanControl) librarySearchPlayer = boardIndex } },
                onStack = { guidedAction("stack-open") { stackOpen = true } },
                onOpenMore = { guidedAction("more-actions") {} },
        onCastCommander = { guidedAction("commander-cast") {
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
        }
        }

        if (showOverview) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tap a player to inspect their board", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) HeaderButton("NEXT · ${phaseSteps[phaseIndex]}", onClick = { guidedAction("table-next") { if (argentumEnabled) sendArgentumPriority() else advanceStep() } }, enabled = !argentumAwaitingUpdate, modifier = tutorialAnchor("table-next"))
                if (gameInProgress && !gameEndedOnline && !players[activePlayer].isComputer && (!onlineMode || localSeat == activePlayer)) {
                    Spacer(Modifier.width(5.dp))
                    HeaderButton("PASS TURN", onClick = { guidedAction("table-pass") { if (argentumEnabled) sendArgentumPriority() else passTurn() } }, enabled = !argentumAwaitingUpdate, modifier = tutorialAnchor("table-pass"))
                } else if (gameInProgress) Text(if (players[activePlayer].deckLoaded) "COMPUTER TURN" else "AI NEEDS DECK", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                else Text("START GAME TO PLAY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                if (!players[selectedPlayer].deckLoaded && !gameEndedOnline && (!onlineMode || selectedPlayer == localSeat)) {
                    Spacer(Modifier.width(6.dp))
                    HeaderButton("IMPORT DECK", onClick = { guidedAction("table-import") { if (onlineMode) selectedPlayer = localSeat; importError = null; importOpen = true } }, modifier = tutorialAnchor("table-import"))
                }
            }
        } else {
            val canPassTurnFromBoard = gameInProgress && !gameEndedOnline && selectedPlayer == activePlayer && !players[selectedPlayer].isComputer && !players[selectedPlayer].isEliminated && (!onlineMode || localSeat == activePlayer) && !argentumAwaitingUpdate
            val footerControlHeight = (54.dp * footerButtonScale).coerceIn(44.dp, 76.dp)
            val handControlHeight = (62.dp * footerButtonScale).coerceIn(48.dp, 82.dp)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                HeaderButton(
                    "PASS TURN", onClick = { guidedAction("pass-turn") { if (argentumEnabled) sendArgentumPriority() else passTurn() } }, enabled = canPassTurnFromBoard,
                    modifier = Modifier.weight(1f).height(footerControlHeight).then(tutorialAnchor("pass-turn"))
                )
                Box(Modifier.weight(1.18f).then(tutorialAnchor("hand-button"))) {
                    HandPeekBar(
                        players[selectedPlayer].hand.size,
                        modifier = Modifier.fillMaxWidth().height(handControlHeight),
                        onClick = { guidedAction("hand-button") { if (canControlSelectedPlayer) showHand = true } }
                    )
                }
                HeaderButton(
                    "BACK TO TABLE", onClick = { guidedAction("player-back-table") { showOverview = true; showHand = false } },
                    modifier = Modifier.weight(1f).height(footerControlHeight).then(tutorialAnchor("player-back-table"))
                )
            }
        }
    }

        @Composable
        fun TableOverlays() {
            Box(Modifier.fillMaxSize()) {
        if (homeOpen) RoomHomeScreen(
        wide = screenWidthDp >= 700,
            tutorialAnchor = ::tutorialAnchor,
        argentumActive = argentumEnabled,
        argentumWorking = argentumDeckValidationRunning,
        deckLoaded = players.getOrNull(localSeat)?.deckLoaded == true,
        deckCommander = players.getOrNull(localSeat)?.commanderName,
        onArgentum = { guidedAction("home-argentum") { if (argentumEnabled) homeOpen = false else startArgentumPractice(vsAi = true) } },
        onImportDeck = { selectedPlayer = localSeat; importError = null; importOpen = true },
        onCreateRoom = { guidedAction("home-create") { startArgentumPractice(vsAi = false) } },
        onJoinRoom = { guidedAction("home-join") { argentumLastError = null; argentumJoinCode = ""; argentumJoinDialogOpen = true } },
        onContinue = { guidedAction("home-continue") { homeOpen = false } },
        onTutorial = { startTutorial() }
    )

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
                            Text(lobbyId, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
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

    if (argentumLeaveConfirm) AlertDialog(
        onDismissRequest = { argentumLeaveConfirm = false },
        title = { Text("Leave Argentum game?") },
        text = { Text(if (argentumSnapshot == null) "This will leave the Argentum room." else "Leaving a live game concedes the match to Argentum.") },
        confirmButton = { TextButton(onClick = {
            if (argentumSnapshot == null) argentumSocket.leaveQuickGameLobby() else argentumSocket.concede()
            argentumSocket.close()
            argentumEnabled = false
            argentumSnapshot = null
            argentumPendingDecision = null
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
            argentumLeaveConfirm = false
        }) { Text("LEAVE GAME") } },
        dismissButton = { TextButton(onClick = { argentumLeaveConfirm = false }) { Text("CANCEL") } }
    )

    if (argentumMulliganPrompt && argentumSnapshot == null) AlertDialog(
        onDismissRequest = {},
        title = { Text("Opening hand") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Argentum is waiting for your decision. Review your cards:")
                players.getOrNull(localSeat)?.hand.orEmpty().forEach { card ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardFace(card, Modifier.width(40.dp), onClick = {}, onLongClick = {})
                        Text(card.name, color = SoftWhite, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !argentumAwaitingUpdate, onClick = { if (argentumSocket.keepHand()) { argentumMulliganPrompt = false; argentumAwaitingUpdate = true } }) { Text("KEEP HAND") } },
        dismissButton = { TextButton(enabled = !argentumAwaitingUpdate, onClick = { if (argentumSocket.mulligan()) argentumAwaitingUpdate = true }) { Text("MULLIGAN") } }
    )

    argentumBottomCardIds?.let { ids -> AlertDialog(
        onDismissRequest = {},
        title = { Text("Choose cards to put on the bottom") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Select exactly $argentumBottomCardCount card(s).", color = Muted, fontSize = 12.sp)
                ids.forEach { id ->
                    val card = players.getOrNull(localSeat)?.hand?.firstOrNull { it.id == (id.hashCode() and 0x7fffffff).coerceAtLeast(1) }
                    Row(Modifier.fillMaxWidth().clickable {
                        argentumBottomSelection = if (id in argentumBottomSelection) argentumBottomSelection - id
                        else if (argentumBottomSelection.size < argentumBottomCardCount) argentumBottomSelection + id else argentumBottomSelection
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in argentumBottomSelection, onCheckedChange = { checked ->
                            argentumBottomSelection = if (checked) argentumBottomSelection + id else argentumBottomSelection - id
                        })
                        Text(card?.name ?: "Card", color = SoftWhite, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = argentumBottomSelection.size == argentumBottomCardCount, onClick = {
            if (argentumSocket.chooseBottomCards(argentumBottomSelection.toList())) { argentumBottomCardIds = null; argentumAwaitingUpdate = true }
        }) { Text("CONFIRM") } }
    ) }

    if (argentumActionChoices.isNotEmpty()) AlertDialog(
        onDismissRequest = { argentumActionChoices = emptyList() },
        title = { Text("Argentum legal actions") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                argentumActionChoices.forEach { info ->
                    val action = info.optJSONObject("action")
                    val needsTargets = info.optBoolean("requiresTargets")
                    TextButton(
                        enabled = !argentumAwaitingUpdate,
                        onClick = {
                            if (needsTargets) {
                                val requirements = info.optJSONArray("targetRequirements")
                                val targets = info.optJSONArray("validTargets")
                                if (argentumPendingDecision?.optString("type") == "ChooseTargetsDecision") {
                                    // The server owns a separate target decision; its legal target IDs are shown in that decision panel.
                                } else if ((requirements?.length() ?: 0) > 1 || info.optInt("targetCount", 1) > 1 || targets == null || targets.length() == 0) {
                                    message = "This Argentum action needs a multi-part target choice that Arcana cannot present yet. It has not been submitted."
                                } else {
                                    argentumTargetAction = JSONObject(info.toString())
                                    argentumTargetChoices = (0 until targets.length()).map { targets.optString(it) }
                                    argentumSelectedTarget = null
                                }
                                argentumActionChoices = emptyList()
                            } else if (action != null) {
                                val epoch = info.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" }
                                    ?: argentumSnapshot?.interactionEpoch
                                if (epoch == null || !submitArgentumAction(action, epoch)) {
                                    message = "This Argentum action is stale or the connection is unavailable. Resync the table and try again."
                                }
                            }
                            argentumActionChoices = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(info.optString("description", action?.optString("type") ?: "Legal action"), color = Mint)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { argentumActionChoices = emptyList() }) { Text("CANCEL") } }
    )

    argentumTargetAction?.let { info ->
        val targets = argentumSnapshot?.targets.orEmpty()
        val playersById = players.associateBy { it.id }
        AlertDialog(
            onDismissRequest = { argentumTargetAction = null },
            title = { Text("Choose a legal target") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(info.optString("description", "Choose one target"), color = Muted, fontSize = 12.sp)
                    argentumTargetChoices.forEach { id ->
                        val target = targets[id]
                        val label = if (target?.type == "Player") playersById[id]?.name ?: target.name
                            else target?.name ?: "Target ${id.takeLast(6)}"
                        Row(Modifier.fillMaxWidth().clickable { argentumSelectedTarget = id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = argentumSelectedTarget == id, onClick = { argentumSelectedTarget = id })
                            Text(label, color = SoftWhite, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = argentumSelectedTarget != null, onClick = {
                val target = argentumSelectedTarget?.let(targets::get)
                val sourceAction = info.optJSONObject("action")
                val epoch = info.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" }
                    ?: argentumSnapshot?.interactionEpoch
                if (target != null && sourceAction != null && epoch != null) {
                    val chosen = when (target.type) {
                        "Player" -> JSONObject().put("type", "Player").put("playerId", target.id)
                        "Permanent" -> JSONObject().put("type", "Permanent").put("entityId", target.id)
                        "Spell" -> JSONObject().put("type", "Spell").put("spellEntityId", target.id)
                        else -> JSONObject().put("type", "Card").put("cardId", target.id)
                            .put("ownerId", target.ownerId).put("zone", target.zone)
                    }
                    val completed = JSONObject(sourceAction.toString()).put("targets", JSONArray().put(chosen))
                    if (!submitArgentumAction(completed, epoch)) message = "Could not submit the targeted Argentum action. Refresh the table and try again."
                } else message = "Argentum's target list is no longer available. Refresh the table before acting."
                argentumTargetAction = null
            }) { Text("CONFIRM TARGET") } },
            dismissButton = { TextButton(onClick = { argentumTargetAction = null }) { Text("CANCEL") } }
        )
    }

    argentumPendingDecision?.takeUnless { it.optString("type") == "PlayCardDecision" }?.let { decision ->
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
                            val values = if (max - min <= 12) (min..max).toList() else listOf(min, max).distinct()
                            values.forEach { value -> TextButton(onClick = {
                                submitArgentumDecision(JSONObject().put("type", "NumberChosenResponse").put("number", value))
                            }, modifier = Modifier.fillMaxWidth()) { Text(value.toString(), color = Mint) } }
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
                        else -> Text("Argentum decision: $kind. This choice type still needs an Arcana control.", color = CounterGold)
                    }
                }
            },
            confirmButton = {
                when (kind) {
                    "YesNoDecision" -> TextButton(onClick = { submitArgentumDecision(JSONObject().put("type", "YesNoResponse").put("choice", true)) }) { Text(decision.optString("yesText", "YES")) }
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
                    else -> Unit
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
            }
        )
    }

    if (changelogOpen) AlertDialog(
        onDismissRequest = { changelogOpen = false },
        title = { Text("What's new · Build ${BuildConfig.VERSION_CODE}") },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.62f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("BUILD 83 · ARGENTUM ROOM FLOW", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Practice, create-room, and join-room actions now use Argentum Commander lobbies. The host can copy the server room code, joining validates the imported deck with Argentum first, and the Argentum stack panel displays server state and submits server-provided Pass Priority actions.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 82 · ARGENTUM NATIVE TABLE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Argentum practice games now run through Arcana’s existing table and player screens. The server validates the imported Commander deck, unsupported cards are removed and replaced only with Argentum-supported basic lands, and Argentum supplies the masked game state and legal actions. The native client keeps its connection active while switching apps and reconnects after a drop. Common opening-hand, target, color, option, number, and card-selection decisions are shown in Arcana dialogs.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 81 · QUICK ACTIONS AND TURN COUNTER", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The player-screen zone strip is now a configurable Quick Actions bar. Add, remove, and reorder zone shortcuts and actions in Adjust UI; empty zones stay visible with a count of 0. The Mana Pool element now appears on the live player board when enabled.\n• Mana pips are larger across card costs and text, and card three-dot menus have smaller visible buttons with an accessible tap area.\n• The TURN counter advances after every eligible player has completed a turn. It remains labeled TURN.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 80 · PLAYER BATTLEFIELD HEIGHT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Removed the extra weighted spacer above the player footer. The player board now receives the full available height, so the battlefield fills the space above the footer instead of leaving a large blank gap.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 79 · TABLE CARDS AND LIBRARY PRIVACY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Deck imports automatically try close Scryfall name matches before opening review; the review menu appears only for cards or deck issues that remain unresolved, and successful corrections are reported.\n• The table overview enlarges cards to use each player panel's available width. Player battlefield windows use the full space above the footer and open at the top when switching boards.\n• General library browsing displays a randomized card list without library positions. The Adjust UI editor now selects the matching list item when you tap or drag a preview element.\n• Mana pool is a separate removable and reorderable player-screen element. Tapping the health/activity bar opens Player Status with life, commander damage, counters, and turn details; the life buttons remain quick controls.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 78 · DRAGGABLE UI EDITOR AND PLAYER LAYOUT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Long-press and drag player-screen elements or hand-tray buttons in the editor's sample layout. The active item lifts and tracks your finger while neighboring items animate into place.\n• Player-screen footer controls stay aligned to the bottom, with a larger hand button. The battlefield automatically fills available space and scrolls when its cards exceed that space.\n• Removed the Large View toggle from the table view.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 77 · DRAG-AND-DROP UI EDITOR", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Adjust UI now lets you drag to reorder player-screen sections, remove and restore sections such as the health/activity bar and commander strip, and add, remove, and reorder hand-tray buttons. Layouts are saved separately for portrait and landscape.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 76 · PLAYER LAYOUT AND HAND ACTIONS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Player-screen footer buttons now align on the bottom edge. Opening-hand cards are larger, and the hand tray includes Cast Commander when it can be cast. Pass Priority is the primary stack action; Resolve Top remains available as a smaller action after everyone passes.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 75 · MANA PIPS AND UI ADJUSTMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Mana-choice buttons show colored mana pips. Open the app menu and tap Adjust UI to go straight to the layout controls.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 74 · HAND TRAY AND PLAYER CONTROLS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Opening-hand cards are larger, and hand-tray cards grow or shrink with the drawer. The tray now holds Draw, Combat, Stack, turn flow, and secondary game commands; the Stack shortcut is off the view selector. The player footer stays at the bottom, and the old drag-to-play hint is gone.\n• Mana symbols use colored pips. Permanents that say to choose a creature type prompt for one as they enter and display the choice. Public card mentions in the game log can open a card preview.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 73 · TARGETING EXPERIMENT", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Targeted spells, Auras, activated abilities, and recognized targeted triggers pause for target selection. The picker lists visible players, permanents, stack items, and cards in visible zones; add a legal target by name if it is not listed. Chosen targets appear on the stack and in the game log, and sync to the room. The picker estimates target count, but players still verify card-specific restrictions. This experimental app installs separately from the stable release.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 72 · PLAYER TABS AND ACTIVE BOARD", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Player view tabs use cropped commander artwork with the player name at the bottom. On the full table, the active player's board opens automatically and the view scrolls to it; the previous active board closes when the turn changes. The player footer has PASS TURN, a larger centered HAND button, and BACK TO TABLE. Mana access is now in the player status header. ADD TOKEN is in MORE; the footer no longer shows the library count. The command-zone strip is more compact while preserving commander casting, cast count, tax, and status.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 71 · CHAT COPY AND PASTE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Chat messages support text selection by long-pressing, with Android's copy action. Added a PASTE control to append clipboard text to the draft; the message field also keeps Android's normal text paste menu.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 70 · DUPLICATE PERMANENT FIX", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Resolving an activated or loyalty ability now leaves its source permanent in its current zone. Only resolving a spell moves that spell card to the battlefield or graveyard.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 69 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Added trigger detection for recognized spell-cast, draw, and creature-entry events. Triggers show their event and effect, offer target-player selection for supported player-target effects, and keep modes or unsupported effects on a manual-resolution path. Rhystic Study and Smothering Tithe keep their pay-or-resolve choices.\n• Expanded simple automatic effect handling for targeted player life, draw, and token effects after a target is chosen.\n• The Activity Log now includes turn phase, spell cost/payment detail, trigger events, automatic changes, and manual-resolution notes, with visual emphasis for payments, triggers, and effects.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 68 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The opening-hand screen now shows card faces. Tap any card to open a large preview; while choosing cards to bottom, use the separate button beneath each card to select it. The selection count and required bottom count remain visible.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 67 · SOURCE UPDATE", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• Added a multiplayer opening-hand step before Ready Up. Each player can keep or mulligan; every mulligan shuffles the whole hand into the library and deals a fresh seven. The first multiplayer mulligan is free.\n• After keeping, players tap the exact number of cards to put on the bottom of their library.\n• Ready Up now requires a confirmed opening hand. LAN and Internet room state carries mulligan status, and room servers reject ready requests until both the deck and hand decision are set.\n• Starting the lobby game preserves each player's chosen hand and library order instead of dealing a new hand.\n\nThis is a source-only update; the Build 66 APK does not include these changes.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 66 · CHANGES SINCE BUILD 65", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• The hand panel can now be resized by dragging its top handle and remembers its height.\n• The stack window closes automatically when the stack is empty and pending triggers or payment choices have finished; reopen it any time from the Stack button.\n• Player views use tighter spacing, compact zone chips, and a battlefield area sized to its cards that scrolls when crowded.\n• Diagnostic logs now capture repeated deck quantities, visible card moves, zone totals, repeated card-instance IDs, and inbound snapshot sequence numbers to help trace import and sync problems.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 65", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Added an import check that flags unmatched or unverifiable cards. Auto Fix closes the review when every card is matched; unresolved cards can be opened in the editor for manual correction.\n• Added automatic handling for supported fixed life, draw, and token effects, plus simple creature-entry life triggers. Effects the app cannot safely parse prompt the player to resolve them manually.\n• Added a persistent Stack button beside the view tabs and another in the hand tray; the tab button highlights while the stack has items.", color = SoftWhite, fontSize = 11.sp)

                Text("EARLIER CHANGES · BUILDS 60–64", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("BUILD 60", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Rebuilt the optional visual guide with screen highlights and straight red arrows.\n• Added Share Room, the default room URL reminder, tapped-entry choices, and shock-land life prompts.\n• Added an empty-stack notice and simplified menu labels.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 61", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Enlarged the tutorial panels and controls. The room URL step now explains that the default address is already set and can continue without opening the keyboard.\n• Improved card-action dialog layout so titles and actions fit.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 62", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Added Next to every tutorial step, plus an import review with Auto Fix and Trim Excess.\n• Added per-card moves from Graveyard and Exile, and hid Set as Commander after one is assigned.\n• Concealed face-down card details. Made Cast from Hand the primary stack action and tucked manual ability entry behind a small control.\n• Replaced clunky view swipes with synchronized table/player paging and aligned the hand-sheet drag handle.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 63", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Improved stack-window sizing.\n• Updated the deck editor to track cards in every zone and keep rename/remove actions available after cards move.\n• Improved AI readiness and recovery from unexpected turn errors.\n• Expanded mana-ability tracking for common mana rocks, dual-color choices, and basic lands.", color = SoftWhite, fontSize = 11.sp)

                Text("BUILD 64", color = CounterGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("• Fixed the Table overview layout so player panels remain visible.", color = SoftWhite, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { changelogOpen = false }) { Text("CLOSE", color = Mint) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    // The guide is layered over the real screen at the end of this composition.

    AnimatedVisibility(
        visible = showHand && !showOverview && gameInProgress && canControlSelectedPlayer,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showHand = false }
        ) {
            AnimatedVisibility(
                visible = showHand && !showOverview && gameInProgress && canControlSelectedPlayer,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(handSheetHeightFraction)
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
                        cards = players[selectedPlayer].hand, isLandscape = isLandscape,
                        availableHeight = LocalConfiguration.current.screenHeightDp.dp * handSheetHeightFraction,
                        stackCount = visibleStackCount,
                        tutorialAnchor = ::tutorialAnchor,
                        highlightedCardId = highlightedHandCardId,
                        actionOrder = handActionOrderRaw.split('|').filter(String::isNotBlank),
                        handRevealed = players[selectedPlayer].handRevealedToAll,
                        canAdvance = canControlSelectedPlayer && selectedPlayer == activePlayer && !players[activePlayer].isComputer,
                        canControl = canControlSelectedPlayer,
                        commanderCanCast = players[selectedPlayer].commanderCard?.let { commander ->
                            val owner = players[selectedPlayer]
                            canControlSelectedPlayer && !owner.isEliminated && !owner.library.any { it.id == commander.id } && !owner.hand.any { it.id == commander.id } && !owner.battlefield.any { it.id == commander.id } && !owner.graveyard.any { it.id == commander.id } && !owner.exile.any { it.id == commander.id } && !owner.revealed.any { it.id == commander.id } && stackActions.none { it.card?.id == commander.id }
                        } == true,
                        onToggleReveal = { revealed -> if (!argentumEnabled) {
                            updatePlayer(selectedPlayer) { it.copy(handRevealedToAll = revealed) }
                            logAction(selectedPlayer, if (revealed) "revealed their hand to everyone" else "hid their hand")
                        } },
                        onClose = { showHand = false },
                        onDraw = { if (!argentumEnabled) { drawCard(selectedPlayer); showHand = true } },
                        onNextStep = { if (argentumEnabled) sendArgentumPriority() else advanceStep() },
                        onPassTurn = { if (argentumEnabled) sendArgentumPriority() else passTurn() },
                        onStack = { guidedAction("stack-open") { if (argentumEnabled) argentumActionChoices = argentumSnapshot?.legalActions.orEmpty() else stackOpen = true } },
                        onCastCommander = {
                            val commander = players.getOrNull(selectedPlayer)?.commanderCard
                            if (argentumEnabled && commander != null) openArgentumActions(commander)
                            else if (commander == null) message = "Set a commander from your deck or battlefield first."
                            else paymentCard = selectedPlayer to commander
                        },
                        onCombat = { guidedAction("combat-action") { if (argentumEnabled) argentumActionChoices = argentumSnapshot?.legalActions.orEmpty() else if (canControlSelectedPlayer && selectedPlayer == activePlayer) combatDialogOpen = true } },
                        onMoreAction = { action ->
                            if (argentumEnabled) argentumActionChoices = argentumSnapshot?.legalActions.orEmpty()
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
                        onSelect = { card -> guidedAction("hand-card") { if (argentumEnabled) openArgentumActions(card) else handActionCard = card } },
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
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "LOBBY" },
        tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
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
        onExportDiagnostics = { guidedAction("lobby-diagnostics") { exportDiagnosticLog() } },
        onTutorialTarget = { key -> guidedAction(key) {} },
        onDismiss = { guidedAction("lobby-close") { cancelRoomJoin(); lobbyOpen = false } },
        onCancelJoin = { guidedAction("lobby-cancel") { cancelRoomJoin() } },
        onSelectJoin = { guidedAction("lobby-join") { lobbyJoinMode = true; lobbyNotice = null } },
        onSelectCreate = { guidedAction("lobby-create") { lobbyJoinMode = false; lobbyNotice = null } },
        onSelectNetwork = { internet -> guidedAction(if (internet) "lobby-internet" else "lobby-local") {
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
        onCreate = { guidedAction("lobby-submit") { if (lobbyInternetMode) startInternetRoom() else {
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
        onJoin = { guidedAction("lobby-submit") { if (lobbyInternetMode) {
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
        onToggleReady = { index -> guidedAction("lobby-ready") {
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
        onImportDeck = { index -> guidedAction("lobby-import") {
            selectedPlayer = index
            lobbyOpen = false
            importError = null
            importOpen = true
        } },
        onAddAi = { guidedAction("lobby-ai") {
            lobbyOpen = false
            addAiOpponent()
        } },
        onRandomizeTurnOrder = { guidedAction("lobby-order") {
            val randomized = players.indices.shuffled()
            lobbyTurnOrder = randomized
            lobbyNotice = "${players[randomized.first()].name} will take the first turn."
        } },
        onStartGame = { guidedAction("lobby-start") {
            val order = lobbyTurnOrder ?: players.indices.toList()
            recordDiagnostic("Host started game with ${players.size} occupied seat(s)")
            resetGame(startImmediately = true, order = order, preserveOpeningHands = true)
            endProposalBy = -1
            endVotes = emptyMap()
            logAction(activePlayer, "started the game and goes first")
        } },
        onLeave = { guidedAction("lobby-leave") {
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
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.target == "hand-actions" },
        tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
        onDismiss = { handActionCard = null }, onAction = { action -> guidedAction("hand-actions") { performHandAction(card, action) } }
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
                            IconButton(onClick = { guidedAction("deck-cancel") { if (!importing) cancelDeckImport() } }, modifier = Modifier.size(44.dp).then(tutorialAnchor("deck-cancel"))) { Icon(Icons.Filled.Close, "Cancel deck import", tint = Muted) }
                        }
                        Text("Paste a deck list or choose a .txt/.csv file. Import replaces this player's deck.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = { guidedAction("deck-load-saved") { savedDecksOpen = true } }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).then(tutorialAnchor("deck-load-saved"))) { Text("SAVED DECKS · ${savedDecks.size}", color = Mint) }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { guidedAction("deck-choose-file") { filePicker.launch(arrayOf("text/*", "application/*")) } }, enabled = !importing && !randomDeckLoading, modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(tutorialAnchor("deck-choose-file"))) { Text("CHOOSE FILE", fontSize = 9.sp) }
                            OutlinedButton(
                                onClick = { guidedAction("deck-random") {
                                    randomDeckLoading = true
                                    importError = null
                                    scope.launch {
                                        try {
                                            importText = fetchRandomCommanderDecklist()
                                            message = "Random Commander deck loaded. Review it, then tap Import."
                                        } catch (error: Exception) {
                                            importError = error.message ?: "Could not fetch a random deck. Check your connection and try again."
                                        } finally { randomDeckLoading = false }
                                    }
                                } },
                                enabled = !importing && !randomDeckLoading,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(tutorialAnchor("deck-random"))
                            ) { Text(if (randomDeckLoading) "FETCHING…" else "RANDOM DECK", fontSize = 9.sp, color = Mint) }
                        }
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp).padding(top = 6.dp),
                            placeholder = { Text("1 Sol Ring\n36 Island\n...") },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !importing && !randomDeckLoading
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
                            TextButton(onClick = { guidedAction("deck-cancel") { if (!importing) cancelDeckImport() } }, modifier = Modifier.heightIn(min = 48.dp).then(tutorialAnchor("deck-cancel"))) { Text("CANCEL", color = Muted) }
                            Button(onClick = { guidedAction("deck-import") { importDeck(importText) } }, enabled = !importing && !randomDeckLoading, modifier = Modifier.heightIn(min = 48.dp).then(tutorialAnchor("deck-import"))) { Text(if (importing || randomDeckLoading) "LOADING" else "IMPORT") }
                        }
                    }
                    tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "DECK_IMPORT" }?.let { step ->
                        GuidedTutorialOverlay(step, tutorialIndex, tutorialStops.size, tutorialBounds[step.target], { goBackTutorial() }, { advanceTutorial() }, { markTutorialSeen(step.view); tutorialOpen = false })
                    }
                }
            }
        }
    }

    if (savedDecksOpen) SavedDecksDialog(
        decks = savedDecks,
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "SAVED_DECKS" },
        tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
        onLoad = { deck -> guidedAction("saved-load") {
            if (onlineMode) selectedPlayer = localSeat
            importText = deck.text
            importError = null
            importOpen = true
            savedDecksOpen = false
        } },
        onDelete = { deck -> guidedAction("saved-delete") {
            savedDecks = savedDecks.filterNot { it.name == deck.name && it.text == deck.text }
            saveSavedDecks(context, savedDecks)
        } },
        onDismiss = { guidedAction("saved-close") { savedDecksOpen = false } }
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
            canMoveCards = zone in listOf("Graveyard", "Exile") && (!onlineMode || index == localSeat && !gameEndedOnline),
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
        entries = gameLog,
        previewableCards = players.flatMapIndexed { index, player ->
            (player.battlefield + player.graveyard + player.exile + player.revealed.filterNot { it.isFaceDown } + listOfNotNull(player.commanderCard))
                .filterNot { it.isFaceDown }.map { index to it }
        },
        onPreviewCard = { owner, card -> preview = CardPreview(owner, card, fromHand = false, canManipulate = false) },
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "GAME_LOG" },
        tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
        onDismiss = { guidedAction("log-close") { logOpen = false } }
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
                tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "CHAT" },
                tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
                onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
                onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
                onDraft = { chatDraft = it }, onDismiss = { guidedAction("chat-close") { chatOpen = false } }, onSend = { guidedAction("chat-send") {
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
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "OPTIONS" },
        tutorialIndex = tutorialIndex, tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
        darkMode = darkMode, timerRunning = turnTimerRunning, timerVisible = turnTimerVisible,
        backgroundAlertsEnabled = backgroundAlertsEnabled,
        autoReturnTableAfterPass = autoReturnTableAfterPass, autoDrawOnDrawStep = autoDrawOnDrawStep,
        autoUntapOnUntapStep = autoUntapOnUntapStep, openHandAfterDraw = openHandAfterDraw,
        reducedSwipeMotion = reducedSwipeMotion, cardSizeScale = cardSizeScale,
        onOpenLayoutLab = { guidedAction("options-layout-lab") { optionsOpen = false; layoutLabOpen = true } },
        onToggleTheme = { guidedAction("options-theme") { onToggleTheme() } },
        onToggleTimerVisibility = { guidedAction("options-timer") { turnTimerVisible = !turnTimerVisible } },
        onToggleBackgroundAlerts = { enabled -> guidedAction("options-alerts") { setBackgroundAlerts(enabled) } },
        onAutoReturnTable = { value -> guidedAction("options-return-table") { autoReturnTableAfterPass = value; optionPrefs.edit().putBoolean("auto_return_table", value).apply() } },
        onAutoDraw = { value -> guidedAction("options-auto-draw") { autoDrawOnDrawStep = value; optionPrefs.edit().putBoolean("auto_draw_step", value).apply() } },
        onAutoUntap = { value -> guidedAction("options-auto-untap") { autoUntapOnUntapStep = value; optionPrefs.edit().putBoolean("auto_untap_step", value).apply() } },
        onOpenHandAfterDraw = { value -> guidedAction("options-open-hand") { openHandAfterDraw = value; optionPrefs.edit().putBoolean("open_hand_after_draw", value).apply() } },
        onReducedMotion = { value -> guidedAction("options-motion") { reducedSwipeMotion = value; optionPrefs.edit().putBoolean("reduced_swipe_motion", value).apply() } },
        onCardSizeScale = { value -> guidedAction("options-card-size") { cardSizeScale = value; optionPrefs.edit().putFloat("card_size_scale", value).putFloat("layout_card_size_$layoutOrientationSuffix", value).apply() } },
        onOpenDeveloperOptions = { guidedAction("options-developer") { optionsOpen = false; developerOptionsOpen = true } },
        onClearAllData = { guidedAction("clear-data") { optionsOpen = false; clearAllConfirmOpen = true } },
        onDismiss = { guidedAction("options-close") { optionsOpen = false } }
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
            optionPrefs.edit()
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
    if (developerOptionsOpen) DeveloperOptionsPage(
        players = players,
        selectedPlayer = selectedPlayer,
        onSelectPlayer = { index -> selectedPlayer = index; showOverview = false; developerOptionsOpen = false },
        onLoadRandomDecks = { makeRandomDecks(); developerOptionsOpen = false },
        onRandomizeMidgame = { randomizeMidgame(); developerOptionsOpen = false },
        onOpenWalkthrough = { startTutorial() },
        onDismiss = { developerOptionsOpen = false }
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
    if (onlineMode && gameEndedOnline) AlertDialog(
        onDismissRequest = {},
        title = { Text("Game ended by unanimous vote") },
        text = { Text("Every player agreed to end the game. The table is now read-only.") },
        confirmButton = { TextButton(onClick = { homeOpen = true; showOverview = true; lobbyOpen = true }) { Text("ROOM LOBBY", color = Mint) } },
        containerColor = Panel, titleContentColor = SoftWhite, textContentColor = Muted
    )

    if (clearAllConfirmOpen) AlertDialog(
        onDismissRequest = { clearAllConfirmOpen = false },
        title = { Text("Clear all app data?") },
        text = { Text("This clears the current game, room, chat, logs, and game settings. The Internet room URL resets to your default. Saved decks and tutorial progress are kept.") },
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
                TextButton(onClick = { stackOpen = false; argentumActionChoices = argentumSnapshot?.legalActions.orEmpty() }, enabled = argentumSnapshot?.legalActions?.isNotEmpty() == true) {
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
        tutorialStep = tutorialStops.getOrNull(tutorialIndex)?.takeIf { tutorialOpen && it.view == "STACK" }, tutorialIndex = tutorialIndex,
        tutorialTotal = tutorialStops.size, tutorialBounds = tutorialBounds, tutorialAnchor = ::tutorialAnchor,
        onTutorialBack = { goBackTutorial() }, onTutorialNext = { advanceTutorial() },
        onTutorialExit = { tutorialStops.getOrNull(tutorialIndex)?.let { markTutorialSeen(it.view) }; tutorialOpen = false },
        onPreviewCard = { action, card ->
            val ownerIndex = players.indexOfFirst { it.id == action.controllerId }.coerceAtLeast(0)
            preview = CardPreview(ownerIndex, card, fromHand = false, canManipulate = false)
        },
        onStackItemTap = { guidedAction("stack-item") {} },
        onCastFromHand = { guidedAction("stack-cast") {
            selectedPlayer = if (onlineMode) localSeat else priorityPlayer
            showOverview = false
            showHand = true
            stackOpen = false
        } },
        onAddTrigger = { title -> guidedAction("stack-add") { stackActions = stackActions + StackAction(title = title, controllerId = players.getOrNull(priorityPlayer)?.id.orEmpty(), kind = "TRIGGER"); priorityPassed = emptySet() } },
        onPassPriority = { guidedAction("stack-pass") {
            val currentId = players.getOrNull(priorityPlayer)?.id
            if (currentId != null) priorityPassed = priorityPassed + currentId
            priorityPlayer = nextTurnIndex(priorityPlayer, gameTurnOrder, players.size, players.indices.filterNot { players[it].isEliminated }.toSet())
        } },
        onResolve = { guidedAction("stack-resolve") { resolveTopOfStack() } },
        onDismiss = { guidedAction("stack-close") { stackOpen = false } }
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

    if (tutorialOpen && !lobbyOpen && !optionsOpen && !layoutLabOpen && !developerOptionsOpen && !stackOpen && !chatOpen && !logOpen && !importOpen && !savedDecksOpen && !deckEditorOpen && handActionCard == null) {
        val step = tutorialStops.getOrNull(tutorialIndex)
        if (step != null && step.view == currentTutorialView()) {
            GuidedTutorialOverlay(
                step = step, index = tutorialIndex, total = tutorialStops.size,
                targetBounds = tutorialBounds[step.target],
                onBack = { goBackTutorial() },
                onNext = { advanceTutorial() },
                onExit = { markTutorialSeen(step.view); tutorialOpen = false }
            )
        }
    }

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
        SnackbarHost(
            hostState = automaticActionSnackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            snackbar = { data -> Snackbar(snackbarData = data, containerColor = PanelLight, contentColor = SoftWhite) }
        )
}
}

private fun samplePlayers(): List<TablePlayer> {
    val one = listOf(
        GameCard(1, "Sol Ring", "Artifact", Color(0xFF9F8C63)),
        GameCard(2, "Arcane Signet", "Artifact", Color(0xFF8D8C9E)),
        GameCard(3, "Command Tower", "Land", Color(0xFF6C9A75)),
        GameCard(4, "Sai, Master Thopterist", "Creature · 1/4", Color(0xFF547D9A))
    )
    val two = listOf(
        GameCard(11, "Forest", "Land", Color(0xFF496B4F), tapped = true),
        GameCard(12, "Llanowar Elves", "Creature · 1/1", Color(0xFF596C4B)),
        GameCard(13, "Cultivate", "Sorcery", Color(0xFF756348))
    )
    return listOf(
        TablePlayer("YOU", library = List(92) { GameCard(100 + it, "Face-down card", "", Color(0xFF263B35)) }, hand = List(7) { GameCard(200 + it, "Face-down card", "", Color(0xFF263B35)) }, battlefield = one),
        TablePlayer("PLAYER 2", library = List(92) { GameCard(300 + it, "Face-down card", "", Color(0xFF263B35)) }, hand = List(7) { GameCard(400 + it, "Face-down card", "", Color(0xFF263B35)) }, battlefield = two)
    )
}

private data class CardPreview(val playerIndex: Int, val card: GameCard, val fromHand: Boolean, val canManipulate: Boolean = true)
@Composable
private fun LibrarySearchDialog(
    player: TablePlayer,
    onPreview: (GameCard) -> Unit,
    onTakeToHand: (GameCard) -> Unit,
    onPutBattlefield: (GameCard) -> Unit,
    onPutGraveyard: (GameCard) -> Unit,
    onPutExile: (GameCard) -> Unit,
    onMoveToTop: (GameCard) -> Unit,
    onMoveToBottom: (GameCard) -> Unit,
    onShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCardId by remember { mutableIntStateOf(-1) }
    // The general browse/search control is not permission to inspect the
    // library's actual stack. Keep a randomized presentation list separate
    // from player.library so selecting a card never changes draw order.
    val unorderedLibrary = remember(player.library) {
        val shuffled = player.library.shuffled()
        if (shuffled.size > 1 && shuffled.map { it.id } == player.library.map { it.id }) {
            shuffled.drop(1) + shuffled.first()
        } else shuffled
    }
    val matches = remember(unorderedLibrary, query) {
        unorderedLibrary.filter { card ->
            query.isBlank() || card.name.contains(query.trim(), ignoreCase = true) || card.type.contains(query.trim(), ignoreCase = true) || card.oracleText.orEmpty().contains(query.trim(), ignoreCase = true)
        }
    }
    val selected = unorderedLibrary.firstOrNull { it.id == selectedCardId }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Panel
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${player.name} · LIBRARY", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${player.library.size} cards · randomized display · library order hidden", color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = onShuffle, modifier = Modifier.heightIn(min = 44.dp)) { Text("SHUFFLE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close library", tint = Muted) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                    singleLine = true,
                    label = { Text("Search name, type, or rules text") },
                    placeholder = { Text("Blank search shows the full library") }
                )
                if (selected != null) {
                    Text("SELECTED · ${selected.name}", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            "TO HAND" to onTakeToHand,
                            "TO BATTLEFIELD" to onPutBattlefield,
                            "TO GRAVEYARD" to onPutGraveyard,
                            "EXILE" to onPutExile,
                            "TOP" to onMoveToTop,
                            "BOTTOM" to onMoveToBottom
                        ).forEach { (label, action) ->
                            TextButton(onClick = { action(selected); selectedCardId = -1 }, modifier = Modifier.heightIn(min = 44.dp)) {
                                Text(label, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Text("Select a card to move it. Long press a card to inspect it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 5.dp))
                }
                HorizontalDivider(color = PanelLight)
                when {
                    player.library.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Library is empty", color = Muted) }
                    matches.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No matching cards", color = Muted) }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(matches.size, key = { matches[it].id }) { index ->
                            val card = matches[index]
                            val isSelected = card.id == selectedCardId
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) PanelLight else Panel)
                                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) Mint else Panel, RoundedCornerShape(9.dp))
                                    .combinedClickable(onClick = { selectedCardId = card.id }, onLongClick = { onPreview(card) })
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CardFace(card, Modifier.width(37.dp), onClick = { selectedCardId = card.id }, onLongClick = { onPreview(card) }, showCounters = false)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(card.type, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isSelected) Text("SELECTED", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("Draw and common turn commands are in the hand tray.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun ManaPoolDialog(
    player: TablePlayer,
    onChange: (String, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val manaColors = listOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless")
    val total = player.manaPool.values.sum()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${player.name.uppercase()} · MANA POOL", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("$total mana tracked", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close mana pool", tint = Muted) }
                }
                Text("Tap + when you produce mana. Payments spend from this pool; it empties when you advance to another step.", color = Muted, fontSize = 10.sp)
                manaColors.forEach { (symbol, name) ->
                    val amount = player.manaPool[symbol] ?: 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ManaPip(symbol)
                            Text(name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onChange(symbol, -1) }, enabled = amount > 0, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−", color = if (amount > 0) SoftWhite else Muted, fontSize = 20.sp) }
                        Text("$amount", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 28.dp))
                        TextButton(onClick = { onChange(symbol, 1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+", color = Mint, fontSize = 20.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear, enabled = total > 0, modifier = Modifier.heightIn(min = 44.dp)) { Text("CLEAR POOL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("DONE", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CreatureTypeChoiceDialog(cardName: String, onConfirm: (String) -> Unit) {
    var creatureType by remember(cardName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Choose a creature type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${cardName} asks you to choose a creature type as it enters.", color = Muted)
                OutlinedTextField(
                    value = creatureType,
                    onValueChange = { creatureType = it.take(40) },
                    singleLine = true,
                    label = { Text("Creature type") },
                    placeholder = { Text("e.g. Elf") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(creatureType.trim()) }, enabled = creatureType.isNotBlank()) {
                Text("CONFIRM", color = if (creatureType.isNotBlank()) Mint else Muted, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Panel,
        titleContentColor = SoftWhite,
        textContentColor = Muted
    )
}
private data class ChatMessage(val author: String, val text: String, val timestamp: String)
private val debugCardPool = listOf(
    "Sol Ring" to "Artifact", "Arcane Signet" to "Artifact", "Command Tower" to "Land", "Island" to "Basic Land — Island", "Forest" to "Basic Land — Forest", "Plains" to "Basic Land — Plains", "Mountain" to "Basic Land — Mountain", "Swamp" to "Basic Land — Swamp",
    "Swords to Plowshares" to "Instant", "Counterspell" to "Instant", "Cultivate" to "Sorcery", "Beast Within" to "Instant", "Lightning Bolt" to "Instant", "Rhystic Study" to "Enchantment", "Smothering Tithe" to "Enchantment", "Llanowar Elves" to "Creature — Elf", "Sakura-Tribe Elder" to "Creature — Snake", "Mulldrifter" to "Creature — Elemental", "Solemn Simulacrum" to "Artifact Creature", "Swiftfoot Boots" to "Artifact", "Commander's Sphere" to "Artifact", "Murder" to "Instant", "Negate" to "Instant", "Elvish Mystic" to "Creature — Elf", "Treasure Token" to "Artifact Token — Treasure", "Goblin Token" to "Creature Token — Goblin", "Thopter Token" to "Artifact Creature Token — Thopter"
)

@Composable
private fun RoomHomeScreen(
    wide: Boolean,
    tutorialAnchor: (String) -> Modifier,
    argentumActive: Boolean,
    argentumWorking: Boolean,
    deckLoaded: Boolean,
    deckCommander: String?,
    onArgentum: () -> Unit,
    onImportDeck: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onContinue: () -> Unit,
    onTutorial: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(Modifier.widthIn(max = if (wide) 560.dp else 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ARCANA TABLE", color = SoftWhite, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Play Magic together", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onArgentum,
                    enabled = !argentumWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).then(tutorialAnchor("home-argentum")),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                ) { Text(when { argentumActive -> "RETURN TO ARGENTUM TABLE"; argentumWorking -> "CHECKING DECK WITH ARGENTUM…"; else -> "PLAY WITH ARGENTUM" }, fontSize = 14.sp, fontWeight = FontWeight.Black) }
                OutlinedButton(
                    onClick = onImportDeck,
                    enabled = !argentumActive && !argentumWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite)
                ) { Text(if (deckLoaded) "CHANGE PLAYER DECK · ${deckCommander ?: "COMMANDER NEEDED"}" else "IMPORT PLAYER DECK", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                if (!argentumActive) {
                Button(
                    onClick = onCreateRoom,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).then(tutorialAnchor("home-create")),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                ) { Text("CREATE ROOM", fontSize = 14.sp, fontWeight = FontWeight.Black) }
                OutlinedButton(
                    onClick = onJoinRoom,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).then(tutorialAnchor("home-join")),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite)
                ) { Text("JOIN ROOM", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (argentumActive) TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).then(tutorialAnchor("home-continue"))) {
                Text("RETURN TO TABLE", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onTutorial, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(tutorialAnchor("home-tutorial"))) {
                Text("FULL VISUAL GUIDE", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Text(label, color = if (enabled) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = modifier.heightIn(min = 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(if (enabled) PanelLight else Panel).combinedClickable(enabled = enabled, onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp))
}

@Composable
private fun ManaPip(symbol: String) {
    val normalized = symbol.uppercase()
    val fill = when (normalized.firstOrNull()) {
        'W' -> Color(0xFFF3EAD2)
        'U' -> Color(0xFF4E93C8)
        'B' -> Color(0xFF69636F)
        'R' -> Color(0xFFC85450)
        'G' -> Color(0xFF4F9363)
        'C' -> Color(0xFF87918D)
        else -> Color(0xFF747D79)
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(fill).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        Text(normalized, color = if (normalized == "W") Color(0xFF40362A) else Color.White, fontSize = if (normalized.length > 1) 9.sp else 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun ManaCostPips(cost: String) {
    val tokenPattern = remember { Regex("[\\[{]([^}\\]]+)[}\\]]") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        var cursor = 0
        tokenPattern.findAll(cost).forEach { match ->
            if (match.range.first > cursor) Text(cost.substring(cursor, match.range.first), fontSize = 12.sp)
            val symbol = match.groupValues[1]
            if (symbol.length == 1 && symbol.first().uppercaseChar() in "WUBRGC") ManaPip(symbol)
            else Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF737C78)).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                Text(symbol, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            cursor = match.range.last + 1
        }
        if (cursor < cost.length) Text(cost.substring(cursor), fontSize = 12.sp)
    }
}

@Composable
private fun ManaSymbolText(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val tokenPattern = remember { Regex("[\\[{]([WUBRGC])[}\\]]") }
    val matches = remember(text) { tokenPattern.findAll(text).toList() }
    val annotated = buildAnnotatedString {
        var cursor = 0
        matches.forEachIndexed { index, match ->
            append(text.substring(cursor, match.range.first))
            appendInlineContent("mana-pip-$index", alternateText = match.value)
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    val inline = matches.mapIndexed { index, match ->
        "mana-pip-$index" to InlineTextContent(
            Placeholder(fontSize * 1.55f, fontSize * 1.55f, PlaceholderVerticalAlign.Center)
        ) { ManaPip(match.groupValues[1]) }
    }.toMap()
    Text(annotated, modifier = modifier, color = color, fontSize = fontSize, inlineContent = inline)
}

@Composable
private fun HandPeekBar(cardCount: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(PanelLight)
            .combinedClickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("HAND  $cardCount", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black)
        if (cardCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(cardCount.coerceAtMost(3)) {
                    Box(Modifier.size(width = 12.dp, height = 25.dp).clip(RoundedCornerShape(3.dp)).background(Panel).border(1.dp, Muted.copy(alpha = 0.6f), RoundedCornerShape(3.dp)))
                }
            }
        }
        Icon(Icons.Filled.ExpandLess, contentDescription = "Open hand tray", tint = Mint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Ink else SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(if (selected) Mint else PanelLight).combinedClickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 12.dp))
}

@Composable
private fun PlayerViewTab(name: String, commander: GameCard?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val artUrl = commander?.artCropUrl ?: commander?.imageUrl
    Box(
        Modifier.width(96.dp).height(50.dp).clip(shape)
            .background(PanelLight)
            .border(if (selected) 2.dp else 1.dp, if (selected) Mint else Color(0xFF33423D), shape)
            .combinedClickable(onClick = onClick)
    ) {
        if (!artUrl.isNullOrBlank()) {
            CardArt(artUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO COMMANDER", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            name.uppercase(), color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Black,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xD9000000)).padding(horizontal = 5.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LobbyDialog(
    tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier,
    onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit, onTutorialTarget: (String) -> Unit,
    players: List<TablePlayer>, roomCreated: Boolean, isHost: Boolean, gameStarted: Boolean, gameEnded: Boolean, joining: Boolean, roomCode: String,
    password: String, joinCode: String, joinPassword: String, readyPlayers: Set<Int>, pendingReady: Boolean?, deckSyncConfirmed: Boolean,
    onTakeMulligan: () -> Unit, onKeepOpeningHand: (List<Int>) -> Unit,
    hostInput: String, internetMode: Boolean, cloudEndpoint: String, playerName: String, hostAddress: String, localSeat: Int, onlineStatus: String,
    turnOrder: List<Int>?, notice: String?, onRemovePlayer: (Int) -> Unit, onExportDiagnostics: () -> Unit, onDismiss: () -> Unit, onCancelJoin: () -> Unit,
    onSelectJoin: () -> Unit, onSelectCreate: () -> Unit, onSelectNetwork: (Boolean) -> Unit, onCloudEndpoint: (String) -> Unit, onPassword: (String) -> Unit,
    onJoinCode: (String) -> Unit, onJoinPassword: (String) -> Unit, onHostInput: (String) -> Unit, onPlayerName: (String) -> Unit,
    onCreate: () -> Unit, onJoin: () -> Unit, onToggleReady: (Int) -> Unit,
    onImportDeck: (Int) -> Unit, onAddAi: () -> Unit, onRandomizeTurnOrder: () -> Unit, onStartGame: () -> Unit, onLeave: () -> Unit
) {
    val context = LocalContext.current
    var removeTarget by remember { mutableIntStateOf(-1) }
    var openingHandDialogOpen by remember { mutableStateOf(false) }
    val decksReady = players.size in 1..4 && players.all { it.deckLoaded }
    val allReady = players.indices.all { it in readyPlayers }
    val canStart = isHost && roomCreated && !gameStarted && decksReady && allReady
    val canHostHandOff = players.drop(1).any { it.isOnline && !it.isComputer }
    val canLeaveRoom = internetMode || !isHost || !gameStarted || gameEnded || canHostHandOff
    val displayedOrder = turnOrder?.takeIf { it.size == players.size } ?: players.indices.toList()
    val firstPlayer = players.getOrNull(displayedOrder.firstOrNull() ?: 0)?.name ?: "—"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GAME LOBBY", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 0.8.sp)
                        Text("COMMANDER · UP TO 4 PLAYERS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        Text("APP VERSION ${BuildConfig.VERSION_NAME} · BUILD ${BuildConfig.VERSION_CODE}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("lobby-close"))) { Icon(Icons.Filled.Close, "Close game lobby", tint = Muted) }
                }

                Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFF3A3021), shape = RoundedCornerShape(9.dp)) {
                    Text(if (internetMode) "INTERNET ROOM · Connect from different networks using the secure room service." else "SAME WI-FI ROOM · All phones must be on the same local network.", color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val createSelected = !joining
                    Button(
                        onClick = onSelectCreate,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp).then(tutorialAnchor("lobby-create")),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (createSelected) Mint else PanelLight,
                            contentColor = if (createSelected) Ink else SoftWhite
                        ),
                        border = if (createSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CREATE ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (createSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val joinSelected = joining
                    Button(
                        onClick = onSelectJoin,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp).then(tutorialAnchor("lobby-join")),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (joinSelected) Mint else PanelLight,
                            contentColor = if (joinSelected) Ink else SoftWhite
                        ),
                        border = if (joinSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("JOIN ROOM", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (joinSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated && onlineStatus != "Connecting") Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val localSelected = !internetMode
                    Button(
                        onClick = { onSelectNetwork(false) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).then(tutorialAnchor("lobby-local")),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (localSelected) Mint else PanelLight,
                            contentColor = if (localSelected) Ink else SoftWhite
                        ),
                        border = if (localSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOCAL WI-FI", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (localSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                    val internetSelected = internetMode
                    Button(
                        onClick = { onSelectNetwork(true) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).then(tutorialAnchor("lobby-internet")),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (internetSelected) Mint else PanelLight,
                            contentColor = if (internetSelected) Ink else SoftWhite
                        ),
                        border = if (internetSelected) null else BorderStroke(1.dp, Color(0xFF52635F)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("INTERNET", fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            if (internetSelected) Text("SELECTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }

                if (!roomCreated || joining) {
                    if (joining) {
                        val isConnecting = onlineStatus == "Connecting"
                        Text(if (internetMode) "Join an internet room" else "Join a room on the same Wi-Fi network", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode) {
                            OutlinedTextField(cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room service URL") })
                            Text("Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                        }
                        else OutlinedTextField(hostInput, onHostInput, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Host IP address · port is automatic") }, placeholder = { Text("192.168.1.25") })
                        OutlinedTextField(playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Your player name") })
                        OutlinedTextField(joinCode, onJoinCode, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room code") })
                        OutlinedTextField(joinPassword, onJoinPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                        Button(onClick = onJoin, enabled = !isConnecting && (if (internetMode) cloudEndpoint.isNotBlank() else hostInput.isNotBlank()) && joinCode.isNotBlank() && joinPassword.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp).then(tutorialAnchor("lobby-submit"))) {
                            Text(if (isConnecting) "CONNECTING…" else "JOIN GAME")
                        }
                        if (isConnecting) {
                            OutlinedButton(onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).then(tutorialAnchor("lobby-cancel"))) {
                                Text("CANCEL JOIN", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isConnecting || notice != null) {
                            Surface(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = if (isConnecting) PanelLight else Color(0xFF3A3021),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isConnecting) CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                                    Text(
                                        notice ?: onlineStatus,
                                        color = if (isConnecting) Mint else Color(0xFFE9978B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = if (isConnecting) 10.dp else 0.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(if (internetMode) "Create a password-protected internet room" else "Create a password-protected room", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        if (internetMode && onlineStatus == "Connecting") {
                            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(9.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                                    Text("Opening a secure internet room…", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                }
                            }
                            OutlinedButton(onClick = onCancelJoin, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).then(tutorialAnchor("lobby-cancel"))) {
                                Text("CANCEL", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (internetMode) {
                                OutlinedTextField(cloudEndpoint, onCloudEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room service URL") })
                                Text("Default address is set for you. You do not need to edit it.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp, top = 3.dp))
                                OutlinedTextField(playerName, onPlayerName, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Your player name") })
                            }
                            OutlinedTextField(password, onPassword, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), singleLine = true, label = { Text("Room password · 4 characters minimum") }, visualTransformation = PasswordVisualTransformation())
                            if (notice != null) Text(notice, color = Color(0xFFFF9C91), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = onCreate, enabled = password.length >= 4 && (!internetMode || cloudEndpoint.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp).then(tutorialAnchor("lobby-submit"))) { Text("CREATE LOBBY") }
                        }
                    }
                } else {
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = PanelLight, shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("ROOM CODE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(roomCode, color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                }
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table room code", roomCode))
                                    onTutorialTarget("lobby-copy")
                                }, modifier = Modifier.then(tutorialAnchor("lobby-copy"))) { Text("COPY", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                            TextButton(onClick = {
                                val network = if (internetMode) "Internet room · $cloudEndpoint" else "Same Wi-Fi · ${hostAddress.ifBlank { "ask the host to share their IP" }}"
                                val invite = "Join my Arcana Table game!\nRoom code: $roomCode\nPassword: $password\n$network"
                                runCatching {
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, invite)
                                    }, "Share room invite"))
                                }
                                onTutorialTarget("lobby-share")
                            }, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp).then(tutorialAnchor("lobby-share"))) {
                                Text("SHARE ROOM", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("PASSWORD PROTECTED · ${"•".repeat(password.length.coerceAtMost(12))}", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (hostAddress.isBlank()) onlineStatus else "SAME WI-FI · $hostAddress", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (hostAddress.isNotBlank()) TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Arcana Table host address", hostAddress))
                                    onTutorialTarget("lobby-copy-ip")
                                }, modifier = Modifier.then(tutorialAnchor("lobby-copy-ip"))) { Text("COPY IP", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("PLAYERS", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("${players.size}/4 SEATS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    players.forEachIndexed { index, player ->
                        val ready = index in readyPlayers
                        val canMarkReady = index == localSeat || isHost && player.isComputer
                        val deckConfirmed = index != localSeat || !roomCreated || isHost || deckSyncConfirmed
                        val deckReady = player.deckLoaded && deckConfirmed
                        val handDecisionMade = player.openingHandKept || player.isComputer
                        val canToggleReady = !gameStarted && deckReady && handDecisionMade && canMarkReady && (index != localSeat || pendingReady == null)
                        Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (index == 0) tutorialAnchor("lobby-player") else Modifier), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (ready) Mint else Panel).border(1.dp, if (ready) Mint else Muted, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("P${index + 1}", color = if (ready) Ink else SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                                        Text(player.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${if (index == 0) "HOST · " else ""}${if (player.isComputer) "AI · " else if (player.isOnline) "ONLINE · " else "OFFLINE · "}${when { !player.deckLoaded -> "DECK REQUIRED"; !deckConfirmed -> "DECK SYNCING"; !handDecisionMade -> "OPENING HAND NEEDED"; else -> "DECK READY" }}${if (index == localSeat) " · YOU" else ""}",
                                            color = if ((player.isOnline || player.isComputer || index == localSeat) && deckReady && handDecisionMade) Mint else CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(if (ready) "READY" else "NOT READY", color = if (ready) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                                    if (!player.deckLoaded && index == localSeat) TextButton(onClick = { onImportDeck(index) }, modifier = Modifier.heightIn(min = 48.dp).then(tutorialAnchor("lobby-import"))) { Text("IMPORT DECK", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (!gameStarted && index == localSeat && deckReady && !player.openingHandKept && !player.isComputer) TextButton(onClick = { openingHandDialogOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("OPENING HAND · ${player.hand.size}", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                    TextButton(onClick = { onToggleReady(index) }, enabled = canToggleReady || ready, modifier = Modifier.heightIn(min = 48.dp).then(tutorialAnchor("lobby-ready"))) { Text(if (gameStarted) "GAME STARTED" else if (index == localSeat && pendingReady != null) "SENDING…" else if (ready) "CANCEL READY" else if (!handDecisionMade && index == localSeat) "CHOOSE HAND" else if (canMarkReady) if (!deckConfirmed) "SYNCING…" else "READY UP" else "WAITING", color = if (canToggleReady || ready) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (isHost && index > 0 && !internetMode) TextButton(onClick = { removeTarget = index }, modifier = Modifier.heightIn(min = 48.dp)) { Text("REMOVE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    if (isHost && !gameStarted && players.size < 4) {
                        OutlinedButton(onClick = onAddAi, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).then(tutorialAnchor("lobby-ai"))) {
                            Text("ADD AI OPPONENT · ${players.size}/4", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (players.size < 4) {
                        repeat(4 - players.size) { seat ->
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), color = Panel.copy(alpha = 0.55f), shape = RoundedCornerShape(9.dp)) {
                                Text("P${players.size + seat + 1} · Waiting for a player to join with the room code", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(11.dp))
                            }
                        }
                    }

                    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("GOES FIRST", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(firstPlayer, color = Mint, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Text(if (turnOrder == null) "Current seat order" else "Randomized turn order", color = Muted, fontSize = 8.sp)
                            }
                            if (isHost) TextButton(onClick = onRandomizeTurnOrder, modifier = Modifier.heightIn(min = 44.dp).then(tutorialAnchor("lobby-order"))) { Text("RANDOMIZE ORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (notice != null) Text(notice, color = CounterGold, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    if (!decksReady) {
                        val missingDecks = players.filterNot { it.deckLoaded }.joinToString { it.name }
                        Text("Waiting for a deck from: $missingDecks", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (!allReady) {
                        val notReady = players.mapIndexedNotNull { index, player -> player.name.takeIf { index !in readyPlayers } }.joinToString()
                        Text("Waiting for ready confirmation from: $notReady", color = CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    } else if (isHost && canStart) {
                        Text("Everyone is ready. Start the game when you’re set.", color = Mint, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    }

                    OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).then(tutorialAnchor("lobby-diagnostics"))) {
                        Text("EXPORT DIAGNOSTIC LOG", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isHost && !gameStarted) Button(onClick = onStartGame, enabled = canStart, modifier = Modifier.fillMaxWidth().padding(top = 11.dp).heightIn(min = 50.dp).then(tutorialAnchor("lobby-start")), colors = ButtonDefaults.buttonColors(disabledContainerColor = PanelLight, disabledContentColor = Muted)) {
                        Text("START GAME", fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                    } else if (isHost) Text(if (gameEnded) "GAME ENDED · unanimous vote" else "GAME IN PROGRESS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    else Text("$onlineStatus · Waiting for the host to start", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    if (isHost && gameStarted && !gameEnded && !canHostHandOff) {
                        Text("A connected player must be available to take over hosting before you can leave.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    TextButton(onClick = onLeave, enabled = canLeaveRoom, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).then(tutorialAnchor("lobby-leave"))) {
                        Text(
                            when {
                                isHost && internetMode && gameStarted && !gameEnded -> "LEAVE GAME · KEEP ROOM OPEN"
                                isHost && gameStarted && !gameEnded -> "LEAVE GAME · TRANSFER HOST"
                                isHost -> "CLOSE ROOM"
                                else -> "LEAVE GAME"
                            },
                            color = if (canLeaveRoom) Color(0xFFE9978B) else Muted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
            }
        }
    }
    if (removeTarget in players.indices && removeTarget > 0) {
        val targetName = players[removeTarget].name
        AlertDialog(
            onDismissRequest = { removeTarget = -1 },
            title = { Text("Remove $targetName?") },
            text = { Text("This removes the player and their seat from the online room.") },
            confirmButton = {
                TextButton(onClick = { onRemovePlayer(removeTarget); removeTarget = -1 }) {
                    Text("REMOVE PLAYER", color = Color(0xFFE9978B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = -1 }) { Text("CANCEL") } }
        )
    }
    if (openingHandDialogOpen && !gameStarted && localSeat in players.indices && players[localSeat].deckLoaded && !players[localSeat].openingHandKept) {
        OpeningHandDialog(
            player = players[localSeat],
            onMulligan = onTakeMulligan,
            onKeep = { bottomIds -> onKeepOpeningHand(bottomIds); openingHandDialogOpen = false },
            onDismiss = { openingHandDialogOpen = false }
        )
    }
}

@Composable
private fun OpeningHandDialog(player: TablePlayer, onMulligan: () -> Unit, onKeep: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var choosingBottomCards by remember(player.id) { mutableStateOf(false) }
    var selectedCardIds by remember(player.id, player.hand.map { it.id }, player.mulligansTaken) { mutableStateOf(emptyList<Int>()) }
    var previewCard by remember(player.id) { mutableStateOf<GameCard?>(null) }
    val bottomCount = (player.mulligansTaken - 1).coerceAtLeast(0)
    val canMulliganAgain = player.mulligansTaken < 8
    val openingCardWidth = (LocalConfiguration.current.screenWidthDp * 0.36f).dp.coerceIn(132.dp, 190.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text("OPENING HAND · ${player.hand.size} CARDS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("Mulligans taken: ${player.mulligansTaken} · First multiplayer mulligan is free", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (choosingBottomCards) "Select exactly $bottomCount card(s) to put on the bottom of your library, then confirm."
                    else "Tap a card to enlarge it. Keep this hand or mulligan; a mulligan shuffles your whole hand into your library, then deals a fresh seven.",
                    color = SoftWhite, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )
                if (choosingBottomCards) Text("Tap a card to enlarge it; use the button under a card to select it for the bottom.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    player.hand.forEach { card ->
                        val selected = card.id in selectedCardIds
                        Column(Modifier.width(openingCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(9.dp)).padding(2.dp)) {
                                CardFace(card, Modifier.fillMaxWidth(), onClick = { previewCard = card }, onLongClick = { previewCard = card }, showCounters = false)
                            }
                            Text(card.name, color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            if (choosingBottomCards) {
                                OutlinedButton(
                                    onClick = { selectedCardIds = if (selected) selectedCardIds - card.id else selectedCardIds + card.id },
                                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).heightIn(min = 38.dp),
                                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, if (selected) Mint else Muted)
                                ) {
                                    Text(if (selected) "${selectedCardIds.indexOf(card.id) + 1} · BOTTOM" else "SELECT", color = if (selected) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (choosingBottomCards) {
                    Text("Selected: ${selectedCardIds.size}/$bottomCount", color = if (selectedCardIds.size == bottomCount) Mint else CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { choosingBottomCards = false; selectedCardIds = emptyList() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("BACK", color = Muted) }
                        Button(onClick = { onKeep(selectedCardIds) }, enabled = selectedCardIds.size == bottomCount, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CONFIRM KEEP") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CANCEL", color = Muted) }
                        OutlinedButton(onClick = onMulligan, enabled = canMulliganAgain, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("MULLIGAN", color = if (canMulliganAgain) CounterGold else Muted) }
                        Button(onClick = { if (bottomCount == 0) onKeep(emptyList()) else choosingBottomCards = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (bottomCount == 0) "KEEP HAND" else "KEEP · BOTTOM $bottomCount") }
                    }
                }
            }
        }
    }
    previewCard?.let { card ->
        CardPreviewPopup(item = CardPreview(playerIndex = 0, card = card, fromHand = true), onDismiss = { previewCard = null })
    }
}

@Composable
private fun OptionsPage(
    tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier,
    onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit,
    darkMode: Boolean, timerRunning: Boolean, timerVisible: Boolean, backgroundAlertsEnabled: Boolean,
    autoReturnTableAfterPass: Boolean, autoDrawOnDrawStep: Boolean, autoUntapOnUntapStep: Boolean, openHandAfterDraw: Boolean,
    reducedSwipeMotion: Boolean, cardSizeScale: Float, onOpenLayoutLab: () -> Unit,
    onToggleTheme: () -> Unit, onToggleTimerVisibility: () -> Unit, onToggleBackgroundAlerts: (Boolean) -> Unit,
    onAutoReturnTable: (Boolean) -> Unit, onAutoDraw: (Boolean) -> Unit, onAutoUntap: (Boolean) -> Unit,
    onOpenHandAfterDraw: (Boolean) -> Unit, onReducedMotion: (Boolean) -> Unit, onCardSizeScale: (Float) -> Unit,
    onOpenDeveloperOptions: () -> Unit, onClearAllData: () -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OPTIONS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("options-close"))) { Icon(Icons.Filled.Close, "Close options", tint = Muted) }
                }
                Text("APPEARANCE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(tutorialAnchor("options-theme"))) { Text("Theme  ·  ${if (darkMode) "Dark" else "Light"}", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                SettingsToggle("Reduce swipe animation", "Skip the animated jump when selecting a view tab.", reducedSwipeMotion, onReducedMotion, tutorialAnchor("options-motion"))
                Text("CARD SIZE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().then(tutorialAnchor("options-card-size")), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.82f to "SMALL", 1f to "STANDARD", 1.18f to "LARGE").forEach { (scale, label) ->
                        TextButton(onClick = { onCardSizeScale(scale) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text(if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) "✓ $label" else label, color = if (kotlin.math.abs(cardSizeScale - scale) < 0.03f) Mint else SoftWhite, fontSize = 9.sp) }
                    }
                }
                TextButton(onClick = onOpenLayoutLab, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                    Text("ADJUST UI LAYOUT  ›", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                }
                Text("NAVIGATION", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                SettingsToggle("Return to Table after passing", "Show the full table when your turn passes.", autoReturnTableAfterPass, onAutoReturnTable, tutorialAnchor("options-return-table"))
                SettingsToggle("Open hand after drawing", "Show the hand tray after a draw-step card is drawn.", openHandAfterDraw, onOpenHandAfterDraw, tutorialAnchor("options-open-hand"))
                Text("GAME", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                SettingsToggle("Draw automatically", "Draw when turn progression reaches the draw step.", autoDrawOnDrawStep, onAutoDraw, tutorialAnchor("options-auto-draw"))
                SettingsToggle("Untap automatically", "Untap your permanents as your new turn begins; stun counters are removed instead of untapping.", autoUntapOnUntapStep, onAutoUntap, tutorialAnchor("options-auto-untap"))
                Text("Turn timer is ${if (timerRunning) "running" else "stopped"}. Timer controls are in the timer menu at the top of the table.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 5.dp))
                TextButton(onClick = onToggleTimerVisibility, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(tutorialAnchor("options-timer"))) { Text(if (timerVisible) "Hide turn timer display" else "Show turn timer display", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                Text("NOTIFICATIONS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).then(tutorialAnchor("options-alerts")), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Background game alerts", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Notify when your turn starts or another player makes a play.", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = backgroundAlertsEnabled, onCheckedChange = onToggleBackgroundAlerts)
                }
                TextButton(onClick = onClearAllData, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).then(tutorialAnchor("clear-data"))) {
                    Text("CLEAR ALL DATA", color = Color(0xFFE9978B), fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth())
                }
                Text("Saved decks and tutorial progress are kept when game data is cleared.", color = Muted, fontSize = 10.sp)
                Text("TESTING", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).then(tutorialAnchor("options-developer"))) { Text("Open Developer Options  ›", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                Text("Room tests connect devices on the same Wi-Fi network. Connections are not encrypted and cannot cross networks yet.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 14.dp))
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
            }
        }
    }
}

@Composable
private fun LayoutLabPage(
    orientationName: String,
    cardSizeScale: Float,
    boardSpacingScale: Float,
    handSheetHeightFraction: Float,
    footerButtonScale: Float,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    handActionOrder: List<String>,
    onCardSizeScale: (Float) -> Unit,
    onBoardSpacingScale: (Float) -> Unit,
    onHandSheetHeightFraction: (Float) -> Unit,
    onFooterButtonScale: (Float) -> Unit,
    onPlayerSectionOrder: (List<String>) -> Unit,
    onQuickActionOrder: (List<String>) -> Unit,
    onHandActionOrder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var previewScreen by remember { mutableStateOf("PLAYER") }
    var selectedElement by remember { mutableStateOf("BATTLEFIELD") }
    var addElementMenuOpen by remember { mutableStateOf(false) }
    val playerSectionCatalog = listOf("STATUS", "MANA", "COMMANDER", "STEP", "ZONES", "REVEALED", "BATTLEFIELD")
    val quickActionCatalog = listOf("LIBRARY", "HAND", "GRAVEYARD", "EXILE", "REVEALED", "MANA", "DRAW", "REVEAL", "STACK", "TOKEN", "COMBAT", "CAST_COMMANDER", "NEXT_STEP", "SHUFFLE", "LOG", "CHAT")
    val handActionCatalog = listOf("DRAW", "REVEAL", "STACK", "CAST_COMMANDER", "COMBAT", "NEXT_STEP", "PASS_TURN", "MORE")
    fun moveItem(items: List<String>, index: Int, direction: Int, save: (List<String>) -> Unit) {
        val destination = (index + direction).coerceIn(0, items.lastIndex)
        if (destination == index) return
        val updated = items.toMutableList().apply { add(destination, removeAt(index)) }
        save(updated)
    }
    fun setPreset(card: Float, spacing: Float, hand: Float, footer: Float) {
        onCardSizeScale(card)
        onBoardSpacingScale(spacing)
        onHandSheetHeightFraction(hand)
        onFooterButtonScale(footer)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ADJUST UI · $orientationName", color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("$orientationName layout · saves automatically", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Close, "Close layout lab", tint = Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("PLAYER", "HAND").forEach { screen ->
                        val selected = previewScreen == screen
                        TextButton(
                            onClick = { previewScreen = screen; selectedElement = if (screen == "HAND") "HAND" else "BATTLEFIELD" },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (selected) PanelLight else Color.Transparent)
                        ) { Text(screen, color = if (selected) Mint else Muted, fontWeight = FontWeight.Bold) }
                    }
                }
                LayoutLabPreview(
                    previewScreen = previewScreen,
                    selectedElement = selectedElement,
                    cardSizeScale = cardSizeScale,
                    boardSpacingScale = boardSpacingScale,
                    handSheetHeightFraction = handSheetHeightFraction,
                    footerButtonScale = footerButtonScale,
                    playerSectionOrder = playerSectionOrder,
                    quickActionOrder = quickActionOrder,
                    handActionOrder = handActionOrder,
                    onSelectElement = { selectedElement = it },
                    onMovePlayerElement = { id, direction ->
                        val index = playerSectionOrder.indexOf(id)
                        if (index >= 0) moveItem(playerSectionOrder, index, direction, onPlayerSectionOrder)
                    },
                    onMoveQuickAction = { id, direction ->
                        val index = quickActionOrder.indexOf(id)
                        if (index >= 0) moveItem(quickActionOrder, index, direction, onQuickActionOrder)
                    },
                    onMoveHandAction = { id, direction ->
                        val index = handActionOrder.indexOf(id)
                        if (index >= 0) moveItem(handActionOrder, index, direction, onHandActionOrder)
                    }
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("COMFORTABLE", "LARGER CARDS", "COMPACT").forEach { preset ->
                        TextButton(
                            onClick = {
                                when (preset) {
                                    "LARGER CARDS" -> setPreset(1.38f, 1.05f, 0.64f, 1f)
                                    "COMPACT" -> setPreset(0.9f, 0.72f, 0.46f, 0.8f)
                                    else -> setPreset(1f, 1.2f, 0.58f, 1.05f)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Text(preset, color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp)
                        }
                    }
                }
                Text("ADJUST · $selectedElement", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (selectedElement) {
                        "BATTLEFIELD" -> {
                            LayoutTuningSlider("Card size", cardSizeScale, 0.75f..1.4f, { "${(it * 100).toInt()}%" }, onCardSizeScale)
                        }
                        "STATUS", "COMMANDER" -> Text("This element can be removed below and restored from ADD ELEMENT.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "ZONES" -> Text("Choose which zone shortcuts and actions appear in the Quick Actions strip. Empty zones stay visible with a count of 0.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        "SPACING", "STEP", "REVEALED" -> LayoutTuningSlider("Space between sections", boardSpacingScale, 0.5f..2f, { "${(it * 100).toInt()}%" }, onBoardSpacingScale)
                        "HAND" -> {
                            LayoutTuningSlider("Hand tray height", handSheetHeightFraction, 0.38f..0.95f, { "${(it * 100).toInt()}%" }, onHandSheetHeightFraction)
                            LayoutTuningSlider("Hand card size", cardSizeScale, 0.75f..1.4f, { "${(it * 100).toInt()}%" }, onCardSizeScale)
                        }
                        "FOOTER" -> LayoutTuningSlider("Footer button size", footerButtonScale, 0.78f..1.3f, { "${(it * 100).toInt()}%" }, onFooterButtonScale)
                    }
                    Text("Tap an element to tune it. Long-press and drag elements in the sample layout; nearby items animate aside as you move.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 8.dp))
                    if (previewScreen == "PLAYER") {
                        Text("PLAYER SCREEN ELEMENTS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                        playerSectionOrder.forEachIndexed { index, id ->
                            LayoutOrderRow(
                                title = layoutSectionTitle(id),
                                subtitle = if (id == "STATUS") "Health, mana and activity" else if (id == "COMMANDER") "Commander card and cast control" else "Drag handle · long press to move",
                                selected = selectedElement == id,
                                onSelect = { selectedElement = id },
                                onMove = { direction -> moveItem(playerSectionOrder, index, direction, onPlayerSectionOrder) },
                                onRemove = { onPlayerSectionOrder(playerSectionOrder - id) }
                            )
                        }
                        if ("ZONES" in playerSectionOrder) {
                            Text("QUICK ACTIONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                            quickActionOrder.forEachIndexed { index, id ->
                                LayoutOrderRow(
                                    title = layoutQuickActionTitle(id),
                                    subtitle = "Shown in the Quick Actions strip",
                                    selected = selectedElement == "QUICK:$id",
                                    onSelect = { selectedElement = "QUICK:$id" },
                                    onMove = { direction -> moveItem(quickActionOrder, index, direction, onQuickActionOrder) },
                                    onRemove = { onQuickActionOrder(quickActionOrder - id) }
                                )
                            }
                            Box {
                                TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD QUICK ACTION  +", color = Mint, fontWeight = FontWeight.Bold) }
                                DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                    quickActionCatalog.filterNot { it in quickActionOrder }.forEach { id ->
                                        DropdownMenuItem(text = { Text("Add ${layoutQuickActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onQuickActionOrder(quickActionOrder + id) })
                                    }
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD ELEMENT  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                playerSectionCatalog.filterNot { it in playerSectionOrder }.forEach { id ->
                                    DropdownMenuItem(text = { Text("Add ${layoutSectionTitle(id)}") }, onClick = { addElementMenuOpen = false; onPlayerSectionOrder(playerSectionOrder + id) })
                                }
                            }
                        }
                    } else {
                        Text("HAND TRAY BUTTONS · HOLD AND DRAG TO REORDER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                        handActionOrder.forEachIndexed { index, id ->
                            LayoutOrderRow(
                                title = layoutHandActionTitle(id),
                                subtitle = "Shown when the action is available",
                                selected = selectedElement == id,
                                onSelect = { selectedElement = id },
                                onMove = { direction -> moveItem(handActionOrder, index, direction, onHandActionOrder) },
                                onRemove = { onHandActionOrder(handActionOrder - id) }
                            )
                        }
                        Box {
                            TextButton(onClick = { addElementMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("ADD BUTTON  +", color = Mint, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = addElementMenuOpen, onDismissRequest = { addElementMenuOpen = false }) {
                                handActionCatalog.filterNot { it in handActionOrder }.forEach { id ->
                                    DropdownMenuItem(text = { Text("Add ${layoutHandActionTitle(id)}") }, onClick = { addElementMenuOpen = false; onHandActionOrder(handActionOrder + id) })
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReset, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("RESET TO DEFAULT", color = Color(0xFFE5B2A7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelLight)) {
                        Text("DONE", color = Mint, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

private fun layoutSectionTitle(id: String): String = when (id) {
    "STATUS" -> "Health and activity bar"
    "MANA" -> "Mana pool"
    "COMMANDER" -> "Commander strip"
    "STEP" -> "Next step"
    "ZONES" -> "Quick Actions"
    "REVEALED" -> "Public reveals"
    "BATTLEFIELD" -> "Battlefield"
    else -> id
}

private fun layoutQuickActionTitle(id: String): String = when (id) {
    "LIBRARY" -> "Library"
    "HAND" -> "Hand"
    "GRAVEYARD" -> "Graveyard"
    "EXILE" -> "Exile"
    "REVEALED" -> "Revealed"
    "MANA" -> "Mana pool"
    "DRAW" -> "Draw"
    "REVEAL" -> "Reveal hand"
    "STACK" -> "Stack"
    "TOKEN" -> "Create token"
    "COMBAT" -> "Combat"
    "CAST_COMMANDER" -> "Cast Commander"
    "NEXT_STEP" -> "Next step"
    "SHUFFLE" -> "Shuffle library"
    "LOG" -> "Game log"
    "CHAT" -> "Chat"
    else -> id
}

private fun layoutHandActionTitle(id: String): String = when (id) {
    "DRAW" -> "Draw"
    "REVEAL" -> "Reveal hand"
    "STACK" -> "Stack"
    "CAST_COMMANDER" -> "Cast Commander"
    "COMBAT" -> "Combat"
    "NEXT_STEP" -> "Next step"
    "PASS_TURN" -> "Pass turn"
    "MORE" -> "More actions"
    else -> id
}

@Composable
private fun LayoutOrderRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val density = LocalDensity.current
    val dragThreshold = with(density) { 34.dp.toPx() }
    val currentMove by rememberUpdatedState(onMove)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) PanelLight.copy(alpha = 0.9f) else Panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) Mint else PanelLight, RoundedCornerShape(8.dp))
            .pointerInput(title, dragThreshold) {
                var accumulatedDrag = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { accumulatedDrag = 0f; onSelect() },
                    onDrag = { change, amount ->
                        change.consume()
                        accumulatedDrag += amount.y
                        if (accumulatedDrag >= dragThreshold) { currentMove(1); accumulatedDrag -= dragThreshold }
                        else if (accumulatedDrag <= -dragThreshold) { currentMove(-1); accumulatedDrag += dragThreshold }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⠿", color = Mint, fontSize = 19.sp, modifier = Modifier.padding(start = 10.dp, end = 9.dp))
        Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
            Text(title, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 8.sp)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Remove $title", tint = Color(0xFFE9978B)) }
    }
}

@Composable
private fun LayoutLabPreview(
    previewScreen: String,
    selectedElement: String,
    cardSizeScale: Float,
    boardSpacingScale: Float,
    handSheetHeightFraction: Float,
    footerButtonScale: Float,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    handActionOrder: List<String>,
    onSelectElement: (String) -> Unit,
    onMovePlayerElement: (String, Int) -> Unit,
    onMoveQuickAction: (String, Int) -> Unit,
    onMoveHandAction: (String, Int) -> Unit
) {
    val sectionGap = (4.dp * boardSpacingScale).coerceIn(2.dp, 10.dp)
    val previewHeight = if (previewScreen == "HAND") (160.dp + 150.dp * handSheetHeightFraction).coerceIn(205.dp, 285.dp) else 226.dp
    Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(13.dp)).background(Color(0xFF101917))) {
        if (previewScreen == "PLAYER") {
            LazyColumn(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                items(playerSectionOrder, key = { "section-$it" }) { section ->
                    LayoutPreviewDraggable(
                        elementId = "section-$section",
                        onSelect = { onSelectElement(section) },
                        onMove = { direction -> onMovePlayerElement(section, direction) }
                    ) { dragModifier -> when (section) {
                    "STATUS" -> Row(
                        dragModifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "STATUS") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp))
                            .clickable { onSelectElement("STATUS") }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("● YOU · ONLINE · ACTIVE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("40  +", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    "MANA" -> Row(
                        dragModifier.fillMaxWidth().height(25.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "MANA") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("MANA") }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MANA POOL", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("3 TOTAL", color = SoftWhite, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    "COMMANDER" -> Row(
                        dragModifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                            .border(if (selectedElement == "COMMANDER") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp))
                            .clickable { onSelectElement("COMMANDER") }.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("COMMAND ZONE · COMMANDER", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                    "STEP" -> Row(dragModifier.fillMaxWidth().height(24.dp).clickable { onSelectElement("STEP") }, verticalAlignment = Alignment.CenterVertically) {
                        Text("NEXT STEP  ›", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 5.dp))
                    }
                    "ZONES" -> Row(dragModifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable { onSelectElement("ZONES") }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        quickActionOrder.forEach { id ->
                            Text("${layoutQuickActionTitle(id).uppercase()}${if (id in setOf("LIBRARY", "HAND", "GRAVEYARD", "EXILE", "REVEALED")) " 0" else ""}", color = SoftWhite, fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PanelLight).padding(horizontal = 5.dp, vertical = 4.dp))
                        }
                    }
                    "REVEALED" -> Text("PUBLIC REVEALS", color = CounterGold, fontSize = 7.sp, modifier = dragModifier.clickable { onSelectElement("REVEALED") }.padding(start = 4.dp))
                    "BATTLEFIELD" -> Column(
                        dragModifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "BATTLEFIELD") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp))
                            .clickable { onSelectElement("BATTLEFIELD") }.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("BATTLEFIELD · grouped by type", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tileWidth = (38.dp * cardSizeScale).coerceIn(31.dp, 54.dp)
                            val tileHeight = tileWidth / 0.72f
                            listOf("CREATURE", "ARTIFACT", "LAND").forEachIndexed { index, label ->
                                Column(Modifier.width(tileWidth).height(tileHeight).clip(RoundedCornerShape(5.dp)).background(listOf(Color(0xFF66513D), Color(0xFF364C52), Color(0xFF4A644C))[index]).padding(3.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = SoftWhite, fontSize = 5.sp, maxLines = 1)
                                    Box(Modifier.fillMaxWidth().height((tileHeight * 0.5f).coerceAtLeast(18.dp)).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.14f)))
                                    Text("CARD NAME", color = SoftWhite, fontSize = 5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                    }
                }
                item(key = "preview-footer") {
                    Row(
                Modifier.fillMaxWidth().height((34.dp * footerButtonScale).coerceIn(28.dp, 46.dp)).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                            .border(if (selectedElement == "FOOTER") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp)).clickable { onSelectElement("FOOTER") },
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("PASS TURN", "HAND", "BACK TO TABLE").forEach { label ->
                            Box(Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF293934)), contentAlignment = Alignment.Center) {
                                Text(label, color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            Row(Modifier.fillMaxWidth().height(30.dp).clickable { onSelectElement("HAND") }, verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR HAND · 5 CARDS", color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("×", color = SoftWhite, fontSize = 18.sp)
            }
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(7.dp)).background(PanelLight)
                    .border(if (selectedElement == "HAND") 2.dp else 0.dp, Mint, RoundedCornerShape(7.dp)).clickable { onSelectElement("HAND") }
                    ,
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                items(handActionOrder, key = { "hand-action-$it" }) { id ->
                    LayoutPreviewDraggable(
                        elementId = "hand-action-$id",
                        horizontal = true,
                        onSelect = { onSelectElement(id) },
                        onMove = { direction -> onMoveHandAction(id, direction) }
                    ) { dragModifier ->
                        Text(layoutHandActionTitle(id).uppercase(), color = Mint, fontSize = 6.sp, fontWeight = FontWeight.Bold,
                            modifier = dragModifier.clip(RoundedCornerShape(4.dp))
                                .border(if (selectedElement == id) 1.dp else 0.dp, if (selectedElement == id) Mint else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { onSelectElement(id) }.padding(horizontal = 5.dp), maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                    .border(if (selectedElement == "HAND") 2.dp else 0.dp, Mint, RoundedCornerShape(8.dp)).clickable { onSelectElement("HAND") }.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val cardWidth = (44.dp * cardSizeScale).coerceIn(34.dp, 62.dp)
                repeat(3) { index ->
                    Box(Modifier.width(cardWidth).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(listOf(Color(0xFF7A513F), Color(0xFF3D6674), Color(0xFF665A3D))[index]), contentAlignment = Alignment.BottomCenter) {
                        Text("SAMPLE CARD", color = SoftWhite, fontSize = 5.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LazyItemScope.LayoutPreviewDraggable(
    elementId: String,
    horizontal: Boolean = false,
    onSelect: () -> Unit = {},
    onMove: (Int) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val dragThreshold = with(density) { 30.dp.toPx() }
    val moveCurrent by rememberUpdatedState(onMove)
    val selectCurrent by rememberUpdatedState(onSelect)
    var dragging by remember(elementId) { mutableStateOf(false) }
    var dragOffset by remember(elementId) { mutableFloatStateOf(0f) }
    val dragModifier = Modifier
        .animateItem()
        .graphicsLayer {
            if (horizontal) translationX = dragOffset else translationY = dragOffset
            scaleX = if (dragging) 1.035f else 1f
            scaleY = if (dragging) 1.035f else 1f
            shadowElevation = if (dragging) 12.dp.toPx() else 0f
        }
        .zIndex(if (dragging) 1f else 0f)
        .pointerInput(elementId, horizontal, dragThreshold) {
            var accumulated = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { accumulated = 0f; dragOffset = 0f; dragging = true; selectCurrent() },
                onDragEnd = { dragOffset = 0f; dragging = false },
                onDragCancel = { dragOffset = 0f; dragging = false },
                onDrag = { change, amount ->
                    change.consume()
                    val delta = if (horizontal) amount.x else amount.y
                    accumulated += delta
                    dragOffset += delta
                    while (accumulated >= dragThreshold) {
                        moveCurrent(1)
                        accumulated -= dragThreshold
                        dragOffset -= dragThreshold
                    }
                    while (accumulated <= -dragThreshold) {
                        moveCurrent(-1)
                        accumulated += dragThreshold
                        dragOffset += dragThreshold
                    }
                }
            )
        }
    content(dragModifier)
}

@Composable
private fun LayoutTuningSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(valueLabel(value), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun SettingsToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RulesLookupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheFile = remember { java.io.File(context.filesDir, "magic-comprehensive-rules.txt") }
    var rulesText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (cacheFile.exists()) rulesText = withContext(Dispatchers.IO) { cacheFile.readText() }
    }
    val ruleLines = remember(rulesText, query) {
        val all = rulesText.lineSequence().toList()
        if (query.isBlank()) all else all.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MAGIC RULEBOOK", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(if (rulesText.isNotBlank()) rulesText.lineSequence().take(3).lastOrNull { it.contains("effective as of", true) } ?: "Official Wizards Comprehensive Rules" else "Official Wizards Comprehensive Rules · offline after download", color = Muted, fontSize = 9.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close rules", tint = Muted) }
                }
                Text("This reader uses Wizards’ official Comprehensive Rules text. Download it once to browse offline; check for updates when connected.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Search rule number or phrase") })
                    TextButton(onClick = {
                        loading = true
                        status = "Downloading official rules…"
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val connection = java.net.URL("https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt").openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 15_000
                                    connection.readTimeout = 25_000
                                    connection.setRequestProperty("User-Agent", "ArcanaTable/0.15.24")
                                    try {
                                        if (connection.responseCode !in 200..299) error("Wizards returned ${connection.responseCode}.")
                                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                                        if (!text.startsWith("Magic: The Gathering Comprehensive Rules")) error("The downloaded file was not recognized as the official rules text.")
                                        cacheFile.writeText(text)
                                        text
                                    } finally { connection.disconnect() }
                                }
                            }
                            result.onSuccess { rulesText = it; status = "Official rules downloaded for offline use." }
                                .onFailure { status = "Download failed. Check your connection and try again." }
                            loading = false
                        }
                    }, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (loading) "DOWNLOADING" else if (rulesText.isBlank()) "DOWNLOAD" else "UPDATE", color = Mint, fontSize = 8.sp) }
                }
                if (status.isNotBlank()) Text(status, color = if (rulesText.isNotBlank()) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                if (rulesText.isBlank()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Download the official Comprehensive Rules to start browsing and searching. The downloaded copy stays on this device for offline use.", color = Muted, fontSize = 12.sp)
                    }
                } else {
                    Text("${ruleLines.size} matching lines", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(ruleLines.size) { index ->
                            Text(ruleLines[index], color = if (ruleLines[index].matches(Regex("^\\d+(?:\\.[a-z]+)?\\..*"))) Mint else SoftWhite, fontSize = 9.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("Source: Wizards of the Coast · magic.wizards.com/en/rules", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun DeveloperOptionsPage(players: List<TablePlayer>, selectedPlayer: Int, onSelectPlayer: (Int) -> Unit, onLoadRandomDecks: () -> Unit, onRandomizeMidgame: () -> Unit, onOpenWalkthrough: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DEVELOPER OPTIONS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close developer options", tint = Muted) }
                }
                Text("Temporarily control a player's view to test how the table behaves.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                players.forEachIndexed { index, player ->
                    TextButton(onClick = { onSelectPlayer(index) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("${if (index == selectedPlayer) "●  " else "○  "}${player.name}  ·  Act as player", color = if (index == selectedPlayer) Mint else SoftWhite, modifier = Modifier.fillMaxWidth())
                    }
                }
                HorizontalDivider(color = PanelLight, modifier = Modifier.padding(vertical = 10.dp))
                Text("RANDOM TEST SETUPS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Replace current player zones with test data.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                TextButton(onClick = onLoadRandomDecks, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Load randomized decks for all players", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = onRandomizeMidgame, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Randomize a mid-game state", color = SoftWhite, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun GuidedTutorialOverlay(step: TutorialStop, index: Int, total: Int, targetBounds: Rect?, onBack: () -> Unit, onNext: () -> Unit, onExit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val accent = Color(0xFFFF3344)
        val viewSteps = tutorialStops.filter { it.view == step.view }
        val viewIndex = (viewSteps.indexOf(step) + 1).coerceAtLeast(1)
        val targetBelowCenter = with(density) { targetBounds?.center?.y?.let { it > maxHeight.toPx() / 2f } ?: true }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.76f))
            targetBounds?.let { bounds ->
                val margin = 6.dp.toPx()
                val left = (bounds.left - margin).coerceAtLeast(0f)
                val top = (bounds.top - margin).coerceAtLeast(0f)
                val right = (bounds.right + margin).coerceAtMost(size.width)
                val bottom = (bounds.bottom + margin).coerceAtMost(size.height)
                val origin = Offset(left, top)
                val targetSize = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
                val radius = CornerRadius(14.dp.toPx())
                drawRoundRect(Color.Transparent, topLeft = origin, size = targetSize, cornerRadius = radius, blendMode = BlendMode.Clear)
                drawRoundRect(accent, topLeft = origin, size = targetSize, cornerRadius = radius, style = Stroke(3.dp.toPx()))
                // A straight shaft and solid triangular head: a conventional arrow, not a sketch.
                val startY = if (targetBelowCenter) 184.dp.toPx() else size.height - 184.dp.toPx()
                val start = Offset(bounds.center.x.coerceIn(18.dp.toPx(), size.width - 18.dp.toPx()), startY.coerceIn(18.dp.toPx(), size.height - 18.dp.toPx()))
                val tip = if (targetBelowCenter) Offset(bounds.center.x, (bounds.top - 10.dp.toPx()).coerceAtLeast(0f))
                    else Offset(bounds.center.x, (bounds.bottom + 10.dp.toPx()).coerceAtMost(size.height))
                val dx = tip.x - start.x
                val dy = tip.y - start.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val ux = dx / length
                val uy = dy / length
                val headLength = 16.dp.toPx()
                val headHalfWidth = 7.dp.toPx()
                val base = Offset(tip.x - ux * headLength, tip.y - uy * headLength)
                val px = -uy * headHalfWidth
                val py = ux * headHalfWidth
                drawLine(accent, start, base, strokeWidth = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val arrowHead = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(base.x + px, base.y + py)
                    lineTo(base.x - px, base.y - py)
                    close()
                }
                drawPath(arrowHead, accent)
            }
        }
        Surface(
            Modifier.align(if (targetBelowCenter) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 20.dp).fillMaxWidth(0.96f).heightIn(min = 224.dp, max = 320.dp),
            shape = RoundedCornerShape(16.dp), color = Panel, shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${step.view} · $viewIndex/${viewSteps.size}", color = Mint, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Exit tutorial", tint = Muted) }
                }
                Text(step.title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(step.explanation, color = Muted, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 5.dp))
                if (targetBounds == null && step.target.isNotBlank()) Text("This control is hidden right now. Tap Next to keep going.", color = CounterGold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("BACK", color = if (index > 0) SoftWhite else Muted, fontSize = 13.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp)) { Text("NEXT ›", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
                if (targetBounds != null && step.target.isNotBlank()) Text("Tap the highlighted control or Next to continue.", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlayerOverview(player: TablePlayer, index: Int, modifier: Modifier = Modifier, compact: Boolean = false, isActivePlayer: Boolean = false, turnNumber: Int, phaseName: String, turnTime: Long?, showTurnTimer: Boolean, tutorialAnchor: (String) -> Modifier, onToggleTimer: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onOpen: () -> Unit, onZone: (String) -> Unit, onPreview: (GameCard) -> Unit, onPublicPreview: (GameCard) -> Unit) {
    var expanded by remember(player.id) { mutableStateOf(isActivePlayer) }
    LaunchedEffect(player.id, isActivePlayer) { expanded = isActivePlayer }
    val paymentCards = player.battlefield.filter { card ->
        if (card.isFaceDown) return@filter false
        val name = card.name.lowercase()
        val oracle = card.oracleText.orEmpty().lowercase()
        name == "rhystic study" || name == "smothering tithe" ||
            (("whenever an opponent" in oracle || "whenever a player" in oracle) && ("unless" in oracle && "pay" in oracle || "may pay" in oracle))
    }
    Column(modifier.animateContentSize().clip(RoundedCornerShape(14.dp)).background(Panel).border(if (isActivePlayer) 2.dp else 1.dp, if (isActivePlayer) Mint else Color(0xFF33423D), RoundedCornerShape(14.dp)).combinedClickable(onClick = onOpen).padding(if (compact) 6.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${if (player.isEliminated) "✕ " else if (player.isOnline) "● " else ""}${player.name} · P${index + 1}${if (player.isComputer) " · AI" else ""}${if (player.isEliminated) " · OUT" else if (isActivePlayer) " · TURN" else ""}", color = if (player.isEliminated) Muted else if (isActivePlayer || player.isOnline) Mint else SoftWhite, fontWeight = FontWeight.Bold, fontSize = if (compact) 9.sp else 12.sp, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text("${player.hand.size} in hand  ·  ${player.library.size} in library", color = Muted, fontSize = 10.sp)
            }
            if (compact) TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("VIEW", color = Mint, fontSize = 9.sp) }
            Text("${player.life}", color = Mint, fontSize = if (compact) 20.sp else 27.sp, fontWeight = FontWeight.Black)
        }
        if (player.commanderName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                player.commanderCard?.let { commander ->
                    val crop = commander.artCropUrl ?: commander.imageUrl
                    if (!crop.isNullOrBlank()) CardArt(
                        crop,
                        Modifier.size(width = if (compact) 60.dp else 92.dp, height = if (compact) 38.dp else 58.dp)
                            .clip(RoundedCornerShape(6.dp)).border(1.dp, CounterGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onPreview(commander) }, onLongClick = { onPreview(commander) }).then(tutorialAnchor("commander-art")),
                        contentScale = ContentScale.Crop
                    ) else Box(
                        Modifier.size(width = if (compact) 60.dp else 92.dp, height = if (compact) 38.dp else 58.dp)
                            .clip(RoundedCornerShape(6.dp)).background(PanelLight).border(1.dp, CounterGold, RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onPreview(commander) }, onLongClick = { onPreview(commander) }),
                        contentAlignment = Alignment.Center
                    ) { Text("COMMANDER", color = CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
                }
                Text("COMMANDER · ${player.commanderName}", color = CounterGold, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val commanderDamageTotal = player.commanderDamageReceived.values.sum()
        val visibleCounters = player.playerCounters.filterValues { it > 0 }
        if (commanderDamageTotal > 0 || visibleCounters.isNotEmpty() || player.isEliminated) {
            Text(buildList {
                if (commanderDamageTotal > 0) add("CMD DAMAGE $commanderDamageTotal")
                visibleCounters.forEach { (name, amount) -> add("$name $amount") }
                if (player.isEliminated) add(player.eliminationReason ?: "ELIMINATED")
            }.joinToString("  ·  "), color = if (player.isEliminated) Color(0xFFE9978B) else CounterGold, fontSize = if (compact) 7.sp else 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("TURN $turnNumber · $phaseName${if (showTurnTimer && turnTime != null) " · ${formatClock(turnTime)}" else ""}", color = if (isActivePlayer) Mint else Muted, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (turnTime != null) Modifier.combinedClickable(onClick = {}, onLongClick = onToggleTimer) else Modifier)
        if (paymentCards.isNotEmpty()) {
            Text("PAYMENT TRIGGER${if (paymentCards.size > 1) "S" else ""} · ${paymentCards.joinToString { it.name }}", color = CounterGold, fontSize = if (compact) 7.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
        val typeCounts = player.battlefield.groupingBy { battlefieldGroups(listOf(it)).firstOrNull()?.first ?: "Other" }.eachCount()
        val zoneSummary = listOfNotNull(
            player.graveyard.size.takeIf { it > 0 }?.let { "GY $it" },
            player.exile.size.takeIf { it > 0 }?.let { "EX $it" },
            player.revealed.size.takeIf { it > 0 }?.let { "PUBLIC $it" }
        ).joinToString("  ·  ")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BOARD · ${player.battlefield.size} permanents", color = Muted, fontSize = if (compact) 8.sp else 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
        if (typeCounts.isNotEmpty()) Text(typeCounts.entries.joinToString("  ·  ") { "${it.key} ${it.value}" }, color = Muted, fontSize = if (compact) 7.sp else 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (zoneSummary.isNotBlank()) Text(zoneSummary, color = Muted, fontSize = if (compact) 7.sp else 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (expanded) {
            Spacer(Modifier.height(5.dp))
            val cardGroups = listOf(
                "BATTLEFIELD" to player.battlefield,
                "GRAVEYARD" to player.graveyard,
                "EXILE" to player.exile,
                "PUBLICLY REVEALED" to player.revealed
            ).filter { it.second.isNotEmpty() }
            if (cardGroups.isEmpty()) Text("No public cards to show", color = Muted, fontSize = 10.sp)
            else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cardGroups.forEach { (title, cards) ->
                        Text(title, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val panelWidth = maxWidth
                            val cardGap = 7.dp
                            val minCardWidth = if (compact) 62.dp else 116.dp
                            val maxCardWidth = if (compact) 120.dp else 190.dp
                            val cardRows = buildList {
                                var row = mutableListOf<GameCard>()
                                var occupiedWidth = 0.dp
                                cards.forEach { card ->
                                    // A tapped card is rotated, so reserve its
                                    // landscape width when deciding how many
                                    // cards fit across the table.
                                    val slotWidth = minCardWidth * if (card.tapped) (1f / 0.72f) else 1f
                                    val nextWidth = occupiedWidth + (if (row.isEmpty()) 0.dp else cardGap) + slotWidth
                                    if (row.isNotEmpty() && nextWidth > panelWidth) {
                                        add(row.toList())
                                        row = mutableListOf()
                                        occupiedWidth = 0.dp
                                    }
                                    occupiedWidth += (if (row.isEmpty()) 0.dp else cardGap) + slotWidth
                                    row.add(card)
                                }
                                if (row.isNotEmpty()) add(row.toList())
                            }
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                cardRows.forEach { rowCards ->
                                    val widthFactors = rowCards.sumOf { if (it.tapped) (1.0 / 0.72) else 1.0 }
                                    val availableWidth = panelWidth - cardGap * (rowCards.size - 1)
                                    val cardWidth = (availableWidth / widthFactors.toFloat()).coerceAtMost(maxCardWidth)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cardGap), verticalAlignment = Alignment.CenterVertically) {
                                        rowCards.forEach { card ->
                                            OverviewCard(card, cardWidth, onClick = { if (title == "BATTLEFIELD") onOpen() else onPublicPreview(card) }, onLongClick = { onPublicPreview(card) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (player.handRevealedToAll && player.hand.isNotEmpty()) {
                        Text("HAND REVEALED TO EVERYONE", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            player.hand.forEach { card -> CardFace(card, Modifier.width(if (compact) 30.dp else 44.dp), onClick = { onPreview(card) }, onLongClick = { onPreview(card) }, showCounters = false) }
                        }
                    }
                }
            }
            if (!compact) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (player.graveyard.isNotEmpty()) ZoneChip("GRAVEYARD", player.graveyard.size) { onZone("Graveyard") }
                if (player.exile.isNotEmpty()) ZoneChip("EXILE", player.exile.size) { onZone("Exile") }
                if (player.revealed.isNotEmpty()) ZoneChip("REVEALED", player.revealed.size) { onZone("Revealed") }
            }
        }
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse player details" else "Expand player details",
            tint = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp).clip(CircleShape).combinedClickable(onClick = { expanded = !expanded }))
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("MOVE ↑", color = Mint, fontSize = 9.sp) }
                TextButton(onClick = onMoveDown, modifier = Modifier.heightIn(min = 48.dp)) { Text("MOVE ↓", color = Mint, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun ZoneChip(name: String, count: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Text("$name  $count", color = if (enabled) SoftWhite else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 34.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(6.dp)).background(if (enabled) PanelLight else Panel).combinedClickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun QuickActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = if (enabled) SoftWhite else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.heightIn(min = 34.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(6.dp))
            .background(if (enabled) PanelLight else Panel).combinedClickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun PlayerBoard(
    player: TablePlayer, playerIndex: Int, isLandscape: Boolean, cardSizeScale: Float = 1f, boardSpacingScale: Float = 1f, gameInProgress: Boolean, isActivePlayer: Boolean, canControl: Boolean, turnNumber: Int, phaseName: String, modifier: Modifier,
    playerSectionOrder: List<String>,
    quickActionOrder: List<String>,
    tutorialAnchor: (String) -> Modifier, stackCount: Int, commanderUnavailable: Boolean,
    onLife: (Int) -> Unit, onDraw: () -> Unit, onShowHand: () -> Unit, onShowHandFromZone: () -> Unit, onToken: () -> Unit, onManageMana: () -> Unit,
    onZone: (String) -> Unit, onPreview: (GameCard) -> Unit, onPublicPreview: (GameCard) -> Unit, onTapCard: (Int) -> Unit,
    onCardAction: (GameCard, String) -> Unit, onReveal: () -> Unit, onShuffle: () -> Unit,
    onNextStep: () -> Unit, onOpenLog: () -> Unit, onOpenChat: () -> Unit, onCombat: () -> Unit,
    onTrackers: () -> Unit, onStack: () -> Unit, onOpenMore: () -> Unit, onSearchLibrary: () -> Unit, onCastCommander: () -> Unit, onConcede: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val battlefieldListState = rememberLazyListState()
    LaunchedEffect(player.id) {
        // Each player board has its own battlefield. Do not carry a previous
        // player's scroll offset into this board, where it can leave card rows
        // clipped at both edges when the player view changes.
        battlefieldListState.scrollToItem(0)
    }
    val battlefieldCardWidth = when {
        screenWidthDp >= 1000 -> (142 * cardSizeScale).dp
        screenWidthDp >= 700 -> (128 * cardSizeScale).dp
        isLandscape -> (108 * cardSizeScale).dp
        else -> (126 * cardSizeScale).dp
    }
    var moreOpen by remember { mutableStateOf(false) }
    var shuffleConfirm by remember { mutableStateOf(false) }
    val sectionContent: Map<String, @Composable ColumnScope.() -> Unit> = mapOf(
            "STATUS" to {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(if (isActivePlayer) 2.dp else 0.dp, if (isActivePlayer) Mint else Panel, RoundedCornerShape(12.dp)).combinedClickable(onClick = onTrackers).padding(horizontal = 9.dp).then(tutorialAnchor("player-status")), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${if (player.isEliminated) "✕ " else if (player.isOnline) "● " else ""}${player.name}${if (player.isEliminated) " · ELIMINATED" else if (player.isOnline) " · ONLINE" else ""}${if (player.isComputer) " · AI" else ""}${if (isActivePlayer && !player.isEliminated) " · ACTIVE" else ""}", color = if (player.isEliminated) Muted else if (isActivePlayer || player.isOnline) Mint else SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("TURN $turnNumber · ${phaseName} · P${playerIndex + 1} · ${player.hand.size} IN HAND · ${player.library.size} IN LIBRARY", color = Muted, fontSize = 8.sp, letterSpacing = 0.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onLife(-1) }, enabled = canControl, modifier = Modifier.size(44.dp).then(tutorialAnchor("life-down"))) { Icon(Icons.Filled.Remove, "Decrease life", tint = if (canControl) SoftWhite else Muted) }
            Text("${player.life}", color = Mint, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 36.dp))
            IconButton(onClick = { onLife(1) }, enabled = canControl, modifier = Modifier.size(44.dp).then(tutorialAnchor("life-up"))) { Icon(Icons.Filled.Add, "Increase life", tint = if (canControl) Mint else Muted) }
        }

            },
            "MANA" to {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(9.dp))
                .background(if (canControl) PanelLight else Panel)
                .combinedClickable(enabled = canControl, onClick = onManageMana)
                .padding(horizontal = 10.dp, vertical = 5.dp).then(tutorialAnchor("mana-status")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANA POOL", color = if (canControl) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Spacer(Modifier.weight(1f))
            Text("${player.manaPool.values.sum()} TOTAL", color = if (canControl) SoftWhite else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

            },
            "COMMANDER" to {
        val commander = player.commanderCard
        if (commander != null || player.commanderDamageReceived.isNotEmpty() || player.playerCounters.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Panel).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                if (commander != null) {
                    CardFace(commander, Modifier.width(30.dp), onClick = { if (!commanderUnavailable && canControl) onCastCommander() }, onLongClick = { if (!commanderUnavailable && canControl) onCastCommander() }, showCounters = false)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text("${if (commanderUnavailable) "IN PLAY / STACK" else "COMMAND ZONE"} · ${commander.name}", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val trackerSummary = player.playerCounters.entries.joinToString(" · ") { "${it.key} ${it.value}" }
                        Text("Cast ${player.commanderCastCount} · tax +{${player.commanderCastCount * 2}}${if (trackerSummary.isNotBlank()) " · $trackerSummary" else ""}", color = Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onCastCommander, enabled = canControl && !commanderUnavailable && !player.isEliminated, modifier = Modifier.heightIn(min = 32.dp).then(tutorialAnchor("commander-cast")), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("CAST", color = if (canControl && !commanderUnavailable && !player.isEliminated) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                } else Text("PLAYER TRACKERS · ${player.playerCounters.entries.joinToString { "${it.key} ${it.value}" }.ifBlank { "none" }}", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                val incomingDamage = player.commanderDamageReceived.entries.sumOf { it.value }
                if (incomingDamage > 0) Text("CMD DMG $incomingDamage", color = if (player.commanderDamageReceived.values.any { it >= 21 }) Color(0xFFE9978B) else CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

            },
            "STEP" to {
        if (gameInProgress) {
            Row(Modifier.fillMaxWidth().then(tutorialAnchor("next-step")), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (player.isComputer || player.isEliminated) Text(if (player.isEliminated) "ELIMINATED · ${player.eliminationReason ?: "OUT"}" else if (player.deckLoaded) "AI TURN IN PROGRESS" else "IMPORT A DECK FOR THE AI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
                else {
                    TextButton(onClick = onNextStep, enabled = isActivePlayer && canControl, modifier = Modifier.heightIn(min = 38.dp).then(tutorialAnchor("next-step"))) {
                        Text(if (!canControl) "OTHER PLAYER · READ ONLY" else if (isActivePlayer) "NEXT STEP  ›" else "WAITING FOR TURN", color = if (isActivePlayer && canControl) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

            },
            "ZONES" to {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).then(tutorialAnchor("zones")), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quickActionOrder.distinct().forEach { action ->
                val zoneCount = when (action) {
                    "LIBRARY" -> player.library.size
                    "HAND" -> player.hand.size
                    "GRAVEYARD" -> player.graveyard.size
                    "EXILE" -> player.exile.size
                    "REVEALED" -> player.revealed.size
                    else -> null
                }
                val click: (() -> Unit)? = when (action) {
                    "LIBRARY" -> onSearchLibrary
                    "HAND" -> onShowHandFromZone
                    "GRAVEYARD" -> ({ onZone("Graveyard") })
                    "EXILE" -> ({ onZone("Exile") })
                    "REVEALED" -> ({ onZone("Revealed") })
                    "MANA" -> onManageMana
                    "DRAW" -> onDraw
                    "REVEAL" -> onReveal
                    "STACK" -> onStack
                    "TOKEN" -> onToken
                    "COMBAT" -> onCombat
                    "CAST_COMMANDER" -> onCastCommander
                    "NEXT_STEP" -> onNextStep
                    "SHUFFLE" -> ({ shuffleConfirm = true })
                    "LOG" -> onOpenLog
                    "CHAT" -> onOpenChat
                    else -> null
                }
                if (click != null) {
                    val label = if (zoneCount != null) action else layoutQuickActionTitle(action).uppercase()
                    if (zoneCount != null) {
                        Box(Modifier.then(tutorialAnchor("zone-${action.lowercase()}"))) {
                            ZoneChip(label, zoneCount, enabled = canControl, onClick = click)
                        }
                    } else QuickActionChip(label, enabled = canControl && (action != "NEXT_STEP" || isActivePlayer), onClick = click)
                }
            }
        }

            },
            "REVEALED" to {
        if (player.revealed.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PUBLIC REVEALS", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    player.revealed.take(10).forEach { card -> CardFace(card, Modifier.width(40.dp), onClick = {}, onLongClick = { onPublicPreview(card) }, showCounters = false) }
                }
            }
        }

            },
            "BATTLEFIELD" to {
        Text("BATTLEFIELD  ·  grouped by type  ·  tap to tap/untap  ·  long-press to inspect", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, modifier = Modifier.padding(start = 3.dp))
        if (player.battlefield.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                Text("Your battlefield is empty", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                state = battlefieldListState,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Panel).padding(5.dp).then(tutorialAnchor("battlefield")),
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(3.dp)
            ) {
                battlefieldGroups(player.battlefield).forEach { (type, cards) ->
                    item(key = "header-$type") { Text("$type  ·  ${cards.size}", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 3.dp, top = 2.dp)) }
                    item(key = "cards-$type") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cards.forEach { card ->
                                BattlefieldCard(
                                    card, battlefieldCardWidth,
                                    interactive = canControl,
                                    isCommander = card.id == player.commanderCard?.id,
                                    canSetCommander = player.commanderCard == null && player.commanderName.isNullOrBlank(),
                                    onTap = { onTapCard(card.id) }, onPreview = { onPreview(card) },
                                    onAction = { action -> onCardAction(card, action) },
                                    tutorialAnchor = if (card.id == player.battlefield.firstOrNull()?.id) tutorialAnchor("card-menu") else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
            }
    )
    val defaultSectionOrder = listOf("STATUS", "MANA", "COMMANDER", "STEP", "ZONES", "REVEALED", "BATTLEFIELD")
    val normalizedSectionOrder = playerSectionOrder.filter { it in defaultSectionOrder }.distinct()
    // The board sits in the space between the top player tabs and the fixed
    // hand tray. Fill that measured height so the weighted battlefield list
    // can use the available space instead of wrapping its card contents.
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp * boardSpacingScale)) {
        normalizedSectionOrder.forEach { section -> sectionContent[section]?.invoke(this) }
    }
}

private fun battlefieldGroups(cards: List<GameCard>): List<Pair<String, List<GameCard>>> {
    val order = listOf("Creatures", "Planeswalkers", "Battles", "Artifacts", "Enchantments", "Lands", "Instants", "Sorceries", "Other", "Face-down")
    fun category(card: GameCard): String {
        if (card.isFaceDown) return "Face-down"
        val type = card.type.lowercase()
        return when {
            "creature" in type -> "Creatures"
            "planeswalker" in type -> "Planeswalkers"
            "battle" in type -> "Battles"
            "artifact" in type -> "Artifacts"
            "enchantment" in type -> "Enchantments"
            "land" in type -> "Lands"
            "instant" in type -> "Instants"
            "sorcery" in type -> "Sorceries"
            else -> "Other"
        }
    }
    val grouped = cards.groupBy(::category)
    return order.mapNotNull { type -> grouped[type]?.let { type to it } }
}

@Composable
private fun BattlefieldCard(card: GameCard, tileWidth: androidx.compose.ui.unit.Dp, interactive: Boolean = true, isCommander: Boolean = false, canSetCommander: Boolean = false, onTap: () -> Unit, onPreview: () -> Unit, onAction: (String) -> Unit, tutorialAnchor: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val activatedAbilities = remember(card.id, card.oracleText) { cardActivatedAbilityLines(card) }
    val cardHeight = tileWidth / 0.72f
    Box(Modifier.width(if (card.tapped) cardHeight else tileWidth).height(cardHeight), contentAlignment = Alignment.Center) {
        CardFace(card, Modifier.width(tileWidth), onClick = onTap, onLongClick = onPreview, animateTapped = true)
        card.chosenCreatureType?.takeIf { it.isNotBlank() }?.let { chosenType ->
            Text(
                "TYPE · $chosenType",
                color = SoftWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color(0xDD101615)).padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        if (interactive) Box(Modifier.align(Alignment.TopEnd).padding(1.dp).then(tutorialAnchor), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xCC101615)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, "${card.name} actions", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(if (card.tapped) "Untap" else "Tap") }, onClick = { menuOpen = false; onAction("TAP") })
                DropdownMenuItem(text = { Text("Add counter") }, onClick = { menuOpen = false; onAction("COUNTER") })
                DropdownMenuItem(text = { Text("Create token") }, onClick = { menuOpen = false; onAction("TOKEN") })
                DropdownMenuItem(text = { Text("Move to graveyard") }, onClick = { menuOpen = false; onAction("GRAVEYARD") })
                DropdownMenuItem(text = { Text("Exile") }, onClick = { menuOpen = false; onAction("EXILE") })
                if (!card.isFaceDown && activatedAbilities.isNotEmpty()) {
                    HorizontalDivider(color = Muted.copy(alpha = 0.3f))
                    Text(if (card.type.contains("planeswalker", ignoreCase = true)) "LOYALTY ABILITIES" else "ACTIVATED ABILITIES", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    activatedAbilities.forEach { ability ->
                        DropdownMenuItem(
                            text = { Text(ability, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
                            onClick = { menuOpen = false; onAction("ABILITY:$ability") }
                        )
                    }
                }
                if (canSetCommander && !card.isFaceDown) DropdownMenuItem(text = { Text("Set as commander") }, onClick = { menuOpen = false; onAction("SET COMMANDER") })
                if (isCommander) DropdownMenuItem(text = { Text("Move commander to command zone") }, onClick = { menuOpen = false; onAction("COMMAND ZONE") })
                if (card.isFaceDown) DropdownMenuItem(text = { Text("Turn face up") }, onClick = { menuOpen = false; onAction("FACE UP") })
                DropdownMenuItem(text = { Text("Inspect card") }, onClick = { menuOpen = false; onAction("INSPECT") })
            }
        }
    }
}

private fun cardActivatedAbilityLines(card: GameCard): List<String> {
    return card.oracleText.orEmpty().lineSequence()
        .map { it.trim() }
        // Avoid java.util.regex here: Android's ICU regex engine rejects some
        // escaped characters in character classes (notably `\\-`), which made
        // merely rendering a battlefield card crash on affected devices.
        .filter { line ->
            if (line.isBlank()) return@filter false
            val colon = line.indexOf(':')
            if (colon !in 1..40 || colon == line.lastIndex) return@filter false
            val cost = line.substring(0, colon).trim()
            val effect = line.substring(colon + 1).trim()
            cost.isNotEmpty() && effect.isNotEmpty() && !cost.contains('.')
        }
        .distinct()
        .take(8)
        .toList()
}

@Composable
private fun OverviewCard(card: GameCard, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val height = width / 0.72f
    Box(Modifier.width(if (card.tapped) height else width).height(height), contentAlignment = Alignment.Center) {
        CardFace(card, Modifier.width(width), onClick = onClick, onLongClick = onLongClick, animateTapped = true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardFace(card: GameCard, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit, showCounters: Boolean = true, animateTapped: Boolean = false) {
    val rotation by animateFloatAsState(targetValue = if (animateTapped && card.tapped) 90f else 0f, animationSpec = tween(260), label = "card-tap")
    Box(
        modifier.aspectRatio(0.72f).graphicsLayer(rotationZ = rotation).clip(RoundedCornerShape(8.dp)).background(card.color)
            .border(if (card.tapped) 2.dp else 1.dp, if (card.tapped) Mint else Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (card.isFaceDown) {
            Box(Modifier.fillMaxSize().background(Color(0xFF12221E)), contentAlignment = Alignment.Center) {
                Text("FACE\nDOWN", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        } else if (!card.imageUrl.isNullOrBlank()) CardArt(card.imageUrl, Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize().background(card.color).padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(card.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(card.type, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (card.isToken && !card.isFaceDown) Text("TOKEN", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xDD15221D)).padding(horizontal = 5.dp, vertical = 3.dp))
        val visibleCounters = card.counters.entries.filter { it.value > 0 }
        val counterText = visibleCounters.take(2).joinToString("  ") { "${it.key} ${it.value}" } + if (visibleCounters.size > 2) "  +${visibleCounters.size - 2}" else ""
        if (showCounters && counterText.isNotBlank()) Text(counterText, color = Ink, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CounterGold).padding(horizontal = 4.dp, vertical = 4.dp))
    }
}

@Composable
private fun CardArt(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(CardBitmapCache.get(url)) }
    LaunchedEffect(url) { if (bitmap == null) bitmap = try { downloadCardBitmap(url) } catch (_: Exception) { null } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Magic card art", modifier = modifier, contentScale = contentScale)
    else Box(modifier.background(Color(0xFF263B35)), contentAlignment = Alignment.Center) { Text("✦", color = Mint, fontSize = 16.sp) }
}

@Composable
private fun HandSheet(cards: List<GameCard>, isLandscape: Boolean, availableHeight: androidx.compose.ui.unit.Dp, highlightedCardId: Int?, handRevealed: Boolean, canAdvance: Boolean, canControl: Boolean, commanderCanCast: Boolean, actionOrder: List<String>, stackCount: Int, tutorialAnchor: (String) -> Modifier, onToggleReveal: (Boolean) -> Unit, onClose: () -> Unit, onDraw: () -> Unit, onCombat: () -> Unit, onNextStep: () -> Unit, onPassTurn: () -> Unit, onStack: () -> Unit, onCastCommander: () -> Unit, onMoreAction: (String) -> Unit, onSelect: (GameCard) -> Unit, onDragDrop: (GameCard, Offset) -> Unit) {
    val handScrollState = rememberScrollState()
    var moreOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val cardHeight = (availableHeight - 190.dp).coerceIn(if (isLandscape) 180.dp else 220.dp, if (isLandscape) 300.dp else 410.dp)
    val cardWidth = cardHeight * 0.72f
    val cardStepPx = with(density) { cardWidth.plus(8.dp).roundToPx() }
    LaunchedEffect(highlightedCardId, cards) {
        val drawnIndex = cards.indexOfFirst { it.id == highlightedCardId }
        if (drawnIndex >= 0) handScrollState.animateScrollTo((drawnIndex * cardStepPx).coerceAtMost(handScrollState.maxValue))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR HAND  ·  ${cards.size} CARDS", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close hand", tint = SoftWhite) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            actionOrder.forEach { action ->
                when (action) {
                    "DRAW" -> if (canControl) HeaderButton("DRAW", onDraw)
                    "REVEAL" -> HeaderButton(if (handRevealed) "HIDE HAND" else "REVEAL HAND", { onToggleReveal(!handRevealed) })
                    "STACK" -> HeaderButton("STACK · $stackCount", onStack, modifier = tutorialAnchor("stack-hand-button"))
                    "CAST_COMMANDER" -> if (commanderCanCast) HeaderButton("CAST COMMANDER", onCastCommander, modifier = tutorialAnchor("commander-cast"))
                    "COMBAT" -> if (canAdvance) HeaderButton("COMBAT", onCombat, modifier = tutorialAnchor("combat-action"))
                    "NEXT_STEP" -> if (canAdvance) HeaderButton("NEXT STEP", onNextStep, modifier = tutorialAnchor("next-step"))
                    "PASS_TURN" -> if (canAdvance) HeaderButton("PASS TURN", onPassTurn, modifier = tutorialAnchor("pass-turn"))
                    "MORE" -> HeaderButton("MORE  ⋮", { moreOpen = true }, modifier = tutorialAnchor("more-actions"))
                }
            }
            if (!canControl && actionOrder.none { it == "REVEAL" || it == "STACK" || it == "MORE" }) Text("WAITING", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                if (canControl) {
                    DropdownMenuItem(text = { Text("Add token") }, modifier = tutorialAnchor("token-action"), onClick = { moreOpen = false; onMoreAction("TOKEN") })
                    DropdownMenuItem(text = { Text("Reveal top card") }, onClick = { moreOpen = false; onMoreAction("REVEAL") })
                    DropdownMenuItem(text = { Text("Shuffle library…") }, onClick = { moreOpen = false; onMoreAction("SHUFFLE") })
                    DropdownMenuItem(text = { Text("Player trackers and commander damage") }, onClick = { moreOpen = false; onMoreAction("TRACKERS") })
                    DropdownMenuItem(text = { Text("Search / browse library") }, onClick = { moreOpen = false; onMoreAction("SEARCH") })
                    DropdownMenuItem(text = { Text("Concede game…", color = Color(0xFFE9978B)) }, onClick = { moreOpen = false; onMoreAction("CONCEDE") })
                    DropdownMenuItem(text = { Text("View graveyard") }, onClick = { moreOpen = false; onMoreAction("GRAVEYARD") })
                    DropdownMenuItem(text = { Text("View exile") }, onClick = { moreOpen = false; onMoreAction("EXILE") })
                    DropdownMenuItem(text = { Text("View revealed zone") }, onClick = { moreOpen = false; onMoreAction("REVEALED") })
                    HorizontalDivider(color = Muted.copy(alpha = 0.3f))
                }
                DropdownMenuItem(text = { Text("Open game log") }, onClick = { moreOpen = false; onMoreAction("LOG") })
                DropdownMenuItem(text = { Text("Open text chat") }, onClick = { moreOpen = false; onMoreAction("CHAT") })
            }
        }
        if (cards.isEmpty()) Text("Your hand is empty", color = Muted, modifier = Modifier.padding(12.dp))
        else Row(Modifier.fillMaxWidth().height(cardHeight + 6.dp).horizontalScroll(handScrollState), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { card ->
                var coordinates by remember(card.id) { mutableStateOf<LayoutCoordinates?>(null) }
                CardFace(
                    card,
                    Modifier.width(cardWidth).height(cardHeight)
                        .onGloballyPositioned { coordinates = it }
                        .pointerInput(card.id) {
                            var dragStart = Offset.Zero
                            var dragDelta = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragStart = it; dragDelta = Offset.Zero },
                                onDrag = { change, amount -> change.consume(); dragDelta += amount },
                                onDragEnd = {
                                    val coords = coordinates
                                    if (coords != null) onDragDrop(card, coords.localToRoot(dragStart + dragDelta))
                                }
                            )
                        }
                        .then(if (cards.firstOrNull()?.id == card.id) tutorialAnchor("hand-card") else Modifier)
                        .then(if (card.id == highlightedCardId) Modifier.border(3.dp, Mint, RoundedCornerShape(8.dp)) else Modifier),
                    onClick = { onSelect(card) }, onLongClick = { onSelect(card) }, showCounters = false
                )
            }
        }
    }
}

@Composable
private fun HandCardActionsDialog(card: GameCard, hasCommander: Boolean, isCommanderCard: Boolean, tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier, onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth()) {
            // Let the dialog wrap its title and actions. fillMaxSize() here made
            // a tiny card menu claim the Dialog's full available height.
            Box {
            Column(Modifier.padding(10.dp).then(tutorialAnchor("hand-actions"))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Choose an action", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(42.dp)) { Icon(Icons.Filled.Close, "Close actions", tint = Muted) }
                }
                val actions = listOf("PLAY", "PLAY FACE DOWN", "REVEAL", "EXILE", "DISCARD", "INSPECT") + if (isCommanderCard) listOf("COMMAND ZONE") else if (hasCommander) emptyList() else listOf("SET COMMANDER")
                actions.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { action ->
                            TextButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                                Text(action, color = if (action == "DISCARD" || action == "EXILE") Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
            }
        }
    }
}

@Composable
private fun DeckEditorDialog(
    player: TablePlayer,
    highlightedCardIds: Set<Int>,
    onRename: (GameCard, String) -> Unit,
    onRemove: (GameCard) -> Unit,
    onAdd: (ScryfallCard) -> Unit,
    onReplace: (GameCard, ScryfallCard) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var menuCard by remember { mutableStateOf<GameCard?>(null) }
    var renameCard by remember { mutableStateOf<GameCard?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var replacementTarget by remember { mutableStateOf<GameCard?>(null) }
    var autoFixingCardId by remember { mutableStateOf<Int?>(null) }
    var autoFixStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun autoFix(target: GameCard, copiesToFix: List<GameCard>) {
        autoFixingCardId = target.id
        autoFixStatus = "Looking up ${target.name}…"
        scope.launch {
            val search = runCatching { Scryfall.searchCards(target.name) }
            val exact = search.getOrNull()?.firstOrNull {
                it.name.equals(target.name, ignoreCase = true) && !it.imageUrl.isNullOrBlank()
            }
            val fuzzy = if (exact == null) runCatching { Scryfall.fuzzyNamedCard(target.name) }.getOrNull() else null
            val candidate = exact ?: fuzzy?.takeIf {
                it.imageUrl?.isNotBlank() == true && cardNameSimilarity(target.name, it.name) >= 0.78
            }
            if (candidate != null) {
                val flaggedCopies = copiesToFix.filter { it.importIssue != null || it.id in highlightedCardIds }
                flaggedCopies.forEach { onReplace(it, candidate) }
                autoFixStatus = if (candidate.name.equals(target.name, ignoreCase = true)) {
                    "Fixed ${flaggedCopies.size} ${target.name} ${if (flaggedCopies.size == 1) "card" else "cards"}."
                } else {
                    "Fixed ${flaggedCopies.size} flagged card(s) as ${candidate.name} (closest clear match)."
                }
            } else {
                replacementTarget = target
                query = target.name
                results = search.getOrNull().orEmpty()
                autoFixStatus = if (search.isFailure) "Card search failed. Check your connection and try again." else "No safe automatic match. Choose the right card from the search results."
            }
            autoFixingCardId = null
        }
    }
    val trackedCards = player.library + player.hand + player.battlefield + player.graveyard + player.exile + player.revealed
    val cards = trackedCards.filter { it.name.contains(query, ignoreCase = true) }
        .groupBy { it.name }.entries.sortedBy { it.key.lowercase() }
    val commander = player.commanderCard?.takeIf { it.name.contains(query, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EDIT ${player.name}'S DECK", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close deck editor", tint = Muted) }
                }
                Text("Tracked cards: ${trackedCards.size}${if (player.commanderCard != null) " + commander" else ""}  ·  ${cards.size + if (commander != null) 1 else 0} names", color = Muted, fontSize = 10.sp)
                OutlinedTextField(query, { query = it; results = emptyList() }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text("Find any Magic card") }, trailingIcon = {
                    TextButton(enabled = query.trim().length >= 2 && !searching, onClick = {
                        searching = true
                        scope.launch { results = Scryfall.searchCards(query.trim()); searching = false }
                    }) { Text(if (searching) "SEARCHING" else "SEARCH ALL", color = Mint, fontSize = 9.sp) }
                })
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (results.isNotEmpty()) {
                        item { Text(if (replacementTarget != null) "CHOOSE A MATCH · REPLACE FLAGGED CARD" else "SEARCH RESULTS · tap + to add to library", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        items(results.size) { index ->
                            val result = results[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(result.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${result.setCode?.uppercase().orEmpty()} ${result.collectorNumber.orEmpty()}  ·  ${result.type}", color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = {
                                    val target = replacementTarget
                                    if (target == null) onAdd(result) else onReplace(target, result)
                                    replacementTarget = null
                                    results = emptyList()
                                    query = ""
                                }, modifier = Modifier.heightIn(min = 40.dp)) { Text(if (replacementTarget == null) "+ ADD" else "REPLACE", color = Mint, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    commander?.let { card ->
                        item(key = "commander-${card.id}") {
                            Text("COMMANDER", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (card.importIssue != null || card.id in highlightedCardIds) Color(0xFF392B21) else PanelLight)
                                    .border(if (card.importIssue != null || card.id in highlightedCardIds) 2.dp else 0.dp, if (card.importIssue != null || card.id in highlightedCardIds) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(card.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    card.importIssue?.let { Text("NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                                if (card.importIssue != null) {
                                    TextButton(onClick = { autoFix(card, listOf(card)) }, enabled = autoFixingCardId == null) {
                                        Text(if (autoFixingCardId == card.id) "…" else "AUTO-FIX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { replacementTarget = card; query = card.name; results = emptyList() }) {
                                        Text("FIND", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                TextButton(onClick = { onRemove(card) }) { Text("REMOVE", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    item { Text("ALL ZONES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
                    if (cards.isEmpty()) item { Text("No matching cards in this deck", color = Muted, fontSize = 10.sp) }
                    items(cards.size, key = { cards[it].key }) { index ->
                        val (name, copies) = cards[index]
                        // Prefer a flagged copy so grouping identical cards never hides an import error.
                        val sample = copies.firstOrNull { it.importIssue != null || it.id in highlightedCardIds } ?: copies.first()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (sample.importIssue != null || sample.id in highlightedCardIds) Color(0xFF392B21) else PanelLight)
                                .border(if (sample.importIssue != null || sample.id in highlightedCardIds) 2.dp else 0.dp, if (sample.importIssue != null || sample.id in highlightedCardIds) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${copies.size}×", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(buildList {
                                    val zones = listOf("hand" to player.hand, "library" to player.library, "battlefield" to player.battlefield, "graveyard" to player.graveyard, "exile" to player.exile, "revealed" to player.revealed)
                                    zones.forEach { (zone, zoneCards) -> copies.count { copy -> zoneCards.any { it.id == copy.id } }.takeIf { it > 0 }?.let { add("$it $zone") } }
                                }.joinToString(" · "), color = Muted, fontSize = 8.sp)
                                sample.importIssue?.let {
                                    Text("NEEDS REVIEW · $it", color = CounterGold, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(
                                        onClick = { autoFix(sample, copies) },
                                        enabled = autoFixingCardId == null,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) { Text(if (autoFixingCardId == sample.id) "TRYING…" else "AUTO-FIX", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Box {
                                TextButton(onClick = { menuCard = sample }, modifier = Modifier.heightIn(min = 40.dp)) { Text("⋮", color = Mint, fontSize = 18.sp) }
                                DropdownMenu(expanded = menuCard?.id == sample.id, onDismissRequest = { menuCard = null }) {
                                    if (sample.importIssue != null) DropdownMenuItem(text = { Text("Search for correct card") }, onClick = { replacementTarget = sample; query = sample.name; results = emptyList(); menuCard = null })
                                    DropdownMenuItem(text = { Text("Remove one copy") }, onClick = { onRemove(sample); menuCard = null })
                                    DropdownMenuItem(text = { Text("Rename card") }, onClick = { renameCard = sample; renameValue = sample.name; menuCard = null })
                                }
                            }
                        }
                    }
                }
                if (autoFixStatus.isNotBlank()) Text(autoFixStatus, color = if (autoFixStatus.startsWith("Fixed")) Mint else CounterGold, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    renameCard?.let { card -> AlertDialog(onDismissRequest = { renameCard = null }, title = { Text("Rename card") }, text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text("Card name") }) }, confirmButton = { TextButton(onClick = { if (renameValue.isNotBlank()) onRename(card, renameValue.trim()); renameCard = null }) { Text("SAVE") } }, dismissButton = { TextButton(onClick = { renameCard = null }) { Text("CANCEL") } }) }
}

private fun cardNameSimilarity(first: String, second: String): Double {
    fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    val a = normalize(first)
    val b = normalize(second)
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        var diagonal = previous[0]
        previous[0] = i + 1
        for (j in b.indices) {
            val above = previous[j + 1]
            previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (a[i] == b[j]) 0 else 1)
            diagonal = above
        }
    }
    return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
}

@Composable
private fun PlayerTrackerDialog(player: TablePlayer, players: List<TablePlayer>, playerIndex: Int, onUpdate: (TablePlayer) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val common = listOf("Poison", "Energy", "Experience", "Rad", "Lore", "Ticket", "Shield", "Stun", "Finality", "The Ring tempts you", "Monarch", "Initiative", "City's Blessing", "Day/Night")
    val matches = common.filter { it.contains(search, true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${player.name} · TRACKERS", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close player trackers", tint = Muted) }
                }
                Text("Commander damage is tracked per opposing commander; 21 from one commander is lethal.", color = Muted, fontSize = 10.sp)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text("COMMANDER CASTS · ${player.commanderCastCount} · TAX +{${player.commanderCastCount * 2}}", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ActionPill("− CAST") { onUpdate(player.copy(commanderCastCount = (player.commanderCastCount - 1).coerceAtLeast(0))) }
                            ActionPill("+ CAST") { onUpdate(player.copy(commanderCastCount = player.commanderCastCount + 1)) }
                        }
                    }
                    item { Text("COMMANDER DAMAGE RECEIVED", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    items(players.indices.filter { it != playerIndex }.size) { row ->
                        val source = players[players.indices.filter { it != playerIndex }[row]]
                        val amount = player.commanderDamageReceived[source.id] ?: 0
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${source.name}${source.commanderName?.let { " · $it" }.orEmpty()}", color = SoftWhite, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived.toMutableMap().apply { if (amount <= 1) remove(source.id) else put(source.id, amount - 1) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text("−", color = SoftWhite) }
                            Text("$amount", color = if (amount >= 21) Color(0xFFE9978B) else Mint, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onUpdate(player.copy(commanderDamageReceived = player.commanderDamageReceived + (source.id to amount + 1))) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text("+", color = Mint) }
                        }
                    }
                    item { Text("COUNTERS AND STATUS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search common trackers") }, singleLine = true) }
                    items(matches.size) { row ->
                        val name = matches[row]
                        val old = player.playerCounters[name] ?: 0
                        val toggle = name in listOf("Monarch", "Initiative", "City's Blessing", "Day/Night", "The Ring tempts you")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = SoftWhite, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { val next = if (toggle) 0 else (old - 1).coerceAtLeast(0); onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text(if (toggle) "OFF" else "−", color = Muted) }
                            Text(if (toggle) if (old > 0) "ON" else "OFF" else "$old", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { val next = if (toggle) if (old > 0) 0 else 1 else old + 1; onUpdate(player.copy(playerCounters = player.playerCounters.toMutableMap().apply { if (next == 0) remove(name) else put(name, next) })) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 42.dp)) { Text(if (toggle) "SET" else "+", color = Mint) }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = custom, onValueChange = { custom = it }, modifier = Modifier.weight(1f), label = { Text("Add custom tracker") }, singleLine = true)
                            TextButton(onClick = { val key = custom.trim(); if (key.isNotEmpty()) { onUpdate(player.copy(playerCounters = player.playerCounters + (key to ((player.playerCounters[key] ?: 0) + 1)))); custom = "" } }, enabled = custom.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("ADD", color = Mint) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSelectionDialog(stackItem: StackAction, candidates: List<StackTarget>, onConfirm: (List<StackTarget>) -> Unit) {
    val range = stackItem.targetCount ?: TargetCountRange(1, 1)
    var selectedTargets by remember(stackItem.id, stackItem.targets) { mutableStateOf(stackItem.targets) }
    var customTargetText by remember(stackItem.id) { mutableStateOf("") }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    val allowed = selectedTargets.size in range.minimum..range.maximum
    val targetLimit = if (range.maximum >= 99) Int.MAX_VALUE else range.maximum
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight), shape = RoundedCornerShape(18.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CHOOSE TARGETS", color = Mint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(stackItem.title, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        range.minimum == range.maximum -> "Select ${range.minimum} target${if (range.minimum == 1) "" else "s"}."
                        range.maximum >= 99 -> "Select any number of targets (${range.minimum} or more)."
                        else -> "Select ${range.minimum} to ${range.maximum} targets."
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
                Text("Check the card text and select only legal targets. The app does not fully parse card-specific restrictions.", color = CounterGold, fontSize = 10.sp, lineHeight = 14.sp)
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.43f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (candidates.isEmpty()) item(key = "no-candidates") {
                        Text("No visible game objects are available. Add a target by name below if the card text allows it.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    }
                    items(candidates, key = { it.key }) { candidate ->
                        val isSelected = selectedTargets.any { it.key == candidate.key }
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedTargets = if (isSelected) selectedTargets.filterNot { it.key == candidate.key }
                                else if (selectedTargets.size < targetLimit) selectedTargets + candidate else selectedTargets
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Mint.copy(alpha = 0.15f) else PanelLight
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    selectedTargets = if (checked && selectedTargets.size < targetLimit) selectedTargets + candidate else selectedTargets.filterNot { it.key == candidate.key }
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.label, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.category, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = customTargetText,
                    onValueChange = { customTargetText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Other legal target") },
                    placeholder = { Text("For example: a counter on a permanent") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val label = customTargetText.trim()
                            if (label.isNotEmpty() && selectedTargets.size < targetLimit) {
                                selectedTargets = selectedTargets + StackTarget("manual:${java.util.UUID.randomUUID()}", label, "MANUAL TARGET")
                                customTargetText = ""
                            }
                        },
                        enabled = customTargetText.isNotBlank() && selectedTargets.size < targetLimit
                    ) { Text("ADD TARGET", color = Mint, fontSize = 10.sp) }
                    Text("${selectedTargets.size} selected", color = Muted, fontSize = 10.sp)
                }
                Button(
                    onClick = { onConfirm(selectedTargets) },
                    enabled = allowed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { Text("CONFIRM TARGETS", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun StackPriorityDialog(players: List<TablePlayer>, stack: List<StackAction>, priorityPlayer: Int, passedCount: Int, eligibleCount: Int, canResolve: Boolean, canAct: Boolean, tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier, onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit, onPreviewCard: (StackAction, GameCard) -> Unit, onStackItemTap: () -> Unit, onCastFromHand: () -> Unit, onAddTrigger: (String) -> Unit, onPassPriority: () -> Unit, onResolve: () -> Unit, onDismiss: () -> Unit) {
    var triggerName by remember { mutableStateOf("") }
    var manualAbilityOpen by remember { mutableStateOf(false) }
    var expandedActionId by remember { mutableStateOf<String?>(null) }
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    val maxStackListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.34f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxDialogHeight), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxWidth().wrapContentHeight()) {
            Column(Modifier.fillMaxWidth().wrapContentHeight().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STACK · PRIORITY", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("stack-close"))) { Icon(Icons.Filled.Close, "Close stack", tint = Muted) }
                }
                Text("Priority: ${players.getOrNull(priorityPlayer)?.name ?: "—"} · passed $passedCount/$eligibleCount", color = Mint, fontSize = 10.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxStackListHeight).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stack.isEmpty()) item {
                        Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = PanelLight, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("NOTHING ON THE STACK", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).then(tutorialAnchor("stack-item")))
                                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("CLOSE STACK", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                            }
                        }
                    }
                    items(stack.indices.reversed().toList()) { index ->
                        val item = stack[index]
                        val owner = players.firstOrNull { it.id == item.controllerId }?.name ?: "Unknown player"
                        val card = item.card
                        val cardIsFaceDown = card?.isFaceDown == true
                        val expanded = expandedActionId == item.id
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight)) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp).then(if (index == stack.lastIndex) tutorialAnchor("stack-item") else Modifier),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (card != null) {
                                    CardFace(card, Modifier.width(48.dp), onClick = { if (!cardIsFaceDown) onPreviewCard(item, card); onStackItemTap() }, onLongClick = { if (!cardIsFaceDown) onPreviewCard(item, card); onStackItemTap() }, showCounters = false)
                                }
                                Column(
                                    Modifier.weight(1f).clickable(enabled = !cardIsFaceDown) { expandedActionId = if (expanded) null else item.id; onStackItemTap() },
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val visibleTitle = if (cardIsFaceDown) "Face-down spell" else item.title
                                    Text("${if (index == stack.lastIndex) "TOP · " else ""}$visibleTitle", color = if (index == stack.lastIndex) Mint else SoftWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("$owner · ${item.kind}", color = Muted, fontSize = 9.sp)
                                    if (item.targets.isNotEmpty()) Text("TARGETS · ${item.targets.joinToString { it.label }}", color = Mint, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    else if (item.targetCount != null && !item.targetsConfirmed) Text("WAITING FOR TARGETS", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    if (card != null && !cardIsFaceDown) {
                                        val facts = listOfNotNull(card.manaCost?.takeIf { it.isNotBlank() }, card.type.takeIf { it.isNotBlank() })
                                        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = CounterGold, fontSize = 9.sp, maxLines = 2)
                                    }
                                    Text(if (cardIsFaceDown) "FACE-DOWN CARD · DETAILS HIDDEN" else if (card != null) "CARD IMAGE: PREVIEW · TEXT: DETAILS" else if (expanded) "TAP TO COLLAPSE" else "TAP FOR DETAILS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (expanded && !cardIsFaceDown) {
                                val oracle = card?.oracleText?.takeIf { it.isNotBlank() }
                                if (oracle != null) {
                                    HorizontalDivider(color = Muted.copy(alpha = 0.25f))
                                    Text(oracle, color = SoftWhite, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                } else if (card == null) {
                                    Text("${item.kind} added by $owner", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                } else {
                                    Text("No rules text is available for this card.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onCastFromHand,
                    enabled = canAct || tutorialStep?.target == "stack-cast",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).then(tutorialAnchor("stack-cast")),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) {
                    Text("CAST FROM HAND", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                TextButton(
                    onClick = { manualAbilityOpen = !manualAbilityOpen },
                    enabled = canAct || tutorialStep?.target == "stack-add",
                    modifier = Modifier.align(Alignment.End).heightIn(min = 34.dp).then(tutorialAnchor("stack-add"))
                ) {
                    Text(if (manualAbilityOpen) "HIDE MANUAL ENTRY" else "ADD MANUAL ABILITY", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (manualAbilityOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = triggerName,
                        onValueChange = { triggerName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Ability or effect") },
                        singleLine = true,
                        enabled = canAct || tutorialStep?.target == "stack-add"
                    )
                    TextButton(
                        onClick = {
                            val title = triggerName.trim()
                            if (canAct && title.isNotBlank()) {
                                onAddTrigger(title)
                                triggerName = ""
                                manualAbilityOpen = false
                            }
                        },
                        enabled = canAct && triggerName.isNotBlank(),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("ADD", color = if (canAct && triggerName.isNotBlank()) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
                Button(
                    onClick = onPassPriority,
                    enabled = canAct || tutorialStep?.target == "stack-pass",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).then(tutorialAnchor("stack-pass")),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF09251D))
                ) { Text("PASS PRIORITY", fontSize = 14.sp, fontWeight = FontWeight.Black) }
                TextButton(
                    onClick = onResolve,
                    enabled = canResolve || tutorialStep?.target == "stack-resolve",
                    modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp).then(tutorialAnchor("stack-resolve"))
                ) { Text("RESOLVE TOP", color = if (canResolve) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            tutorialStep?.let { step ->
                GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit)
            }
            }
        }
    }
}

@Composable
private fun CombatDialog(players: List<TablePlayer>, attackerIndex: Int, onDismiss: () -> Unit, onLog: (String) -> Unit, onCombatDamage: (GameCard, Int, Int) -> Unit) {
    val attackers = players.getOrNull(attackerIndex)?.battlefield.orEmpty().filter { it.type.contains("creature", ignoreCase = true) && !it.tapped }
    val blockers = players.flatMapIndexed { index, player ->
        if (index == attackerIndex || player.isEliminated) emptyList() else player.battlefield.filter { it.type.contains("creature", ignoreCase = true) && !it.tapped }.map { index to it }
    }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var assignments by remember { mutableStateOf(emptyMap<Int, Set<Int>>()) }
    var defenders by remember { mutableStateOf(emptyMap<Int, Int>()) }
    var damage by remember { mutableStateOf(emptyMap<Int, String>()) }
    fun combatCreature(card: GameCard): CombatCreature {
        fun stat(base: String?, power: Boolean): Int? {
            if (card.isFaceDown) return null
            val printed = printedCreatureStat(base) ?: return null
            val plusOne = (card.counters["+1/+1"] ?: 0) + if (power) (card.counters["+1/+0"] ?: 0) else (card.counters["+0/+1"] ?: 0)
            val minusOne = card.counters["-1/-1"] ?: 0
            return printed + plusOne - minusOne
        }
        return CombatCreature(card.id, stat(card.power, true), stat(card.toughness, false), card.keywords.toSet(), card.oracleText.orEmpty())
    }
    fun aiBlocks(attacker: GameCard, defenderIndex: Int, currentAssignments: Map<Int, Set<Int>>): Set<Int> {
        val defender = players.getOrNull(defenderIndex)?.takeIf { it.isComputer } ?: return emptySet()
        val reserved = currentAssignments.filterKeys { it != attacker.id }.values.flatten().toSet()
        return chooseAiBlockers(
            combatCreature(attacker),
            defender.battlefield.filter { it.type.contains("creature", true) && !it.tapped }.map(::combatCreature),
            defender.life,
            reserved
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COMBAT · ${players.getOrNull(attackerIndex)?.name ?: "PLAYER"}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close combat", tint = Muted) }
                }
                Text("Select attackers, assign blockers, and enter damage. The app records declarations; resolve combat rules manually.", color = Muted, fontSize = 10.sp)
                if (attackers.isEmpty()) Text("No untapped creatures are available to attack.", color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(attackers.size, key = { attackers[it].id }) { index ->
                        val card = attackers[index]
                        var blockMenu by remember(card.id) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = card.id in selected, onCheckedChange = { checked ->
                                    selected = if (checked) selected + card.id else selected - card.id
                                    if (checked) {
                                        if (card.id !in damage) printedCreatureStat(card.power)?.takeIf { it > 0 }?.let { damage = damage + (card.id to it.toString()) }
                                        val defender = defenders[card.id] ?: players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated }
                                        if (defender != null) {
                                            defenders = defenders + (card.id to defender)
                                            assignments = assignments + (card.id to aiBlocks(card, defender, assignments))
                                        }
                                    } else assignments = assignments - card.id
                                })
                                Text(card.name, color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (card.tapped) "TAPPED" else "READY", color = Muted, fontSize = 8.sp)
                            }
                            if (card.id in selected) {
                                val defaultDefender = players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated } ?: attackerIndex
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        TextButton(onClick = { blockMenu = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("TARGET: ${players.getOrNull(defenders[card.id] ?: defaultDefender)?.name ?: "PLAYER"} ▾", color = Mint, fontSize = 9.sp) }
                                        DropdownMenu(expanded = blockMenu, onDismissRequest = { blockMenu = false }) {
                                            players.forEachIndexed { targetIndex, target ->
                                            if (targetIndex != attackerIndex && !target.isEliminated) DropdownMenuItem(text = { Text(target.name) }, onClick = {
                                                defenders = defenders + (card.id to targetIndex)
                                                assignments = assignments + (card.id to aiBlocks(card, targetIndex, assignments))
                                                blockMenu = false
                                            })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    OutlinedTextField(value = damage[card.id].orEmpty(), onValueChange = { damage = damage + (card.id to it.filter(Char::isDigit)) }, modifier = Modifier.width(105.dp), singleLine = true, label = { Text("Damage") }, placeholder = { Text(card.power ?: "?") })
                                }
                                val defenderIndex = defenders[card.id] ?: defaultDefender
                                if (players.getOrNull(defenderIndex)?.isComputer == true) Text("AI BLOCK PICK · CHANGE ANYTIME", color = Mint, fontSize = 8.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                else Text("Blockers assigned", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                                blockers.filter { it.first == (defenders[card.id] ?: defaultDefender) }.forEach { (owner, blocker) ->
                                    val reservedByOther = assignments.any { (attackerId, blockerIds) -> attackerId != card.id && blocker.id in blockerIds }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 7.dp)) {
                                        Checkbox(checked = blocker.id in assignments[card.id].orEmpty(), enabled = !reservedByOther, onCheckedChange = { checked ->
                                            val current = assignments[card.id].orEmpty()
                                            assignments = assignments + (card.id to if (checked) current + blocker.id else current - blocker.id)
                                        })
                                        Text("${players[owner].name} · ${blocker.name}${if (reservedByOther) " · ASSIGNED" else ""}", color = if (reservedByOther) Muted else SoftWhite, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Blockers available: ${blockers.size}  ·  tapped creatures excluded", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    TextButton(onClick = {
                        if (selected.isNotEmpty()) {
                            val names = attackers.filter { it.id in selected }.joinToString { card ->
                                val amount = damage[card.id].orEmpty().takeIf { it.isNotBlank() }?.let { " ($it damage)" }.orEmpty()
                                val blocker = assignments[card.id].orEmpty().mapNotNull { id -> blockers.firstOrNull { it.second.id == id }?.let { "${players[it.first].name}'s ${it.second.name}" } }.joinToString().let { if (it.isBlank()) "unblocked" else "blocked by $it" }
                                val defender = defenders[card.id] ?: players.indices.firstOrNull { it != attackerIndex && !players[it].isEliminated } ?: attackerIndex
                                val damageAmount = damage[card.id].orEmpty().toIntOrNull() ?: 0
                                if (assignments[card.id].orEmpty().isEmpty() && damageAmount > 0) onCombatDamage(card, defender, damageAmount)
                                "${card.name} → ${players.getOrNull(defender)?.name ?: "player"}$amount · $blocker"
                            }
                            onLog("declared attackers: $names")
                        }
                        onDismiss()
                    }) { Text("LOG COMBAT", color = Mint, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CardPreviewPopup(item: CardPreview, onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val previewWidth = minOf(280.dp, (screenHeight - 64.dp).coerceAtLeast(180.dp) * 0.72f)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = LocalView.current.parent as? DialogWindowProvider
        SideEffect {
            dialogWindow?.window?.setDimAmount(0f)
            dialogWindow?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        Box(Modifier.fillMaxSize().combinedClickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Box(Modifier.combinedClickable(onClick = {}, onLongClick = {}), contentAlignment = Alignment.TopEnd) {
            CardFace(item.card, Modifier.width(previewWidth), onClick = {}, onLongClick = {}, showCounters = false)
            IconButton(onClick = onDismiss, modifier = Modifier.padding(5.dp).size(42.dp).clip(CircleShape).background(Color(0xCC101615))) {
                Icon(Icons.Filled.Close, "Close card preview", tint = Color.White)
            }
            }
        }
    }
}

@Composable
private fun CardZoneDialog(zone: String, player: String, cards: List<GameCard>, onDismiss: () -> Unit, onPreview: (GameCard) -> Unit, canMoveCards: Boolean = false, onMoveCard: (GameCard, String) -> Unit = { _, _ -> }, commanderId: Int? = null, canMoveCommander: Boolean = false) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$player · $zone (${cards.size})", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close", tint = Muted) }
                }
                if (cards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No cards here", color = Muted) }
                else LazyVerticalGrid(columns = GridCells.Adaptive(90.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(cards.size, key = { cards[it].id }) { index ->
                        val card = cards[index]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth()) {
                                CardFace(card, Modifier.fillMaxWidth(), onClick = { onPreview(card) }, onLongClick = { onPreview(card) })
                                if (canMoveCards) {
                                    var menuOpen by remember(card.id) { mutableStateOf(false) }
                                    Box(Modifier.align(Alignment.TopEnd).padding(1.dp), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xCC101615)), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.MoreVert, if (card.isFaceDown) "Card actions" else "Move ${card.name}", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        listOf("Hand", "Battlefield", "Library (top)", "Library (bottom)", "Graveyard", "Exile", "Revealed")
                                            .filterNot { it == zone }
                                            .forEach { destination ->
                                                DropdownMenuItem(
                                                    text = { Text("Move to $destination") },
                                                    onClick = { menuOpen = false; onMoveCard(card, destination) }
                                                )
                                            }
                                        if (canMoveCommander && card.id == commanderId) DropdownMenuItem(
                                            text = { Text("Move to Command Zone") },
                                            onClick = { menuOpen = false; onMoveCard(card, "Command Zone") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val counterCatalog = listOf(
    "+1/+1", "-1/-1", "+1/+0", "+0/+1", "+2/+2", "-0/-1", "Loyalty", "Charge", "Shield", "Stun", "Poison", "Energy", "Experience", "Time", "Age", "Quest", "Level", "Lore", "Fate", "Finality", "Rad", "Oil", "Incubation", "Hatchling", "Brick", "Blood", "Bounty", "Deathtouch", "Flying", "First strike", "Double strike", "Lifelink", "Menace", "Reach", "Trample", "Vigilance", "Hexproof", "Indestructible", "Ward", "Paw", "Ki", "Petrification", "Sleep", "Slime", "Spore", "Muster", "Currency", "Blaze", "Aegis", "Arrow", "Arrowhead", "Awakening", "Banishment", "Blight", "Book", "Burden", "Cage", "Call", "Carrion", "Coin", "Corruption", "Credit", "Culmination", "Cube", "Crystal", "Death", "Delay", "Despair", "Doom", "Dream", "Echo", "Egg", "Elixir", "Eon", "Eruption", "Fade", "Filibuster", "Flame", "Flood", "Foreshadow", "Fungus", "Fuse", "Gold", "Growth", "Hour", "Ice", "Ink", "Javelin", "Jewel", "Knowledge", "Luck", "Mining", "Mire", "Mine", "Nest", "Night", "Omen", "Ore", "Page", "Paralyzation", "Petal", "Phylactery", "Plague", "Pressure", "Rust", "Scream", "Sleight", "Soul", "Storage", "Study", "Theft", "Tide", "Tower", "Training", "Trap", "Treasure", "Unity", "Valor", "Verse", "Vitality", "Vortex", "Wind", "Wish", "Worm", "Yell"
)

@Composable
private fun CounterPickerDialog(card: GameCard, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = counterCatalog.filter { it.contains(query, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Add a counter · ${card.name}", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Search counter types") })
                if (query.isNotBlank() && matches.none { it.equals(query.trim(), true) }) {
                    TextButton(onClick = { onAdd(query.trim()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("ADD CUSTOM “${query.trim()}” COUNTER", color = CounterGold) }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(matches) { counter ->
                        TextButton(onClick = { onAdd(counter) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(counter, color = SoftWhite, modifier = Modifier.weight(1f)); Text("+", color = CounterGold, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { Text("CANCEL") }
            }
        }
    }
}

@Composable
private fun TokenPickerDialog(onDismiss: () -> Unit, onAdd: (ScryfallCard) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(400)
        loading = true
        try {
            results = Scryfall.searchTokens(query)
            error = if (results.isEmpty()) "No token cards found. Try another name." else null
        } catch (_: Exception) { error = "Token search needs an internet connection." }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add a token", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Search Scryfall’s token cards", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close token search", tint = Muted) }
                }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, label = { Text("Search tokens") }, placeholder = { Text("Treasure, Clue, Zombie…") })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Treasure", "Clue", "Food", "Blood", "Zombie", "Goblin", "Soldier").forEach { suggestion ->
                        AssistChip(onClick = { query = suggestion }, label = { Text(suggestion, fontSize = 9.sp) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                error?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(6.dp)) }
                if (query.trim().length >= 2 && results.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                    TextButton(onClick = { onAdd(ScryfallCard(query.trim(), "Custom token", null)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Text("ADD CUSTOM TOKEN: ${query.trim()}", color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { token ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(PanelLight).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardFace(GameCard(-token.name.hashCode(), token.name, token.type, Color(0xFF6D836D), imageUrl = token.imageUrl, isToken = true), Modifier.width(46.dp), onClick = {}, onLongClick = {}, showCounters = false)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(token.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(token.type, color = Muted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onAdd(token) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("ADD", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, onClick: () -> Unit) {
    Text(label, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(PanelLight).combinedClickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp))
}

private fun formatClock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun savePlayers(context: android.content.Context, players: List<TablePlayer>) {
    fun cardJson(card: GameCard) = JSONObject().apply {
        put("id", card.id); put("name", card.name); put("type", card.type); put("colorArgb", card.color.toArgb())
        put("tapped", card.tapped); put("image", card.imageUrl ?: JSONObject.NULL); put("token", card.isToken)
        put("faceDown", card.isFaceDown); put("oracle", card.oracleText ?: JSONObject.NULL)
        put("manaValue", card.manaValue ?: JSONObject.NULL)
        put("manaCost", card.manaCost ?: JSONObject.NULL)
        put("power", card.power ?: JSONObject.NULL); put("toughness", card.toughness ?: JSONObject.NULL)
        put("keywords", JSONArray().apply { card.keywords.forEach { put(it) } })
        put("chosenCreatureType", card.chosenCreatureType ?: JSONObject.NULL)
        put("artCrop", card.artCropUrl ?: JSONObject.NULL)
        put("importIssue", card.importIssue ?: JSONObject.NULL)
        put("counters", JSONObject().apply { card.counters.forEach { (name, amount) -> put(name, amount) } })
    }
    val data = JSONArray().apply {
        players.forEach { player ->
            put(JSONObject().apply {
                put("name", player.name); put("id", player.id); put("commander", player.commanderName ?: JSONObject.NULL); put("commanderCard", player.commanderCard?.let { cardJson(it) } ?: JSONObject.NULL); put("computer", player.isComputer); put("deckLoaded", player.deckLoaded); put("handRevealedToAll", player.handRevealedToAll); put("life", player.life)
                put("commanderCastCount", player.commanderCastCount); put("isEliminated", player.isEliminated); put("eliminationReason", player.eliminationReason ?: JSONObject.NULL)
                put("playerCounters", JSONObject().apply { player.playerCounters.forEach { (counter, amount) -> put(counter, amount) } })
                put("commanderDamageReceived", JSONObject().apply { player.commanderDamageReceived.forEach { (playerId, amount) -> put(playerId, amount) } })
                put("manaPool", JSONObject().apply { player.manaPool.forEach { (color, amount) -> put(color, amount) } })
                listOf("library" to player.library, "hand" to player.hand, "battlefield" to player.battlefield, "graveyard" to player.graveyard, "exile" to player.exile, "revealed" to player.revealed)
                    .forEach { (key, cards) -> put(key, JSONArray().apply { cards.forEach { put(cardJson(it)) } }) }
            })
        }
    }
    runCatching { context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE).edit().putString("players", data.toString()).apply() }
}

private fun saveSavedDecks(context: android.content.Context, decks: List<SavedDeck>) {
    val data = JSONArray().apply { decks.forEach { put(JSONObject().put("name", it.name).put("text", it.text)) } }
    runCatching { context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE).edit().putString("savedDecks", data.toString()).apply() }
}

private fun loadSavedDecks(context: android.content.Context): List<SavedDeck> = runCatching {
    val deckPrefs = context.getSharedPreferences("arcana-saved-decks", android.content.Context.MODE_PRIVATE)
    val raw = deckPrefs.getString("savedDecks", null) ?: run {
        val legacyPrefs = context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString("savedDecks", null) ?: return emptyList()
        deckPrefs.edit().putString("savedDecks", legacy).apply()
        legacyPrefs.edit().remove("savedDecks").apply()
        legacy
    }
    val data = JSONArray(raw)
    (0 until data.length()).mapNotNull { index ->
        data.optJSONObject(index)?.let { item ->
            val name = item.optString("name").trim()
            val text = item.optString("text").trim()
            if (name.isBlank() || text.isBlank()) null else SavedDeck(name, text)
        }
    }
}.getOrDefault(emptyList())

private fun loadSavedPlayers(context: android.content.Context): List<TablePlayer>? = runCatching {
    val raw = context.getSharedPreferences("arcana-table", android.content.Context.MODE_PRIVATE).getString("players", null) ?: return null
    val data = JSONArray(raw)
    if (data.length() !in 1..4) return null
    fun parseCard(card: JSONObject): GameCard {
        val countersJson = card.optJSONObject("counters") ?: JSONObject()
        val counters = countersJson.keys().asSequence().associateWith { countersJson.optInt(it, 0) }.filterValues { it > 0 }
        return GameCard(
            id = card.optInt("id"), name = card.optString("name"), type = card.optString("type"),
            color = Color(if (card.has("colorArgb")) card.optInt("colorArgb", 0xFF547D9A.toInt()) else card.optLong("color", 0xFF547D9AL).toInt()),
            tapped = card.optBoolean("tapped"),
            imageUrl = card.optString("image").takeUnless { it == "null" || it.isBlank() }, isToken = card.optBoolean("token"),
            counters = counters, isFaceDown = card.optBoolean("faceDown"),
            oracleText = card.optString("oracle").takeUnless { it == "null" || it.isBlank() },
            manaValue = card.optDouble("manaValue").takeUnless { card.isNull("manaValue") },
            importIssue = card.optString("importIssue").takeUnless { it == "null" || it.isBlank() },
            artCropUrl = card.optString("artCrop").takeUnless { it == "null" || it.isBlank() },
            manaCost = if (card.has("manaCost") && !card.isNull("manaCost")) card.optString("manaCost") else null,
            power = card.optString("power").takeUnless { it == "null" || it.isBlank() },
            toughness = card.optString("toughness").takeUnless { it == "null" || it.isBlank() },
            keywords = card.optJSONArray("keywords")?.let { values -> (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            chosenCreatureType = card.optString("chosenCreatureType").takeUnless { it == "null" || it.isBlank() }
        )
    }
    fun cards(owner: JSONObject, key: String): List<GameCard> {
        val list = owner.optJSONArray(key) ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val card = list.optJSONObject(index) ?: return@mapNotNull null
            parseCard(card)
        }.filterNot { isDeckSectionHeading(it.name) }
    }
    (0 until data.length()).mapNotNull { index ->
        val player = data.optJSONObject(index) ?: return@mapNotNull null
        val commanderCard = player.optJSONObject("commanderCard")?.let(::parseCard)?.takeUnless { isDeckSectionHeading(it.name) }
        val commanderName = player.optString("commander").takeUnless { it == "null" || it.isBlank() || isDeckSectionHeading(it) }
        TablePlayer(
            name = player.optString("name", "PLAYER ${index + 1}"),
            id = player.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            commanderName = commanderCard?.name ?: commanderName,
            commanderCard = commanderCard,
            isComputer = player.optBoolean("computer"),
            deckLoaded = player.optBoolean("deckLoaded"), handRevealedToAll = player.optBoolean("handRevealedToAll"), life = player.optInt("life", 40),
            commanderCastCount = player.optInt("commanderCastCount").coerceAtLeast(0),
            isEliminated = player.optBoolean("isEliminated"), eliminationReason = player.optString("eliminationReason").takeUnless { it == "null" || it.isBlank() },
            playerCounters = player.optJSONObject("playerCounters")?.let { counters -> buildMap { counters.keys().forEach { put(it, counters.optInt(it).coerceAtLeast(0)) } } }.orEmpty(),
            commanderDamageReceived = player.optJSONObject("commanderDamageReceived")?.let { damage -> buildMap { damage.keys().forEach { put(it, damage.optInt(it).coerceAtLeast(0)) } } }.orEmpty(),
            manaPool = player.optJSONObject("manaPool")?.let { pool -> buildMap { pool.keys().forEach { color -> put(color, pool.optInt(color).coerceAtLeast(0)) } } }.orEmpty(),
            library = cards(player, "library"), hand = cards(player, "hand"), battlefield = cards(player, "battlefield"),
            graveyard = cards(player, "graveyard"), exile = cards(player, "exile"), revealed = cards(player, "revealed")
        )
    }.takeIf { it.size == data.length() && it.size in 2..4 }
}.getOrNull()

@Composable
private fun SavedDecksDialog(decks: List<SavedDeck>, tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier, onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit, onLoad: (SavedDeck) -> Unit, onDelete: (SavedDeck) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SAVED DECKS · ${decks.size}", color = SoftWhite, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("saved-close"))) { Icon(Icons.Filled.Close, "Close saved decks", tint = Muted) }
                }
                if (decks.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No saved decks yet. Choose ‘Save this imported deck for later’ when importing.", color = Muted, fontSize = 12.sp) }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(decks.size) { index ->
                            val deck = decks[index]
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                                    Text(deck.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${parseDeckList(deck.text).sumOf { it.count }} cards", color = Muted, fontSize = 9.sp)
                                }
                                TextButton(onClick = { onLoad(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) tutorialAnchor("saved-load") else Modifier)) { Text("LOAD", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onDelete(deck) }, modifier = Modifier.heightIn(min = 44.dp).then(if (index == 0) tutorialAnchor("saved-delete") else Modifier)) { Text("DELETE", color = Color(0xFFE9978B), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).then(tutorialAnchor("saved-close"))) { Text("CLOSE", color = Muted) }
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
            }
        }
    }
}

@Composable
private fun GameLogDialog(entries: List<String>, previewableCards: List<Pair<Int, GameCard>>, onPreviewCard: (Int, GameCard) -> Unit, tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int, tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier, onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f), shape = RoundedCornerShape(14.dp), color = Panel) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTIVITY LOG · ${entries.size}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("log-close"))) { Icon(Icons.Filled.Close, "Close game log", tint = Muted) }
                }
                Text("Actions · mana payments · triggers · effects", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 7.dp))
                if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No actions logged yet", color = Muted) }
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(entries.size) { index ->
                        val entry = entries[entries.lastIndex - index]
                        val entryColor = when {
                            "trigger" in entry.lowercase() -> Mint
                            "paid " in entry.lowercase() || "payment" in entry.lowercase() -> CounterGold
                            "automatically" in entry.lowercase() || "effect" in entry.lowercase() || "life" in entry.lowercase() || "token" in entry.lowercase() -> Color(0xFF34749B)
                            else -> SoftWhite
                        }
                        val preview = remember(entry, previewableCards) {
                            previewableCards.sortedByDescending { it.second.name.length }
                                .firstOrNull { (_, card) -> entry.contains(card.name, ignoreCase = true) }
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            ManaSymbolText(entry, color = entryColor, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(3.dp))
                            preview?.let { (owner, card) ->
                                CardFace(card, Modifier.width(31.dp), onClick = { onPreviewCard(owner, card) }, onLongClick = { onPreviewCard(owner, card) }, showCounters = false)
                            }
                        }
                    }
                }
                Text("Unsupported or ambiguous effects are marked for manual resolution.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
            }
        }
    }
}

@Composable
private fun LocalChatPanel(
    players: List<TablePlayer>, activePlayer: Int, messages: List<ChatMessage>, draft: String,
    modifier: Modifier = Modifier, tutorialStep: TutorialStop?, tutorialIndex: Int, tutorialTotal: Int,
    tutorialBounds: Map<String, Rect>, tutorialAnchor: (String) -> Modifier,
    onTutorialBack: () -> Unit, onTutorialNext: () -> Unit, onTutorialExit: () -> Unit,
    onDraft: (String) -> Unit, onDismiss: () -> Unit, onSend: () -> Unit
) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp), color = Panel) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TABLE CHAT", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).then(tutorialAnchor("chat-close"))) { Icon(Icons.Filled.Close, "Close chat", tint = Muted) }
                }
                Text("Room messages · sending as ${players.getOrNull(activePlayer)?.name ?: "PLAYER"}", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(messages.size) { index ->
                        val item = messages[index]
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(9.dp)) {
                            Text("${item.author} · ${item.timestamp}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            SelectionContainer { Text(item.text, color = SoftWhite, fontSize = 12.sp) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Message") })
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val pastedText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!pastedText.isNullOrEmpty()) onDraft(draft + pastedText)
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("PASTE", color = Mint, fontSize = 9.sp) }
                    TextButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).then(tutorialAnchor("chat-send"))) { Text("SEND", color = Mint) }
                }
            }
            tutorialStep?.let { step -> GuidedTutorialOverlay(step, tutorialIndex, tutorialTotal, tutorialBounds[step.target], onTutorialBack, onTutorialNext, onTutorialExit) }
        }
    }
}
