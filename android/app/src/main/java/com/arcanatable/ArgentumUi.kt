package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
)

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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                ArgentumConnectionStatus.CONNECTED -> if (model.priorityLabel != null && !model.awaitingUpdate && !model.gameOver) StripButton(model.priorityLabel, Mint, onPrimary)
                else -> Unit
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

/** Attack declaration built from Argentum's DeclareAttackers legal action. */
@Composable
fun ArgentumAttackDialog(plan: ArgentumAttackPlan, nameOf: (String) -> String, busy: Boolean, onConfirm: (JSONObject) -> Unit, onDismiss: () -> Unit) {
    var selection by remember(plan) { mutableStateOf(plan.options.filter { it.mandatory }.associate { it.attackerId to it.targets.first() }) }
    val missing = plan.missingMandatory(selection)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Declare attackers") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (plan.options.isEmpty()) Text("Argentum lists no creatures that can attack. Confirm to attack with nothing.", color = Muted, fontSize = 12.sp)
                plan.options.forEach { option ->
                    val chosen = selection[option.attackerId]
                    Row(Modifier.fillMaxWidth().clickable(enabled = !option.mandatory) {
                        selection = if (chosen != null) selection - option.attackerId else selection + (option.attackerId to option.targets.first())
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chosen != null, enabled = !option.mandatory, onCheckedChange = { on ->
                            selection = if (on) selection + (option.attackerId to option.targets.first()) else selection - option.attackerId
                        })
                        Text(nameOf(option.attackerId) + if (option.mandatory) " · must attack" else "", color = SoftWhite, fontSize = 13.sp)
                    }
                    if (chosen != null && option.targets.size > 1) option.targets.forEach { target ->
                        Row(Modifier.fillMaxWidth().padding(start = 36.dp).clickable { selection = selection + (option.attackerId to target) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen == target, onClick = { selection = selection + (option.attackerId to target) })
                            Text("→ ${nameOf(target)}", color = Muted, fontSize = 12.sp)
                        }
                    } else if (chosen != null) Text("→ ${nameOf(chosen)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp))
                }
                if (missing.isNotEmpty()) Text("Required to attack: ${missing.joinToString { nameOf(it) }}", color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Argentum checks the declaration and applies any attack costs or restrictions.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = !busy && missing.isEmpty(), onClick = { onConfirm(plan.buildAction(selection)) }) {
            Text(if (selection.isEmpty()) "NO ATTACK" else "ATTACK WITH ${selection.size}")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("LATER") } }
    )
}

/** Block declaration built from Argentum's DeclareBlockers legal action. */
@Composable
fun ArgentumBlockDialog(plan: ArgentumBlockPlan, nameOf: (String) -> String, busy: Boolean, onConfirm: (JSONObject) -> Unit, onDismiss: () -> Unit) {
    var selection by remember(plan) { mutableStateOf(plan.mandatory.filterKeys { it in plan.blockers }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Declare blockers") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Attacking: ${plan.attackers.joinToString { nameOf(it) }.ifBlank { "unknown" }}", color = Muted, fontSize = 12.sp)
                if (plan.blockers.isEmpty()) Text("Argentum lists no creatures that can block. Confirm to take the damage.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                plan.blockers.forEach { blocker ->
                    val chosen = selection[blocker].orEmpty()
                    val cap = plan.maxBlocks[blocker] ?: 1
                    Text(nameOf(blocker) + if (cap > 1) " · can block $cap" else "", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().clickable { selection = selection - blocker }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen.isEmpty(), onClick = { selection = selection - blocker })
                        Text("Don't block", color = Muted, fontSize = 12.sp)
                    }
                    plan.attackers.forEach { attacker ->
                        val on = attacker in chosen
                        val toggle = {
                            val next = if (on) chosen - attacker else if (cap > 1) (chosen + attacker).take(cap) else listOf(attacker)
                            selection = if (next.isEmpty()) selection - blocker else selection + (blocker to next)
                        }
                        Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                            if (cap > 1) Checkbox(checked = on, onCheckedChange = { toggle() }) else RadioButton(selected = on, onClick = toggle)
                            Text("Block ${nameOf(attacker)}", color = SoftWhite, fontSize = 12.sp)
                        }
                    }
                }
                Text("Argentum validates blocks, menace and other restrictions when you confirm.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onConfirm(plan.buildAction(selection)) }) {
            Text(if (selection.isEmpty()) "NO BLOCKS" else "BLOCK WITH ${selection.size}")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("LATER") } }
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
