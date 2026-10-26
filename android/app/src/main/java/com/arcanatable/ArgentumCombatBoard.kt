@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.arcanatable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/**
 * What the combat board shows for one id: a battlefield card (art, power/toughness, keywords) or a
 * player / other attack target. Built by MainActivity from Argentum's masked state only.
 */
data class CombatPiece(val id: String, val name: String, val card: GameCard? = null, val life: Int? = null)

private val CombatKeywords = listOf("FLYING", "REACH", "FIRST_STRIKE", "DOUBLE_STRIKE", "TRAMPLE", "DEATHTOUCH", "LIFELINK", "MENACE", "VIGILANCE", "INDESTRUCTIBLE", "HEXPROOF", "SHADOW", "HORSEMANSHIP")
private val StandoutCombatKeywords = setOf("trample", "flying", "deathtouch", "first strike", "double strike", "menace")

/** "2/2 first strike" — power/toughness and combat keywords, as on the summary line. */
fun combatStats(card: GameCard?): String {
    if (card == null) return ""
    val pt = if (card.power != null && card.toughness != null) "${card.power}/${card.toughness}" else ""
    val keys = card.keywords.map { it.uppercase().replace(' ', '_') }.filter { it in CombatKeywords }
        .joinToString(" ") { it.lowercase().replace('_', ' ') }
    return listOf(pt, keys).filter(String::isNotBlank).joinToString(" ")
}

/** "Ramosian Captain 2/2 first strike ← 3 Rabbit Token (1/1)" for each blocked attacker. */
fun blockSummary(attackers: List<CombatPiece>, blockers: List<CombatPiece>, selection: Map<String, List<String>>): List<String> {
    val byId = blockers.associateBy { it.id }
    return attackers.mapNotNull { attacker ->
        val mine = selection.filterValues { attacker.id in it }.keys.mapNotNull(byId::get)
        if (mine.isEmpty()) return@mapNotNull null
        val groups = mine.groupBy { it.name to combatStats(it.card) }.entries.joinToString(", ") { (key, list) ->
            val (name, stats) = key
            (if (list.size > 1) "${list.size} ${name}s" else name) + if (stats.isNotBlank()) " ($stats)" else ""
        }
        listOf(attacker.name, combatStats(attacker.card)).filter(String::isNotBlank).joinToString(" ") + " ← " + groups
    }
}

@Composable
private fun CombatTile(piece: CombatPiece, width: androidx.compose.ui.unit.Dp, selected: Boolean, linked: Boolean, enabled: Boolean,
                       onClick: () -> Unit, onBounds: (Rect) -> Unit, badge: String? = null) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    val ring = when { selected -> CounterGold; linked -> Mint; else -> Color.Transparent }
    Column(
        Modifier.width(width).onGloballyPositioned { onBounds(it.boundsInRoot()) }.testTag("combat-${piece.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(3.dp, ring, RoundedCornerShape(10.dp)).padding(3.dp)) {
            // The board itself shows who attacks and blocks, so the card's own combat badge is dropped.
            if (piece.card != null) CardFace(piece.card.copy(combatRole = null), Modifier.fillMaxWidth(), onClick = { if (enabled) onClick() }, onLongClick = { zoomed = piece.card.toCombatPick() }, showCounters = true)
            else Box(
                Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(8.dp)).background(PanelLight)
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(piece.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    piece.life?.let { Text("$it", color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                }
            }
            if (badge != null) Text(badge, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(5.dp)).background(Mint).padding(horizontal = 5.dp, vertical = 2.dp))
        }
        if (piece.card != null) Text(piece.name, color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        piece.card?.let { card ->
            if (card.power != null && card.toughness != null) Text("${card.power}/${card.toughness}", color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            combatKeywords(card).forEach { keyword ->
                Text(keyword, color = if (keyword in StandoutCombatKeywords) Color(0xFFFFB4A8) else CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(3.dp)).background(Panel).padding(horizontal = 3.dp, vertical = 1.dp))
            }
        }
    }
    zoomed?.let { PickZoom(it) { zoomed = null } }
}

private fun GameCard.toCombatPick() = PickCard(id.toString(), name, imageUrl?.takeIf { !isFaceDown }, type, group = "Combat", groupOrder = 0, isToken = isToken)

private fun combatKeywords(card: GameCard): List<String> = card.keywords.map { it.uppercase().replace(' ', '_') }
    .filter { it in CombatKeywords }.distinct().map { it.lowercase().replace('_', ' ') }

@Composable
private fun CombatBoardFrame(
    title: String, hint: String, topLabel: String, bottomLabel: String,
    top: List<CombatPiece>, bottom: List<CombatPiece>,
    links: List<Pair<String, String>>, // bottom id → top id
    selectedId: String?, linkedIds: Set<String>,
    topEnabled: (String) -> Boolean, bottomEnabled: (String) -> Boolean,
    topBadge: (String) -> String? = { null },
    bottomBadge: (String) -> String? = { null },
    onTop: (String) -> Unit, onBottom: (String) -> Unit,
    summary: List<String>, warning: String?,
    confirmLabel: String, confirmEnabled: Boolean, onConfirm: () -> Unit,
    extraButton: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    topExtra: (@Composable () -> Unit)? = null,
    /** The running lesson's instruction; the board covers the coach card, so it shows here. */
    tutorialCue: Pair<String, String>? = null,
) {
    val config = LocalConfiguration.current
    val combatScale = LocalContext.current.getSharedPreferences("arcana_options", android.content.Context.MODE_PRIVATE)
        .getFloat("adjust_combat_card_scale", 1f).coerceIn(.65f, 1.35f)
    val screenWidth = config.screenWidthDp
    // Short screens (phone landscape) put the two rows side by side so both stay visible.
    val sideBySide = config.screenHeightDp < 520 && screenWidth > config.screenHeightDp
    val tileWidth = (when {
        sideBySide -> ((config.screenHeightDp - 230) * 0.68f).coerceIn(58f, 104f).dp
        screenWidth >= 900 -> 118.dp; screenWidth >= 600 -> 108.dp; else -> 92.dp
    }) * combatScale
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val lineColor = Mint
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(16.dp), color = Ink) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }.testTag("combat-board")) {
                // Lines between linked cards, drawn behind the cards so they show in the gaps.
                Canvas(Modifier.fillMaxSize()) {
                    links.forEach { (from, to) ->
                        val a = bounds["b:$from"] ?: return@forEach
                        val b = bounds["t:$to"] ?: return@forEach
                        if (kotlin.math.abs(a.top - b.top) < 40f) {
                            // Same row (side-by-side layout): arc underneath both cards so each link stays visible.
                            val start = Offset(a.center.x, a.bottom) - origin
                            val end = Offset(b.center.x, b.bottom) - origin
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(start.x, start.y)
                                quadraticTo((start.x + end.x) / 2, maxOf(start.y, end.y) + 50f, end.x, end.y)
                            }
                            drawPath(path, lineColor.copy(alpha = 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round))
                        } else drawLine(lineColor.copy(alpha = 0.9f), a.center - origin, b.center - origin, strokeWidth = 6f, cap = StrokeCap.Round)
                    }
                }
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(title, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    Text(hint, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    val topSection: @Composable () -> Unit = {
                        Text(topLabel, color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Row(Modifier.fillMaxWidth().tutorialTarget("combat-top").horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            top.forEach { piece ->
                                CombatTile(piece, tileWidth, selected = false, linked = piece.id in linkedIds, enabled = topEnabled(piece.id),
                                    onClick = { onTop(piece.id) }, onBounds = { bounds["t:${piece.id}"] = it }, badge = topBadge(piece.id))
                            }
                            if (top.isEmpty()) Text("Nothing here.", color = Muted, fontSize = 12.sp)
                        }
                        topExtra?.invoke()
                    }
                    val bottomSection: @Composable () -> Unit = {
                        Text(bottomLabel, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Row(Modifier.fillMaxWidth().tutorialTarget("combat-bottom").horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            bottom.forEach { piece ->
                                CombatTile(piece, tileWidth, selected = piece.id == selectedId, linked = piece.id in linkedIds, enabled = bottomEnabled(piece.id),
                                    onClick = { onBottom(piece.id) }, onBounds = { bounds["b:${piece.id}"] = it }, badge = bottomBadge(piece.id))
                            }
                            if (bottom.isEmpty()) Text("No creatures can do this right now.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (sideBySide) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1f)) { topSection() }
                        Column(Modifier.weight(1f)) { bottomSection() }
                    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        topSection()
                        Spacer(Modifier.heightIn(min = 46.dp))
                        bottomSection()
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp).testTag("combat-summary").tutorialTarget("combat-summary")) {
                        summary.forEach { Text(it, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        if (summary.isEmpty()) Text("Nothing assigned yet.", color = Muted, fontSize = 11.sp)
                        warning?.let { Text(it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("LATER", color = Muted) }
                        extraButton?.invoke()
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = confirmEnabled, onClick = onConfirm,
                            modifier = Modifier.tutorialTarget("combat-confirm").clip(RoundedCornerShape(8.dp)).background(if (confirmEnabled) Mint else PanelLight)) {
                            Text(confirmLabel, color = if (confirmEnabled) Ink else Muted, fontWeight = FontWeight.Black)
                        }
                    }
                }
                TutorialLayer(Modifier.matchParentSize())
            }
        }
    }
}

/**
 * Block declaration on a board: attackers on top, your possible blockers underneath. Tap a
 * blocker, then the attacker it blocks; tap a selected blocker again to clear it. Only blockers
 * Argentum lists can be used, and Argentum validates the result (flying, menace, ...).
 */
@Composable
fun ArgentumBlockBoard(plan: ArgentumBlockPlan, piece: (String) -> CombatPiece, busy: Boolean, onConfirm: (JSONObject) -> Unit, onDismiss: () -> Unit, tutorialCue: Pair<String, String>? = null) {
    var selection by remember(plan) { mutableStateOf(plan.mandatory.filterKeys { it in plan.blockers }) }
    var pending by remember(plan) { mutableStateOf<String?>(null) }
    val attackers = plan.attackers.map(piece)
    val blockers = plan.blockers.map(piece)
    val links = selection.flatMap { (blocker, list) -> list.map { blocker to it } }
    CombatBoardFrame(
        title = "DECLARE BLOCKERS",
        hint = if (pending != null) "Now tap the attacker ${piece(pending!!).name} should block. Tap it again to clear." else "Tap one of your creatures, then the attacker it blocks.",
        topLabel = "ATTACKING YOU", bottomLabel = "YOUR BLOCKERS",
        top = attackers, bottom = blockers, links = links,
        selectedId = pending, linkedIds = links.flatMap { listOf(it.first, it.second) }.toSet(),
        topEnabled = { pending != null }, bottomEnabled = { true },
        topBadge = { id -> selection.count { id in it.value }.takeIf { it > 0 }?.let { "×$it" } },
        onBottom = { id ->
            pending = when {
                pending == id && selection[id] != null -> { selection = selection - id; null }
                pending == id -> null
                else -> id
            }
        },
        onTop = { attacker ->
            val blocker = pending ?: return@CombatBoardFrame
            val cap = plan.maxBlocks[blocker] ?: 1
            val current = selection[blocker].orEmpty()
            val next = when {
                attacker in current -> current - attacker
                cap > 1 -> (current + attacker).take(cap)
                else -> listOf(attacker)
            }
            selection = if (next.isEmpty()) selection - blocker else selection + (blocker to next)
            if (cap <= 1 || next.size >= cap) pending = null
        },
        summary = blockSummary(attackers, blockers, selection),
        warning = null,
        confirmLabel = if (selection.isEmpty()) "NO BLOCKS" else "BLOCK WITH ${selection.size}",
        confirmEnabled = !busy,
        onConfirm = { onConfirm(plan.buildAction(selection)) },
        extraButton = { if (selection.isNotEmpty()) TextButton(onClick = { selection = plan.mandatory.filterKeys { it in plan.blockers }; pending = null }) { Text("CLEAR", color = Muted) } },
        onDismiss = onDismiss,
        tutorialCue = tutorialCue,
    )
}

/**
 * Attack declaration on the same board in reverse: what you can attack on top, your possible
 * attackers underneath. With a single defender, tapping a creature toggles it; otherwise tap the
 * creature, then the player or planeswalker it attacks.
 */
@Composable
fun ArgentumAttackBoard(plan: ArgentumAttackPlan, piece: (String) -> CombatPiece, busy: Boolean, onConfirm: (JSONObject) -> Unit, onDismiss: () -> Unit, opponentBlockers: List<Pair<String, List<GameCard>>> = emptyList(), tutorialCue: Pair<String, String>? = null) {
    var selection by remember(plan) { mutableStateOf(plan.options.filter { it.mandatory }.associate { it.attackerId to it.targets.first() }) }
    var pending by remember(plan) { mutableStateOf<String?>(null) }
    val targets = plan.options.flatMap { it.targets }.distinct()
    val single = targets.size == 1
    val optionFor = plan.options.associateBy { it.attackerId }
    val missing = plan.missingMandatory(selection)
    // Identical creatures (same name, stats and choices) share one tile: tapping it sends the whole stack.
    val groups = attackGroups(plan, piece)
    val groupOf = groups.flatMap { g -> g.value.map { it to g.key } }.toMap()
    fun members(rep: String) = groups[rep] ?: listOf(rep)
    val links = selection.map { (attacker, target) -> (groupOf[attacker] ?: attacker) to target }.distinct()
    CombatBoardFrame(
        title = "DECLARE ATTACKERS",
        hint = when {
            pending != null -> "Now tap what ${piece(pending!!).name} attacks."
            single -> "Tap the creatures you want to attack with. A stack (×N) attacks together."
            else -> "Tap a creature, then the player or planeswalker it attacks."
        },
        topLabel = "YOU CAN ATTACK", bottomLabel = "YOUR CREATURES",
        top = targets.map(piece), bottom = groups.keys.map(piece), links = links,
        selectedId = pending, linkedIds = (links.map { it.first } + selection.values).toSet(),
        topEnabled = { target -> pending?.let { target in optionFor[it]?.targets.orEmpty() } ?: false },
        bottomEnabled = { true },
        topBadge = { id -> selection.count { it.value == id }.takeIf { it > 0 }?.let { "×$it" } },
        bottomBadge = { rep -> members(rep).size.takeIf { it > 1 }?.let { n -> val on = members(rep).count { it in selection }; if (on in 1 until n) "$on/$n" else "×$n" } },
        onBottom = { rep ->
            val option = optionFor[rep] ?: return@CombatBoardFrame
            val group = members(rep)
            val anyOn = group.any { it in selection }
            when {
                anyOn && (single || option.targets.size == 1 || pending == rep) -> {
                    selection = selection - group.filterNot { optionFor[it]?.mandatory == true }.toSet(); pending = null
                }
                single || option.targets.size == 1 -> selection = selection + group.associateWith { option.targets.first() }
                pending == rep -> pending = null
                else -> pending = rep
            }
        },
        onTop = { target ->
            val rep = pending ?: return@CombatBoardFrame
            members(rep).forEach { a -> if (target in optionFor[a]?.targets.orEmpty()) selection = selection + (a to target) }
            pending = null
        },
        summary = selection.entries.groupBy { (attacker, target) -> Triple(piece(attacker).name, combatStats(piece(attacker).card), target) }.map { (key, list) ->
            val (name, stats, target) = key
            listOf(if (list.size > 1) "${list.size} × $name" else name, stats).filter(String::isNotBlank).joinToString(" ") + " → " + piece(target).name
        },
        warning = if (missing.isNotEmpty()) "Must attack: ${missing.joinToString { piece(it).name }}" else null,
        confirmLabel = if (selection.isEmpty()) "NO ATTACK" else "ATTACK WITH ${selection.size}",
        confirmEnabled = !busy && missing.isEmpty(),
        onConfirm = { onConfirm(plan.buildAction(selection)) },
        extraButton = {
            if (selection.size < plan.options.size) TextButton(onClick = {
                val target = targets.firstOrNull()
                selection = plan.options.associate { o -> o.attackerId to (selection[o.attackerId] ?: target?.takeIf { it in o.targets } ?: o.targets.first()) }
                pending = null
            }) { Text("ALL", color = Mint, fontWeight = FontWeight.Black) }
            else TextButton(onClick = { selection = plan.options.filter { it.mandatory }.associate { it.attackerId to it.targets.first() }; pending = null }) { Text("CLEAR", color = Muted) }
        },
        onDismiss = onDismiss,
        topExtra = if (opponentBlockers.isEmpty()) null else ({ OpponentBlockersStrip(opponentBlockers) }),
        tutorialCue = tutorialCue,
    )
}

/** Attack options grouped into stacks of identical creatures; key = the stack's first creature. */
fun attackGroups(plan: ArgentumAttackPlan, piece: (String) -> CombatPiece): Map<String, List<String>> =
    plan.options.groupBy { o -> val p = piece(o.attackerId); listOf(p.name, combatStats(p.card), o.targets.joinToString(","), o.mandatory.toString(), p.card?.counters?.toString().orEmpty()) }
        .values.associate { list -> list.first().attackerId to list.map { it.attackerId } }

/**
 * Combat damage on the board: each creature that assigns damage, with the amount it puts on each
 * blocker, attacker or player shown under that card. Starts from Argentum's own assignment.
 */
@Composable
fun ArgentumDamageBoard(resolution: ArgentumCombatResolution, playerId: String, piece: (String) -> CombatPiece, amounts: Map<String, Int>,
                        busy: Boolean, onChange: (Map<String, Int>) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    val mine = resolution.editableBy(playerId)
    val problem = resolution.problem(amounts)
    // The decision names every combatant; prefer that over a bare id when the card isn't on our table.
    val pieceOf: (String) -> CombatPiece = { id -> piece(id).let { p -> if (p.card == null && p.life == null) p.copy(name = resolution.names[id] ?: p.name) else p } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(16.dp), color = Ink) {
            Column(Modifier.fillMaxSize().padding(12.dp).testTag("damage-board")) {
                Text(if (resolution.firstStrike) "FIRST-STRIKE DAMAGE" else "COMBAT DAMAGE", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(if (mine.isEmpty()) "Nothing for you to adjust. Confirm to let Argentum apply combat damage." else "Use − and + under each card to split damage. Argentum's suggested split is already filled in.",
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    mine.groupBy { it.sourceId }.forEach { (source, edges) ->
                        val left = resolution.remaining(source, amounts)
                        val sourcePiece = pieceOf(source)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (sourcePiece.card != null) CardFace(sourcePiece.card, Modifier.width(84.dp), onClick = {}, onLongClick = { zoomed = sourcePiece.card.toCombatPick() })
                                Text(sourcePiece.name, color = SoftWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${edges.maxOf { it.maximum }} dmg" + if (left > 0) " · $left left" else "", color = if (left > 0) CounterGold else Muted, fontSize = 9.sp)
                            }
                            Text("→", color = Mint, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 6.dp))
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                edges.forEach { edge ->
                                    val value = amounts[edge.id] ?: edge.defaultAmount
                                    val target = pieceOf(edge.targetId)
                                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (target.card != null) CardFace(target.card, Modifier.width(80.dp), onClick = {}, onLongClick = { zoomed = target.card.toCombatPick() })
                                        else Text(target.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 18.dp))
                                        if (target.card != null) Text(target.name, color = SoftWhite, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(enabled = value > 0, onClick = { onChange(amounts + (edge.id to value - 1)) }, modifier = Modifier.size(40.dp)) { Text("−", fontSize = 18.sp) }
                                            Text("$value", color = if (!edge.isTrampleDrain && value >= edge.lethal) Mint else SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.widthIn(min = 20.dp))
                                            TextButton(enabled = value < edge.maximum && left > 0, onClick = { onChange(amounts + (edge.id to value + 1)) }, modifier = Modifier.size(40.dp)) { Text("+", fontSize = 18.sp) }
                                        }
                                        Text(if (edge.isTrampleDrain) "trample" else "lethal ${edge.lethal}" + if (value >= edge.lethal) " ✓" else "", color = Muted, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                problem?.let { Text(it, color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("VIEW TABLE", color = Muted) }
                    Spacer(Modifier.weight(1f))
                    val ok = !busy && problem == null
                    TextButton(enabled = ok, onClick = onConfirm, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (ok) Mint else PanelLight)) {
                        Text("CONFIRM DAMAGE", color = if (ok) Ink else Muted, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
    zoomed?.let { PickZoom(it) { zoomed = null } }
}


/** Keywords that matter when an opponent blocks, shown as badges on the blockers strip. */
private val BlockingKeywords = listOf("FLYING", "REACH", "FIRST_STRIKE", "DOUBLE_STRIKE", "DEATHTOUCH", "INDESTRUCTIBLE", "SHADOW", "HORSEMANSHIP")

/** Blocking-relevant keyword badges for a creature ("FLYING", "REACH" → "flying", "reach"). */
fun blockingKeywords(card: GameCard): List<String> =
    card.keywords.map { it.uppercase().replace(' ', '_') }.filter { it in BlockingKeywords }.distinct()
        .map { it.lowercase().replace('_', ' ') }

/** Each opponent's untapped creatures (who could block), opponents in seat order; empty ones left out. */
fun opponentBlockers(players: List<TablePlayer>, you: Int): List<Pair<String, List<GameCard>>> =
    players.mapIndexedNotNull { seat, p ->
        if (seat == you || p.isEliminated) return@mapIndexedNotNull null
        val ready = p.battlefield.filter { it.type.contains("Creature", ignoreCase = true) && !it.tapped }
        if (ready.isEmpty()) null else p.name to ready
    }

/**
 * "Who can block": one row per opponent of their untapped creatures as small cards with power/
 * toughness and blocking keywords. Hold a card to see it large.
 */
@Composable
private fun OpponentBlockersStrip(groups: List<Pair<String, List<GameCard>>>) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("opponent-blockers")) {
        groups.forEach { (name, cards) ->
            Text("${name.uppercase()} CAN BLOCK WITH", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { card ->
                    val shape = RoundedCornerShape(6.dp)
                    Column(Modifier.width(56.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.716f).clip(shape).background(Color(0xFF1C2523))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
                            .combinedClickable(onClick = {}, onLongClick = {
                                zoomed = PickCard(card.id.toString(), card.name, card.imageUrl?.takeIf { !card.isFaceDown }, card.type, group = name, groupOrder = 0, isToken = card.isToken)
                            })) {
                            val url = card.imageUrl
                            if (url != null && !card.isFaceDown) CardArt(url, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, fallbackName = card.name, fallbackType = card.type)
                            else Text(card.name, color = SoftWhite, fontSize = 8.sp, modifier = Modifier.padding(3.dp))
                            if (card.power != null && card.toughness != null) Text("${card.power}/${card.toughness}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xE6101615)).padding(horizontal = 3.dp, vertical = 1.dp))
                        }
                        blockingKeywords(card).forEach { kw ->
                            Text(kw, color = CounterGold, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(3.dp)).background(Panel).padding(horizontal = 3.dp))
                        }
                    }
                }
            }
        }
    }
    zoomed?.let { item -> PickZoom(item) { zoomed = null } }
}
