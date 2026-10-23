package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KratosGodOfWar
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KratosGodOfWarScenarioTest {
    @Test
    fun allCreaturesGainHasteAndEachEndStepDamagesThatPlayerForTheirIdleCreatures() {
        val d = scenario("Mountain", "Mountain", "Mountain", "Grizzly Bears").apply {
            registerCard(KratosGodOfWar)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p1 = d.player1
        val p2 = d.player2
        val p1Bear = d.putCreatureOnBattlefield(p1, "Grizzly Bears")
        val p2Bear = d.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val kratosCard = d.putCardInHand(p1, "Kratos, God of War")
        d.giveMana(p1, Color.RED, 3)
        assertNull(d.castSpell(p1, kratosCard).error)
        d.resolveAll()

        val kratos = d.findPermanent(p1, "Kratos, God of War")!!
        assertTrue("Kratos grants haste to creatures controlled by any player", d.projected.hasKeyword(p2Bear, Keyword.HASTE))

        d.passPriorityUntil(Step.END, activePlayer = p1)
        d.resolveAll()
        assertEquals("P1's Bear and Kratos did not attack", 18, d.getLifeTotal(p1))

        d.passPriorityUntil(Step.END, activePlayer = p2)
        d.resolveAll()
        assertEquals("P2's Bear did not attack", 19, d.getLifeTotal(p2))
        assertNotNull("Kratos remains on the battlefield", d.state.getEntity(kratos))
        assertNotNull(d.state.getEntity(p1Bear))
    }
}
