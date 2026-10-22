package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GreatUncleanOne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class GreatUncleanOneScenarioTest {
    @Test fun twoPlayerEndStepLosesTwoThenMintsOnePlaguebearer() {
        val d = customScenario(GreatUncleanOne)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Great Unclean One")

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        val plaguebearer = d.permanentsNamed(p, "Plaguebearer of Nurgle")
        assertEquals(1, plaguebearer.size)
        assertEquals(1, d.projected.getPower(plaguebearer.single()))
        assertEquals(3, d.projected.getToughness(plaguebearer.single()))
    }

    @Test fun threePlayerCountUsesLifeTotalsAfterTheLossAndExcludesEqualLife() {
        val d = CardPool.driver()
        val players = d.initMultiplayer(List(3) { Deck.of("Swamp" to 40) })
        val (p1, p2, p3) = players
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p1)
        d.putCreatureOnBattlefield(p1, "Great Unclean One")
        // p2 (18->16) qualifies, but p3 (22->20) ties with controller after losing 2.
        d.setLifeTotal(p2, 18)
        d.setLifeTotal(p3, 22)

        d.passPriorityUntil(Step.END, activePlayer = p1)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(p1))
        assertEquals(16, d.getLifeTotal(p2))
        assertEquals(20, d.getLifeTotal(p3))
        assertEquals(1, d.permanentsNamed(p1, "Plaguebearer of Nurgle").size)
        assertTrue(d.permanentsNamed(p2, "Plaguebearer of Nurgle").isEmpty())
    }

    @Test fun noOpponentBelowControllerLifeCreatesNoTokens() {
        val d = customScenario(GreatUncleanOne)
        val p = d.player1
        d.setLifeTotal(p, 10)
        d.putCreatureOnBattlefield(p, "Great Unclean One")
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(d.player2))
        assertTrue(d.permanentsNamed(p, "Plaguebearer of Nurgle").isEmpty())
    }
}
