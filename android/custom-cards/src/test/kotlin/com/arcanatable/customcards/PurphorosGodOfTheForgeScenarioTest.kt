package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PurphorosGodOfTheForge
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.activate
import com.arcanatable.customcards.support.projected
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurphorosGodOfTheForgeScenarioTest {
    @Test
    fun devotionEnablesCreatureDamageTriggerAndTeamPump() {
        val d = CardPool.driver("Goblin Raider", "Goblin Piker").apply {
            registerCard(PurphorosGodOfTheForge)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val p = d.player1
        val purphoros = d.putPermanentOnBattlefield(p, "Purphoros, God of the Forge")
        repeat(3) { d.putCreatureOnBattlefield(p, "Goblin Raider") }
        assertFalse(d.projected.isCreature(purphoros))

        val firstCreature = d.putCreatureOnBattlefield(p, "Goblin Raider")
        assertTrue(d.projected.isCreature(purphoros))
        val piker = d.putCardInHand(p, "Goblin Piker")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertEquals(null, d.castSpell(p, piker).error)
        d.resolveAll()
        val enteredCreature = d.findPermanent(p, "Goblin Piker")!!
        assertEquals(18, d.getLifeTotal(d.player2))

        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)
        assertEquals(null, d.activate(p, purphoros).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(firstCreature))
        assertEquals(3, d.projected.getPower(enteredCreature))
    }
}
