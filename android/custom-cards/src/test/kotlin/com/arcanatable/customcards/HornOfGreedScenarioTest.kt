package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HornOfGreed
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class HornOfGreedScenarioTest {
    @Test
    fun playingALandMakesThatPlayerDraw() {
        val d = customScenario(HornOfGreed)
        val player = d.player1
        val handBefore = d.getHandSize(player)
        d.putPermanentOnBattlefield(player, "Horn of Greed")
        val forest = d.putCardInHand(player, "Forest")

        d.playLand(player, forest)
        d.resolveAll()

        assertEquals(1, d.zoneNames(player, Zone.BATTLEFIELD).count { it == "Forest" })
        assertEquals(handBefore + 1, d.getHandSize(player))
    }
}
