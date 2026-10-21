package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeployToTheFront
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class DeployToTheFrontScenarioTest {
    @Test
    fun createsSoldiersEqualToAllPlayersCreaturesNotJustControllers() {
        val d = customScenario(DeployToTheFront)
        val p = d.player1
        val q = d.player2
        d.putPermanentOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(q, "Grizzly Bears")
        d.putPermanentOnBattlefield(q, "Grizzly Bears")
        val card = d.putCardInHand(p, "Deploy to the Front")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(3, d.permanentsNamed(p, "Soldier").size)
        assertEquals(0, d.permanentsNamed(q, "Soldier").size)
    }

    @Test
    fun emptyBattlefieldCreatesNoTokens() {
        val d = customScenario(DeployToTheFront)
        val p = d.player1
        val card = d.putCardInHand(p, "Deploy to the Front")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Soldier").isEmpty())
    }
}
