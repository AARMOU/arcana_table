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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.arcanatable.engine.ai.AiDifficulty
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
@OptIn(ExperimentalMaterial3Api::class)
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
    /** Debug builds only; always empty in release, so the card never renders there. */
    testScenarios: List<TestScenario> = emptyList(),
    onTestScenario: (TestScenario) -> Unit = {},
    onPlay: () -> Unit,
    onChangeDeck: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onReturnToTable: () -> Unit,
) {
    var scenarioPickerOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = if (LocalConfiguration.current.screenHeightDp < 520) 820.dp else if (wide) 560.dp else 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioText("HomeScreen.Text.1", "Home and setup", true, if (testScenarios.isNotEmpty()) "ARCANA QA · B${BuildConfig.VERSION_CODE}" else "ARCANA TABLE", color = SoftWhite, fontSize = if (testScenarios.isNotEmpty()) 17.sp else 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (testScenarios.isNotEmpty() && !argentumActive) StudioOutlinedButton("HomeScreen.OutlinedButton.1", "Home and setup",
                    onClick = { scenarioPickerOpen = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("test-scenarios-entry")
                ) { StudioText("HomeScreen.Text.2", "Home and setup", true, "TESTS (${testScenarios.size})", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                StudioIconButton("HomeScreen.IconButton.1", "Home and setup", onClick = onSettings, modifier = Modifier.size(48.dp).tutorialTarget("home-settings").clip(CircleShape).border(1.dp, PanelLight, CircleShape)) {
                    StudioIcon("HomeScreen.Icon.1", "Home and setup", Icons.Filled.Settings, contentDescription = "Settings", tint = SoftWhite)
                }
            }
            // No developer mascot in the release-look preview.
            if (resumeAvailable) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp).testTag("resume-card")
            ) {
                StudioText("HomeScreen.Text.3", "Home and setup", false, "ARGENTUM GAME MAY STILL BE RUNNING", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                StudioText("HomeScreen.Text.4", "Home and setup", false, "Arcana closed or lost its connection during a game. It won't reconnect on its own; resume when you're ready.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton("HomeScreen.Button.1", "Home and setup", onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 48.dp).tutorialTarget("home-resume"), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { StudioText("HomeScreen.Text.5", "Home and setup", false, "RESUME GAME", fontWeight = FontWeight.Black) }
                    StudioOutlinedButton("HomeScreen.OutlinedButton.2", "Home and setup", onClick = onDismissResume, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { StudioText("HomeScreen.Text.6", "Home and setup", false, "END THAT GAME") }
                }
            }
            if (offlineResumeAvailable && !argentumActive) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                    .padding(14.dp).testTag("offline-resume-card")
            ) {
                StudioText("HomeScreen.Text.7", "Home and setup", false, "SAVED OFFLINE GAME", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                StudioText("HomeScreen.Text.8", "Home and setup", false, "Restore the complete Argentum engine state, including the stack, pending decisions and private zones.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton("HomeScreen.Button.2", "Home and setup", onClick = onResumeOffline, modifier = Modifier.weight(1f).heightIn(min = 48.dp).tutorialTarget("home-resume"),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                        StudioText("HomeScreen.Text.9", "Home and setup", false, "RESUME", fontWeight = FontWeight.Black)
                    }
                    StudioOutlinedButton("HomeScreen.OutlinedButton.3", "Home and setup", onClick = onDiscardOffline, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        StudioText("HomeScreen.Text.10", "Home and setup", false, "DISCARD SAVE")
                    }
                }
            }
            // Phone landscape: deck on the left, PLAY on the right, so nothing needs scrolling.
            val short = LocalConfiguration.current.screenHeightDp < 520
            val deckPanel: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    StudioText("HomeScreen.Text.11", "Home and setup", false, "YOUR DECK", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, PanelLight, RoundedCornerShape(20.dp)).background(Panel).testTag("home-deck").tutorialTarget("home-deck")) {
                        Box(Modifier.fillMaxWidth().height(if (short) 120.dp else if (wide) 280.dp else 230.dp).background(Color(0xFF22302C))) {
                            if (commanderArt != null) CardArt(commanderArt, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, fallbackName = deck?.commander)
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB3101615)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                StudioText("HomeScreen.Text.12", "Home and setup", true, deck?.commander?.let { "COMMANDER · ${it.uppercase()}" } ?: if (deck == null) "NO DECK YET" else "NO COMMANDER", color = TurnColors.You, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StudioText("HomeScreen.Text.13", "Home and setup", true, deck?.name ?: "Import your deck to play", color = SoftWhite, fontSize = if (short) 18.sp else 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioOutlinedButton("HomeScreen.OutlinedButton.4", "Home and setup", onClick = onChangeDeck, enabled = !argentumActive, modifier = Modifier.weight(1f).heightIn(min = 48.dp).tutorialTarget("home-change-deck")) { StudioText("HomeScreen.Text.14", "Home and setup", false, "Change deck", color = SoftWhite, fontWeight = FontWeight.Bold) }
                            // Existing unified importer: same onImport callback as the deck picker.
                            StudioOutlinedButton("HomeScreen.OutlinedButton.5", "Home and setup",
                                onClick = onImport,
                                enabled = !argentumActive,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-import"),
                            ) { StudioText("HomeScreen.Text.15", "Home and setup", false, "Import new", color = SoftWhite, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            val playButton: @Composable () -> Unit = {
                if (argentumActive) StudioButton("HomeScreen.Button.3", "Home and setup",
                    onClick = onReturnToTable, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) { StudioText("HomeScreen.Text.16", "Home and setup", false, "RETURN TO TABLE", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
                else StudioButton("HomeScreen.Button.4", "Home and setup",
                    onClick = onPlay, enabled = !working && !resumeAvailable && !offlineResumeAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("home-play").tutorialTarget("home-play"),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) { StudioText("HomeScreen.Text.17", "Home and setup", true, if (working) (workingLabel ?: "PREPARING…") else "PLAY", fontSize = if (working) 15.sp else 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
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
                StudioText("HomeScreen.Text.18", "Home and setup", true, "Build ${BuildConfig.VERSION_CODE} · Based on Build ${BuildConfig.BASE_BUILD}", color = Muted, fontSize = 12.sp)
            }
        }
    }
    if (scenarioPickerOpen && testScenarios.isNotEmpty()) ModalBottomSheet(
        onDismissRequest = { scenarioPickerOpen = false },
        containerColor = Panel,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp).testTag("test-scenarios-picker"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StudioText("HomeScreen.Text.19", "Home and setup", false, "TEST SCENARIOS · DEBUG ONLY", color = CounterGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
            StudioText("HomeScreen.Text.20", "Home and setup", false, "Choose a ready-made offline game against the AI.", color = Muted, fontSize = 13.sp)
            testScenarios.forEach { scenario ->
                StudioOutlinedButton("HomeScreen.OutlinedButton.6", "Home and setup",
                    onClick = { scenarioPickerOpen = false; onTestScenario(scenario) },
                    enabled = !working && !argentumActive,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("test-scenario-${scenario.id}"),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        StudioText("HomeScreen.Text.21", "Home and setup", true, scenario.title, color = SoftWhite, fontWeight = FontWeight.Bold)
                        StudioText("HomeScreen.Text.22", "Home and setup", true, scenario.description, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** After PLAY: offline vs AI (with the AI's deck) or online with Argentum. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlaySheet(
    deckName: String?,
    aiDecks: List<DeckChoice>,
    aiDeckId: String?,
    onAiDeck: (String?) -> Unit,
    onOffline: () -> Unit,
    aiCount: Int = 1,
    onAiCount: (Int) -> Unit = {},
    aiDifficulties: List<AiDifficulty> = List(3) { AiDifficulty.DEFAULT },
    onAiDifficulty: (seat: Int, level: AiDifficulty) -> Unit = { _, _ -> },
    onOnline: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onDismiss: () -> Unit,
    onWifiMultiHost: () -> Unit = {},
    onOnlineMultiHost: () -> Unit = {},
    onOnlineMultiJoin: () -> Unit = {},
    onWifiJoin: () -> Unit = {},
    tutorialStep: ArcanaLessonStep? = null,
) {
    var online by remember { mutableStateOf(false) }
    // All visible multiplayer choices route to the full 2–4 seat lobby.
    var friendMode by remember { mutableStateOf<Int?>(null) }
    var showAllDecks by remember { mutableStateOf(false) }
    // During a lesson the sheet opens fully, so every control a step points at is on screen.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = tutorialStep != null)) {
        Box {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StudioText("HomeScreen.Text.23", "Home and setup", false, "How do you want to play?", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
            StudioText("HomeScreen.Text.24", "Home and setup", true, deckName?.let { "Your deck: $it" } ?: "No deck yet: offline uses a quick Portal duel.", color = Muted, fontSize = 14.sp, modifier = Modifier.tutorialTarget("setup-human-deck"))
            PlayOption("Offline vs AI", "Runs on your phone. No internet needed.", selected = !online && friendMode == null, onClick = { online = false; friendMode = null }, tag = "play-offline-option") {
                StudioText("HomeScreen.Text.25", "Home and setup", false, "PLAYERS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.tutorialTarget("setup-players"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("2 · you vs AI", aiCount == 1) { onAiCount(1) }
                    if (ReleaseFeatures.multiplayerPreviewEnabled) {
                        DeckChip("3 players", aiCount == 2) { onAiCount(2) }
                        DeckChip("4 players", aiCount == 3) { onAiCount(3) }
                    }
                }
                if (!ReleaseFeatures.multiplayerPreviewEnabled) StudioText("HomeScreen.Text.26", "Home and setup", false, "Four-player support coming soon.", color = Muted, fontSize = 12.sp)
                StudioText("HomeScreen.Text.27", "Home and setup", true, if (aiCount > 1) "FIRST AI'S DECK (the others are random)" else "AI'S DECK", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.tutorialTarget("setup-ai-deck"))
                val shown = if (showAllDecks) aiDecks else aiDecks.filter { !it.builtIn || it.id in setOf("tokens_win", "artificer_v4", "ayula_bears", "sliver_overlord", "hazel_squirrels", "mabel_heir", "velociraptor_beefcake") || it.id == aiDeckId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("Random", aiDeckId == null) { onAiDeck(null) }
                    shown.forEach { d -> DeckChip(d.name, aiDeckId == d.id) { onAiDeck(d.id) } }
                    if (!showAllDecks && shown.size < aiDecks.size) DeckChip("${aiDecks.size - shown.size} more decks ▾", false) { showAllDecks = true }
                }
                StudioText("HomeScreen.Text.28", "Home and setup", false, "AI DIFFICULTY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                for (seat in 0 until aiCount.coerceIn(1, 3)) {
                    AiDifficultySlider(
                        label = if (aiCount > 1) "AI ${seat + 1}" else "AI",
                        level = aiDifficulties.getOrElse(seat) { AiDifficulty.DEFAULT },
                        onLevel = { onAiDifficulty(seat, it) },
                        tag = "play-ai-difficulty-$seat",
                    )
                }
                if (aiDifficulties.take(aiCount).any { it >= AiDifficulty.HARD })
                    StudioText("HomeScreen.Text.29", "Home and setup", false, "Hard and Brutal think longer on each turn, especially in 4-player games.", color = Muted, fontSize = 12.sp)
            }
            PlayOption("Multiplayer", "Host or join 2–4 player Commander over the internet or Wi-Fi.", selected = friendMode != null, onClick = { online = false; if (friendMode == null) friendMode = 6 }, tag = "play-wifi-option") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckChip("Host online", friendMode == 6) { online = false; friendMode = 6 }
                    DeckChip("Join online", friendMode == 7) { online = false; friendMode = 7 }
                    DeckChip("Host on Wi-Fi", friendMode == 5) { online = false; friendMode = 5 }
                    DeckChip("Join on Wi-Fi", friendMode == 2) { online = false; friendMode = 2 }
                }
            }
            StudioButton("HomeScreen.Button.5", "Home and setup",
                onClick = { when { online -> onOnline(); friendMode == 2 -> onWifiJoin(); friendMode == 5 -> onWifiMultiHost(); friendMode == 6 -> onOnlineMultiHost(); friendMode == 7 -> onOnlineMultiJoin(); else -> onOffline() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("play-start").tutorialTarget("setup-start"),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
            ) { StudioText("HomeScreen.Text.30", "Home and setup", true, when { online -> "PLAY WITH ARGENTUM"; friendMode == 2 -> "JOIN WI-FI LOBBY"; friendMode == 5 -> "HOST WI-FI LOBBY"; friendMode == 6 -> "HOST ONLINE LOBBY"; friendMode == 7 -> "JOIN ONLINE LOBBY"; aiCount > 1 -> "PLAY OFFLINE · ${aiCount + 1} PLAYERS"; else -> "PLAY OFFLINE VS AI" }, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
        }
        TutorialLayer(Modifier.matchParentSize())
        }
    }
}

@Composable
private fun PlayOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, tag: String, disabled: Boolean = false, extra: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(if (selected) 2.dp else 1.dp, if (selected) Mint else PanelLight, RoundedCornerShape(18.dp))
            .background(if (selected) PanelLight else Panel).studioClickable("HomeScreen.clickable.1", "Home and setup", onClick = onClick).padding(14.dp).testTag(tag)
            .then(if (tag == "play-offline-option") Modifier.tutorialTarget("setup-offline") else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioRadioButton("HomeScreen.RadioButton.1", "Home and setup", selected = selected, onClick = onClick, enabled = !disabled)
            Column {
                StudioText("HomeScreen.Text.31", "Home and setup", true, title, color = if (disabled) Muted.copy(alpha = .6f) else SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                StudioText("HomeScreen.Text.32", "Home and setup", true, subtitle, color = if (disabled) Muted.copy(alpha = .6f) else Muted, fontSize = 13.sp)
            }
        }
        if (selected) extra()
    }
}

/** One AI seat's difficulty: a four-step slider (Easy to Brutal) with the level's name and what it means. */
@Composable
private fun AiDifficultySlider(label: String, level: AiDifficulty, onLevel: (AiDifficulty) -> Unit, tag: String) {
    val levels = AiDifficulty.entries
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StudioText("HomeScreen.Text.33", "Home and setup", true, label, color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            StudioText("HomeScreen.Text.34", "Home and setup", true, level.label.uppercase(), color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        StudioSlider("HomeScreen.Slider.1", "Home and setup",
            value = level.ordinal.toFloat(),
            onValueChange = { onLevel(AiDifficulty.fromIndex(kotlin.math.round(it).toInt())) },
            valueRange = 0f..(levels.size - 1).toFloat(),
            steps = levels.size - 2,
            colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = PanelLight, activeTickColor = Ink, inactiveTickColor = Muted),
            modifier = Modifier.semantics { contentDescription = "$label difficulty: ${level.label}" },
        )
        StudioText("HomeScreen.Text.35", "Home and setup", true, level.blurb, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DeckChip(label: String, selected: Boolean, onClick: () -> Unit) {
    StudioText("HomeScreen.Text.36", "Home and setup", true, label, color = if (selected) Ink else SoftWhite, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (selected) Mint else Color.Transparent).border(1.dp, if (selected) Mint else PanelLight, RoundedCornerShape(22.dp))
            .studioClickable("HomeScreen.clickable.2", "Home and setup", onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp))
}

/** Pick from precons, Arcana decks, Argentum EDHREC decks, and then your other decks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckPickerSheet(decks: List<DeckChoice>, activeId: String?, onPick: (DeckChoice) -> Unit, onImport: () -> Unit, onDismiss: () -> Unit, onDeckBuilder: (() -> Unit)? = null, tutorialStep: ArcanaLessonStep? = null) {
    // During a lesson the sheet opens fully, so every control a step points at is on screen.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = tutorialStep != null)) {
        Box {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            StudioText("HomeScreen.Text.37", "Home and setup", false, "Choose your deck", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
            listOf(
                DeckChoiceCategory.PRECON to "WOTC PRECONS",
                DeckChoiceCategory.ARCANA to "ARCANA",
                DeckChoiceCategory.ARGENTUM to "ARGENTUM PREMADE DECKS",
                DeckChoiceCategory.OTHER to "YOUR OTHER DECKS"
            ).filter { ReleaseFeatures.deckImportEnabled || it.first != DeckChoiceCategory.OTHER }.forEach { (category, heading) ->
                val list = decks.filter { it.category == category }
                StudioText("HomeScreen.Text.38", "Home and setup", true, heading, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    .then(if (category == DeckChoiceCategory.PRECON) Modifier.tutorialTarget("deck-categories") else Modifier))
                if (list.isEmpty()) StudioText("HomeScreen.Text.39", "Home and setup", true, if (ReleaseFeatures.deckImportEnabled) "None yet. Import a deck and it lands here." else "No decks in this group.", color = Muted, fontSize = 13.sp)
                list.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(if (d.id == activeId) PanelLight else Panel)
                            .border(1.dp, if (d.id == activeId) Mint else PanelLight, RoundedCornerShape(12.dp)).clickable { onPick(d) }.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp)
                            .then(if (d.id == (decks.firstOrNull { it.id == activeId } ?: decks.firstOrNull())?.id) Modifier.tutorialTarget("deck-select") else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StudioText("HomeScreen.Text.40", "Home and setup", true, d.name.removeSuffix(" · WIP"), color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.name.endsWith(" · WIP")) {
                                    StudioText("HomeScreen.Text.41", "Home and setup", false, "WIP", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            d.commander?.let { StudioText("HomeScreen.Text.42", "Home and setup", true, it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (d.id == activeId) StudioText("HomeScreen.Text.43", "Home and setup", false, "PLAYING", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (ReleaseFeatures.deckImportEnabled) {
                StudioOutlinedButton("HomeScreen.OutlinedButton.7", "Home and setup", onClick = onImport, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) { StudioText("HomeScreen.Text.44", "Home and setup", false, "Import a new deck", color = SoftWhite, fontWeight = FontWeight.Bold) }
            } else StudioText("HomeScreen.Text.45", "Home and setup", false, "Deck importing coming soon.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            // Developer builds only (DeckBuilderFlag): search the library and build or edit decks.
            onDeckBuilder?.let { open ->
                StudioOutlinedButton("HomeScreen.OutlinedButton.8", "Home and setup", onClick = open, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp).testTag("open-deck-builder")) {
                    StudioText("HomeScreen.Text.46", "Home and setup", false, "Deck editor · library", color = Mint, fontWeight = FontWeight.Bold)
                }
            }
        }
        TutorialLayer(Modifier.matchParentSize())
        }
    }
}
