package com.arcanatable

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Battlefield layout (Build 96): rows wrap instead of running off the side, identical cards share one
 * spot with a count, and a two-finger pinch zooms the cards in and out. The zoom is remembered for
 * each screen shape (phone portrait / landscape, folded / unfolded), so each keeps its own size.
 */

/** Identical permanents shown as one card with a count. */
data class CardStack(val cards: List<GameCard>) {
    val top: GameCard get() = cards.first()
    val count: Int get() = cards.size
}

/**
 * Groups identical cards: same name and state (tapped, counters, power/toughness, damage, combat role,
 * summoning sickness, keywords), keeping the board's order. Face-down cards and commanders never stack,
 * because they must stay individually recognisable.
 */
fun stackIdentical(cards: List<GameCard>, keepApart: Set<Int> = emptySet()): List<CardStack> {
    data class Key(val name: String, val tapped: Boolean, val token: Boolean, val counters: Map<String, Int>, val power: String?, val toughness: String?,
        val damage: Int, val role: String?, val sick: Boolean, val keywords: List<String>, val chosenType: String?, val image: String?)
    val groups = LinkedHashMap<Any, MutableList<GameCard>>()
    for (card in cards) {
        val key: Any = if (card.isFaceDown || card.isCommander || card.id in keepApart) card.id
            else Key(card.name, card.tapped, card.isToken, card.counters, card.power, card.toughness, card.damage, card.combatRole, card.summoningSick, card.keywords.sorted(), card.chosenCreatureType, card.imageUrl)
        groups.getOrPut(key) { mutableListOf() }.add(card)
    }
    return groups.values.map { CardStack(it) }
}

/**
 * Equipment, Auras and other attachments sit with the permanent they're attached to. Returns the cards
 * that stand on their own (hosts included) and, per host id, what is attached to it in board order.
 * An attachment whose host isn't in [cards] (an Aura on another player's permanent) stays on its own.
 * Attachments on an attachment are followed back to the permanent at the bottom of the chain.
 */
fun splitAttachments(cards: List<GameCard>): Pair<List<GameCard>, Map<Int, List<GameCard>>> {
    val byId = cards.associateBy { it.id }
    fun root(card: GameCard): Int? {
        var hostId = card.attachedTo ?: return null
        val seen = mutableSetOf(card.id)
        while (true) {
            val host = byId[hostId] ?: return null
            if (!seen.add(host.id)) return null
            hostId = host.attachedTo ?: return host.id
        }
    }
    val hostOf = cards.mapNotNull { card -> root(card)?.let { card.id to it } }.toMap()
    val attached = LinkedHashMap<Int, MutableList<GameCard>>()
    cards.forEach { card -> hostOf[card.id]?.let { attached.getOrPut(it) { mutableListOf() }.add(card) } }
    return cards.filter { it.id !in hostOf } to attached
}

const val MIN_BOARD_ZOOM = 0.45f
const val MAX_BOARD_ZOOM = 1.6f

/** The board zoom for this screen shape, saved as it changes. */
@Composable
fun rememberBoardZoom(): MutableFloatState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val key = boardZoomKey(configuration.screenWidthDp, configuration.screenHeightDp)
    val prefs = context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE)
    return remember(key) { mutableFloatStateOf(prefs.getFloat(key, 1f).coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM)) }
}

/** Screen shape buckets: portrait/landscape × narrow (phone, folded) / wide (unfolded, tablet). */
fun boardZoomKey(widthDp: Int, heightDp: Int): String =
    "board_zoom_${if (widthDp > heightDp) "landscape" else "portrait"}_${if (minOf(widthDp, heightDp) >= 600) "wide" else "narrow"}"

fun saveBoardZoom(context: Context, widthDp: Int, heightDp: Int, zoom: Float) {
    context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit().putFloat(boardZoomKey(widthDp, heightDp), zoom).apply()
}

/** Table overview zoom is shared by its player panels, but independent of full player-board zoom. */
fun tableZoomKey(widthDp: Int, heightDp: Int): String =
    "table_zoom_${if (widthDp > heightDp) "landscape" else "portrait"}_${if (minOf(widthDp, heightDp) >= 600) "wide" else "narrow"}"

@Composable
fun rememberTableZoom(): MutableFloatState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val key = tableZoomKey(configuration.screenWidthDp, configuration.screenHeightDp)
    val prefs = context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE)
    return remember(key) { mutableFloatStateOf(prefs.getFloat(key, 1f).coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM)) }
}

fun saveTableZoom(context: Context, widthDp: Int, heightDp: Int, zoom: Float) {
    context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).edit()
        .putFloat(tableZoomKey(widthDp, heightDp), zoom.coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM)).apply()
}

/**
 * Two-finger pinch on the board changes [zoom]. One finger is left alone, so scrolling, tapping and
 * holding cards work exactly as before; [onEnd] runs when the fingers lift (to save the zoom).
 */
fun Modifier.pinchToZoom(zoom: MutableFloatState, onEnd: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoomed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) {
                    zoom.floatValue = (zoom.floatValue * factor).coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM)
                    zoomed = true
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zoomed) onEnd(zoom.floatValue)
    }
}

/**
 * One type group of the battlefield ("Creatures · 5"): wrapping rows of card stacks. [card] draws a
 * single card at the given width; this adds the stack depth and the count badge around it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrappingCardStacks(stacks: List<CardStack>, cardWidth: Dp, card: @Composable (CardStack, Dp) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().testTag("battlefield-row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stacks.forEach { stack -> StackedCard(stack, cardWidth, card) }
    }
}

@Composable
private fun StackedCard(stack: CardStack, cardWidth: Dp, card: @Composable (CardStack, Dp) -> Unit) {
    if (stack.count == 1) { card(stack, cardWidth); return }
    val depth = 5.dp
    val height = cardWidth / 0.72f
    val width = if (stack.top.tapped) height else cardWidth
    Box(Modifier.width(width + depth).height(height + depth).semantics { contentDescription = "${stack.top.name}, ${stack.count} copies" }) {
        // The cards underneath, peeking out down and to the right.
        Box(Modifier.offset(depth, depth).width(width).height(height).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26332F)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)))
        Box(Modifier.offset(depth / 2, depth / 2).width(width).height(height).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E3D38)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
        card(stack, cardWidth)
        Text(
            "×${stack.count}", color = Color(0xFF101615), fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFFFD166)).padding(horizontal = 6.dp, vertical = 1.dp).testTag("stack-count")
        )
    }
}

/**
 * A permanent with Equipment/Auras attached. Collapsed, the attachments peek out above the permanent
 * and one tap fans the pile apart; fanned, every card is reachable on its own (preview, activate, tap)
 * and tapping the pile's COLLAPSE bar folds it back.
 */
@Composable
fun AttachedPile(
    host: GameCard,
    attachments: List<GameCard>,
    cardWidth: Dp,
    expanded: Boolean,
    onToggle: () -> Unit,
    glow: Boolean,
    card: @Composable (GameCard, Dp, Boolean) -> Unit,
) {
    val height = cardWidth / 0.72f
    if (expanded) {
        androidx.compose.foundation.layout.Column(
            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFFFD166).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .background(Color(0x22FFD166)).padding(4.dp).testTag("attached-pile-open")
        ) {
            Text(
                "COLLAPSE  ·  ${attachments.size + 1}", color = Color(0xFFFFD166), fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .clickable(onClickLabel = "Collapse ${host.name}") { onToggle() }
                    .padding(horizontal = 6.dp, vertical = 4.dp).testTag("attached-pile-collapse")
            )
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card(host, cardWidth, false)
                attachments.forEach { card(it, cardWidth, false) }
            }
        }
        return
    }
    val shown = attachments.take(3)
    val peek = height * 0.16f
    val width = if (host.tapped) height else cardWidth
    Box(
        Modifier.width(width).height(height + peek * shown.size)
            .semantics { contentDescription = "${host.name} with ${attachments.joinToString { it.name }} attached. Tap to spread." }
            .testTag("attached-pile")
    ) {
        // Attachments behind the permanent, the newest closest to it.
        shown.forEachIndexed { index, attached ->
            Box(Modifier.offset(y = peek * index)) { card(attached, cardWidth, false) }
        }
        Box(Modifier.offset(y = peek * shown.size)) { card(host, cardWidth, glow) }
        // Taps on a collapsed pile only spread it, so nothing underneath is acted on by accident.
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Spread ${host.name}") { onToggle() })
        Text(
            "+${attachments.size}", color = Color(0xFF101615), fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFFFD166)).padding(horizontal = 6.dp, vertical = 1.dp).testTag("attached-count")
        )
    }
}
