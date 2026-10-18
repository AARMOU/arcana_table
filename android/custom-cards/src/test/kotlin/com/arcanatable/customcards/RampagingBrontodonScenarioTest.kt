package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RampagingBrontodon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class RampagingBrontodonScenarioTest {
    @Test
    fun attackTriggerAddsOnePowerAndToughnessPerLandYouControl() {
        val d = customScenario(RampagingBrontodon, "Forest")
        val player = d.player1
        val brontodon = d.putCreatureOnBattlefield(player, "Rampaging Brontodon")
        d.putPermanentOnBattlefield(player, "Forest")
        d.putPermanentOnBattlefield(player, "Forest")
        d.removeSummoningSickness(brontodon)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(brontodon), d.player2).error)
        d.resolveAll()

        assertEquals(9, d.projected.getPower(brontodon))
        assertEquals(9, d.projected.getToughness(brontodon))
    }
}
