package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The user's three premade decks (Ayula, Slivers, Hazel): every card runs offline, for you or the AI. */
class NewPremadeDecksTest {
    @Test fun nonEdhrecArgentumDecksAreArcanaAndEdhrecDecksStayArgentum() {
        val json = File("src/main/assets/argentum/commander-decks.json").readText()
        val premades = OfflineGameSetup.parsePremades(json)
        val arcanaIds = premades.filter { argentumDeckCategory(it.id) == DeckChoiceCategory.ARCANA }.map { it.id }.toSet()
        val argentumIds = premades.filter { argentumDeckCategory(it.id) == DeckChoiceCategory.ARGENTUM }.map { it.id }.toSet()

        assertEquals(
            setOf("animated_army", "tokens_win", "artificer_v4", "ayula_bears", "sliver_overlord", "hazel_squirrels", "mabel_heir", "velociraptor_beefcake"),
            arcanaIds
        )
        assertEquals(
            setOf("edhrec_baylen", "edhrec_bello", "edhrec_animar", "edhrec_wandering_minstrel", "edhrec_avatar_aang"),
            argentumIds
        )
    }

    @Test fun everyCardResolves() {
        val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() })
        val json = File("src/main/assets/argentum/commander-decks.json").readText()
        val premades = OfflineGameSetup.parsePremades(json)
        for (id in listOf("ayula_bears", "sliver_overlord", "hazel_squirrels", "mabel_heir", "velociraptor_beefcake")) {
            val deck = premades.first { it.id == id }
            assertEquals("$id has 100 cards", 100, deck.cards.size)
            val missing = deck.cards.filter { library.canonical(it) == null }.distinct()
            assertTrue("$id: cards Argentum can't run: $missing", missing.isEmpty())
            // As your deck, against itself as the AI's deck.
            val (setup, _) = OfflineGameSetup.build(library, "You", deck.cards, deck.commander, json, aiDeckId = id)
            assertEquals(deck.commander, setup.you.commander)
            assertEquals(99, setup.you.deck.size)
            assertEquals(99, setup.ai.deck.size)
            assertTrue("$id notes: ${setup.notes}", setup.notes.none { it.startsWith("Left out") })
        }
    }
}
