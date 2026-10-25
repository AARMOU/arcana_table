package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RishkarsExpertise
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class RishkarsExpertiseScenarioTest {
    private fun setup() = customScenario(RishkarsExpertise, "Craw Wurm", "Grizzly Bears", "Hill Giant")

    private fun GameTestDriver.castExpertise(c: Choices) {
        val spell = putCardInHand(player1, "Rishkar's Expertise")
        giveMana(player1, Color.GREEN, 2); giveColorlessMana(player1, 4)
        assertNull(castSpell(player1, spell).error)
        resolveAll(c)
    }

    @Test fun drawsGreatestPowerAndCastsAFreeSpellFromHand() {
        val d = setup()
        d.putCreatureOnBattlefield(d.player1, "Craw Wurm")      // power 6
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")  // power 2
        d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.putCardInHand(d.player1, "Hill Giant")
        val handBefore = d.getHand(d.player1).size // includes Hill Giant
        d.castExpertise(Choices(pick = listOf("Hill Giant")))
        assertEquals("drew 6, Hill Giant left the hand", handBefore + 6 - 1, d.getHand(d.player1).size)
        assertEquals(1, d.permanentsNamed(d.player1, "Hill Giant").size)
        assertEquals("nothing was paid for Hill Giant", 0, d.manaPool(d.player1).colorless)
    }

    @Test fun spellsWithManaValueSixAreNotOffered() {
        val d = setup()
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCardInHand(d.player1, "Craw Wurm") // mana value 6
        d.castExpertise(Choices(pick = listOf("Craw Wurm")))
        assertTrue(d.permanentsNamed(d.player1, "Craw Wurm").isEmpty())
        assertTrue(d.getHand(d.player1).any { d.getCardName(it) == "Craw Wurm" })
    }

    @Test fun castingIsOptionalAndNoCreaturesDrawsNothing() {
        val d = setup()
        d.putCardInHand(d.player1, "Grizzly Bears")
        val before = d.getHand(d.player1).size
        d.castExpertise(Choices(pick = emptyList()))
        assertEquals(before, d.getHand(d.player1).size)
        assertTrue(d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
    }
}
