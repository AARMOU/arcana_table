package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EleshNornGrandCenobite
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class EleshNornGrandCenobiteScenarioTest {
    @Test
    fun buffsOtherCreaturesYouControlAndShrinksCreaturesOpponentsControl() {
        val d = scenario("Grizzly Bears").apply { registerCard(EleshNornGrandCenobite) }
        val p1 = d.player1
        val p2 = d.player2
        val friendlyBear = d.putCreatureOnBattlefield(p1, "Grizzly Bears")
        d.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val nornCard = d.putCardInHand(p1, "Elesh Norn, Grand Cenobite")
        d.giveMana(p1, Color.WHITE, 2)
        d.giveColorlessMana(p1, 5)

        assertNull(d.castSpell(p1, nornCard).error)
        d.resolveAll()

        val norn = d.findPermanent(p1, "Elesh Norn, Grand Cenobite")!!
        assertEquals("Elesh Norn does not pump herself", 4, d.projected.getPower(norn))
        assertEquals(7, d.projected.getToughness(norn))
        assertEquals("other creatures you control get +2/+2", 4, d.projected.getPower(friendlyBear))
        assertEquals(4, d.projected.getToughness(friendlyBear))
        assertTrue("the opposing 2/2 dies to -2/-2", d.getCreatures(p2).none { d.getCardName(it) == "Grizzly Bears" })
    }
}
