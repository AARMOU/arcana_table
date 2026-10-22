package com.arcanatable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

enum class DeckChoiceCategory { PRECON, ARCANA, ARGENTUM, OTHER }

internal fun argentumDeckCategory(deckId: String): DeckChoiceCategory = when {
    deckId == "animated_army" -> DeckChoiceCategory.PRECON
    deckId.startsWith("edhrec_") -> DeckChoiceCategory.ARGENTUM
    else -> DeckChoiceCategory.ARCANA
}

/** A deck you can play: one of yours, a precon, or an Argentum premade deck. */
data class DeckChoice(
    val id: String,
    val name: String,
    val commander: String?,
    val builtIn: Boolean,
    val category: DeckChoiceCategory = DeckChoiceCategory.OTHER
)

data class AppPreconDeck(val id: String, val name: String, val commander: String, val decklist: String)

/**
 * Home: your deck at the top, one PLAY button, small footer. PLAY opens [PlaySheet] (offline vs AI
 * or online with Argentum). Approved design, Build 97.
 */
@Composable
fun ArcanaHomeScreen(
    wide: Boolean,
    deck: DeckChoice?,
    commanderArt: String?,
    argentumActive: Boolean,
    working: Boolean,
    workingLabel: String?,
    resumeAvailable: Boolean,
    onResume: () -> Unit,
    onDismissResume: () -> Unit,
    offlineResumeAvailable: Boolean = false,
    onResumeOffline: () -> Unit = {},
    onDiscardOffline: () -> Unit = {},
    onPlay: () -> Unit,
    onChangeDeck: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onReturnToTable: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = if (LocalConfiguration.current.screenHeightDp < 520) 820.dp else if (wide) 560.dp else 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ARCANA TABLE", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, PanelLight, CircleShape)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = SoftWhite)
                }
            }
            if (resumeAvailable) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp).testTag("resume-card")
            ) {
                Text("ARGENTUM GAME MAY STILL BE RUNNING", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("Arcana closed or lost its connection during a game. It won't reconnect on its own; resume when you're ready.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("RESUME GAME", fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = onDismissResume, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("END THAT GAME") }
                }
            }
            if (offlineResumeAvailable && !argentumActive) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                    .padding(14.dp).testTag("offline-resume-card")
            ) {
                Text("SAVED OFFLINE GAME", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("Restore the complete Argentum engine state, including the stack, pending decisions and private zones.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResumeOffline, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                        Text("RESUME", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(onClick = onDiscardOffline, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("DISCARD SAVE")
                    }
                }
            }
            // Phone landscape: deck on the left, PLAY on the right, so nothing needs scrolling.
            val short = LocalConfiguration.current.screenHeightDp < 520
            val deckPanel: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    Text("YOUR DECK", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, PanelLight, RoundedCornerShape(20.dp)).background(Panel).testTag("home-deck")) {
                        Box(Modifier.fillMaxWidth().height(if (short) 120.dp else if (wide) 280.dp else 230.dp).background(Color(0xFF22302C))) {
                            if (commanderArt != null) CardArt(commanderArt, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, fallbackName = deck?.commander)
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB3101615)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(deck?.commander?.let { "COMMANDER · ${it.uppercase()}" } ?: if (deck == null) "NO DECK YET" else "NO COMMANDER", color = TurnColors.You, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(deck?.name ?: "Import your deck to play", color = SoftWhite, fontSize = if (short) 18.sp else 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onChangeDeck, enabled = !argentumActive, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Change deck", color = SoftWhite, fontWeight = FontWeight.Bold) }
                            // Import is parked for now (plumbing kept in onImport): greyed, tap says so.
                            val homeContext = androidx.compose.ui.platform.LocalContext.current
                            OutlinedButton(
                                onClick = { android.widget.Toast.makeText(homeContext, "Not yet available", android.widget.Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-import"),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PanelLight.copy(alpha = 0.5f)),
                            ) { Text("Import new", color = Muted.copy(alpha = 0.6f), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            val playButton: @Composable () -> Unit = {
                if (argentumActive) Button(
                    onClick = onReturnToTable, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) { Text("RETURN TO TABLE", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
                else Button(
                    onClick = onPlay, enabled = !working && !resumeAvailable && !offlineResumeAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("home-play"),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) { Text(if (working) (workingLabel ?: "PREPARING…") else "PLAY", fontSize = if (working) 15.sp else 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
            }
            if (short) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                deckPanel(Modifier.weight(1.2f))
                Column(Modifier.weight(1f)) { playButton() }
            } else {
                deckPanel(Modifier.fillMaxWidth())
                playButton()
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("Build ${BuildConfig.VERSION_CODE} · Based on Build ${BuildConfig.BASE_BUILD}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

/** After PLAY: offline vs AI (with the AI's deck) or online with Argentum. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaySheet(
    deckName: String?,
    aiDecks: List<DeckChoice>,
    aiDeckId: String?,
    onAiDeck: (String?) -> Unit,
    onOffline: () -> Unit,
    aiCount: Int = 1,
    onAiCount: (Int) -> Unit = {},
    onOnline: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onDismiss: () -> Unit,
    onWifiHost: () -> Unit = {},
    onWifiJoin: () -> Unit = {},
    onOnlineHost: () -> Unit = {},
    onOnlineJoin: () -> Unit = {},
) {
    var online by remember { mutableStateOf(false) }
    // Play with a friend: null = not chosen; 1 host on Wi-Fi, 2 join on Wi-Fi, 3 host online, 4 join online.
    var friendMode by remember { mutableStateOf<Int?>(null) }
    var showAllDecks by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("How do you want to play?", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(deckName?.let { "Your deck: $it" } ?: "No deck yet: offline uses a quick Portal duel.", color = Muted, fontSize = 14.sp)
            PlayOption("Offline vs AI", "Runs on your phone. No internet needed.", selected = !online && friendMode == null, onClick = { online = false; friendMode = null }, tag = "play-offline-option") {
                Text("PLAYERS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("2 · you vs AI", aiCount == 1) { onAiCount(1) }
                    DeckChip("3 players", aiCount == 2) { onAiCount(2) }
                    DeckChip("4 players", aiCount == 3) { onAiCount(3) }
                }
                Text(if (aiCount > 1) "FIRST AI'S DECK (the others are random)" else "AI'S DECK", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                val shown = if (showAllDecks) aiDecks else aiDecks.filter { !it.builtIn || it.id in setOf("tokens_win", "artificer_v4", "ayula_bears", "sliver_overlord", "hazel_squirrels", "mabel_heir", "velociraptor_beefcake") || it.id == aiDeckId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("Random", aiDeckId == null) { onAiDeck(null) }
                    shown.forEach { d -> DeckChip(d.name, aiDeckId == d.id) { onAiDeck(d.id) } }
                    if (!showAllDecks && shown.size < aiDecks.size) DeckChip("${aiDecks.size - shown.size} more decks ▾", false) { showAllDecks = true }
                }
            }
            PlayOption("Play with a friend", "Two phones. One hosts (runs the game), the other joins. Same Wi-Fi, or online with a room code. Commander decks.", selected = friendMode != null, onClick = { online = false; if (friendMode == null) friendMode = 1 }, tag = "play-wifi-option") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("Host on Wi-Fi", friendMode == 1) { online = false; friendMode = 1 }
                    DeckChip("Join on Wi-Fi", friendMode == 2) { online = false; friendMode = 2 }
                    DeckChip("Host online", friendMode == 3) { online = false; friendMode = 3 }
                    DeckChip("Join online", friendMode == 4) { online = false; friendMode = 4 }
                }
            }
            // Argentum server play parked for now (Build 113); plumbing kept.
            val context = LocalContext.current
            PlayOption("Online with Argentum", "Not yet available", selected = false, onClick = { Toast.makeText(context, "Not yet available", Toast.LENGTH_SHORT).show() }, tag = "play-online-option", disabled = true) {}
            Button(
                onClick = { when { online -> onOnline(); friendMode == 1 -> onWifiHost(); friendMode == 2 -> onWifiJoin(); friendMode == 3 -> onOnlineHost(); friendMode == 4 -> onOnlineJoin(); else -> onOffline() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("play-start"),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
            ) { Text(when { online -> "PLAY WITH ARGENTUM"; friendMode == 1 -> "HOST A WI-FI GAME"; friendMode == 2 -> "JOIN A WI-FI GAME"; friendMode == 3 -> "HOST AN ONLINE GAME"; friendMode == 4 -> "JOIN AN ONLINE GAME"; aiCount > 1 -> "PLAY OFFLINE · ${aiCount + 1} PLAYERS"; else -> "PLAY OFFLINE VS AI" }, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun PlayOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, tag: String, disabled: Boolean = false, extra: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(if (selected) 2.dp else 1.dp, if (selected) Mint else PanelLight, RoundedCornerShape(18.dp))
            .background(if (selected) PanelLight else Panel).clickable(onClick = onClick).padding(14.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = !disabled)
            Column {
                Text(title, color = if (disabled) Muted.copy(alpha = .6f) else SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = if (disabled) Muted.copy(alpha = .6f) else Muted, fontSize = 13.sp)
            }
        }
        if (selected) extra()
    }
}

@Composable
private fun DeckChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Ink else SoftWhite, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (selected) Mint else Color.Transparent).border(1.dp, if (selected) Mint else PanelLight, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp))
}

/** Pick from precons, Arcana decks, Argentum EDHREC decks, and then your other decks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckPickerSheet(decks: List<DeckChoice>, activeId: String?, onPick: (DeckChoice) -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Choose your deck", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
            listOf(
                DeckChoiceCategory.PRECON to "WOTC PRECONS",
                DeckChoiceCategory.ARCANA to "ARCANA",
                DeckChoiceCategory.ARGENTUM to "ARGENTUM PREMADE DECKS",
                DeckChoiceCategory.OTHER to "YOUR OTHER DECKS"
            ).forEach { (category, heading) ->
                val list = decks.filter { it.category == category }
                Text(heading, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                if (list.isEmpty()) Text("None yet. Import a deck and it lands here.", color = Muted, fontSize = 13.sp)
                list.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(if (d.id == activeId) PanelLight else Panel)
                            .border(1.dp, if (d.id == activeId) Mint else PanelLight, RoundedCornerShape(12.dp)).clickable { onPick(d) }.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            d.commander?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (d.id == activeId) Text("PLAYING", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) { Text("Import a new deck", color = SoftWhite, fontWeight = FontWeight.Bold) }
        }
    }
}
