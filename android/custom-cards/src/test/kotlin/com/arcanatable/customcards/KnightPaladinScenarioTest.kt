package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnightPaladin
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class KnightPaladinScenarioTest {
    @Test
    fun enteringVehicleDealsFourToOpponentAndHasCrewOne() {
        val d = customScenario(KnightPaladin, "Grizzly Bears")
        val p = d.player1
        val crewMember = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(crewMember)
        val vehicle = d.putCardInHand(p, "Knight Paladin")
        d.giveColorlessMana(p, 5)

        assertNull(d.castSpell(p, vehicle).error)
        d.resolveAll()

        val permanent = d.findPermanent(p, "Knight Paladin")!!
        assertEquals(16, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.TRAMPLE))
        assertNull("Crew 1 should accept the 2-power Grizzly Bears",
            d.submit(CrewVehicle(p, permanent, listOf(crewMember))).error)
        d.resolveAll()
        assertTrue("Crewed Knight Paladin must become a creature", d.projected.isCreature(permanent))
    }
}
