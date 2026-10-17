package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private val WarnRed = Color(0xFFFFB4A8)

/** Facts the status strip shows. Built by MainActivity from live Argentum state only. */
data class ArgentumStatusModel(
    val connection: ArgentumConnectionStatus,
    val gameActive: Boolean,
    val gameOver: Boolean,
    val awaitingUpdate: Boolean,
    val turnLabel: String?,
    val priorityLabel: String?,
    val needsHandling: String?,
    val stepKey: String = "",
    val seats: List<ArgentumSeatChip> = emptyList(),
    val stack: List<ArgentumStackItem> = emptyList(),
    val feed: List<ArgentumLogLine> = emptyList(),
)

/** A player summary in the HUD. */
data class ArgentumSeatChip(val name: String, val life: Int, val isYou: Boolean, val isActive: Boolean, val hasPriority: Boolean)

/** Argentum Step names grouped into the five parts of a turn shown by the phase tracker. */
private val PhaseGroups = listOf(
    "BEGIN" to setOf("UNTAP", "UPKEEP", "DRAW"),
    "MAIN 1" to setOf("PRECOMBAT_MAIN"),
    "COMBAT" to setOf("BEGIN_COMBAT", "DECLARE_ATTACKERS", "DECLARE_BLOCKERS", "FIRST_STRIKE_COMBAT_DAMAGE", "COMBAT_DAMAGE", "END_COMBAT"),
    "MAIN 2" to setOf("POSTCOMBAT_MAIN"),
    "END" to setOf("END", "END_STEP", "CLEANUP"),
)

@Composable
fun PhaseTracker(stepKey: String, modifier: Modifier = Modifier.fillMaxWidth().padding(top = 6.dp), accent: Color = Mint) {
    val key = stepKey.uppercase().replace(' ', '_')
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380
    Row(modifier.testTag("phase-tracker"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PhaseGroups.forEach { (label, steps) ->
            val current = key in steps
            Text(
                label, color = if (current) TurnColors.OnColor else Muted, fontSize = if (narrow) 8.sp else 9.sp, fontWeight = FontWeight.Black, letterSpacing = if (narrow) 0.sp else 0.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (current) accent else PanelLight).padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * Player tab for Argentum games: commander art, name and life in one control, so life is shown
 * once. A dot marks whose turn it is, a mint ring who has priority; life flashes on change.
 */
@Composable
fun ArgentumSeatTab(seat: ArgentumSeatChip, artUrl: String?, artLabel: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, turnLabel: String? = null) {
    var last by remember { mutableIntStateOf(seat.life) }
    val flash = remember { androidx.compose.animation.Animatable(Color.Transparent) }
    androidx.compose.runtime.LaunchedEffect(seat.life) {
        if (seat.life != last) {
            flash.snapTo(if (seat.life < last) Color(0x88E35656) else Color(0x8889E3B0))
            last = seat.life
            flash.animateTo(Color.Transparent, androidx.compose.animation.core.tween(1200))
        }
    }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(if (selected) Mint.copy(alpha = 0.18f) else PanelLight).background(flash.value)
            .border(if (selected || seat.isActive) 2.dp else 1.dp, if (seat.isActive) TurnColors.forSeat(seat.isYou) else if (selected) Mint else Color(0xFF33423D), shape)
            .clickable(onClick = onClick).padding(end = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(40.dp).fillMaxHeight().background(Panel)) {
            if (!artUrl.isNullOrBlank()) CardArt(artUrl, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, label = artLabel)
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(if (seat.isYou) "YOU" else seat.name.uppercase(), color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 420) 84.dp else 130.dp))
            // Whose turn it is, in words, in that player's colour.
            if (turnLabel != null) Text(turnLabel, color = TurnColors.forSeat(seat.isYou), fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.testTag("seat-turn-label"))
        }
        Spacer(Modifier.width(7.dp))
        Text("${seat.life}", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SeatLifeChip(seat: ArgentumSeatChip, modifier: Modifier = Modifier) {
    // Flash red on life loss and green on gain so changes during the opponent's turn are noticed.
    var last by remember { mutableIntStateOf(seat.life) }
    val flash = remember { androidx.compose.animation.Animatable(Color.Transparent) }
    androidx.compose.runtime.LaunchedEffect(seat.life) {
        if (seat.life != last) {
            flash.snapTo(if (seat.life < last) Color(0x66E35656) else Color(0x6689E3B0))
            last = seat.life
            flash.animateTo(Color.Transparent, androidx.compose.animation.core.tween(1200))
        }
    }
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(PanelLight).background(flash.value)
            .then(if (seat.hasPriority) Modifier.border(1.5.dp, Mint, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (seat.isActive) { Box(Modifier.size(6.dp).clip(CircleShape).background(Mint)); Spacer(Modifier.width(5.dp)) }
        Text(if (seat.isYou) "YOU" else seat.name, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        Text("${seat.life}", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Always-visible engine/game status for Argentum games, with a recovery action for every
 * non-healthy state so the player never faces a silent or indefinite wait.
 */
@Composable
fun ArgentumStatusStrip(
    model: ArgentumStatusModel,
    onResume: () -> Unit,
    onReconnect: () -> Unit,
    onPrimary: () -> Unit,
    onNeedsHandling: () -> Unit,
    onLeave: () -> Unit,
    onOpenLog: () -> Unit = {},
    /** Compact table: phase, life and passing live elsewhere; show only problems, stack and the latest event. */
    compact: Boolean = false,
) {
    val (dot, headline, detail) = when (model.connection) {
        ArgentumConnectionStatus.CONNECTED -> Triple(Mint, "ENGINE CONNECTED", when {
            model.gameOver -> "Game over"
            model.awaitingUpdate -> "Waiting for Argentum to confirm…"
            model.gameActive -> listOfNotNull("Game active", model.turnLabel).joinToString(" · ")
            else -> "No game active"
        })
        ArgentumConnectionStatus.CONNECTING -> Triple(CounterGold, "CONNECTING", "Reaching the Argentum engine…")
        ArgentumConnectionStatus.DISCONNECTED -> Triple(WarnRed, "ENGINE DISCONNECTED", "Retrying automatically. The game continues on Argentum.")
        ArgentumConnectionStatus.PAUSED -> Triple(CounterGold, "PAUSED", "Connection closed while Arcana was in the background. Resume when you're ready.")
        ArgentumConnectionStatus.REPLACED -> Triple(WarnRed, "OPENED ELSEWHERE", "This Argentum session is active on another device or tab.")
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
            .padding(horizontal = 10.dp, vertical = 6.dp).testTag("argentum-status")
    ) {
        val healthy = model.connection == ArgentumConnectionStatus.CONNECTED && !model.awaitingUpdate
        if (!compact || !healthy || model.gameOver) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
            }
            when (model.connection) {
                ArgentumConnectionStatus.PAUSED -> StripButton("RESUME", Mint, onResume)
                ArgentumConnectionStatus.DISCONNECTED -> StripButton("RECONNECT", Mint, onReconnect)
                ArgentumConnectionStatus.REPLACED -> StripButton("LEAVE", WarnRed, onLeave)
                ArgentumConnectionStatus.CONNECTED -> if (!compact && model.priorityLabel != null && !model.awaitingUpdate && !model.gameOver) StripButton(model.priorityLabel, Mint, onPrimary)
                else -> Unit
            }
        }
        if (model.connection == ArgentumConnectionStatus.CONNECTED && model.gameActive) {
            if (!compact && model.stepKey.isNotBlank()) PhaseTracker(model.stepKey)
            if (!compact && model.seats.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.seats.forEach { SeatLifeChip(it, Modifier.weight(1f)) }
            }
            if (model.stack.isNotEmpty()) Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(horizontal = 8.dp, vertical = 6.dp).testTag("argentum-stack")
            ) {
                Text("ON THE STACK · ${model.stack.size}", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                model.stack.take(4).forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Text(if (i == 0) "▶" else "·", color = if (i == 0) Mint else Muted, fontSize = 10.sp, modifier = Modifier.width(14.dp))
                        Text(item.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(" · ${if (item.isYours) "you" else item.controllerName}" + if (item.targetNames.isNotEmpty()) " → ${item.targetNames.joinToString()}" else "",
                            color = Muted, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (model.stack.size > 4) Text("+${model.stack.size - 4} more", color = Muted, fontSize = 10.sp)
            }
            if (!compact && model.feed.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 6.dp).clickable(onClick = onOpenLog).testTag("argentum-feed")) {
                model.feed.take(if (compact) 1 else 2).forEachIndexed { i, line ->
                    Text(line.description, color = when { line.isYours == false -> CounterGold; i == 0 -> SoftWhite; else -> Muted },
                        fontSize = if (i == 0) 11.sp else 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        if (model.needsHandling != null) Row(
            Modifier.fillMaxWidth().padding(top = 5.dp).clip(RoundedCornerShape(8.dp)).background(CounterGold.copy(alpha = 0.16f))
                .clickable(onClick = onNeedsHandling).padding(horizontal = 8.dp, vertical = 5.dp).testTag("needs-handling"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NEEDS HANDLING", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(model.needsHandling, color = SoftWhite, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
            Text("OPEN", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StripButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).background(PanelLight)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp)
    )
}

/** Searchable on-device viewer for the diagnostic log, with category filters and export. */
@Composable
fun DiagnosticLogDialog(log: DiagnosticLog, revision: Int, onExport: (List<DiagnosticEntry>) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf(emptySet<DiagnosticCategory>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val results = remember(revision, query, categories) { log.search(query, categories).asReversed() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(10.dp), shape = RoundedCornerShape(16.dp), color = Ink) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DIAGNOSTIC LOG", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("CLOSE") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search events") }, modifier = Modifier.fillMaxWidth().testTag("diagnostic-search")
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category in categories,
                            onClick = { categories = if (category in categories) categories - category else categories + category },
                            label = { Text(category.label, fontSize = 11.sp) }
                        )
                    }
                }
                Text("${results.size} of ${log.entries.size} events · newest first · server addresses, credentials and IDs are redacted; private card details are omitted from exports",
                    color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
                if (results.isEmpty()) Text(if (log.entries.isEmpty()) "No events recorded yet." else "No events match this search.", color = Muted, modifier = Modifier.padding(top = 16.dp))
                SelectionContainer(Modifier.weight(1f).padding(top = 6.dp)) {
                    LazyColumn(Modifier.fillMaxSize().testTag("diagnostic-list")) {
                        items(results, key = { it.sequence }) { entry ->
                            val tint = when (entry.category) {
                                DiagnosticCategory.ERROR -> WarnRed
                                DiagnosticCategory.UNSUPPORTED -> CounterGold
                                DiagnosticCategory.CONNECTION -> Mint
                                else -> SoftWhite
                            }
                            Text(entry.displayLine(), color = tint, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmClear = true }, enabled = log.entries.isNotEmpty()) { Text("CLEAR", color = WarnRed) }
                    TextButton(onClick = { onExport(log.entries) }, enabled = log.entries.isNotEmpty()) { Text("EXPORT ALL") }
                    TextButton(onClick = { onExport(results.asReversed()) }, enabled = results.isNotEmpty() && (query.isNotBlank() || categories.isNotEmpty())) { Text("EXPORT FILTERED") }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear diagnostic log?") },
        text = { Text("This deletes the recorded events on this device. It does not affect any game.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("CLEAR", color = WarnRed) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } }
    )
}

/** Picks X for an Argentum action with an {X} cost, within the server's affordable range. */
@Composable
fun ArgentumXDialog(description: String, range: IntRange, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(range) { mutableIntStateOf(range.last) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose X") },
        text = {
            Column {
                Text(description, color = Muted, fontSize = 12.sp)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = value > range.first, onClick = { value-- }) { Text("−", fontSize = 22.sp) }
                    Text("X = $value", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 90.dp))
                    TextButton(enabled = value < range.last, onClick = { value++ }) { Text("+", fontSize = 22.sp) }
                }
                Text("Argentum allows ${range.first}–${range.last} with your available mana.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("CAST") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

/** Deck cards Argentum cannot run. Nothing is removed or replaced until the player chooses. */
data class ArgentumDeckReview(
    val unsupported: List<String>,
    val commanderUnsupported: Boolean,
    val commander: String,
    val vsAi: Boolean,
    val joinCode: String?,
)

@Composable
fun ArgentumDeckReviewDialog(review: ArgentumDeckReview, onPlayWithout: () -> Unit, onSandbox: () -> Unit, onEditDeck: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Cards need handling") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Argentum's engine can't run ${review.unsupported.size} card(s) in this deck yet. Nothing has been changed.", color = SoftWhite, fontSize = 13.sp)
                if (review.commanderUnsupported) Text("Your commander, ${review.commander}, is one of them.", color = CounterGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(10.dp)) {
                    review.unsupported.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("NEEDS HANDLING", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text(name, color = SoftWhite, fontSize = 12.sp)
                        }
                    }
                }
                Text("Choose how to continue:", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Text("• Play on Argentum without them: the unsupported cards are left out of this game only and Argentum-supported basic lands fill the gap" +
                    (if (review.commanderUnsupported) ". A supported legendary creature from your deck becomes commander for this game." else ".") +
                    " Your saved deck stays unchanged.", color = Muted, fontSize = 11.sp)
                Text("• Arcana sandbox: play the full deck on Arcana's manual table. Rules are not enforced by Argentum; use the table tools to resolve cards yourself.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onPlayWithout) { Text("PLAY ON ARGENTUM WITHOUT THEM") }
                TextButton(onClick = onSandbox) { Text("PLAY FULL DECK IN SANDBOX") }
                Row {
                    TextButton(onClick = onEditDeck) { Text("EDIT DECK") }
                    TextButton(onClick = onCancel) { Text("CANCEL") }
                }
            }
        }
    )
}

/** Reorder editor for OrderObjectsDecision / ReorderLibraryDecision. */
@Composable
fun ArgentumOrderEditor(items: List<String>, nameOf: (String) -> String, firstLabel: String?, lastLabel: String?, onChange: (List<String>) -> Unit) {
    Column {
        if (firstLabel != null) Text("Top / first: $firstLabel", color = Muted, fontSize = 10.sp)
        items.forEachIndexed { index, id ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", color = Muted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                Text(nameOf(id), color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = index > 0, onClick = { onChange(items.toMutableList().apply { add(index - 1, removeAt(index)) }) }) { Text("↑") }
                TextButton(enabled = index < items.lastIndex, onClick = { onChange(items.toMutableList().apply { add(index + 1, removeAt(index)) }) }) { Text("↓") }
            }
        }
        if (lastLabel != null) Text("Bottom / last: $lastLabel", color = Muted, fontSize = 10.sp)
    }
}

/** +/- editor for DistributeDecision and AssignDamageDecision amounts. */
@Composable
fun ArgentumAmountEditor(ids: List<String>, nameOf: (String) -> String, amounts: Map<String, Int>, total: Int, minEach: Map<String, Int>, maxEach: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    val used = amounts.values.sum()
    Column {
        Text("Assigned $used of $total", color = if (used == total) Mint else CounterGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ids.forEach { id ->
            val value = amounts[id] ?: 0
            val min = minEach[id] ?: 0
            val max = maxEach[id] ?: total
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(nameOf(id), color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = value > min, onClick = { onChange(amounts + (id to value - 1)) }) { Text("−") }
                Text("$value", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 26.dp))
                TextButton(enabled = value < max && used < total, onClick = { onChange(amounts + (id to value + 1)) }) { Text("+") }
            }
        }
    }
}

/**
 * Combat damage board for Argentum's CombatResolutionDecision. Starts from Argentum's default
 * amounts (so CONFIRM is a one-tap "use the engine's assignment"), lets the player move damage
 * between the edges they own, and shows lethal hints.
 */
@Composable
fun ArgentumCombatDamageEditor(resolution: ArgentumCombatResolution, playerId: String, amounts: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    val mine = resolution.editableBy(playerId)
    Column {
        Text(if (resolution.firstStrike) "First-strike damage" else "Combat damage", color = Muted, fontSize = 11.sp)
        if (mine.isEmpty()) Text("Nothing for you to adjust. Confirm to let Argentum apply combat damage.", color = SoftWhite, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        mine.groupBy { it.sourceId }.forEach { (source, edges) ->
            val left = resolution.remaining(source, amounts)
            Text("${resolution.names[source] ?: "Creature"} · ${edges.maxOf { it.maximum }} damage" + if (left > 0) " · $left unassigned" else "",
                color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            edges.forEach { edge ->
                val value = amounts[edge.id] ?: edge.defaultAmount
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("→ ${resolution.names[edge.targetId] ?: "Target"}", color = SoftWhite, fontSize = 12.sp)
                        val hint = when {
                            edge.isTrampleDrain -> "trample"
                            edge.direction == "ATTACKER_TO_BLOCKER" || edge.direction == "BLOCKER_TO_ATTACKER" -> "lethal ${edge.lethal}" + if (value >= edge.lethal) " ✓" else ""
                            else -> null
                        }
                        if (hint != null) Text(hint, color = if (value >= edge.lethal && !edge.isTrampleDrain) Mint else Muted, fontSize = 10.sp)
                    }
                    TextButton(enabled = value > 0, onClick = { onChange(amounts + (edge.id to value - 1)) }) { Text("−") }
                    Text("$value", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 26.dp))
                    TextButton(enabled = value < edge.maximum && left > 0, onClick = { onChange(amounts + (edge.id to value + 1)) }) { Text("+") }
                }
            }
        }
        resolution.problem(amounts)?.let { Text(it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

private val ManaColorTint = mapOf(
    "WHITE" to Color(0xFFF3EAD2), "BLUE" to Color(0xFF4E93C8), "BLACK" to Color(0xFF8A8390),
    "RED" to Color(0xFFD9645F), "GREEN" to Color(0xFF5FAE74), "COLORLESS" to Color(0xFFB8C4BE),
)

/**
 * One dialog for every input a legal action can need: X, any number of target slots, divided
 * damage and a mana colour. Choices come only from the server's LegalActionInfo.
 */
@Composable
fun ArgentumActionComposer(plan: ArgentumActionPlan, nameOf: (String) -> String, busy: Boolean, onConfirm: (picks: Map<Int, List<String>>, x: Int?, distribution: Map<String, Int>, color: String?) -> Unit, onDismiss: () -> Unit) {
    var picks by remember(plan) { mutableStateOf(emptyMap<Int, List<String>>()) }
    var x by remember(plan) { mutableIntStateOf(plan.xRange?.last ?: 0) }
    var distribution by remember(plan) { mutableStateOf(emptyMap<String, Int>()) }
    var color by remember(plan) { mutableStateOf(plan.manaColors.singleOrNull()) }
    val chosen = plan.chosenTargets(picks)
    // Keep the division in step with the chosen targets: spread the total as evenly as allowed.
    androidx.compose.runtime.LaunchedEffect(chosen, plan.distributeTotal) {
        val total = plan.distributeTotal ?: return@LaunchedEffect
        if (chosen.isEmpty()) { distribution = emptyMap(); return@LaunchedEffect }
        if (chosen.toSet() == distribution.keys && distribution.values.sum() == total) return@LaunchedEffect
        val base = total / chosen.size
        distribution = chosen.mapIndexed { i, id -> id to base + if (i < total % chosen.size) 1 else 0 }.toMap()
    }
    val problem = plan.problem(picks, distribution, color)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.description) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                plan.xRange?.let { range ->
                    Text("X", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = x > range.first, onClick = { x-- }) { Text("−", fontSize = 20.sp) }
                        Text("$x", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 40.dp))
                        TextButton(enabled = x < range.last, onClick = { x++ }) { Text("+", fontSize = 20.sp) }
                        Text("up to ${range.last}", color = Muted, fontSize = 10.sp)
                    }
                }
                plan.slots.forEach { slot ->
                    val mine = picks[slot.index].orEmpty()
                    val legal = plan.legalFor(slot, picks)
                    Text(slot.description, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(if (slot.min == slot.max) "Choose ${slot.max}" else "Choose ${slot.min}–${slot.max}", color = Muted, fontSize = 10.sp)
                    if (legal.isEmpty()) Text("No legal choices right now.", color = Muted, fontSize = 12.sp)
                    legal.forEach { id ->
                        val on = id in mine
                        val toggle = {
                            picks = picks + (slot.index to when {
                                on -> mine - id
                                slot.max == 1 -> listOf(id)
                                mine.size < slot.max -> mine + id
                                else -> mine
                            })
                            // Later slots may depend on this pick; drop picks that are no longer legal.
                            picks = plan.slots.filter { it.index > slot.index }.fold(picks) { acc, later ->
                                acc + (later.index to acc[later.index].orEmpty().filter { it in plan.legalFor(later, acc) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (slot.max == 1) RadioButton(selected = on, onClick = toggle) else Checkbox(checked = on, onCheckedChange = { toggle() })
                            Text(nameOf(id), color = SoftWhite, fontSize = 13.sp)
                        }
                    }
                }
                plan.distributeTotal?.let { total ->
                    Text("Divide $total", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (chosen.isEmpty()) Text("Choose targets first.", color = Muted, fontSize = 11.sp)
                    else ArgentumAmountEditor(chosen, nameOf, distribution, total, chosen.associateWith { plan.distributeMin }, emptyMap()) { distribution = it }
                }
                if (plan.manaColors.isNotEmpty()) {
                    Text("Mana colour", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        plan.manaColors.forEach { c ->
                            FilterChip(selected = color == c, onClick = { color = c }, label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(ManaColorTint[c] ?: Muted))
                                    Spacer(Modifier.width(5.dp)); Text(c.lowercase().replaceFirstChar(Char::uppercase), fontSize = 11.sp)
                                }
                            })
                        }
                    }
                }
                problem?.let { Text(it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                Text("Argentum checks legality, costs and payment when you confirm.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(enabled = !busy && problem == null, onClick = { onConfirm(picks, plan.xRange?.let { x }, distribution, color) }) { Text("CONFIRM") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

/** SplitPilesDecision: put each card into a pile (or leave it out when allowed). */
@Composable
fun ArgentumPileEditor(split: ArgentumPileSplit, nameOf: (String) -> String, assign: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    Column {
        split.cards.forEach { card ->
            Text(nameOf(card), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (split.allowUnassigned) FilterChip(selected = (assign[card] ?: -1) < 0, onClick = { onChange(assign + (card to -1)) }, label = { Text("None", fontSize = 11.sp) })
                split.allowedPiles(card).forEach { p ->
                    FilterChip(selected = assign[card] == p, onClick = { onChange(assign + (card to p)) }, label = { Text(split.labels[p], fontSize = 11.sp) })
                }
            }
        }
        split.problem(assign)?.let { Text(it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** ChooseReplacementDecision: which effect applies, and how. */
@Composable
fun ArgentumReplacementEditor(choice: ArgentumReplacementChoice, fromIndex: Int, toIndex: Int, onChange: (Int, Int) -> Unit) {
    Column {
        if (choice.from.size > 1) Text("Apply", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        if (choice.from.size > 1) choice.from.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().clickable { onChange(i, choice.allowedTo(i).firstOrNull() ?: 0) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = fromIndex == i, onClick = { onChange(i, choice.allowedTo(i).firstOrNull() ?: 0) }); Text(label, color = SoftWhite, fontSize = 12.sp)
            }
        }
        Text("Result", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
        choice.allowedTo(fromIndex).forEach { i ->
            Row(Modifier.fillMaxWidth().clickable { onChange(fromIndex, i) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = toIndex == i, onClick = { onChange(fromIndex, i) }); Text(choice.to.getOrNull(i).orEmpty(), color = SoftWhite, fontSize = 12.sp)
            }
        }
    }
}

/** BudgetModalDecision: buy modes (repeats allowed) within the budget. */
@Composable
fun ArgentumBudgetEditor(budget: ArgentumBudgetModes, counts: Map<Int, Int>, onChange: (Map<Int, Int>) -> Unit) {
    val spent = budget.spent(counts)
    Column {
        Text("Spent $spent of ${budget.budget}", color = if (spent <= budget.budget) Mint else CounterGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        budget.modes.forEachIndexed { i, (cost, text) ->
            val n = counts[i] ?: 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text, color = SoftWhite, fontSize = 12.sp)
                    Text("cost $cost", color = Muted, fontSize = 10.sp)
                }
                TextButton(enabled = n > 0, onClick = { onChange(counts + (i to n - 1)) }) { Text("−") }
                Text("$n", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp))
                TextButton(enabled = spent + cost <= budget.budget, onClick = { onChange(counts + (i to n + 1)) }) { Text("+") }
            }
        }
    }
}
