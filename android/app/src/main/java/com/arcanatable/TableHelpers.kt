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

internal fun samplePlayers(): List<TablePlayer> {
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

internal data class ChatMessage(val author: String, val text: String, val timestamp: String)
@Composable
internal fun HandPeekBar(cardCount: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(PanelLight)
            .combinedClickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("HAND  $cardCount", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ExpandLess, contentDescription = "Open hand tray", tint = Mint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun ViewChip(label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Text(label, color = if (selected) Ink else SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = if (compact) 44.dp else 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(if (selected) Mint else PanelLight).combinedClickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 12.dp))
}

@Composable
internal fun PlayerViewTab(name: String, commander: GameCard?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val artUrl = commander?.artCropUrl ?: commander?.imageUrl
    Box(
        Modifier.width(96.dp).height(50.dp).clip(shape)
            .background(PanelLight)
            .border(if (selected) 2.dp else 1.dp, if (selected) Mint else Color(0xFF33423D), shape)
            .combinedClickable(onClick = onClick)
    ) {
        if (!artUrl.isNullOrBlank()) {
            CardArt(artUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, label = commander?.name)
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

internal fun layoutSectionTitle(id: String): String = when (id) {
    "STATUS" -> "Health and activity bar"
    "MANA" -> "Mana pool"
    "COMMANDER" -> "Commander strip"
    "STEP" -> "Next step"
    "ZONES" -> "Quick Actions"
    "REVEALED" -> "Public reveals"
    "BATTLEFIELD" -> "Battlefield"
    else -> id
}

internal fun layoutQuickActionTitle(id: String): String = when (id) {
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
    "PASS_TO_TURN" -> "Pass to my turn"
    "UNDO" -> "Undo"
    "NEXT_STEP" -> "Next step"
    "SHUFFLE" -> "Shuffle library"
    "LOG" -> "Game log"
    "CHAT" -> "Chat"
    else -> id
}

internal fun layoutHandActionTitle(id: String): String = when (id) {
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
internal fun LayoutOrderRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
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
internal fun LayoutTuningSlider(
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
internal fun SettingsToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ZoneChip(name: String, count: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val target = when (name.uppercase()) {
        "GRAVEYARD" -> "quick-graveyard"
        "EXILE" -> "quick-exile"
        "LIBRARY" -> "quick-library"
        else -> "quick-${name.lowercase()}"
    }
    Text("$name  $count", color = if (enabled) SoftWhite else Muted, fontSize = 8.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.tutorialTarget(target)
            .heightIn(min = 34.dp).wrapContentHeight(Alignment.CenterVertically)
            .clip(RoundedCornerShape(6.dp)).background(if (enabled) PanelLight else Panel)
            .combinedClickable(enabled = enabled, onClick = { TutorialActions.tap(target); onClick() })
            .padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
internal fun QuickActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val target = when {
        label.startsWith("STACK") -> "stack-button"
        label.startsWith("GAME LOG") -> "quick-log"
        label.startsWith("PASS TO MY TURN") || label.startsWith("STOP AUTO-PASS") -> "quick-pass-to-turn"
        label.startsWith("HAND") -> "hand-handle"
        label.startsWith("UNDO") -> "quick-undo"
        else -> "quick-action-${label.lowercase().replace(' ', '-')}"
    }
    Text(label, color = if (enabled) SoftWhite else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.tutorialTarget(target).heightIn(min = 34.dp)
            .wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(6.dp))
            .background(if (enabled) PanelLight else Panel)
            .combinedClickable(enabled = enabled, onClick = { TutorialActions.tap(target); onClick() })
            .padding(horizontal = 8.dp, vertical = 6.dp))
}

internal fun manaLetter(color: String): String = when (color.uppercase()) {
    "WHITE", "W" -> "W"; "BLUE", "U" -> "U"; "BLACK", "B" -> "B"; "RED", "R" -> "R"; "GREEN", "G" -> "G"; "COLORLESS", "C" -> "C"
    else -> color.take(1).uppercase()
}

internal fun battlefieldGroups(cards: List<GameCard>): List<Pair<String, List<GameCard>>> {
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

internal fun cardActivatedAbilityLines(card: GameCard): List<String> {
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

internal fun cardNameSimilarity(first: String, second: String): Double {
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

internal val counterCatalog = listOf(
    "+1/+1", "-1/-1", "+1/+0", "+0/+1", "+2/+2", "-0/-1", "Loyalty", "Charge", "Shield", "Stun", "Poison", "Energy", "Experience", "Time", "Age", "Quest", "Level", "Lore", "Fate", "Finality", "Rad", "Oil", "Incubation", "Hatchling", "Brick", "Blood", "Bounty", "Deathtouch", "Flying", "First strike", "Double strike", "Lifelink", "Menace", "Reach", "Trample", "Vigilance", "Hexproof", "Indestructible", "Ward", "Paw", "Ki", "Petrification", "Sleep", "Slime", "Spore", "Muster", "Currency", "Blaze", "Aegis", "Arrow", "Arrowhead", "Awakening", "Banishment", "Blight", "Book", "Burden", "Cage", "Call", "Carrion", "Coin", "Corruption", "Credit", "Culmination", "Cube", "Crystal", "Death", "Delay", "Despair", "Doom", "Dream", "Echo", "Egg", "Elixir", "Eon", "Eruption", "Fade", "Filibuster", "Flame", "Flood", "Foreshadow", "Fungus", "Fuse", "Gold", "Growth", "Hour", "Ice", "Ink", "Javelin", "Jewel", "Knowledge", "Luck", "Mining", "Mire", "Mine", "Nest", "Night", "Omen", "Ore", "Page", "Paralyzation", "Petal", "Phylactery", "Plague", "Pressure", "Rust", "Scream", "Sleight", "Soul", "Storage", "Study", "Theft", "Tide", "Tower", "Training", "Trap", "Treasure", "Unity", "Valor", "Verse", "Vitality", "Vortex", "Wind", "Wish", "Worm", "Yell"
)

@Composable
internal fun ActionPill(label: String, onClick: () -> Unit) {
    Text(label, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 50.dp).wrapContentHeight(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp)).background(PanelLight).combinedClickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp))
}

internal fun formatClock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

@Composable
internal fun LocalChatPanel(
    players: List<TablePlayer>, activePlayer: Int, messages: List<ChatMessage>, draft: String,
    modifier: Modifier = Modifier, 
    onDraft: (String) -> Unit, onDismiss: () -> Unit, onSend: () -> Unit
) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp), color = Panel) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TABLE CHAT", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close chat", tint = Muted) }
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
                    TextButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("SEND", color = Mint) }
                }
            }
        }
    }
}

/** Same card, same question: the key "always yes this turn" remembers. */
internal fun autoYesKey(decision: JSONObject): String {
    val context = decision.optJSONObject("context")
    val source = context?.optString("sourceName")?.takeIf { it.isNotBlank() && it != "null" } ?: context?.optString("sourceId").orEmpty()
    return source + "|" + decision.optString("prompt")
}
