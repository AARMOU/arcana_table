package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CelestineTheLivingSaint
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CelestineTheLivingSaintScenarioTest {
    @Test
    fun lifelinkSetsTheEndStepManaValueLimitAndReturnsAnEligibleCreature() {
        val d = customScenario(CelestineTheLivingSaint, "Grizzly Bears", "Serra Angel")
        val p = d.player1
        val celestine = d.putCreatureOnBattlefield(p, "Celestine, the Living Saint")
        val bear = d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Serra Angel")
        assertTrue(d.projected.hasKeyword(celestine, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(celestine, Keyword.LIFELINK))
        d.removeSummoningSickness(celestine)
        val lifeBeforeCombat = d.getLifeTotal(p)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(celestine), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(lifeBeforeCombat + 3, d.getLifeTotal(p))

        d.passPriorityUntil(Step.END, activePlayer = p)
        assertNotNull("Healing Tears should request its legal target", d.state.pendingDecision)
        d.resolveAll(Choices(targets = listOf(bear)))

        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Serra Angel"))
    }

    @Test
    fun noLifeGainedMeansNoCreatureCanBeReturned() {
        val d = customScenario(CelestineTheLivingSaint, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Celestine, the Living Saint")
        d.putCardInGraveyard(p, "Grizzly Bears")

        d.passPriorityUntil(Step.END, activePlayer = p)
        assertTrue("A mana value 2 creature is not legal when X is 0", d.state.stack.isEmpty())
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun creatureAboveTheLifeGainedLimitIsNotAValidTarget() {
        val d = customScenario(CelestineTheLivingSaint, "Serra Angel")
        val p = d.player1
        val celestine = d.putCreatureOnBattlefield(p, "Celestine, the Living Saint")
        d.putCardInGraveyard(p, "Serra Angel")
        d.removeSummoningSickness(celestine)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(celestine), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(23, d.getLifeTotal(p))
        d.passPriorityUntil(Step.END, activePlayer = p)

        assertTrue("A mana value 5 creature is not legal when X is 3", d.state.stack.isEmpty())
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Serra Angel"))
    }
}
