package com.arcanatable.customcards
import com.arcanatable.customcards.cards.UlamogsCrusher
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test
class UlamogsCrusherScenarioTest {
    @Test fun attackMakesDefendingPlayerSacrificeTwoPermanents() {
        val d = customScenario(UlamogsCrusher)
        val p = d.player1
        val crusher = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")
        d.removeSummoningSickness(crusher)
        repeat(2) { d.putPermanentOnBattlefield(d.player2, "Plains") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(crusher), d.player2)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player2, "Plains").isEmpty())
    }
}
