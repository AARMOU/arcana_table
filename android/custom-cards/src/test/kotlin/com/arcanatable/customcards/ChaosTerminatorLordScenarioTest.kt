package com.arcanatable.customcards
import com.arcanatable.customcards.cards.ChaosTerminatorLord
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test
class ChaosTerminatorLordScenarioTest {
    @Test fun beginningOfCombatGivesAnotherAlliedCreatureDoubleStrike() {
        val d = customScenario(ChaosTerminatorLord, "Grizzly Bears")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Chaos Terminator Lord")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll(Choices(targets = listOf(bear)))
        assertTrue(d.projected.hasKeyword(bear, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(lord, Keyword.DOUBLE_STRIKE))
    }
}
