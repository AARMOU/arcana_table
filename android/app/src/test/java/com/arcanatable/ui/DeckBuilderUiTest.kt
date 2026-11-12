package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle() // past the autosave pause (virtual clock)

        rule.onNodeWithTag("library-search").performTextReplacement("sol ring")
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle() // past the search debounce
        rule.waitUntil(30_000) { rule.onAllNodes(hasTestTag("add-Sol Ring")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("add-Sol Ring").performClick()

        // Autosave after a short pause.
        rule.mainClock.advanceTimeBy(1_000)
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

    /** Deck library tiles, search, delete with undo; then the FILTERS panel and the card grid. */
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun libraryTilesAndFiltersPanel() {
        var decks by mutableStateOf(listOf(
            SavedDeck("Goblins", "Commander\n1 Krenko, Mob Boss\nDeck\n1 Sol Ring\n", "Krenko, Mob Boss", updatedAt = System.currentTimeMillis(), tags = listOf("Casual")),
            SavedDeck("Lands", "Deck\n1 Forest\n", updatedAt = 1L),
        ))
        rule.setContent {
            DeckManagerDialog(decks = decks, loadCatalog = { catalog }, onDecksChanged = { decks = it }, onPlay = {}, onDismiss = {})
        }
        rule.waitUntil(120_000) { rule.onAllNodes(hasText("Ready") or hasText("to fix", substring = true)).fetchSemanticsNodes().size >= 2 }
        rule.onNodeWithTag("deck-tile-Goblins").assertExists()
        rule.onNodeWithText("#Casual", substring = true).assertExists()

        rule.onNodeWithTag("deck-library-search").performTextInput("forest")
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasTestTag("deck-tile-Goblins")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("deck-library-search").performTextReplacement("")
        rule.waitForIdle()
        UiTestSupport.screenshot(rule.activity, "deck-library-tiles")

        rule.onNodeWithContentDescription("More for Lands").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Goblins"), decks.map { it.name })
        rule.onNodeWithTag("deck-undo-delete").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Goblins", "Lands"), decks.map { it.name })

        rule.onNodeWithTag("deck-new").performClick()
        rule.waitUntil(120_000) { rule.onAllNodes(hasTestTag("open-filters")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("open-filters").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("filter-oracle")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("filter-oracle").performTextInput("flying")
        rule.onNodeWithTag("filter-oracle-add").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("SHOW", substring = true) and hasText("CARDS", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        UiTestSupport.screenshot(rule.activity, "deck-builder-filters")
        rule.onNodeWithTag("filters-show").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Text: flying").assertExists()
        rule.onNodeWithText("FILTERS · 1").assertExists()

        rule.onNodeWithTag("results-view").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.waitUntil(30_000) { rule.onAllNodes(SemanticsMatcher("grid add button") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("grid-add-") == true }).fetchSemanticsNodes().isNotEmpty() }
        UiTestSupport.screenshot(rule.activity, "deck-builder-grid")
    }

    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverBuildsAndAutosaves() = buildDeck("fold-cover", folded = true)
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfoldedBuildsAndAutosaves() = buildDeck("fold-unfolded", folded = false)
}
