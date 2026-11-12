package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Maybeboard, mana sources, and the saved-deck library (tiles, search, sort, tags, undo). */
class DeckLibraryTest {
    companion object {
        lateinit var catalog: SupportedCardCatalog

        @BeforeClass @JvmStatic fun load() {
            val assets = File("src/main/assets")
            assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.jsonl").exists())
            catalog = SupportedCardCatalog.load { File(assets, it).takeIf(File::exists)?.inputStream() }
        }
    }

    @Test fun maybeboardRoundTripsAndNeverReachesTheGame() {
        val d = DeckDraft("t").setCommander("Krenko, Mob Boss").add("Sol Ring").add("Mountain", 3).add("Goblin Guide")
            .moveToMaybeboard("Goblin Guide").addToMaybeboard("Lightning Bolt").copy(sideboard = listOf(DeckLine("Shock", 1)))
        assertEquals(5, d.deckSize) // commander + Sol Ring + 3 Mountain
        val back = DeckDraft.fromText("t", d.toText(), catalog)
        assertEquals(listOf("Goblin Guide", "Lightning Bolt"), back.maybeboard.map { it.name })
        assertEquals(listOf("Shock"), back.sideboard.map { it.name })
        assertEquals(d.main, back.main)
        val game = parseDeckList(d.toText()).map { it.name }
        assertTrue("maybeboard stays out of the game deck", "Goblin Guide" !in game && "Lightning Bolt" !in game)
        assertEquals(1, back.moveFromMaybeboard("Goblin Guide").countOf("Goblin Guide"))
        assertTrue(back.removeFromMaybeboard("Lightning Bolt").maybeboard.none { it.name == "Lightning Bolt" })
        // A pasted Moxfield-style maybeboard is kept on save instead of dropped.
        val pasted = DeckDraft.fromText("p", "Commander\n1 Krenko, Mob Boss\nDeck\n1 Sol Ring\n\nMaybeboard\n1 Goblin Guide\n", catalog)
        assertEquals(listOf("Goblin Guide"), pasted.maybeboard.map { it.name })
    }

    @Test fun landSourcesCountLandsByColor() {
        val g = setOf('G', 'W')
        assertEquals(setOf('G'), landColors(catalog.find("Forest")!!, g))
        assertEquals(setOf('G', 'U'), landColors(catalog.find("Breeding Pool")!!, g))
        assertEquals(g, landColors(catalog.find("Command Tower")!!, g))
        assertEquals(g, landColors(catalog.find("Evolving Wilds")!!, g))
        val stats = deckStats(DeckDraft("s").setCommander("Krenko, Mob Boss").add("Mountain", 10).add("Command Tower").add("Goblin Guide"), catalog)
        assertEquals(11, stats.landSources['R'])
        assertEquals(0, stats.landSources['G'])
    }

    @Test fun libraryTilesSearchSortTagsAndUndo() {
        val decks = listOf(
            SavedDeck("Goblins", "Commander\n1 Krenko, Mob Boss\nDeck\n1 Sol Ring", "Krenko, Mob Boss", updatedAt = 5, tags = listOf("Casual")),
            SavedDeck("Lands", "Deck\n1 Forest", updatedAt = 9),
            SavedDeck("Old", "Deck\n1 Island"),
        )
        val summaries = decks.associate { it.name to summarizeDeck(it, catalog) }
        assertEquals(setOf('R'), summaries.getValue("Goblins").identity)
        assertEquals(2, summaries.getValue("Goblins").cards)
        assertTrue(summaries.getValue("Goblins").problems!! > 0) // 2 of 100
        assertTrue(summaries.getValue("Goblins").commanderImageUrl != null)
        assertNull(summarizeDeck(decks[0], null).problems)

        assertEquals(listOf("Lands", "Goblins", "Old"), SavedDeckOps.visible(decks, "", null, DeckLibrarySort.RECENT, summaries).map { it.name })
        assertEquals(listOf("Goblins"), SavedDeckOps.visible(decks, "sol ring", null, DeckLibrarySort.NAME, summaries).map { it.name })
        assertEquals(listOf("Goblins"), SavedDeckOps.visible(decks, "krenko", null, DeckLibrarySort.NAME, summaries).map { it.name })
        assertEquals(listOf("Goblins"), SavedDeckOps.visible(decks, "", "casual", DeckLibrarySort.NAME, summaries).map { it.name })

        val tagged = SavedDeckOps.setTags(decks, decks[1], listOf(" Testing ", "testing", "Casual"))
        assertEquals(listOf("Testing", "Casual"), tagged[1].tags)
        assertEquals(listOf("Casual", "Testing"), SavedDeckOps.allTags(tagged))

        val deleted = SavedDeckOps.delete(decks, decks[1])
        assertEquals(decks, SavedDeckOps.restore(deleted, decks[1], 1))
        assertEquals("restore never duplicates a name", deleted, SavedDeckOps.restore(deleted, decks[0], 0))

        assertNull(editedAgo(0L, 1_000L))
        assertEquals("edited 5 min ago", editedAgo(1_000L, 1_000L + 5 * 60_000L))
        assertEquals("edited yesterday", editedAgo(1L, 1L + 30L * 3_600_000L))
    }
}
