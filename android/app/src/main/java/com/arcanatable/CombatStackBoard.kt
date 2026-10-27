@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/*
 * Stacked combat screens. Identical creatures form one stack (×N) in a centered grid instead of a
 * long row; − / + on a stack (or tapping it for the picker) chooses exactly how many attack or
 * block. Rules for stacks, counts and previews live in CombatStacks.kt.
 */

private val Danger = Color(0xFFFFB4A8)

private data class CombatSizing(val tile: Dp, val card: Dp)

@Composable
private fun combatSizing(small: Boolean = false): CombatSizing {
    val config = LocalConfiguration.current
    val scale = LocalContext.current.getSharedPreferences("arcana_options", android.content.Context.MODE_PRIVATE)
        .getFloat("adjust_combat_card_scale", 1f).coerceIn(.65f, 1.35f)
    val shortScreen = config.screenHeightDp < 520 && config.screenWidthDp > config.screenHeightDp
    // Two stacks per row on a phone; wider screens fit more because the grid wraps.
    val tile = when {
        shortScreen -> 112f
        config.screenWidthDp >= 600 -> 168f
        else -> ((config.screenWidthDp - 16 - 24 - 12) / 2f).coerceIn(132f, 176f)
    } * (if (small) 0.8f else 1f)
    val card = (if (shortScreen) 62f else if (small) 70f else 92f) * scale
    return CombatSizing(tile.dp, card.dp)
}

/** The shared frame: title, a centered scrolling middle and a tray. [dismissable] = false keeps it open. */
@Composable
private fun CombatScreen(
    title: String, subtitle: String, dismissable: Boolean, onDismiss: () -> Unit,
    tray: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissable, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(16.dp), color = Ink) {
            Box(Modifier.fillMaxSize().testTag("combat-board")) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(title, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
                        Text(subtitle, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val viewport = maxHeight
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = content,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                            .background(Panel).border(1.dp, PanelLight, RoundedCornerShape(14.dp)).padding(10.dp),
                        content = tray,
                    )
                }
                TutorialLayer(Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = Muted) =
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center)

@Composable
private fun CenteredGrid(tag: String, content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().tutorialTarget(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun RoundStep(label: String, description: String, enabled: Boolean, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (enabled) PanelLight else Panel)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) SoftWhite else Muted, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold) }
}

/** − n / N + under a stack. */
@Composable
private fun StackStepper(count: Int, total: Int, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Ink).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundStep("−", "One fewer", canMinus, onClick = onMinus)
        Text("$count / $total", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 48.dp))
        RoundStep("+", "One more", canPlus, onClick = onPlus)
    }
}

/** Card art for a stack, with the cards behind it showing when there is more than one. */
@Composable
private fun StackPile(piece: CombatPiece, count: Int, cardWidth: Dp, chosen: Int, ring: Color, onTap: () -> Unit, onHold: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val cardHeight = cardWidth / 0.72f
    Box(Modifier.width(cardWidth + 10.dp).height(cardHeight + 10.dp)) {
        if (count > 1) {
            Box(Modifier.offset(x = 10.dp).width(cardWidth).height(cardHeight).clip(shape).background(Color(0xFF1E2A27)).border(1.dp, Color(0xFF33433E), shape))
            Box(Modifier.offset(x = 5.dp, y = 5.dp).width(cardWidth).height(cardHeight).clip(shape).background(Color(0xFF22302C)).border(1.dp, Color(0xFF3A4C46), shape))
        }
        Box(Modifier.offset(y = if (chosen > 0) 6.dp else 10.dp).width(cardWidth).clip(shape).border(if (ring != Color.Transparent) 3.dp else 0.dp, ring, shape)) {
            val card = piece.card
            if (card != null) CardFace(card.copy(combatRole = null), Modifier.fillMaxWidth(), onClick = onTap, onLongClick = onHold, showCounters = true)
            else Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(PanelLight).clickable(onClick = onTap), contentAlignment = Alignment.Center) {
                Text(piece.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 3, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
            }
        }
        if (count > 1) Text("×$count", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(11.dp)).background(CounterGold).padding(horizontal = 7.dp, vertical = 2.dp))
        if (chosen > 0) Text("✓ $chosen", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(11.dp)).background(Mint).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Combat keywords as small badges: evasion and damage keywords stand out. */
@Composable
private fun KeywordBadges(card: GameCard?) {
    val keywords = card?.let(::combatKeywords).orEmpty()
    if (keywords.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        keywords.forEach { keyword ->
            Text(keyword, color = if (keyword in setOf("flying", "trample", "deathtouch", "first strike", "double strike", "menace")) Danger else CounterGold,
                fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Ink).padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}

/**
 * One stack in the grid. The whole tile is the tap target (so the name works too); holding the
 * card shows it large.
 */
@Composable
private fun StackTile(
    stack: CombatStack, piece: CombatPiece, sizing: CombatSizing, chosen: Int, selected: Boolean, enabled: Boolean,
    note: String?, noteColor: Color, onTap: () -> Unit, stepper: (@Composable () -> Unit)?, footer: (@Composable () -> Unit)? = null,
) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    val hold = { piece.card?.let { zoomed = it.toStackPick() }; Unit }
    val ring = when { selected -> CounterGold; chosen > 0 -> Mint; else -> Color.Transparent }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(sizing.tile).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .background(if (selected) Color(0x26FFD166) else if (chosen > 0) Color(0x1F89E3B0) else Panel)
            .border(1.dp, if (selected) CounterGold else if (chosen > 0) Mint else PanelLight, shape)
            .combinedClickable(enabled = enabled, onClick = onTap, onLongClick = hold)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("combat-${stack.rep}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StackPile(piece, stack.size, sizing.card, chosen, ring, onTap = { if (enabled) onTap() }, onHold = hold)
        Text(piece.name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        KeywordBadges(piece.card)
        note?.let { Text(it, color = noteColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center) }
        stepper?.invoke()
        footer?.invoke()
    }
    zoomed?.let { PickZoom(it) { zoomed = null } }
}

private fun GameCard.toStackPick() = PickCard(id.toString(), name, imageUrl?.takeIf { !isFaceDown }, type, group = "Combat", groupOrder = 0, isToken = isToken)

/** A row in the stack picker: one target (attacking) or the single choice (blocking). */
private data class PickerRow(val key: String, val label: String, val detail: String?, val max: Int, val initial: Int)

/**
 * Centered picker for an exact number from a stack: the cards fan out and the chosen ones lift.
 * One row per target when the stack can attack several; the total never passes [limit].
 */
@Composable
private fun StackPickerDialog(
    piece: CombatPiece, stackSize: Int, limit: Int, question: String, confirmVerb: String,
    rows: List<PickerRow>, onDone: (Map<String, Int>) -> Unit, onCancel: () -> Unit,
) {
    var counts by remember { mutableStateOf(rows.associate { it.key to it.initial }) }
    val total = counts.values.sum()
    fun set(key: String, value: Int) {
        val row = rows.first { it.key == key }
        val others = total - (counts[key] ?: 0)
        counts = counts + (key to value.coerceIn(0, minOf(row.max, limit - others)))
    }
    val primary = rows.first().key
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
                .border(1.dp, Color(0xFF33433E), RoundedCornerShape(22.dp)).verticalScroll(rememberScrollState()).padding(16.dp)
                .testTag("stack-picker"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(listOf(piece.name.uppercase(), combatStats(piece.card), "$stackSize READY").filter(String::isNotBlank).joinToString(" · "))
            Text(question, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            // The fan: first [total] cards lift and get a ring.
            val shown = minOf(stackSize, 10)
            val cardW = 58.dp
            Box(Modifier.width(cardW + (24 * (shown - 1)).dp).height(cardW / 0.72f + 14.dp)) {
                repeat(shown) { i ->
                    val lifted = i < total
                    Box(Modifier.offset(x = (24 * i).dp, y = if (lifted) 0.dp else 14.dp).width(cardW).alpha(if (lifted) 1f else 0.7f)
                        .clip(RoundedCornerShape(6.dp)).border(2.dp, if (lifted) Mint else Color(0xFF3A4C46), RoundedCornerShape(6.dp))) {
                        val card = piece.card
                        if (card != null) CardFace(card.copy(combatRole = null), Modifier.fillMaxWidth(), onClick = {}, onLongClick = {}, showCounters = false)
                        else Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(PanelLight))
                    }
                }
            }
            if (rows.size == 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundStep("−", "One fewer", total > 0, size = 56.dp) { set(primary, (counts[primary] ?: 0) - 1) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 84.dp)) {
                        Text("$total", color = Mint, fontSize = 44.sp, fontWeight = FontWeight.Black)
                        Text("of ${rows.first().max}", color = Muted, fontSize = 11.sp)
                    }
                    RoundStep("+", "One more", total < minOf(limit, rows.first().max), size = 56.dp) { set(primary, (counts[primary] ?: 0) + 1) }
                }
            } else rows.forEach { row ->
                val value = counts[row.key] ?: 0
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("→ ${row.label}", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        row.detail?.let { Text(it, color = Muted, fontSize = 10.sp, maxLines = 1) }
                    }
                    RoundStep("−", "One fewer at ${row.label}", value > 0) { set(row.key, value - 1) }
                    Text("$value", color = if (value > 0) Mint else SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 34.dp))
                    RoundStep("+", "One more at ${row.label}", value < row.max && total < limit) { set(row.key, value + 1) }
                }
            }
            val primaryMax = minOf(limit, rows.first().max)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("None" to 0, "Half" to (primaryMax + 1) / 2, "All $primaryMax" to primaryMax).forEach { (label, n) ->
                    TextButton(onClick = { counts = rows.associate { it.key to 0 } + (primary to n) },
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF33433E), RoundedCornerShape(12.dp))) {
                        Text(label, color = SoftWhite, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF33433E), RoundedCornerShape(12.dp))) {
                    Text("Cancel", color = SoftWhite, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onDone(counts) }, modifier = Modifier.weight(2f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(Mint)) {
                    Text(if (total == 0) "NONE" else "$confirmVerb $total", color = Ink, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick,
        modifier = Modifier.tutorialTarget("combat-confirm").heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Mint else PanelLight)) {
        Text(label, color = if (enabled) Ink else Muted, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun TrayButton(label: String, color: Color = SoftWhite, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 44.dp)) { Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}

private fun lifeLine(player: TablePlayer?, life: Int?): String? = (player?.life ?: life)?.let { "$it life" }

/**
 * Attack declaration: what you can attack along the top, your creatures as centered stacks.
 * Tap a single creature to send it; − / + or the picker choose how many of a stack attack. With
 * several opponents (or planeswalkers and battles) each stack has its own target.
 */
@Composable
fun ArgentumAttackBoard(
    plan: ArgentumAttackPlan, piece: (String) -> CombatPiece, busy: Boolean, onConfirm: (JSONObject) -> Unit, onDismiss: () -> Unit,
    opponentBlockers: List<Pair<String, List<GameCard>>> = emptyList(),
    players: List<TablePlayer> = emptyList(), viewerId: String? = null,
) {
    val optionFor = plan.options.associateBy { it.attackerId }
    val base = plan.options.filter { it.mandatory }.associate { it.attackerId to it.targets.first() }
    var selection by remember(plan) { mutableStateOf(base) }
    val targets = plan.options.flatMap { it.targets }.distinct()
    var preferred by remember(plan) { mutableStateOf(targets.firstOrNull { id -> players.any { it.id == id } } ?: targets.firstOrNull()) }
    var picking by remember(plan) { mutableStateOf<CombatStack?>(null) }
    var showBlockers by remember(plan) { mutableStateOf(false) }
    val stacks = attackGroups(plan, piece).map { (rep, ids) -> CombatStack(rep, ids) }
    val missing = plan.missingMandatory(selection)
    val sizing = combatSizing()
    val playerOf = players.associateBy { it.id }
    val damage = attackPreview(selection, piece)
    val replay = viewerId?.let { CombatMemory.replay(it, stacks, base, optionFor, piece) }?.takeIf { it != selection }

    CombatScreen(
        title = "DECLARE ATTACKERS",
        subtitle = if (targets.size > 1) "Tap a target, then pick how many of each stack attack it. Hold a card to read it."
            else "Tap a creature to attack, or use − / + on a stack. Hold a card to read it.",
        dismissable = true, onDismiss = onDismiss,
        tray = {
            val attacking = selection.size
            val power = damage.values.sum()
            Text(if (attacking == 0) "No attackers yet" else "$attacking attacking · about $power damage if unblocked",
                color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("combat-summary").tutorialTarget("combat-summary"))
            selection.entries.groupBy { (attacker, target) -> Triple(piece(attacker).name, combatStats(piece(attacker).card), target) }.forEach { (key, list) ->
                val (name, stats, target) = key
                Text(listOf(if (list.size > 1) "${list.size} × $name" else name, stats).filter(String::isNotBlank).joinToString(" ") + " → " + piece(target).name,
                    color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (missing.isNotEmpty()) Text("Must attack: ${missing.joinToString { piece(it).name }}", color = CounterGold, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TrayButton("LATER", Muted, onDismiss)
                    if (replay != null) TrayButton("SAME AS LAST TURN", Mint) { selection = replay }
                    if (selection.size < plan.options.size) TrayButton("ALL", Mint) {
                        selection = plan.options.associate { o -> o.attackerId to (selection[o.attackerId] ?: preferred?.takeIf { it in o.targets } ?: o.targets.first()) }
                    } else TrayButton("CLEAR", Muted) { selection = base }
                }
                ConfirmButton(if (selection.isEmpty()) "NO ATTACK" else "ATTACK WITH ${selection.size}", !busy && missing.isEmpty()) {
                    viewerId?.let { CombatMemory.remember(it, stacks, selection, piece) }
                    onConfirm(plan.buildAction(selection))
                }
            }
        },
    ) {
        // What you can attack: players with life and commander damage, planeswalkers, battles.
        SectionLabel(if (targets.size > 1) "ATTACKING · TAP TO CHOOSE" else "ATTACKING", CounterGold)
        FlowRow(Modifier.fillMaxWidth().tutorialTarget("combat-top"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                val p = piece(target)
                val player = playerOf[target]
                val chosen = target == preferred && targets.size > 1
                val incoming = damage[target] ?: 0
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.widthIn(min = 96.dp).clip(shape).background(if (chosen) Mint else Panel).border(1.dp, if (chosen) Mint else PanelLight, shape)
                        .clickable { preferred = target }.padding(horizontal = 10.dp, vertical = 6.dp).testTag("combat-target-$target"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ink = if (chosen) Ink else SoftWhite
                    Text(p.name, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = lifeLine(player, p.life) ?: p.card?.counters?.filterValues { it > 0 }?.entries?.joinToString { "${it.key} ${it.value}" }?.ifBlank { null }
                        ?: p.card?.type?.substringBefore('—')?.trim()
                    detail?.let { Text(it, color = if (chosen) Ink else Muted, fontSize = 10.sp, maxLines = 1) }
                    if (incoming > 0) Text("−$incoming if unblocked", color = if (chosen) Ink else Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (player != null) commanderDamageLines(player, commanderPreview(selection, target, piece)).forEach { (line, lethal) ->
                        Text(line, color = if (chosen) Ink else if (lethal) Danger else CounterGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        if (opponentBlockers.isNotEmpty()) {
            TextButton(onClick = { showBlockers = !showBlockers }) {
                Text(if (showBlockers) "HIDE WHO CAN BLOCK ▴" else "WHO CAN BLOCK ▾", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (showBlockers) OpponentBlockersStrip(opponentBlockers)
        }
        SectionLabel("YOUR CREATURES", Mint)
        if (stacks.isEmpty()) Text("No creatures can attack right now.", color = Muted, fontSize = 12.sp)
        CenteredGrid("combat-bottom") {
            stacks.forEach { stack ->
                val p = piece(stack.rep)
                val allocation = stackAllocation(selection, stack.ids)
                val count = allocation.values.sum()
                val legal = optionFor[stack.rep]?.targets.orEmpty()
                val mandatoryCount = stack.ids.count { optionFor[it]?.mandatory == true }
                val onTap: () -> Unit = {
                    when {
                        stack.size > 1 || legal.size > 1 && targets.size > 1 -> picking = stack
                        count > 0 && mandatoryCount == 0 -> selection = selection - stack.ids.toSet()
                        else -> selection = stepStack(selection, stack, +1, optionFor, preferred)
                    }
                }
                StackTile(
                    stack, p, sizing, chosen = count, selected = false, enabled = true,
                    note = when {
                        mandatoryCount > 0 -> "MUST ATTACK"
                        stack.size == 1 && count > 0 -> "ATTACKING"
                        stack.size == 1 -> "TAP TO ATTACK"
                        else -> null
                    },
                    noteColor = if (mandatoryCount > 0) CounterGold else if (count > 0) Mint else Muted,
                    onTap = onTap,
                    stepper = if (stack.size > 1) ({
                        StackStepper(count, stack.size, canMinus = count > mandatoryCount, canPlus = count < stack.size,
                            onMinus = { selection = stepStack(selection, stack, -1, optionFor, preferred) },
                            onPlus = { selection = stepStack(selection, stack, +1, optionFor, preferred) })
                    }) else null,
                    footer = if (targets.size > 1 && legal.isNotEmpty()) ({
                        val text = when {
                            allocation.size > 1 -> "→ split ${allocation.size} ways ▾"
                            allocation.size == 1 -> "→ ${piece(allocation.keys.first()).name} ▾"
                            else -> "→ ${piece(stackTarget(selection, stack, optionFor, preferred) ?: legal.first()).name} ▾"
                        }
                        Text(text, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = stack }.padding(horizontal = 6.dp, vertical = 6.dp))
                    }) else null,
                )
            }
        }
    }

    picking?.let { stack ->
        val p = piece(stack.rep)
        val allocation = stackAllocation(selection, stack.ids)
        val legal = optionFor[stack.rep]?.targets.orEmpty()
        val order = (listOfNotNull(preferred?.takeIf { it in legal }) + legal).distinct()
        StackPickerDialog(
            piece = p, stackSize = stack.size, limit = stack.size,
            question = if (stack.size == 1) "Attack with ${p.name}?" else "How many attack?",
            confirmVerb = "ATTACK WITH",
            rows = order.map { target ->
                val player = playerOf[target]
                PickerRow(target, piece(target).name, lifeLine(player, piece(target).life), stack.ids.count { target in optionFor[it]?.targets.orEmpty() }, allocation[target] ?: 0)
            },
            onDone = { counts -> selection = allocateStack(selection, stack.ids, counts, optionFor); picking = null },
            onCancel = { picking = null },
        )
    }
}

/** "Krenko 14/21 (+3)" for each commander that has hit, or is about to hit, [player]. Pair = text to lethal. */
private fun commanderDamageLines(player: TablePlayer, adding: Map<String, Int>): List<Pair<String, Boolean>> =
    (player.commanderDamageReceived.keys + adding.keys).distinct().mapNotNull { name ->
        val now = player.commanderDamageReceived[name] ?: 0
        val plus = adding[name] ?: 0
        if (now + plus <= 0) return@mapNotNull null
        val total = now + plus
        ("CMD ${name.substringBefore(',')} $total/21" + if (plus > 0) " (+$plus est.)" else "") to (total >= 21)
    }

/**
 * Block declaration: the attackers coming at you as stacks across the top, your creatures as
 * stacks underneath. Tap an attacker stack, then choose how many of each of your stacks block it.
 * There is no way to close it without declaring: "NO BLOCKS" is the way out.
 */
@Composable
fun ArgentumBlockBoard(
    plan: ArgentumBlockPlan, piece: (String) -> CombatPiece, busy: Boolean, warning: String? = null,
    onConfirm: (JSONObject) -> Unit, @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit = {},
    defender: TablePlayer? = null,
) {
    val required = plan.mandatory.filterKeys { it in plan.blockers }
    var selection by remember(plan) { mutableStateOf(required) }
    val mine = defender?.battlefield?.map { it.id }?.toSet().orEmpty()
    // An attacker can be blocked by you when it attacks you or something of yours; unknown counts as you.
    fun blockable(attacker: String): Boolean {
        val target = plan.attackTargets[attacker] ?: return true
        return defender == null || target == defender.id || piece(target).card?.id?.let { it in mine } == true
    }
    val attackerStacks = stacksOf(plan.attackers, piece) { plan.attackTargets[it].orEmpty() }
    val blockerStacks = stacksOf(plan.blockers, piece) { b -> "${plan.maxBlocks[b] ?: 1}|${b in required}" }
    var focus by remember(plan) { mutableStateOf(attackerStacks.firstOrNull { blockable(it.rep) }?.rep) }
    var picking by remember(plan) { mutableStateOf<CombatStack?>(null) }
    val focusStack = attackerStacks.firstOrNull { it.rep == focus }
    val counts = blockersOn(selection)
    val sizing = combatSizing()
    val small = combatSizing(small = true)
    val againstMe = plan.attackers.filter { a -> plan.attackTargets[a].let { it == null || it == defender?.id } }
    val damage = incomingDamage(againstMe, selection, piece)
    val commanderHits = incomingCommanderDamage(againstMe, selection, piece)
    val menace = menaceShort(selection, piece)
    val blockersOf = { ids: List<String> -> selection.filterValues { list -> list.any { it in ids } }.keys }

    CombatScreen(
        title = "DECLARE BLOCKERS",
        subtitle = if (focusStack != null) "Choose how many of your creatures block ${piece(focusStack.rep).name}${if (focusStack.size > 1) " (×${focusStack.size})" else ""}."
            else "Tap an attacker, then choose your blockers. Hold a card to read it.",
        dismissable = false, onDismiss = {},
        tray = {
            val life = defender?.life
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("combat-summary").tutorialTarget("combat-summary")) {
                Text("Estimated: you take ", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("$damage", color = if (life != null && damage >= life) Danger else CounterGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(" damage" + (life?.let { " · ${it - damage} life left" } ?: ""), color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            defender?.let { d -> commanderDamageLines(d, commanderHits).forEach { (line, lethal) -> Text(line, color = if (lethal) Danger else CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
            blockSummary(plan.attackers.map(piece), plan.blockers.map(piece), selection).forEach {
                Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            menace.forEach { Text("${piece(it).name} has menace: add a second blocker or remove the block.", color = CounterGold, fontSize = 11.sp) }
            warning?.let { Text(it, color = CounterGold, fontSize = 11.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TrayButton("SUGGEST BLOCKS", Mint) {
                        selection = suggestBlocks(plan.attackers.filter(::blockable), plan.blockers, defender?.life ?: 40,
                            defender?.commanderDamageReceived.orEmpty(), plan.mandatory, piece)
                    }
                    if (selection != required) TrayButton("CLEAR", Muted) { selection = required }
                }
                ConfirmButton(if (selection.isEmpty()) "NO BLOCKS" else "BLOCK WITH ${selection.size}", !busy && menace.isEmpty()) {
                    onConfirm(plan.buildAction(selection))
                }
            }
        },
    ) {
        SectionLabel("ATTACKING · TAP ONE TO BLOCK IT", CounterGold)
        CenteredGrid("combat-top") {
            attackerStacks.forEach { stack ->
                val p = piece(stack.rep)
                val blocked = stack.ids.count { (counts[it] ?: 0) > 0 }
                val canBlock = blockable(stack.rep)
                val target = plan.attackTargets[stack.rep]?.takeIf { it != defender?.id }
                StackTile(
                    stack, p, small, chosen = 0, selected = stack.rep == focus, enabled = canBlock,
                    note = when {
                        !canBlock -> "attacking ${target?.let { piece(it).name } ?: "someone else"}"
                        blocked == 0 -> (if (target != null) "→ ${piece(target).name} · " else "") + "unblocked"
                        stack.size > 1 -> "$blocked of ${stack.size} blocked"
                        else -> "blocked"
                    },
                    noteColor = if (blocked > 0) Mint else Muted,
                    onTap = { focus = stack.rep },
                    stepper = null,
                )
            }
        }
        SectionLabel("YOUR BLOCKERS", Mint)
        if (blockerStacks.isEmpty()) Text("None of your creatures can block.", color = Muted, fontSize = 12.sp)
        CenteredGrid("combat-bottom") {
            blockerStacks.forEach { stack ->
                val p = piece(stack.rep)
                val here = focusStack?.let { stackBlocking(selection, stack, it) } ?: 0
                val elsewhere = stack.ids.filter { b -> selection[b].orEmpty().any { a -> focusStack == null || a !in focusStack.ids } }
                val free = stack.size - elsewhere.size - here
                val problem = focusStack?.let { f -> blockProblem(piece(f.rep).card, p.card) }
                val focusOk = focusStack != null && problem == null
                val busyNote = elsewhere.takeIf { it.isNotEmpty() }?.let { busyIds ->
                    val on = busyIds.flatMap { selection[it].orEmpty() }.map { piece(it).name }.distinct()
                    (if (stack.size > 1) "${busyIds.size} blocking " else "blocking ") + on.joinToString(limit = 2)
                }
                val maxHere = here + free
                StackTile(
                    stack, p, sizing, chosen = here, selected = false, enabled = focusOk || here > 0,
                    note = problem ?: busyNote ?: if (focusStack == null) null else if (stack.size == 1 && here == 0) "TAP TO BLOCK" else null,
                    noteColor = if (problem != null) Danger else if (busyNote != null) CounterGold else Muted,
                    onTap = {
                        val f = focusStack
                        when {
                            f == null -> Unit
                            stack.size > 1 -> picking = stack
                            here > 0 -> selection = removeBlock(selection, stack, f, plan.mandatory)
                            else -> {
                                // A lone creature moves to this attacker rather than adding a second job.
                                val cleared = if (elsewhere.isNotEmpty() && (plan.maxBlocks[stack.rep] ?: 1) <= 1) selection - stack.ids.toSet() else selection
                                selection = addBlock(cleared, stack, f, plan.maxBlocks, piece)
                            }
                        }
                    },
                    stepper = if (stack.size > 1 && focusStack != null) ({
                        StackStepper(here, maxHere, canMinus = here > 0, canPlus = focusOk && free > 0,
                            onMinus = { selection = removeBlock(selection, stack, focusStack, plan.mandatory) },
                            onPlus = { selection = addBlock(selection, stack, focusStack, plan.maxBlocks, piece) })
                    }) else null,
                )
            }
        }
        if (focusStack != null && blockersOf(focusStack.ids).isEmpty() && againstMe.isNotEmpty())
            Text("Unblocked attackers deal their damage to you.", color = Muted, fontSize = 10.sp)
    }

    val pickingStack = picking
    val pickingFocus = focusStack
    if (pickingStack != null && pickingFocus != null) {
        val stack = pickingStack
        val f = pickingFocus
        val here = stackBlocking(selection, stack, f)
        val elsewhere = stack.ids.count { b -> selection[b].orEmpty().any { it !in f.ids } }
        val max = stack.size - elsewhere
        StackPickerDialog(
            piece = piece(stack.rep), stackSize = stack.size, limit = max,
            question = "How many block ${piece(f.rep).name}${if (f.size > 1) " (×${f.size})" else ""}?",
            confirmVerb = "BLOCK WITH",
            rows = listOf(PickerRow(f.rep, piece(f.rep).name, null, max, here)),
            onDone = { picked ->
                var next = selection
                val want = picked[f.rep] ?: 0
                repeat(stack.size) {
                    val now = stackBlocking(next, stack, f)
                    next = when {
                        now < want -> addBlock(next, stack, f, plan.maxBlocks, piece)
                        now > want -> removeBlock(next, stack, f, plan.mandatory)
                        else -> next
                    }
                }
                selection = next
                picking = null
            },
            onCancel = { picking = null },
        )
    }
}
