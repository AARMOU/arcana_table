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

internal data class TableColors(val background: Color, val panel: Color, val raised: Color, val accent: Color, val text: Color, val muted: Color, val counter: Color)
internal val darkTableColors = TableColors(Color(0xFF101615), Color(0xFF192220), Color(0xFF26332F), Color(0xFF89E3B0), Color(0xFFEAF1ED), Color(0xFFA4B1AB), Color(0xFFFFD166))
internal val lightTableColors = TableColors(Color(0xFFF2F5F1), Color(0xFFFFFFFF), Color(0xFFE4EBE6), Color(0xFF196E4A), Color(0xFF17231D), Color(0xFF5E6C64), Color(0xFF8D6500))
internal val LocalTableColors = staticCompositionLocalOf { darkTableColors }
internal var appInForeground by mutableStateOf(true)
internal const val GAME_ALERT_CHANNEL = "arcana_game_alerts"
internal const val DEFAULT_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.arcanamtg.workers.dev"
internal const val LEGACY_EXAMPLE_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.example.workers.dev"

internal fun isLegacyExampleCloudEndpoint(endpoint: String): Boolean =
    endpoint.trim().trimEnd('/').equals(LEGACY_EXAMPLE_CLOUD_ROOM_ENDPOINT, ignoreCase = true)

internal fun postGameAlert(context: Context, title: String, text: String) {
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
internal val Ink: Color @Composable get() = LocalTableColors.current.background
internal val Panel: Color @Composable get() = LocalTableColors.current.panel
internal val PanelLight: Color @Composable get() = LocalTableColors.current.raised
internal val Mint: Color @Composable get() = LocalTableColors.current.accent
internal val SoftWhite: Color @Composable get() = LocalTableColors.current.text
internal val Muted: Color @Composable get() = LocalTableColors.current.muted
internal val CounterGold: Color @Composable get() = LocalTableColors.current.counter


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
    val isCommander: Boolean = false,
    /** Engine-provided board facts (Argentum games): "ATTACKING" / "BLOCKING", marked damage, summoning sickness. */
    val combatRole: String? = null,
    val damage: Int = 0,
    val summoningSick: Boolean = false,
)

/** Sandbox only: the originating zone matters when a land is played from outside the hand. */
internal data class LandEntryPrompt(val playerIndex: Int, val card: GameCard, val isShockLand: Boolean, val conditionText: String, val sourceZone: String = "Hand")

/** Locate and remove the *actual* card from the chosen source when manually casting or playing.
 * Argentum games must use the rules engine's own zone transitions instead.
 */
internal fun TablePlayer.cardsInPlaySource(zone: String): List<GameCard> = when (zone) {
    "Hand" -> hand
    "Graveyard" -> graveyard
    "Exile" -> exile
    else -> emptyList()
}

internal fun TablePlayer.removeFromPlaySource(zone: String, cardId: Int): TablePlayer? {
    if (cardsInPlaySource(zone).none { it.id == cardId }) return null
    return when (zone) {
        "Hand" -> copy(hand = hand.filterNot { it.id == cardId })
        "Graveyard" -> copy(graveyard = graveyard.filterNot { it.id == cardId })
        "Exile" -> copy(exile = exile.filterNot { it.id == cardId })
        else -> null
    }
}

internal fun shockLandChoiceRequired(card: GameCard): Boolean {
    val text = card.oracleText.orEmpty().lowercase()
    return card.type.contains("land", ignoreCase = true) &&
        ("pay 2 life" in text || "pay 2 life" in card.name.lowercase()) &&
        ("enters tapped" in text || "enters the battlefield tapped" in text)
}

internal fun landEntryCondition(card: GameCard): String? {
    if (!card.type.contains("land", ignoreCase = true)) return null
    val text = card.oracleText.orEmpty()
    val relevant = text.lines().firstOrNull { line ->
        val lower = line.lowercase()
        ("enters tapped" in lower || "enters the battlefield tapped" in lower) &&
            ("unless" in lower || "if" in lower || "pay" in lower)
    }
    return relevant?.trim()?.takeIf(String::isNotBlank)
}

internal fun landAlwaysEntersTapped(card: GameCard): Boolean {
    val text = card.oracleText.orEmpty().lowercase()
    return card.type.contains("land", ignoreCase = true) &&
        ("enters tapped" in text || "enters the battlefield tapped" in text) &&
        landEntryCondition(card) == null
}

internal data class ManaChoicePrompt(
    val playerIndex: Int,
    val cardName: String,
    val options: List<ManaProductionOption>
)

internal data class CreatureTypeChoicePrompt(val playerIndex: Int, val cardId: Int, val cardName: String)

internal fun needsCreatureTypeChoice(card: GameCard): Boolean =
    !card.isFaceDown && Regex("\\bas this enters(?: the battlefield)?, choose a creature type\\b", RegexOption.IGNORE_CASE)
        .containsMatchIn(card.oracleText.orEmpty())

internal data class AiManaPlan(val landIds: Set<Int>, val remainingPool: Map<String, Int>)

internal fun planAiMana(player: TablePlayer, card: GameCard): AiManaPlan? {
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

internal fun aiCardPriority(card: GameCard): Double {
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

internal fun manaProductionOptions(card: GameCard): List<ManaProductionOption> {
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

internal data class SavedDeck(val name: String, val text: String, val commander: String? = null)
