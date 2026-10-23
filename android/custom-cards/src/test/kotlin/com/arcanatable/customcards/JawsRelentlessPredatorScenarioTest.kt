package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class JawsRelentlessPredatorScenarioTest {
    @Test
    fun combatDamageMakesBloodAndArtifactsDyingPingOpponents() {
        val d = scenario()
        val p = d.player1
        val jaws = d.putCreatureOnBattlefield(p, "Jaws, Relentless Predator")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(jaws), d.player2)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(15, d.getLifeTotal(d.player2))
        assertEquals(5, d.permanentsNamed(p, "Blood").size)

        val treasure = d.putToken(p, "Treasure")
        val sac = d.abilityActions(p, treasure).first().action
        d.submit(sac)
        d.resolveAll()
        assertEquals("a noncreature artifact was sacrificed", 14, d.getLifeTotal(d.player2))
    }
}
