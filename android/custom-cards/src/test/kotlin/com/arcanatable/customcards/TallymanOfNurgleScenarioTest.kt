package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TallymanOfNurgle
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class TallymanOfNurgleScenarioTest {
    @Test
    fun aCreatureDyingBeforeEndStepDrawsOneAndLosesOneLife() {
        val d = customScenario(TallymanOfNurgle, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val tallyman = d.putCreatureOnBattlefield(p, "Tallyman of Nurgle")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val bear = d.findPermanent(d.player2, "Grizzly Bears")!!
        assertTrue(d.projected.hasKeyword(tallyman, Keyword.LIFELINK))
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(bear)).error)
        d.resolveAll()
        val beforeEnd = d.getHand(p).size

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()

        assertEquals(19, d.getLifeTotal(p))
        assertEquals(beforeEnd + 1, d.getHand(p).size)
    }

    @Test
    fun noDeathThisTurnMeansNoChantTrigger() {
        val d = customScenario(TallymanOfNurgle)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Tallyman of Nurgle")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(before, d.getHand(p).size)
    }
}
