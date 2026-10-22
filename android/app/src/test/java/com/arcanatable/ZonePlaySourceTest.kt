package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/** Graveyard/exile play cannot silently remove a different zone's card or duplicate one on the stack. */
class ZonePlaySourceTest {
    private val hand = GameCard(101, "Hand Spell", "Instant", Color.White)
    private val graveyard = GameCard(202, "Flashback Spell", "Sorcery", Color.White)
    private val exile = GameCard(303, "Adventure Spell", "Instant", Color.White)
    private val player = TablePlayer("Tester", hand = listOf(hand), graveyard = listOf(graveyard), exile = listOf(exile))

    @Test fun castFromGraveyardLeavesOtherZonesAlone() {
        val after = assertNotNullPlayer(player.removeFromPlaySource("Graveyard", graveyard.id))
        assertTrue(after.graveyard.isEmpty())
        assertEquals(listOf(hand), after.hand)
        assertEquals(listOf(exile), after.exile)
    }

    @Test fun castFromExileLeavesGraveyardAndHandAlone() {
        val after = assertNotNullPlayer(player.removeFromPlaySource("Exile", exile.id))
        assertTrue(after.exile.isEmpty())
        assertEquals(listOf(hand), after.hand)
        assertEquals(listOf(graveyard), after.graveyard)
    }

    @Test fun wrongOrStaleSourceCannotMoveCard() {
        assertNull(player.removeFromPlaySource("Hand", graveyard.id))
        assertNull(player.removeFromPlaySource("Exile", graveyard.id))
        assertNull(player.removeFromPlaySource("Unknown", graveyard.id))
        val after = assertNotNullPlayer(player.removeFromPlaySource("Exile", exile.id))
        assertNull(after.removeFromPlaySource("Exile", exile.id))
    }

    @Test fun landEntryPreservesZoneOfOrigin() {
        val prompt = LandEntryPrompt(0, GameCard(404, "Land", "Land", Color.White), false, "", "Graveyard")
        assertEquals("Graveyard", prompt.sourceZone)
        assertEquals("Hand", prompt.copy(sourceZone = "Hand").sourceZone)
    }

    private fun assertNotNullPlayer(value: TablePlayer?): TablePlayer {
        assertNotNull(value)
        return requireNotNull(value)
    }
}
