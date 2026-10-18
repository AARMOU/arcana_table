package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CuriousAltisaur
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuriousAltisaurScenarioTest {
    @Test
    fun dinosaurCombatDamageToAPlayerDrawsForItsController() {
        val d = CardPool.driver().apply {
            registerCard(CuriousAltisaur)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = d.player1
        val altisaur = d.putCreatureOnBattlefield(player, "Curious Altisaur")
        d.removeSummoningSickness(altisaur)
        val handBefore = d.getHand(player).size

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(altisaur), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHand(player).size)
    }
}
