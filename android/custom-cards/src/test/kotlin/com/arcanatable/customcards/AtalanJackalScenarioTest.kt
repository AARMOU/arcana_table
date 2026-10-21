package com.arcanatable.customcards
import com.arcanatable.customcards.cards.AtalanJackal
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test
class AtalanJackalScenarioTest {
    @Test fun combatDamageToPlayerMayFetchTappedBasicLand() {
        val d = CardPool.driver().apply {
            registerCard(AtalanJackal)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val jackal = d.putCreatureOnBattlefield(p, "Atalan Jackal")
        d.removeSummoningSickness(jackal)
        assertTrue(d.projected.hasKeyword(jackal, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(jackal, Keyword.TRAMPLE))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(jackal), d.player2)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = true, pick = listOf("Forest")))
        val land = d.permanentsNamed(p, "Forest").single()
        assertTrue(d.isTapped(land))
    }
}
