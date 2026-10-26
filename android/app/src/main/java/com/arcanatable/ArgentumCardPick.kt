@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arcanatable

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * One choosable thing in a card-grid pick (targets, "choose a card"): its picture when there is one,
 * and the group it is shown under, which is who controls it ("You", an opponent's name, "Players").
 */
data class PickCard(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val typeLine: String = "",
    val group: String,
    val groupOrder: Int,
    val isYou: Boolean = false,
    val isToken: Boolean = false,
    val isPlayer: Boolean = false,
    val detail: String? = null,
)

/** Lists with more entries than this get a search box. */
const val SEARCH_THRESHOLD = 8

/** Case-insensitive "contains" on every word typed, so "gob war" finds "Goblin Warrior". */
fun matchesSearch(text: String, query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = text.lowercase()
    return words.all { it in haystack }
}

@Composable
fun PickSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = query, onValueChange = { onQuery(it.take(40)) }, singleLine = true,
        placeholder = { Text("Search…") }, modifier = modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("pick-search"))
}

/**
 * Cards in a grid, grouped by who controls them. Tap a card to select or unselect it; hold one to see
 * it large. [selected] cards get a thick mint border and a tick; cards that can't be picked right now fade.
 */
@Composable
fun CardPickGrid(items: List<PickCard>, selected: Set<String>, isEnabled: (String) -> Boolean, onTap: (String) -> Unit, tileWidth: Dp = 0.dp) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    var query by remember(items.map { it.id }) { mutableStateOf("") }
    val width = if (tileWidth > 0.dp) tileWidth else (LocalConfiguration.current.screenWidthDp.dp * 0.2f).coerceIn(84.dp, 128.dp)
    Column(Modifier.fillMaxWidth().testTag("card-pick-grid")) {
        if (items.size > SEARCH_THRESHOLD) PickSearchField(query, { query = it })
        val shown = items.filter { it.id in selected || matchesSearch(it.name + " " + it.typeLine, query) }
        if (shown.isEmpty()) Text("Nothing matches \"$query\".", color = Muted, fontSize = 12.sp)
        shown.groupBy { it.groupOrder to it.group }.toSortedMap(compareBy { it.first }).forEach { (key, group) ->
            Text(key.second.uppercase(), color = if (group.first().isYou) TurnColors.You else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { item ->
                    PickTile(item, item.id in selected, isEnabled(item.id), width, onTap = { onTap(item.id) }, onHold = { zoomed = item })
                }
            }
        }
    }
    zoomed?.let { item -> PickZoom(item) { zoomed = null } }
}

@Composable
private fun PickTile(item: PickCard, isSelected: Boolean, enabled: Boolean, width: Dp, onTap: () -> Unit, onHold: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.width(width).alpha(if (enabled || isSelected) 1f else 0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.716f).clip(shape).background(Color(0xFF1C2523))
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Mint else Color.White.copy(alpha = 0.25f), shape)
            .combinedClickable(enabled = true, onClick = { if (enabled || isSelected) onTap() }, onLongClick = onHold)
            .testTag("pick-${item.id}")) {
            PickFace(item, Modifier.fillMaxSize())
            if (isSelected) Text("✓", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Mint))
            if (item.isToken) Text("TOKEN", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xDD15221D)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Text(item.name, color = if (isSelected) Mint else SoftWhite, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
        item.detail?.let { Text(it, color = Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
    }
}

/** The picture, or a readable text card for tokens, players and anything without art. */
@Composable
fun PickFace(item: PickCard, modifier: Modifier) {
    Box(modifier) {
        if (!item.imageUrl.isNullOrBlank()) CardArt(item.imageUrl, Modifier.fillMaxSize(), fallbackName = item.name, fallbackType = item.typeLine)
        else Column(Modifier.fillMaxSize().background(if (item.isPlayer) Color(0xFF263B35) else Color(0xFF2A2F3A)).padding(6.dp),
            verticalArrangement = if (item.isPlayer) Arrangement.Center else Arrangement.SpaceBetween,
            horizontalAlignment = if (item.isPlayer) Alignment.CenterHorizontally else Alignment.Start) {
            Text(if (item.isPlayer) "PLAYER" else item.name, color = if (item.isPlayer) Muted else SoftWhite, fontSize = if (item.isPlayer) 9.sp else 11.sp, fontWeight = FontWeight.Black, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(if (item.isPlayer) item.name else item.typeLine, color = if (item.isPlayer) SoftWhite else Muted, fontSize = if (item.isPlayer) 13.sp else 9.sp,
                fontWeight = if (item.isPlayer) FontWeight.Black else FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = if (item.isPlayer) TextAlign.Center else TextAlign.Start)
        }
    }
}

/** Big view of one card on top of the current dialog; tap anywhere to close. */
@Composable
fun PickZoom(item: PickCard, onDismiss: () -> Unit) {
    val screen = LocalConfiguration.current
    val width = minOf(screen.screenWidthDp * 0.82f, screen.screenHeightDp * 0.6f, 380f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = LocalView.current.parent as? DialogWindowProvider
        SideEffect { window?.window?.setDimAmount(0.7f); window?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT)) }
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PickFace(item, Modifier.width(width).aspectRatio(0.716f).clip(RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)))
                Text("Tap anywhere to close", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun ArgentumStackItem.toPick() = PickCard(id, name, imageUrl, group = "Stack", groupOrder = 0, isYou = isYours, detail = controllerName)

/**
 * The stack as a small pile of cards at the right edge of the table, shown only while something is on
 * it. The top item sits in front, older ones peek out above it; each is edged in its controller's
 * colour. Tap a card to see it large, tap the header to open the Stack window.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.StackPile(stack: List<ArgentumStackItem>, onOpenStack: () -> Unit) {
    if (stack.isEmpty()) return
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    val cardWidth = 72.dp
    val peek = 22.dp
    val shown = stack.take(5).reversed() // oldest first, so the top item is drawn last (in front)
    Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(cardWidth + 8.dp).testTag("stack-pile").tutorialTarget("argentum-stack"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("STACK · ${stack.size}", color = CounterGold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xE6101615)).clickable(onClick = onOpenStack).padding(horizontal = 6.dp, vertical = 3.dp))
        Box(Modifier.width(cardWidth).padding(top = 4.dp)) {
            shown.forEachIndexed { i, item ->
                val shape = RoundedCornerShape(6.dp)
                Box(Modifier.padding(top = peek * i).width(cardWidth).aspectRatio(0.716f).clip(shape).background(Color(0xFF1C2523))
                    .border(2.dp, TurnColors.forSeat(item.isYours), shape).clickable { zoomed = item.toPick() }) {
                    PickFace(item.toPick(), Modifier.fillMaxSize())
                }
            }
        }
        stack.first().let { top ->
            val line = listOfNotNull(top.controllerName.takeIf { it.isNotBlank() }, top.targetNames.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "→ ")).joinToString(" ")
            if (line.isNotBlank()) Text(line, color = SoftWhite, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xE6101615)).padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
    zoomed?.let { PickZoom(it) { zoomed = null } }
}

/** The Stack window's list: top first, each with its picture, who cast it and its targets. Tap a picture to see it large. */
@Composable
fun StackCardList(stack: List<ArgentumStackItem>) {
    var zoomed by remember { mutableStateOf<PickCard?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stack.forEachIndexed { i, item ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(70.dp).aspectRatio(0.716f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1C2523))
                    .border(2.dp, TurnColors.forSeat(item.isYours), RoundedCornerShape(6.dp)).clickable { zoomed = item.toPick() }) {
                    PickFace(item.toPick(), Modifier.fillMaxSize())
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(if (i == 0) "TOP · resolves next" else "#${i + 1}", color = if (i == 0) CounterGold else Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(item.name, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (item.controllerName.isNotBlank()) Text(item.controllerName, color = TurnColors.forSeat(item.isYours), fontSize = 11.sp)
                    if (item.targetNames.isNotEmpty()) Text("→ " + item.targetNames.joinToString(", "), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
    zoomed?.let { PickZoom(it) { zoomed = null } }
}

/**
 * Adds a decision's own card info (name, type, picture) to a pick card. Library cards (searches like
 * Green Sun's Zenith) are not in any table zone, so the decision's `cardInfo` / `cards` map is the
 * only place their details live; when it has no picture, fall back to Scryfall's art by exact name.
 */
fun PickCard.withDecisionInfo(info: org.json.JSONObject?, nameKnown: Boolean): PickCard {
    fun str(key: String) = info?.optString(key)?.takeUnless { it.isBlank() || it == "null" }
    val shownName = if (!nameKnown) str("name") ?: name else name
    val image = imageUrl ?: str("imageUri") ?: artByName(shownName, isToken)
    return copy(name = shownName, imageUrl = image, typeLine = typeLine.ifBlank { str("typeLine").orEmpty() })
}

/** Library-search picks are grouped under one heading so the grid reads as "your library". */
fun libraryPick(id: String, info: org.json.JSONObject?, fallbackName: String): PickCard =
    PickCard(id, fallbackName, null, group = "Library", groupOrder = 0, isYou = true).withDecisionInfo(info, nameKnown = false)
