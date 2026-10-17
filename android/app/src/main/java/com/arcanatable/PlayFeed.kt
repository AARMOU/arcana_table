package com.arcanatable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** One thing someone played: a spell cast, a land played, or an ability. */
data class PlayedCard(val key: Int, val name: String, val imageUrl: String?, val byYou: Boolean, val kind: Kind, val detail: String) {
    enum class Kind { SPELL, LAND, ABILITY }
}

/** What happened on the AI's turn, for the one-line recap when your turn starts. */
data class TurnRecap(val plays: List<String> = emptyList(), val attackers: Int = 0, val lifeLost: Int = 0, val died: List<String> = emptyList()) {
    val isEmpty: Boolean get() = plays.isEmpty() && attackers == 0 && lifeLost == 0 && died.isEmpty()
    fun line(opponent: String): String {
        val parts = mutableListOf<String>()
        if (plays.isNotEmpty()) parts += "played " + plays.groupingBy { it }.eachCount().entries.take(6).joinToString(", ") { (n, c) -> if (c > 1) "$n ×$c" else n } + if (plays.distinct().size > 6) ", …" else ""
        if (attackers > 0) parts += "attacked with $attackers"
        if (lifeLost > 0) parts += "you lost $lifeLost life"
        if (died.isNotEmpty()) parts += "died: " + died.take(4).joinToString(", ") + if (died.size > 4) ", …" else ""
        return "$opponent's turn: " + parts.ifEmpty { listOf("nothing happened") }.joinToString("; ") + "."
    }
}

/**
 * Turns Argentum's client events into the played-card feed. Tokens are left out of the feed (lands
 * are kept); abilities and triggers become short banners instead of feed entries.
 */
object PlayFeedParser {
    private var counter = 0

    fun parse(events: JSONArray?, cards: JSONObject?): List<PlayedCard> {
        if (events == null) return emptyList()
        val out = mutableListOf<PlayedCard>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            when (e.optString("type")) {
                "spellCast" -> {
                    val card = cards?.optJSONObject(e.optString("spellId"))
                    if (card?.optBoolean("isToken") == true) continue
                    out += PlayedCard(++counter, e.optString("spellName"), card.image(), e.optBoolean("isYours"), PlayedCard.Kind.SPELL, e.optString("description"))
                }
                "permanentEntered" -> {
                    val card = cards?.optJSONObject(e.optString("cardId"))
                    val land = card?.optString("typeLine").orEmpty().contains("Land", ignoreCase = true) && !card!!.optString("typeLine").contains("Creature", ignoreCase = true)
                    if (!land || card?.optBoolean("isToken") == true) continue
                    out += PlayedCard(++counter, e.optString("cardName"), card.image(), e.optBoolean("isYours"), PlayedCard.Kind.LAND, e.optString("description"))
                }
                "abilityActivated", "abilityTriggered" -> {
                    val text = e.optString("abilityDescription").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    out += PlayedCard(++counter, e.optString("sourceName"), cards?.optJSONObject(e.optString("sourceId")).image(), e.optBoolean("isYours"), PlayedCard.Kind.ABILITY,
                        (if (e.optString("type") == "abilityTriggered") "Trigger: " else "") + text)
                }
            }
        }
        return out
    }

    /** Adds this update's events to the running recap of the opponent's turn. */
    fun recap(events: JSONArray?, cards: JSONObject?, youId: String?, so: TurnRecap): TurnRecap {
        if (events == null) return so
        var r = so
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            when (e.optString("type")) {
                "spellCast" -> if (!e.optBoolean("isYours")) r = r.copy(plays = r.plays + e.optString("spellName"))
                "permanentEntered" -> {
                    val type = cards?.optJSONObject(e.optString("cardId"))?.optString("typeLine").orEmpty()
                    if (!e.optBoolean("isYours") && type.contains("Land", true) && !type.contains("Creature", true)) r = r.copy(plays = r.plays + e.optString("cardName"))
                }
                "creatureAttacked" -> r = r.copy(attackers = r.attackers + 1)
                "lifeChanged" -> if (e.optBoolean("isYours") || (youId != null && e.optString("playerId") == youId)) {
                    val change = e.optInt("newLife") - e.optInt("oldLife")
                    if (change < 0) r = r.copy(lifeLost = r.lifeLost - change)
                }
                "creatureDied" -> e.optString("creatureName").takeIf(String::isNotBlank)?.let { r = r.copy(died = r.died + it) }
            }
        }
        return r
    }

    private fun JSONObject?.image(): String? = this?.optString("imageUri")?.takeUnless { it.isBlank() || it == "null" }
}

/**
 * The opponent's card, big, for about 1.5 s: tap to dismiss, hold to keep reading it. Below it
 * (top-left of the table) the last few cards anyone played, and abilities as a small banner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.PlayFeedOverlay(popup: PlayedCard?, feed: List<PlayedCard>, banner: PlayedCard?, recap: String?, onDismissPopup: () -> Unit, onPreview: (PlayedCard) -> Unit,
    feedOpen: Boolean = true, onToggleFeed: () -> Unit = {}, popupDurationSeconds: Float = 1.5f) {
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(popup?.key, holding, popupDurationSeconds) { if (popup != null && !holding) { delay((popupDurationSeconds.coerceIn(1f, 3f) * 1000).toLong()); onDismissPopup() } }
    AnimatedVisibility(visible = popup != null, enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)), exit = fadeOut(tween(250)),
        modifier = Modifier.align(Alignment.Center).testTag("played-card-popup")) {
        val shown = popup ?: return@AnimatedVisibility
        Column(Modifier.width(230.dp).pointerInput(shown.key) {
            detectTapGestures(onTap = { onDismissPopup() }, onPress = { holding = true; tryAwaitRelease(); holding = false })
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (shown.byYou) "YOU PLAYED" else "OPPONENT PLAYED", color = TurnColors.forSeat(shown.byYou), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Box(Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(12.dp)).border(2.dp, TurnColors.forSeat(shown.byYou), RoundedCornerShape(12.dp)).background(Color(0xFF1C2523))) {
                if (shown.imageUrl != null) CardArt(shown.imageUrl, Modifier.fillMaxWidth().aspectRatio(0.716f), contentScale = ContentScale.Crop, fallbackName = shown.name)
                else Text(shown.name, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center).padding(12.dp))
            }
            Text(shown.detail, color = SoftWhite, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC101615)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
    // The strip can be folded into a small tab at the screen edge, and opened again from there.
    if (feed.isNotEmpty() && !feedOpen) Text("›  ${feed.size.coerceAtMost(5)}", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(Color(0xE6101615))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .combinedClickable(onClick = onToggleFeed).padding(horizontal = 8.dp, vertical = 14.dp).testTag("played-card-feed-tab"))
    if (feed.isNotEmpty() && feedOpen) Column(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).testTag("played-card-feed"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("‹ hide", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xCC101615)).combinedClickable(onClick = onToggleFeed).padding(vertical = 6.dp).testTag("played-card-feed-hide"))
        feed.takeLast(5).reversed().forEach { item ->
            Box(Modifier.width(44.dp).aspectRatio(0.716f).clip(RoundedCornerShape(5.dp)).border(1.5.dp, TurnColors.forSeat(item.byYou), RoundedCornerShape(5.dp)).background(Color(0xFF1C2523))
                .combinedClickable(onClick = { onPreview(item) }, onLongClick = { onPreview(item) })) {
                if (item.imageUrl != null) CardArt(item.imageUrl, Modifier.fillMaxWidth().aspectRatio(0.716f), contentScale = ContentScale.Crop, fallbackName = item.name)
                else Text(item.name, color = SoftWhite, fontSize = 7.sp, maxLines = 4, modifier = Modifier.padding(2.dp))
            }
        }
    }
    AnimatedVisibility(visible = banner != null, enter = fadeIn(tween(120)), exit = fadeOut(tween(300)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).testTag("ability-banner")) {
        val shown = banner ?: return@AnimatedVisibility
        Text("${shown.name}: ${shown.detail}", color = SoftWhite, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xEE101615)).border(1.dp, TurnColors.forSeat(shown.byYou), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
    }
    AnimatedVisibility(visible = recap != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(400)), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).testTag("turn-recap")) {
        Text(recap.orEmpty(), color = SoftWhite, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 380.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xF0101615)).border(1.dp, TurnColors.forSeat(false), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
