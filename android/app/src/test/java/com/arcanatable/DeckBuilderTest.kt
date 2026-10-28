package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Deck builder model: edits, undo, saved text round trips, Commander validation and stats. */
class DeckBuilderTest {
    companion object {
        private val assets = File("src/main/assets")
        lateinit var catalog: SupportedCardCatalog

        @BeforeClass @JvmStatic fun load() {
            assumeTrue("full Argentum library asset present", File(assets, "argentum/all-cards.jsonl").exists())
            catalog = SupportedCardCatalog.load { File(assets, it).takeIf(File::exists)?.inputStream() }
        }

        /** A legal mono-green 100 built from whatever this library ships, so it survives library updates. */
        fun legalGreenDeck(): DeckDraft {
            val lib = CardLibrary(catalog)
            val commander = lib.search("is:commander t:creature id=g legal:commander", 50).cards.first { it.commanderLegal == true }
            val spells = lib.search("id:g -t:basic legal:commander", 5000).cards
                .filter { it.name != commander.name && it.commanderLegal == true && !it.custom }
                .take(61)
            var d = DeckDraft("Green test").setCommander(commander.name)
            spells.forEach { d = d.add(it.name) }
            return d.add("Forest", 99 - d.mainCount)
        }
    }

    @Test fun editsAddRemoveCountAndCommander() {
        var d = DeckDraft("t").add("Sol Ring").add("Sol Ring").add("Forest", 3)
        assertEquals(2, d.countOf("sol ring"))
        d = d.remove("Sol Ring")
        assertEquals(1, d.countOf("Sol Ring"))
        d = d.setCount("Forest", 10).setCount("Sol Ring", 0)
        assertEquals(0, d.countOf("Sol Ring"))
        assertEquals(10, d.mainCount)

        d = d.add("Krenko, Mob Boss").setCommander("Krenko, Mob Boss")
        assertEquals(listOf("Krenko, Mob Boss"), d.commanders)
        assertEquals("commander leaves the main deck", 0, d.countOf("Krenko, Mob Boss"))
        assertEquals(11, d.deckSize)

        d = d.setCommander("Llanowar Elves")
        assertEquals(listOf("Llanowar Elves"), d.commanders)
        assertEquals("replaced commander goes back into the deck, never lost", 1, d.countOf("Krenko, Mob Boss"))
    }

    @Test fun sideboardMovesAndReplace() {
        var d = DeckDraft("t").add("Counterspell", 1).add("Sol Ring")
        d = d.moveToSideboard("Counterspell")
        assertEquals(0, d.countOf("Counterspell"))
        assertEquals(1, d.sideboard.single { it.name == "Counterspell" }.count)
        d = d.moveToMain("Counterspell")
        assertTrue(d.sideboard.isEmpty())
        d = d.replace("Sol Ring", "Mind Stone")
        assertEquals(0, d.countOf("Sol Ring")); assertEquals(1, d.countOf("Mind Stone"))
    }

    @Test fun undoRedoAndHistoryLimit() {
        val s = DeckEditSession(DeckDraft("t"), maxHistory = 3)
        s.apply { it.add("A") }; s.apply { it.add("B") }; s.apply { it.add("C") }
        assertEquals(listOf("A", "B", "C"), s.draft.main.map { it.name })
        s.undo(); assertEquals(listOf("A", "B"), s.draft.main.map { it.name })
        s.redo(); assertEquals(3, s.draft.main.size)
        s.undo(); s.apply { it.add("D") }
        assertFalse("a new edit clears redo", s.canRedo)
        s.apply { it.add("E") }; s.apply { it.add("F") }   // now A B D E F, five edits deep
        repeat(10) { s.undo() }
        assertEquals("history keeps only the last 3 earlier states", listOf("A", "B"), s.draft.main.map { it.name })
        val before = s.draft
        s.apply { it }
        assertEquals("no-op edits don't create history", before, s.draft)
    }

    @Test fun savedTextRoundTripsThroughBothParsers() {
        val d = DeckDraft("Round trip", commanders = listOf("Krenko, Mob Boss"),
            main = listOf(DeckLine("Sol Ring", 1), DeckLine("Mountain", 30), DeckLine("Lightning Bolt", 1)),
            sideboard = listOf(DeckLine("Counterspell", 1)), companion = "Lurrus of the Dream-Den")
        val text = d.toText()
        val back = DeckDraft.fromText("Round trip", text, catalog)
        assertEquals(d.commanders, back.commanders)
        assertEquals(d.main.toSet(), back.main.toSet())
        assertEquals(d.sideboard, back.sideboard)
        assertEquals(d.companion, back.companion)

        // The game's loader (compatibility parser) must see exactly commander + main, never sideboard or companion.
        val game = parseDeckList(text)
        assertEquals(1, game.count { it.commander })
        assertEquals("Krenko, Mob Boss", game.single { it.commander }.name)
        assertEquals(d.deckSize, game.sumOf { it.count })
        assertTrue(game.none { it.name == "Counterspell" || it.name == "Lurrus of the Dream-Den" })
    }

    @Test fun importedListsKeepEveryZoneAndUnreadLines() {
        val raw = """
            Commander
            1 Krenko, Mob Boss (DDT) 52
            Deck
            1 Sol Ring
            1 Totally Fake Card
            12 Mountain
            https://example.com/not-a-card
            Sideboard
            1 Counterspell
        """.trimIndent()
        val d = DeckDraft.fromText("Imported", raw, catalog)
        assertEquals(listOf("Krenko, Mob Boss"), d.commanders)
        assertEquals("unsupported card kept, not dropped or swapped", 1, d.countOf("Totally Fake Card"))
        assertEquals(1, d.sideboard.size)
        assertEquals(listOf("https://example.com/not-a-card"), d.unreadLines)
        assertTrue("unread lines survive a save", d.toText().contains("not-a-card"))
        val again = DeckDraft.fromText("Imported", d.toText(), catalog)
        assertEquals("…and a reload doesn't duplicate them", 1, again.unreadLines.size)
    }

    @Test fun legalDeckHasNoErrors() {
        val d = legalGreenDeck()
        assertEquals(100, d.deckSize)
        val errors = validateCommanderDeck(d, catalog).filter { it.level == IssueLevel.ERROR }
        assertTrue(errors.joinToString { it.message }, errors.isEmpty())
    }

    @Test fun validationReportsEachProblemWithoutChangingTheDeck() {
        val legal = legalGreenDeck()
        val broken = legal.add("Lightning Bolt").add("Sol Ring", 2).add("Totally Fake Card")
        val snapshot = broken.copy()
        val issues = validateCommanderDeck(broken, catalog)
        val text = issues.joinToString("\n") { it.message }
        assertTrue(text, issues.any { "100 cards" in it.message })
        assertTrue(text, issues.any { "outside your commander's colors" in it.message && "Lightning Bolt" in it.cards })
        assertTrue(text, issues.any { "Only one copy of Sol Ring" in it.message })
        assertTrue(text, issues.any { "can't play" in it.message && "Totally Fake Card" in it.cards })
        assertEquals("validation never edits", snapshot, broken)
    }

    @Test fun commanderRules() {
        val none = validateCommanderDeck(DeckDraft("t").add("Forest", 100), catalog)
        assertTrue(none.any { it.message.startsWith("Choose a commander") })

        val notLegendary = validateCommanderDeck(DeckDraft("t", commanders = listOf("Llanowar Elves")), catalog)
        assertTrue(notLegendary.any { "can't be a commander" in it.message })

        val badPair = validateCommanderDeck(DeckDraft("t", commanders = listOf("Krenko, Mob Boss", "Llanowar Elves")), catalog)
        assertTrue(badPair.any { "can't share command" in it.message })

        val partners = validateCommanderDeck(DeckDraft("t", commanders = listOf("Ishai, Ojutai Dragonspeaker", "Kodama of the East Tree")), catalog)
        assertFalse(partners.any { "can't share command" in it.message })
        assertTrue("honest about the single engine slot", partners.any { it.level == IssueLevel.WARNING && "one commander slot" in it.message })
    }

    @Test fun basicsAndAnyNumberCardsMayRepeat() {
        assertTrue(allowsMultiple("Forest", catalog, 40))
        assertFalse(allowsMultiple("Sol Ring", catalog, 2))
        // Any card whose own text allows multiples, if this library ships one.
        CardLibrary(catalog).search("o:\"a deck can have any number of cards named\"", 5).cards.firstOrNull()?.let {
            assertTrue(allowsMultiple(it.name, catalog, 30))
        }
    }

    @Test fun bannedCardsAreWarned() {
        val d = legalGreenDeck().replace(legalGreenDeck().main.first { it.name != "Forest" }.name, "Fastbond")
        assertTrue(validateCommanderDeck(d, catalog).any { it.level == IssueLevel.WARNING && "Fastbond" in it.cards })
    }

    @Test fun statsCountCurveColorsAndUnsupported() {
        val d = DeckDraft("t", commanders = listOf("Krenko, Mob Boss"),
            main = listOf(DeckLine("Mountain", 10), DeckLine("Lightning Bolt", 1), DeckLine("Sol Ring", 1), DeckLine("Totally Fake Card", 2)))
        val s = deckStats(d, catalog)
        assertEquals(15, s.total)
        assertEquals(1, s.creatures)
        assertEquals(10, s.lands)
        assertEquals(2, s.unsupported)
        assertEquals(2, s.curve[1]) // Bolt + Sol Ring
        assertEquals(1, s.curve[4]) // Krenko
        assertEquals(setOf('R'), s.identity)
        assertEquals(3, s.pips['R']) // {R} + {2}{R}{R}
        assertEquals(2.0, s.averageManaValue, 0.001)
    }

    @Test fun groupingKeepsUnknownCardsVisible() {
        val d = DeckDraft("t", main = listOf(DeckLine("Totally Fake Card", 1), DeckLine("Sol Ring", 1), DeckLine("Forest", 5)))
        DeckGrouping.entries.forEach { g ->
            val groups = groupDeck(d, catalog, g)
            assertEquals(7, groups.sumOf { it.second.sumOf { l -> l.count } })
            assertTrue(groups.any { it.first == "Not in library" })
        }
    }

    @Test fun savedDeckOperationsProtectOtherDecks() {
        val a = SavedDeck("Alpha", "1 Sol Ring\n"); val b = SavedDeck("Beta", "1 Forest\n")
        var decks = listOf(a, b)
        decks = SavedDeckOps.duplicate(decks, a)
        assertEquals(listOf("Alpha copy", "Alpha", "Beta"), decks.map { it.name })
        decks = SavedDeckOps.duplicate(decks, a)
        assertEquals("Alpha copy (2)", decks.first().name)
        decks = SavedDeckOps.rename(decks, b, "Alpha")
        assertEquals("rename never collides", "Alpha (2)", decks.last().name)
        decks = SavedDeckOps.upsert(decks, "Alpha", a.copy(text = "1 Mind Stone\n"))
        assertEquals("upsert keeps position", "1 Mind Stone\n", decks[2].text)
        assertEquals(4, decks.size)
        decks = SavedDeckOps.delete(decks, decks[2])
        assertEquals(listOf("Alpha copy (2)", "Alpha copy", "Alpha (2)"), decks.map { it.name })
        assertNotNull(decks.firstOrNull { it.text == b.text })
    }

    @Test fun builtDeckLoadsIntoTheOfflineEngineLikeAnImport() {
        // The game path reads saved text with parseDeckList; every card name must resolve to a library definition.
        val d = legalGreenDeck()
        val entries = parseDeckList(d.toText())
        val names = entries.flatMap { e -> List(e.count) { e.name } }
        assertEquals(100, names.size)
        assertTrue(names.all { catalog.find(it) != null })
        assertTrue(parseDeckListDetailed(d.toText(), catalog).gameBlockers.isEmpty())
    }
}
