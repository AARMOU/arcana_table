package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arcanatable.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-port-xhdpi")
class HandDrawerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val cards = mutableStateOf((1..7).map { GameCard(it, "Hand card $it", "Creature", Color.DarkGray) })
    private val busy = mutableStateOf(false)

    private fun render(noActions: Boolean = false, palette: TableColors = darkTableColors) {
        rule.setContent {
            CompositionLocalProvider(LocalTableColors provides palette) { MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF192220))) {
                    val height = maxHeight
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        HandSheet(cards.value, false, height, null, false, true, true, false, emptyList(), 2,
                            {}, {}, {}, {}, {}, { calls += "pass" }, { calls += "stack" }, {},
                            { calls += it }, { calls += "select:${it.id}" }, { _, _ -> },
                            argentumMode = true, argentumPrimaryLabel = "PASS → ATTACKERS", legalActionCount = if (noActions) 0 else 3,
                            onUndo = if (noActions) null else ({ calls += "undo" }), onActions = { calls += "actions" },
                            onCast = { calls += "cast:${it?.id}" }, castableIds = if (noActions) emptySet() else setOf(1, 3), actionsBusy = busy.value,
                            libraryCount = 92, graveyardCount = 3, exileCount = 0,
                            onPreview = { calls += "preview:${it.id}" })
                    }
                }
            }
        } }
        rule.waitForIdle()
    }
    @Test fun allEightControlsHaveDistinctCorrectDestinationsAndCastUsesSelection() {
        render()
        for (tag in listOf("pass", "undo", "actions", "stack", "graveyard", "exile"))
            rule.onNodeWithTag("hand-$tag").performClick()
        assertEquals(listOf("pass", "undo", "actions", "stack", "GRAVEYARD", "EXILE"), calls)
        rule.onNodeWithTag("hand-cast").performClick()
        assertEquals("cast:null", calls.last()) // real integration shows legal hand choices, never blind-casts
        rule.onNodeWithText("Hand card 3").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hand-cast").performClick()
        assertEquals("cast:3", calls.last())
        rule.onNodeWithTag("hand-library").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your library").assertExists()
        rule.onNodeWithText("92 cards.", substring = true).assertExists()
        rule.onNodeWithText("CLOSE").performClick()
    }
    @Test fun busyStateDisablesWritesButAllowsPublicInspection() {
        busy.value = true; render()
        listOf("pass", "undo", "actions", "cast").forEach { rule.onNodeWithTag("hand-$it").assertIsNotEnabled() }
        rule.onNodeWithTag("hand-stack").assertIsEnabled().performClick()
        rule.onNodeWithTag("hand-graveyard").assertIsEnabled().performClick()
        assertEquals(listOf("stack", "GRAVEYARD"), calls)
    }
    @Test fun selectingUncastableCardDisablesCastAndRemovalClearsSelection() {
        render()
        rule.onNodeWithText("Hand card 2").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hand-cast").assertIsNotEnabled()
        rule.runOnIdle { cards.value = cards.value.filterNot { it.id == 2 } }
        rule.waitForIdle()
        rule.onNodeWithTag("hand-selection").assertTextEquals("Select a card · hold to inspect")
        rule.onNodeWithTag("hand-cast").assertIsEnabled()
    }
    private fun screenshot(name: String) {
        render()
        rule.onNodeWithTag("hand-cast").assertIsDisplayed()
        rule.onNodeWithTag("hand-stack").assertIsDisplayed()
        rule.onNodeWithTag("hand-exile").assertIsDisplayed()
        rule.onNodeWithText("Hand card 7").performScrollTo().assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity, "hand-owner-$name")
    }
    @Test fun referencePortrait() = screenshot("portrait")
    @Test @Config(qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCover() = screenshot("fold-cover")
    @Test @Config(qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfolded() = screenshot("fold-open")
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi") fun landscape() = screenshot("landscape")
    @Test fun changingHandSizeRendersActualCardsAndEmptyState() {
        render()
        rule.runOnIdle { cards.value = (1..24).map { GameCard(it, "Dynamic $it", "Land", Color.DarkGray) } }
        rule.waitForIdle()
        rule.onNodeWithText("Dynamic 24").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { cards.value = emptyList() }
        rule.waitForIdle()
        rule.onNodeWithText("Your hand is empty").assertExists()
        rule.onNodeWithText("Dynamic 24").assertDoesNotExist()
    }

    @Test fun noLegalChoicesCannotOpenActionsOrCast() {
        render(noActions = true)
        rule.onNodeWithTag("hand-actions").assertIsNotEnabled()
        rule.onNodeWithTag("hand-cast").assertIsNotEnabled()
        rule.onNodeWithTag("hand-undo").assertIsNotEnabled()
        rule.onNodeWithTag("hand-stack").assertIsEnabled()
    }
    @Test fun oceanPaletteRendersTheSameLiveControls() {
        render(palette = tablePaletteByName("Deep Ocean"))
        rule.onNodeWithTag("hand-actions").assertTextEquals("ACTIONS · 3")
        UiTestSupport.screenshot(rule.activity, "hand-owner-ocean")
    }
}
