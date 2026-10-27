package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class AdjustableAction(val id: String, val label: String)

@Composable
internal fun AdjustUiPage(
    orientationName: String,
    cardSize: Float, onCardSize: (Float) -> Unit,
    spacing: Float, onSpacing: (Float) -> Unit,
    handHeight: Float, onHandHeight: (Float) -> Unit,
    footerScale: Float, onFooterScale: (Float) -> Unit,
    handScale: Float, onHandScale: (Float) -> Unit,
    arrangement: HandArrangement, onArrangement: (HandArrangement) -> Unit,
    actions: List<String>, onActions: (List<String>) -> Unit,
    popup: Boolean, onPopup: (Boolean) -> Unit,
    feed: Boolean, onFeed: (Boolean) -> Unit,
    banners: Boolean, onBanners: (Boolean) -> Unit,
    recap: Boolean, onRecap: (Boolean) -> Unit,
    turnBanner: Boolean, onTurnBanner: (Boolean) -> Unit,
    turnBuzz: Boolean, onTurnBuzz: (Boolean) -> Unit,
    stackCards: Boolean, onStackCards: (Boolean) -> Unit,
    respondGlow: Boolean, onRespondGlow: (Boolean) -> Unit,
    popupSeconds: Float, onPopupSeconds: (Float) -> Unit,
    combatSize: Float, onCombatSize: (Float) -> Unit,
    onReset: () -> Unit, onDismiss: () -> Unit,
    stackPile: Boolean = true, onStackPile: (Boolean) -> Unit = {},
    autoOpenHand: Boolean = true, onAutoOpenHand: (Boolean) -> Unit = {},
) {
    val catalog = listOf(
        AdjustableAction("GRAVEYARD", "Graveyard"), AdjustableAction("EXILE", "Exile"),
        AdjustableAction("STACK", "Stack"), AdjustableAction("LOG", "Game log"),
        AdjustableAction("CAST_COMMANDER", "Cast commander"), AdjustableAction("PASS_TO_TURN", "Pass to my turn"),
        AdjustableAction("UNDO", "Undo"), AdjustableAction("HAND", "Hand"),
    )
    var addOpen by remember { mutableStateOf(false) }
    val label = { id: String -> catalog.firstOrNull { it.id == id }?.label ?: id }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f), shape = RoundedCornerShape(18.dp), color = Panel) {
          Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ADJUST UI · $orientationName", color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("Changes save as you make them", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, "Close", tint = Muted) }
                }
                // A live sample stays visible while the controls below scroll.
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101917)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR TURN · MAIN", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("40 life", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Text("YOUR BATTLEFIELD", color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((5 * spacing).coerceIn(2f, 12f).dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        listOf("CREATURE", "ARTIFACT", "LAND").forEach { title ->
                            Box(Modifier.width((48 * cardSize).coerceIn(34f, 64f).dp).height((67 * cardSize).coerceIn(48f, 90f).dp)
                                .clip(RoundedCornerShape(6.dp)).background(PanelLight).border(1.dp, Mint.copy(alpha = .45f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(title, color = SoftWhite, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                                    if (stackCards && title == "CREATURE") Text("×2", color = CounterGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(actions, key = { "sample-$it" }) { id -> Text(label(id).uppercase(), color = SoftWhite, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(PanelLight).padding(horizontal = 6.dp, vertical = 5.dp)) }
                    }
                    if (feed || banners || recap || turnBanner || popup) {
                        Text(buildList {
                            if (popup) add("AI PLAY POP-UP · ${"%.1f".format(popupSeconds)} SEC")
                            if (feed) add("RECENT PLAYS")
                            if (banners) add("ABILITY BANNER")
                            if (recap) add("AI TURN RECAP")
                            if (turnBanner) add("TURN BANNER")
                            if (turnBuzz) add("TURN BUZZ")
                        }.joinToString("   ·   "), color = CounterGold, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().height((30 + handHeight * 24).dp).padding(top = 5.dp).clip(RoundedCornerShape(6.dp)).background(PanelLight), verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR HAND · 5 CARDS", color = SoftWhite, fontSize = (8 * handScale).sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("PASS     UNDO     ACTIONS", color = if (respondGlow) CounterGold else Mint, fontSize = 7.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp).verticalScroll(rememberScrollState())) {
                    Box(Modifier.tutorialTarget("layout-sizes")) { AdjustSection("TABLE SIZE") }
                    AdjustSlider("Card size", cardSize, .75f..1.4f, { "${(it * 100).toInt()}%" }, onCardSize)
                    AdjustSlider("Board spacing", spacing, .5f..2f, { "${(it * 100).toInt()}%" }, onSpacing)
                    AdjustSlider("Hand tray height", handHeight, .38f.. .95f, { "${(it * 100).toInt()}%" }, onHandHeight)
                    AdjustSlider("Bottom button size", footerScale, .78f..1.3f, { "${(it * 100).toInt()}%" }, onFooterScale)
                    AdjustSlider("Hand card size", handScale, .7f..1.4f, { "${(it * 100).toInt()}%" }, onHandScale)
                    Text("Hand arrangement", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { HandArrangement.entries.forEach { item ->
                        FilterChip(selected = item == arrangement, onClick = { onArrangement(item) }, label = { Text(item.label, fontSize = 9.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint, selectedLabelColor = Ink, labelColor = SoftWhite))
                    } }
                    Box(Modifier.tutorialTarget("layout-quick-actions")) { AdjustSection("QUICK ACTIONS") }
                    Text("Choose and order the shortcuts shown during an Argentum game.", color = Muted, fontSize = 10.sp)
                    actions.forEachIndexed { index, id ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label(id), color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { if (index > 0) onActions(actions.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { if (index < actions.lastIndex) onActions(actions.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < actions.lastIndex) { Text("↓") }
                            TextButton(onClick = { onActions(actions - id) }) { Text("REMOVE", color = Color(0xFFE9978B), fontSize = 9.sp) }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { addOpen = true }, enabled = actions.size < catalog.size, modifier = Modifier.fillMaxWidth()) { Text("ADD ACTION +") }
                        DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                            catalog.filter { it.id !in actions }.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { addOpen = false; onActions(actions + item.id) }) }
                        }
                    }
                    AdjustSection("GAME DISPLAY")
                    AdjustToggle("Show played card pop-up", "Show a card when the AI plays it.", popup, onPopup)
                    AdjustSlider("Notice time", popupSeconds, 1f..8f, { "${"%.1f".format(it)} sec" }, onPopupSeconds)
                    AdjustToggle("Recent-plays strip", "Show or hide the strip of recent cards.", feed, onFeed)
                    AdjustToggle("Ability and trigger banners", "Show short notices when abilities happen.", banners, onBanners)
                    AdjustToggle("Stack on the table", "Show what's on the stack as cards at the edge of the table.", stackPile, onStackPile)
                    AdjustToggle("AI turn recap", "Summarize the AI's turn when yours begins.", recap, onRecap)
                    AdjustToggle("Open hand on your turn", "Open your hand tray automatically when your turn starts.", autoOpenHand, onAutoOpenHand)
                    AdjustToggle("Turn banner", "Show a banner when a turn begins.", turnBanner, onTurnBanner)
                    AdjustToggle("Buzz on your turn", "Give a short buzz when your turn begins.", turnBuzz, onTurnBuzz)
                    AdjustToggle("Stack identical cards", "Show matching battlefield cards as one stack.", stackCards, onStackCards)
                    AdjustToggle("Glow when you can respond", "Highlight the pass button when Argentum is waiting for your response.", respondGlow, onRespondGlow)
                    AdjustSlider("Combat card size", combatSize, .65f..1.35f, { "${(it * 100).toInt()}%" }, onCombatSize)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReset, modifier = Modifier.weight(1f).heightIn(min = 48.dp).tutorialTarget("layout-reset")) { Text("RESET TO DEFAULT", color = Color(0xFFE5B2A7), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelLight)) { Text("DONE", color = Mint, fontWeight = FontWeight.Black) }
                }
            }
            TutorialLayer(Modifier.matchParentSize())
          }
        }
    }
}

@Composable private fun AdjustSection(title: String) = Text(title, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, modifier = Modifier.padding(top = 13.dp, bottom = 3.dp))

@Composable private fun AdjustToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { Text(title, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 9.sp, lineHeight = 12.sp) }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Mint))
    }
}

@Composable private fun AdjustSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, color = SoftWhite, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(valueLabel(value), color = Mint, fontSize = 10.sp) }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = PanelLight))
    }
}
