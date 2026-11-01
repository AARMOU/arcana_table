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

private fun GameCard.toCombatPick() = PickCard(id.toString(), name, imageUrl?.takeIf { !isFaceDown }, type, group = "Combat", groupOrder = 0, isToken = isToken)

/** Combat keywords on a creature as players say them ("FIRST_STRIKE" → "first strike"). */
internal fun combatKeywords(card: GameCard): List<String> = card.keywords.map { it.uppercase().replace(' ', '_') }
    .filter { it in CombatKeywords }.distinct().map { it.lowercase().replace('_', ' ') }

/** Attack options grouped into stacks of identical creatures; key = the stack's first creature. */
fun attackGroups(plan: ArgentumAttackPlan, piece: (String) -> CombatPiece): Map<String, List<String>> =
    plan.options.groupBy { o -> listOf(stackKey(piece(o.attackerId)), o.targets.joinToString(",")) }
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
internal fun OpponentBlockersStrip(groups: List<Pair<String, List<GameCard>>>, cardWidth: androidx.compose.ui.unit.Dp = 56.dp) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("opponent-blockers")) {
        groups.forEach { (name, cards) ->
            Text("${name.uppercase()} CAN BLOCK WITH", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { card ->
                    val shape = RoundedCornerShape(6.dp)
                    Column(Modifier.width(cardWidth)) {
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
