package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KodamaOfTheWestTree
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class KodamaOfTheWestTreeScenarioTest {
    @Test
    fun modifiedCreatureGainsTrampleAndCombatDamageFindsTappedBasicLand() {
        val d = CardPool.driver("Scale the Heights", "Forest").apply {
            registerCard(KodamaOfTheWestTree)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val kodamaCard = d.putCardInHand(p, "Kodama of the West Tree")
        d.giveMana(p, Color.GREEN, 2)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, kodamaCard).error)
        d.resolveAll()

        val kodama = d.findPermanent(p, "Kodama of the West Tree")!!
        val scale = d.putCardInHand(p, "Scale the Heights")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, scale, listOf(kodama)).error)
        d.resolveAll()

        d.putCardOnTopOfLibrary(p, "Forest")
        assertTrue("the +1/+1 counter makes Kodama modified and grants trample", d.projected.hasKeyword(kodama, com.wingedsheep.sdk.core.Keyword.TRAMPLE))
        d.removeSummoningSickness(kodama)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(kodama), d.player2).error)
        while (d.currentStep != Step.POSTCOMBAT_MAIN) {
            if (d.state.pendingDecision != null) d.decide(Choices(pick = listOf("Forest")))
            else d.bothPass()
        }
        d.resolveAll(Choices(pick = listOf("Forest")))

        assertEquals("Kodama's modified-creature trigger searched after combat damage", 16, d.getLifeTotal(d.player2))
        val forest = d.findPermanent(p, "Forest")
        assertNotNull("the searched Forest entered", forest)
        assertNotNull("Kodama puts the searched land onto the battlefield tapped", d.state.getEntity(forest!!)!!.get<TappedComponent>())
    }
}
