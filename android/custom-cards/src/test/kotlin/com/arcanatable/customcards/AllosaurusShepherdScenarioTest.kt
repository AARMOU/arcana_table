package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AllosaurusShepherd
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class AllosaurusShepherdScenarioTest {
    @Test
    fun activationMakesYourElvesFiveFiveDinosaursUntilEndOfTurn() {
        val d = CardPool.driver("Llanowar Elves").apply {
            registerCard(AllosaurusShepherd)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val elves = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val shepherdCard = d.putCardInHand(p, "Allosaurus Shepherd")
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, shepherdCard).error)
        d.resolveAll()

        val shepherd = d.findPermanent(p, "Allosaurus Shepherd")!!
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 4)
        assertNull(d.activate(p, shepherd).error)
        d.resolveAll()

        assertEquals(5, d.projected.getPower(elves))
        assertEquals(5, d.projected.getToughness(elves))
        assertTrue(d.projected.hasSubtype(elves, "Dinosaur"))
        assertTrue("the Elf subtype is retained", d.projected.hasSubtype(elves, "Elf"))
    }
}
