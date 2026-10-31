package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import com.wingedsheep.engine.registry.CardRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GoosegetaStrictDeckTest {
    private val assets = File("src/main/assets")
    private fun library() = OfflineCardLibrary({ path -> File(assets,path).takeIf { it.exists() }?.inputStream() })

    @Test fun actualRuntimeLoadsBothExactDecksWithoutSubstitution() {
        val decks = JSONObject(File("../cloud-room/src/goose-decks.json").readText()).getJSONArray("decks")
        for (i in 0 until decks.length()) {
            val deck = decks.getJSONObject(i)
            val cards = deck.getJSONArray("cards").let { a -> (0 until a.length()).map(a::getString) }
            val (seat, notes) = OfflineGameSetup.lanSeat(library(), CardRegistry(), "Goosegeta", cards, deck.getString("commander"), strict = true)
            assertNotNull("Exact supported deck ${deck.getString("id")}: $notes", seat)
            assertEquals(cards, seat!!.deck)
            assertTrue(notes.isEmpty())
        }
    }

    @Test fun strictGuestRejectsMissingOffColorAndDuplicateCardsWithoutFillingBasics() {
        val valid = List(99) { "Forest" }
        for (bad in listOf(valid.drop(1), valid.drop(1) + "Unknown Goose", valid.drop(1) + "Island",
            valid.drop(2) + listOf("Sol Ring", "Sol Ring"))) {
            val (seat, notes) = OfflineGameSetup.lanSeat(library(), CardRegistry(), "Goosegeta", bad, "Bello, Bard of the Brambles", strict = true)
            assertNull(seat)
            assertTrue(notes.isNotEmpty())
        }
    }
}
