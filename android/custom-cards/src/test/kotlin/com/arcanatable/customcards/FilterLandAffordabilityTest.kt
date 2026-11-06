package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/** #51: dependent filter mana must agree between the host's affordability flag and payment. */
class FilterLandAffordabilityTest {
    private data class Board(val d: GameTestDriver, val plains: EntityId, val filter: EntityId, val spell: EntityId)

    private fun board(name: String): Board {
        val d = scenario("Skycloud Expanse", name)
        return Board(d, d.putPermanentOnBattlefield(d.player1, "Plains"),
            d.putPermanentOnBattlefield(d.player1, "Skycloud Expanse"), d.putCardInHand(d.player1, name))
    }

    private fun Board.affordable(): Boolean = LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
        .enrich(d.legalActions(d.player1), d.state, d.player1)
        .singleOrNull { (it.action as? CastSpell)?.cardId == spell }?.isAffordable ?: false

    @Test fun genericWhiteIsAffordableBeforeFloatingMana() = assertAffordable("Voice of Victory")
    @Test fun whiteBlueIsAffordableBeforeFloatingMana() = assertAffordable("Deft Duelist")

    private fun assertAffordable(name: String) {
        val b = board(name)
        assertTrue("$name must be offered as affordable with Plains + Skycloud", b.affordable())
    }

    @Test fun genericWhiteDirectAutoPayment() = assertDirectPayment("Voice of Victory")
    @Test fun whiteBlueDirectAutoPayment() = assertDirectPayment("Deft Duelist")

    private fun assertDirectPayment(name: String) {
        val b = board(name)
        assertNull("$name direct auto-payment", b.d.castSpell(b.d.player1, b.spell).error)
        assertTrue(b.d.isTapped(b.plains))
        assertTrue(b.d.isTapped(b.filter))
        assertEquals(0, b.d.manaPool(b.d.player1).white)
        assertEquals(0, b.d.manaPool(b.d.player1).blue)
        assertEquals(1, b.d.state.stack.size)
    }

    @Test fun manuallyActivatingFilterProducesOneWhiteAndOneBlueAndEnablesCasting() {
        val b = board("Voice of Victory")
        assertNull(b.d.activate(b.d.player1, b.filter).error)
        assertTrue(b.d.isTapped(b.plains))
        assertTrue(b.d.isTapped(b.filter))
        assertEquals(1, b.d.manaPool(b.d.player1).white)
        assertEquals(1, b.d.manaPool(b.d.player1).blue)
        assertTrue(b.affordable())
        assertNull(b.d.castSpell(b.d.player1, b.spell).error)
        assertEquals(0, b.d.manaPool(b.d.player1).white)
        assertEquals(0, b.d.manaPool(b.d.player1).blue)
    }

    @Test fun filterCannotInventDoubleWhiteOrDoubleBlue() {
        for (name in listOf("White Knight", "Lord of Atlantis")) {
            val b = board(name)
            assertFalse("$name cannot be paid by WU", b.affordable())
            assertNotNull(b.d.castSpell(b.d.player1, b.spell).error)
            assertFalse("rejected cast must not tap Plains", b.d.isTapped(b.plains))
            assertFalse("rejected cast must not tap Skycloud", b.d.isTapped(b.filter))
            assertTrue(b.d.state.stack.isEmpty())
        }
    }

    @Test fun thirdPlainsEnablesDoubleWhiteButNotDoubleBlue() {
        for ((name, expected) in listOf("White Knight" to true, "Lord of Atlantis" to false)) {
            val b = board(name)
            b.d.putPermanentOnBattlefield(b.d.player1, "Plains")
            assertEquals(name, expected, b.affordable())
            val result = b.d.castSpell(b.d.player1, b.spell)
            if (expected) assertNull(result.error) else assertNotNull(result.error)
        }
    }
}
