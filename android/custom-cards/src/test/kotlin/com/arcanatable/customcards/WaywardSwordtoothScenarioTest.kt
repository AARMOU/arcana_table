package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression against upstream Argentum's WaywardSwordtooth card, not a duplicate Arcana definition. */
class WaywardSwordtoothScenarioTest {
    @Test
    fun grantsAnAdditionalLandPlay() {
        val d = scenario("Wayward Swordtooth", "Forest")
        d.putCreatureOnBattlefield(d.player1, "Wayward Swordtooth")
        repeat(2) {
            val land = d.putCardInHand(d.player1, "Forest")
            assertNull(d.playLand(d.player1, land).error)
        }
    }

    @Test
    fun cannotAttackWithoutTheCitysBlessing() {
        val belowThreshold = scenario("Wayward Swordtooth", "Forest")
        val swordtooth1 = belowThreshold.putCreatureOnBattlefield(belowThreshold.player1, "Wayward Swordtooth")
        repeat(8) { belowThreshold.putPermanentOnBattlefield(belowThreshold.player1, "Forest") }
        belowThreshold.removeSummoningSickness(swordtooth1)
        belowThreshold.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(belowThreshold.declareAttackers(belowThreshold.player1, listOf(swordtooth1), belowThreshold.player2).error)
    }
}
