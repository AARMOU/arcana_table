package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DenyTheWitch
import com.arcanatable.customcards.cards.CompanyCommander
import com.arcanatable.customcards.cards.SpawningBed
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class DenyTheWitchStackKindsScenarioTest {
    private fun counterStackObject(d: GameTestDriver, stackObject: com.wingedsheep.sdk.model.EntityId) {
        val p = d.player1
        val deny = d.putCardInHand(p, "Deny the Witch")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = deny,
            targets = listOf(ChosenTarget.Spell(stackObject)),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
    }

    @Test fun counteringCreatureEtbTriggerLeavesCreatureButPreventsTokens() {
        val d = customScenario(DenyTheWitch, CompanyCommander)
        val p = d.player1
        val opp = d.player2
        d.putCreatureOnBattlefield(p, "Company Commander")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opp)
        val creature = d.putCardInHand(opp, "Company Commander")
        d.giveColorlessMana(opp, 2)
        d.giveMana(opp, Color.WHITE)
        d.giveMana(opp, Color.BLACK)
        assertNull(d.castSpell(opp, creature).error)
        assertNull(d.bothPass().error)
        assertEquals("Company Commander", d.findPermanent(opp, "Company Commander")?.let { d.getName(it) })
        val triggerId = d.getTopOfStack()!!
        assertNull(d.passPriority(opp).error)
        counterStackObject(d, triggerId)
        assertEquals(1, d.getCreatures(opp).size)
        assertEquals(19, d.getLifeTotal(opp))
    }

    @Test fun counteringActivatedNonmanaAbilityPreventsSpawnProduction() {
        val d = customScenario(DenyTheWitch, SpawningBed)
        val p = d.player1
        val opp = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opp)
        val land = d.putLandOnBattlefield(opp, "Spawning Bed")
        d.giveColorlessMana(opp, 6)
        assertNull(d.activate(opp, land, index = 1).error)
        val triggerId = d.getTopOfStack()!!
        assertNull(d.passPriority(opp).error)
        counterStackObject(d, triggerId)
        assertTrue(d.permanentsNamed(opp, "Eldrazi Scion").isEmpty())
        assertEquals(19, d.getLifeTotal(opp))
    }
}
