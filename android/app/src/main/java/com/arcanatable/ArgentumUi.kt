package com.arcanatable

import androidx.compose.runtime.CompositionLocalProvider

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
            StudioText("ArgentumUi.Text.1", "Table and player", true,
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
            .studioClickable("ArgentumUi.clickable.1", "Table and player", onClick = onClick).padding(end = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(40.dp).fillMaxHeight().background(Panel)) {
            if (!artUrl.isNullOrBlank()) CardArt(artUrl, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, label = artLabel)
        }
        Spacer(Modifier.width(7.dp))
        Column {
            StudioText("ArgentumUi.Text.2", "Table and player", true, if (seat.isYou) "YOU" else seat.name.uppercase(), color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 420) 84.dp else 130.dp))
            // Whose turn it is, in words, in that player's colour.
            if (turnLabel != null) StudioText("ArgentumUi.Text.3", "Table and player", true, turnLabel, color = TurnColors.forSeat(seat.isYou), fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.testTag("seat-turn-label"))
        }
        Spacer(Modifier.width(7.dp))
        StudioText("ArgentumUi.Text.4", "Table and player", true, "${seat.life}", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Black)
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
        StudioText("ArgentumUi.Text.5", "Table and player", true, if (seat.isYou) "YOU" else seat.name, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        StudioText("ArgentumUi.Text.6", "Table and player", true, "${seat.life}", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black)
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
                StudioText("ArgentumUi.Text.7", "Table and player", true, headline, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                StudioText("ArgentumUi.Text.8", "Table and player", true, detail, color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
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
                StudioText("ArgentumUi.Text.9", "Table and player", true, "ON THE STACK · ${model.stack.size}", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                model.stack.take(4).forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        StudioText("ArgentumUi.Text.10", "Table and player", true, if (i == 0) "▶" else "·", color = if (i == 0) Mint else Muted, fontSize = 10.sp, modifier = Modifier.width(14.dp))
                        StudioText("ArgentumUi.Text.11", "Table and player", true, item.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        StudioText("ArgentumUi.Text.12", "Table and player", true, " · ${if (item.isYours) "you" else item.controllerName}" + if (item.targetNames.isNotEmpty()) " → ${item.targetNames.joinToString()}" else "",
                            color = Muted, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (model.stack.size > 4) StudioText("ArgentumUi.Text.13", "Table and player", true, "+${model.stack.size - 4} more", color = Muted, fontSize = 10.sp)
            }
            if (!compact && model.feed.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 6.dp).studioClickable("ArgentumUi.clickable.2", "Table and player", onClick = onOpenLog).testTag("argentum-feed")) {
                model.feed.take(if (compact) 1 else 2).forEachIndexed { i, line ->
                    StudioText("ArgentumUi.Text.14", "Table and player", true, line.description, color = when { line.isYours == false -> CounterGold; i == 0 -> SoftWhite; else -> Muted },
                        fontSize = if (i == 0) 11.sp else 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        if (model.needsHandling != null) Row(
            Modifier.fillMaxWidth().padding(top = 5.dp).clip(RoundedCornerShape(8.dp)).background(CounterGold.copy(alpha = 0.16f))
                .studioClickable("ArgentumUi.clickable.3", "Table and player", onClick = onNeedsHandling).padding(horizontal = 8.dp, vertical = 5.dp).testTag("needs-handling"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioText("ArgentumUi.Text.15", "Table and player", false, "NEEDS HANDLING", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            StudioText("ArgentumUi.Text.16", "Table and player", true, model.needsHandling, color = SoftWhite, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
            StudioText("ArgentumUi.Text.17", "Table and player", false, "OPEN", color = CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StripButton(label: String, color: Color, onClick: () -> Unit) {
    val studio=studioWidget("ArgentumUi.StripButton", "Table and player", "action", label, true, onClick=onClick)
    if(studio?.style?.visible==false){StudioNativeCopies(studio);return}
    val studioClick:()->Unit={studio?.click() ?: onClick()}
    CompositionLocalProvider(LocalStudioControlParent provides studio?.parent(true)) {
    StudioText("ArgentumUi.Text.18", "Table and player", true,
        label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.studioWidget(studio).heightIn(min = 44.dp).clip(studio?.style?.radius?.let{RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: (RoundedCornerShape(8.dp))).background(studio?.style?.fill?.let(::parseTableColorHex) ?: (PanelLight))
            .studioClickable("ArgentumUi.clickable.4", "Table and player", onClick = studioClick).padding(horizontal = 12.dp, vertical = 13.dp)
    )

    }
    StudioNativeCopies(studio)
}

/** Searchable on-device viewer for the diagnostic log, with category filters and export. */
@Composable
fun DiagnosticLogDialog(log: DiagnosticLog, revision: Int, onExport: (List<DiagnosticEntry>) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf(emptySet<DiagnosticCategory>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val results = remember(revision, query, categories) { log.search(query, categories).asReversed() }
    StudioDialog("Argentum Ui 1", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(10.dp), shape = RoundedCornerShape(16.dp), color = Ink) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("ArgentumUi.Text.19", "Table and player", false, "DIAGNOSTIC LOG", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    StudioTextButton("ArgentumUi.TextButton.1", "Table and player", onClick = onDismiss) { StudioText("ArgentumUi.Text.20", "Table and player", false, "CLOSE") }
                }
                StudioOutlinedTextField("ArgentumUi.OutlinedTextField.1", "Table and player",
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { StudioText("ArgentumUi.Text.21", "Table and player", false, "Search events") }, modifier = Modifier.fillMaxWidth().testTag("diagnostic-search")
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticCategory.entries.forEach { category ->
                        StudioFilterChip("ArgentumUi.FilterChip.1", "Table and player",
                            selected = category in categories,
                            onClick = { categories = if (category in categories) categories - category else categories + category },
                            label = { StudioText("ArgentumUi.Text.22", "Table and player", true, category.label, fontSize = 11.sp) }
                        )
                    }
                }
                StudioText("ArgentumUi.Text.23", "Table and player", true, "${results.size} of ${log.entries.size} events · newest first · server addresses, credentials and IDs are redacted; private card details are omitted from exports",
                    color = Muted, fontSize = 10.sp, lineHeight = 12.sp)
                if (results.isEmpty()) StudioText("ArgentumUi.Text.24", "Table and player", true, if (log.entries.isEmpty()) "No events recorded yet." else "No events match this search.", color = Muted, modifier = Modifier.padding(top = 16.dp))
                SelectionContainer(Modifier.weight(1f).padding(top = 6.dp)) {
                    LazyColumn(Modifier.fillMaxSize().testTag("diagnostic-list")) {
                        items(results, key = { it.sequence }) { entry ->
                            val tint = when (entry.category) {
                                DiagnosticCategory.ERROR -> WarnRed
                                DiagnosticCategory.UNSUPPORTED -> CounterGold
                                DiagnosticCategory.CONNECTION -> Mint
                                else -> SoftWhite
                            }
                            StudioText("ArgentumUi.Text.25", "Table and player", true, entry.displayLine(), color = tint, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StudioTextButton("ArgentumUi.TextButton.2", "Table and player", onClick = { confirmClear = true }, enabled = log.entries.isNotEmpty()) { StudioText("ArgentumUi.Text.26", "Table and player", false, "CLEAR", color = WarnRed) }
                    StudioTextButton("ArgentumUi.TextButton.3", "Table and player", onClick = { onExport(log.entries) }, enabled = log.entries.isNotEmpty()) { StudioText("ArgentumUi.Text.27", "Table and player", false, "EXPORT ALL") }
                    StudioTextButton("ArgentumUi.TextButton.4", "Table and player", onClick = { onExport(results.asReversed()) }, enabled = results.isNotEmpty() && (query.isNotBlank() || categories.isNotEmpty())) { StudioText("ArgentumUi.Text.28", "Table and player", false, "EXPORT FILTERED") }
                }
            }
        }
    }
    if (confirmClear) StudioAlertDialog("Argentum Ui 2",
        onDismissRequest = { confirmClear = false },
        title = { StudioText("ArgentumUi.Text.29", "Table and player", false, "Clear diagnostic log?") },
        text = { StudioText("ArgentumUi.Text.30", "Table and player", false, "This deletes the recorded events on this device. It does not affect any game.") },
        confirmButton = { StudioTextButton("ArgentumUi.TextButton.5", "Table and player", onClick = { confirmClear = false; onClear() }) { StudioText("ArgentumUi.Text.31", "Table and player", false, "CLEAR", color = WarnRed) } },
        dismissButton = { StudioTextButton("ArgentumUi.TextButton.6", "Table and player", onClick = { confirmClear = false }) { StudioText("ArgentumUi.Text.32", "Table and player", false, "CANCEL") } }
    )
}

/** Picks X for an Argentum action with an {X} cost, within the server's affordable range. */
@Composable
fun ArgentumXDialog(description: String, range: IntRange, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(range) { mutableIntStateOf(range.last) }
    StudioAlertDialog("Argentum Ui 3",
        onDismissRequest = onDismiss,
        title = { StudioText("ArgentumUi.Text.33", "Table and player", false, "Choose X") },
        text = {
            Column {
                StudioText("ArgentumUi.Text.34", "Table and player", true, description, color = Muted, fontSize = 12.sp)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioTextButton("ArgentumUi.TextButton.7", "Table and player", enabled = value > range.first, onClick = { value-- }) { StudioText("ArgentumUi.Text.35", "Table and player", false, "−", fontSize = 22.sp) }
                    StudioText("ArgentumUi.Text.36", "Table and player", true, "X = $value", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 90.dp))
                    StudioTextButton("ArgentumUi.TextButton.8", "Table and player", enabled = value < range.last, onClick = { value++ }) { StudioText("ArgentumUi.Text.37", "Table and player", false, "+", fontSize = 22.sp) }
                }
                StudioText("ArgentumUi.Text.38", "Table and player", true, "Argentum allows ${range.first}–${range.last} with your available mana.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { StudioTextButton("ArgentumUi.TextButton.9", "Table and player", onClick = { onConfirm(value) }) { StudioText("ArgentumUi.Text.39", "Table and player", false, "CAST") } },
        dismissButton = { StudioTextButton("ArgentumUi.TextButton.10", "Table and player", onClick = onDismiss) { StudioText("ArgentumUi.Text.40", "Table and player", false, "CANCEL") } }
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
    StudioAlertDialog("Argentum Ui 4",
        onDismissRequest = onCancel,
        title = { StudioText("ArgentumUi.Text.41", "Table and player", false, "Cards need handling") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                StudioText("ArgentumUi.Text.42", "Table and player", true, "Argentum's engine can't run ${review.unsupported.size} card(s) in this deck yet. Nothing has been changed.", color = SoftWhite, fontSize = 13.sp)
                if (review.commanderUnsupported) StudioText("ArgentumUi.Text.43", "Table and player", true, "Your commander, ${review.commander}, is one of them.", color = CounterGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(10.dp)) {
                    review.unsupported.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            StudioText("ArgentumUi.Text.44", "Table and player", false, "NEEDS HANDLING", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            StudioText("ArgentumUi.Text.45", "Table and player", true, name, color = SoftWhite, fontSize = 12.sp)
                        }
                    }
                }
                StudioText("ArgentumUi.Text.46", "Table and player", false, "Choose how to continue:", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                StudioText("ArgentumUi.Text.47", "Table and player", false, "• Play on Argentum without them: the unsupported cards are left out of this game only and Argentum-supported basic lands fill the gap" +
                    (if (review.commanderUnsupported) ". A supported legendary creature from your deck becomes commander for this game." else ".") +
                    " Your saved deck stays unchanged.", color = Muted, fontSize = 11.sp)
                StudioText("ArgentumUi.Text.48", "Table and player", false, "• Arcana sandbox: play the full deck on Arcana's manual table. Rules are not enforced by Argentum; use the table tools to resolve cards yourself.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                StudioTextButton("ArgentumUi.TextButton.11", "Table and player", onClick = onPlayWithout) { StudioText("ArgentumUi.Text.49", "Table and player", false, "PLAY ON ARGENTUM WITHOUT THEM") }
                StudioTextButton("ArgentumUi.TextButton.12", "Table and player", onClick = onSandbox) { StudioText("ArgentumUi.Text.50", "Table and player", false, "PLAY FULL DECK IN SANDBOX") }
                Row {
                    StudioTextButton("ArgentumUi.TextButton.13", "Table and player", onClick = onEditDeck) { StudioText("ArgentumUi.Text.51", "Table and player", false, "EDIT DECK") }
                    StudioTextButton("ArgentumUi.TextButton.14", "Table and player", onClick = onCancel) { StudioText("ArgentumUi.Text.52", "Table and player", false, "CANCEL") }
                }
            }
        }
    )
}

/** Reorder editor for OrderObjectsDecision / ReorderLibraryDecision. */
@Composable
fun ArgentumOrderEditor(items: List<String>, nameOf: (String) -> String, firstLabel: String?, lastLabel: String?, onChange: (List<String>) -> Unit) {
    Column {
        if (firstLabel != null) StudioText("ArgentumUi.Text.53", "Table and player", true, "Top / first: $firstLabel", color = Muted, fontSize = 10.sp)
        items.forEachIndexed { index, id ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioText("ArgentumUi.Text.54", "Table and player", true, "${index + 1}.", color = Muted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                StudioText("ArgentumUi.Text.55", "Table and player", true, nameOf(id), color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                StudioTextButton("ArgentumUi.TextButton.15", "Table and player", enabled = index > 0, onClick = { onChange(items.toMutableList().apply { add(index - 1, removeAt(index)) }) }) { StudioText("ArgentumUi.Text.56", "Table and player", false, "↑") }
                StudioTextButton("ArgentumUi.TextButton.16", "Table and player", enabled = index < items.lastIndex, onClick = { onChange(items.toMutableList().apply { add(index + 1, removeAt(index)) }) }) { StudioText("ArgentumUi.Text.57", "Table and player", false, "↓") }
            }
        }
        if (lastLabel != null) StudioText("ArgentumUi.Text.58", "Table and player", true, "Bottom / last: $lastLabel", color = Muted, fontSize = 10.sp)
    }
}

/** +/- editor for DistributeDecision and AssignDamageDecision amounts. */
@Composable
fun ArgentumAmountEditor(ids: List<String>, nameOf: (String) -> String, amounts: Map<String, Int>, total: Int, minEach: Map<String, Int>, maxEach: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    val used = amounts.values.sum()
    Column {
        StudioText("ArgentumUi.Text.59", "Table and player", true, "Assigned $used of $total", color = if (used == total) Mint else CounterGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ids.forEach { id ->
            val value = amounts[id] ?: 0
            val min = minEach[id] ?: 0
            val max = maxEach[id] ?: total
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StudioText("ArgentumUi.Text.60", "Table and player", true, nameOf(id), color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                StudioTextButton("ArgentumUi.TextButton.17", "Table and player", enabled = value > min, onClick = { onChange(amounts + (id to value - 1)) }) { StudioText("ArgentumUi.Text.61", "Table and player", false, "−") }
                StudioText("ArgentumUi.Text.62", "Table and player", true, "$value", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 26.dp))
                StudioTextButton("ArgentumUi.TextButton.18", "Table and player", enabled = value < max && used < total, onClick = { onChange(amounts + (id to value + 1)) }) { StudioText("ArgentumUi.Text.63", "Table and player", false, "+") }
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
        StudioText("ArgentumUi.Text.64", "Table and player", true, if (resolution.firstStrike) "First-strike damage" else "Combat damage", color = Muted, fontSize = 11.sp)
        if (mine.isEmpty()) StudioText("ArgentumUi.Text.65", "Table and player", false, "Nothing for you to adjust. Confirm to let Argentum apply combat damage.", color = SoftWhite, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        mine.groupBy { it.sourceId }.forEach { (source, edges) ->
            val left = resolution.remaining(source, amounts)
            StudioText("ArgentumUi.Text.66", "Table and player", true, "${resolution.names[source] ?: "Creature"} · ${edges.maxOf { it.maximum }} damage" + if (left > 0) " · $left unassigned" else "",
                color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            edges.forEach { edge ->
                val value = amounts[edge.id] ?: edge.defaultAmount
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StudioText("ArgentumUi.Text.67", "Table and player", true, "→ ${resolution.names[edge.targetId] ?: "Target"}", color = SoftWhite, fontSize = 12.sp)
                        val hint = when {
                            edge.isTrampleDrain -> "trample"
                            edge.direction == "ATTACKER_TO_BLOCKER" || edge.direction == "BLOCKER_TO_ATTACKER" -> "lethal ${edge.lethal}" + if (value >= edge.lethal) " ✓" else ""
                            else -> null
                        }
                        if (hint != null) StudioText("ArgentumUi.Text.68", "Table and player", true, hint, color = if (value >= edge.lethal && !edge.isTrampleDrain) Mint else Muted, fontSize = 10.sp)
                    }
                    StudioTextButton("ArgentumUi.TextButton.19", "Table and player", enabled = value > 0, onClick = { onChange(amounts + (edge.id to value - 1)) }) { StudioText("ArgentumUi.Text.69", "Table and player", false, "−") }
                    StudioText("ArgentumUi.Text.70", "Table and player", true, "$value", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 26.dp))
                    StudioTextButton("ArgentumUi.TextButton.20", "Table and player", enabled = value < edge.maximum && left > 0, onClick = { onChange(amounts + (edge.id to value + 1)) }) { StudioText("ArgentumUi.Text.71", "Table and player", false, "+") }
                }
            }
        }
        resolution.problem(amounts)?.let { StudioText("ArgentumUi.Text.72", "Table and player", true, it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
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
fun ArgentumActionComposer(plan: ArgentumActionPlan, nameOf: (String) -> String, busy: Boolean, onConfirm: (picks: Map<Int, List<String>>, x: Int?, distribution: Map<String, Int>, color: String?, removal: Map<String, Int>, crew: List<String>) -> Unit, onDismiss: () -> Unit) {
    var picks by remember(plan) { mutableStateOf(emptyMap<Int, List<String>>()) }
    var x by remember(plan) { mutableIntStateOf(plan.xRange?.last ?: 0) }
    var distribution by remember(plan) { mutableStateOf(emptyMap<String, Int>()) }
    var color by remember(plan) { mutableStateOf(plan.manaColors.singleOrNull()) }
    // Counters to remove: start from a complete split so one tap on CONFIRM works.
    var removal by remember(plan) { mutableStateOf(plan.counterRemoval?.initial().orEmpty()) }
    // Crew: start from the lightest choice that's enough, so one tap on CONFIRM works.
    var crew by remember(plan) { mutableStateOf(plan.crew?.initial().orEmpty()) }
    val chosen = plan.chosenTargets(picks)
    // Keep the division in step with the chosen targets: spread the total as evenly as allowed.
    androidx.compose.runtime.LaunchedEffect(chosen, plan.distributeTotal) {
        val total = plan.distributeTotal ?: return@LaunchedEffect
        if (chosen.isEmpty()) { distribution = emptyMap(); return@LaunchedEffect }
        if (chosen.toSet() == distribution.keys && distribution.values.sum() == total) return@LaunchedEffect
        val base = total / chosen.size
        distribution = chosen.mapIndexed { i, id -> id to base + if (i < total % chosen.size) 1 else 0 }.toMap()
    }
    val problem = plan.problem(picks, distribution, color, removal, crew)
    StudioAlertDialog("Argentum Ui 5",
        onDismissRequest = onDismiss,
        title = { StudioText("ArgentumUi.Text.73", "Table and player", true, plan.description) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                plan.xRange?.let { range ->
                    NumberPicker(x, range, onChange = { x = it }, label = "X")
                }
                plan.slots.forEach { slot ->
                    val mine = picks[slot.index].orEmpty()
                    val legal = plan.legalFor(slot, picks)
                    StudioText("ArgentumUi.Text.74", "Table and player", true, slot.description, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    StudioText("ArgentumUi.Text.75", "Table and player", true, if (slot.min == slot.max) "Choose ${slot.max}" else "Choose ${slot.min}–${slot.max}", color = Muted, fontSize = 10.sp)
                    if (legal.isEmpty()) StudioText("ArgentumUi.Text.76", "Table and player", false, "No legal choices right now.", color = Muted, fontSize = 12.sp)
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
                        Row(Modifier.fillMaxWidth().studioClickable("ArgentumUi.clickable.5", "Table and player", onClick = toggle).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (slot.max == 1) StudioRadioButton("ArgentumUi.RadioButton.1", "Table and player", selected = on, onClick = toggle) else StudioCheckbox("ArgentumUi.Checkbox.1", "Table and player", checked = on, onCheckedChange = { toggle() })
                            StudioText("ArgentumUi.Text.77", "Table and player", true, nameOf(id), color = SoftWhite, fontSize = 13.sp)
                        }
                    }
                }
                plan.distributeTotal?.let { total ->
                    StudioText("ArgentumUi.Text.78", "Table and player", true, "Divide $total", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (chosen.isEmpty()) StudioText("ArgentumUi.Text.79", "Table and player", false, "Choose targets first.", color = Muted, fontSize = 11.sp)
                    else ArgentumAmountEditor(chosen, nameOf, distribution, total, chosen.associateWith { plan.distributeMin }, emptyMap()) { distribution = it }
                }
                plan.counterRemoval?.let { cr ->
                    StudioText("ArgentumUi.Text.80", "Table and player", true, "Cost: remove ${cr.total} counter${if (cr.total == 1) "" else "s"}", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    StudioText("ArgentumUi.Text.81", "Table and player", false, "Choose which permanents give them up.", color = Muted, fontSize = 10.sp)
                    ArgentumAmountEditor(cr.sources.keys.toList(), { id -> "${nameOf(id)} (${cr.sources[id]})" }, removal, cr.total, emptyMap(), cr.sources) { removal = it }
                }
                plan.crew?.let { c ->
                    StudioText("ArgentumUi.Text.82", "Table and player", true, "Crew ${c.required}: tap creatures with total power ${c.required} or more", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    StudioText("ArgentumUi.Text.83", "Table and player", true, "Chosen power ${c.total(crew)} of ${c.required}", color = if (c.total(crew) >= c.required) Mint else CounterGold, fontSize = 11.sp)
                    c.power.forEach { (id, power) ->
                        val on = id in crew
                        val toggle = { crew = if (on) crew - id else crew + id }
                        Row(Modifier.fillMaxWidth().studioClickable("ArgentumUi.clickable.6", "Table and player", onClick = toggle).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioCheckbox("ArgentumUi.Checkbox.2", "Table and player", checked = on, onCheckedChange = { toggle() })
                            StudioText("ArgentumUi.Text.84", "Table and player", true, "${nameOf(id)} (power $power)${if (id in c.canAttack) " · could attack" else ""}", color = SoftWhite, fontSize = 13.sp)
                        }
                    }
                }
                if (plan.manaColors.isNotEmpty()) {
                    StudioText("ArgentumUi.Text.85", "Table and player", false, "Mana colour", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        plan.manaColors.forEach { c ->
                            StudioFilterChip("ArgentumUi.FilterChip.2", "Table and player", selected = color == c, onClick = { color = c }, label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(ManaColorTint[c] ?: Muted))
                                    Spacer(Modifier.width(5.dp)); StudioText("ArgentumUi.Text.86", "Table and player", true, c.lowercase().replaceFirstChar(Char::uppercase), fontSize = 11.sp)
                                }
                            })
                        }
                    }
                }
                problem?.let { StudioText("ArgentumUi.Text.87", "Table and player", true, it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                StudioText("ArgentumUi.Text.88", "Table and player", false, "Argentum checks legality, costs and payment when you confirm.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { StudioTextButton("ArgentumUi.TextButton.21", "Table and player", enabled = !busy && problem == null, onClick = { onConfirm(picks, plan.xRange?.let { x }, distribution, color, removal, crew) }) { StudioText("ArgentumUi.Text.89", "Table and player", false, "CONFIRM") } },
        dismissButton = { StudioTextButton("ArgentumUi.TextButton.22", "Table and player", onClick = onDismiss) { StudioText("ArgentumUi.Text.90", "Table and player", false, "CANCEL") } }
    )
}

/** SplitPilesDecision: put each card into a pile (or leave it out when allowed). */
@Composable
fun ArgentumPileEditor(split: ArgentumPileSplit, nameOf: (String) -> String, assign: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    Column {
        split.cards.forEach { card ->
            StudioText("ArgentumUi.Text.91", "Table and player", true, nameOf(card), color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (split.allowUnassigned) StudioFilterChip("ArgentumUi.FilterChip.3", "Table and player", selected = (assign[card] ?: -1) < 0, onClick = { onChange(assign + (card to -1)) }, label = { StudioText("ArgentumUi.Text.92", "Table and player", false, "None", fontSize = 11.sp) })
                split.allowedPiles(card).forEach { p ->
                    StudioFilterChip("ArgentumUi.FilterChip.4", "Table and player", selected = assign[card] == p, onClick = { onChange(assign + (card to p)) }, label = { StudioText("ArgentumUi.Text.93", "Table and player", true, split.labels[p], fontSize = 11.sp) })
                }
            }
        }
        split.problem(assign)?.let { StudioText("ArgentumUi.Text.94", "Table and player", true, it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** ChooseReplacementDecision: which effect applies, and how. */
@Composable
fun ArgentumReplacementEditor(choice: ArgentumReplacementChoice, fromIndex: Int, toIndex: Int, onChange: (Int, Int) -> Unit) {
    Column {
        if (choice.from.size > 1) StudioText("ArgentumUi.Text.95", "Table and player", false, "Apply", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        if (choice.from.size > 1) choice.from.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().clickable { onChange(i, choice.allowedTo(i).firstOrNull() ?: 0) }, verticalAlignment = Alignment.CenterVertically) {
                StudioRadioButton("ArgentumUi.RadioButton.2", "Table and player", selected = fromIndex == i, onClick = { onChange(i, choice.allowedTo(i).firstOrNull() ?: 0) }); StudioText("ArgentumUi.Text.96", "Table and player", true, label, color = SoftWhite, fontSize = 12.sp)
            }
        }
        StudioText("ArgentumUi.Text.97", "Table and player", false, "Result", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
        choice.allowedTo(fromIndex).forEach { i ->
            Row(Modifier.fillMaxWidth().clickable { onChange(fromIndex, i) }, verticalAlignment = Alignment.CenterVertically) {
                StudioRadioButton("ArgentumUi.RadioButton.3", "Table and player", selected = toIndex == i, onClick = { onChange(fromIndex, i) }); StudioText("ArgentumUi.Text.98", "Table and player", true, choice.to.getOrNull(i).orEmpty(), color = SoftWhite, fontSize = 12.sp)
            }
        }
    }
}

/** BudgetModalDecision: buy modes (repeats allowed) within the budget. */
@Composable
fun ArgentumBudgetEditor(budget: ArgentumBudgetModes, counts: Map<Int, Int>, onChange: (Map<Int, Int>) -> Unit) {
    val spent = budget.spent(counts)
    Column {
        StudioText("ArgentumUi.Text.99", "Table and player", true, "Spent $spent of ${budget.budget}", color = if (spent <= budget.budget) Mint else CounterGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        budget.modes.forEachIndexed { i, (cost, text) ->
            val n = counts[i] ?: 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StudioText("ArgentumUi.Text.100", "Table and player", true, text, color = SoftWhite, fontSize = 12.sp)
                    StudioText("ArgentumUi.Text.101", "Table and player", true, "cost $cost", color = Muted, fontSize = 10.sp)
                }
                StudioTextButton("ArgentumUi.TextButton.23", "Table and player", enabled = n > 0, onClick = { onChange(counts + (i to n - 1)) }) { StudioText("ArgentumUi.Text.102", "Table and player", false, "−") }
                StudioText("ArgentumUi.Text.103", "Table and player", true, "$n", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp))
                StudioTextButton("ArgentumUi.TextButton.24", "Table and player", enabled = spent + cost <= budget.budget, onClick = { onChange(counts + (i to n + 1)) }) { StudioText("ArgentumUi.Text.104", "Table and player", false, "+") }
            }
        }
    }
}
