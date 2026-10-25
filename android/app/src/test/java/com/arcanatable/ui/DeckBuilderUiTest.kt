package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.arcanatable.DeckManagerDialog
import com.arcanatable.SavedDeck
import com.arcanatable.SupportedCardCatalog
import com.arcanatable.parseDeckList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The developer-preview deck builder on the Fold's cover screen (tabs) and unfolded (two panes):
 * search the offline library, pick a commander, add a card, and see the deck autosaved as the same
 * deck-list text the game loads.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckBuilderUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val catalog by lazy { SupportedCardCatalog.load { File("src/main/assets", it).takeIf(File::exists)?.inputStream() } }

    private fun buildDeck(name: String, folded: Boolean) {
        var decks by mutableStateOf(listOf<SavedDeck>())
        rule.setContent {
            DeckManagerDialog(decks = decks, loadCatalog = { catalog }, onDecksChanged = { decks = it }, onPlay = {}, onDismiss = {})
        }
        rule.onNodeWithTag("deck-new").performClick()
        rule.waitUntil(120_000) { rule.onAllNodes(hasTestTag("library-search")).fetchSemanticsNodes().isNotEmpty() }

        if (folded) {
            rule.onNodeWithText("Library").assertExists()
            rule.onAllNodesWithText("Stats").fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        } else {
            // Unfolded: library search and the deck's commander section are on screen together.
            rule.onNodeWithText("COMMANDER").assertExists()
        }

        rule.onNodeWithTag("library-search").performTextInput("krenko mob")
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("Krenko, Mob Boss")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("CMDR").performClick()
        UiTestSupport.settle(rule, 300)

        rule.onNodeWithTag("library-search").performTextClearance()
        rule.onNodeWithTag("library-search").performTextInput("sol ring")
        rule.waitUntil(30_000) { rule.onAllNodes(hasTestTag("add-Sol Ring")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("add-Sol Ring").performClick()

        // Autosave after a short pause.
        rule.waitUntil(10_000) { decks.isNotEmpty() && parseDeckList(decks.first().text).any { it.name == "Sol Ring" } }
        val saved = parseDeckList(decks.single().text)
        assertEquals("Krenko, Mob Boss", saved.single { it.commander }.name)
        assertEquals(1, saved.single { it.name == "Sol Ring" }.count)
        assertEquals("Krenko, Mob Boss", decks.single().commander)

        if (folded) rule.onNodeWithText("Deck 2").performClick()
        UiTestSupport.settle(rule, 500)
        assertTrue(rule.onAllNodesWithText("Sol Ring").fetchSemanticsNodes().isNotEmpty())
        UiTestSupport.screenshot(rule.activity, "deck-builder-$name")
    }

    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverBuildsAndAutosaves() = buildDeck("fold-cover", folded = true)
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfoldedBuildsAndAutosaves() = buildDeck("fold-unfolded", folded = false)
}
