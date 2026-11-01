package com.arcanatable.customcards

import com.arcanatable.customcards.cards.StarOfExtinction
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class StarOfExtinctionScenarioTest {
    @Test
    fun destroysTargetLandAndDealsDamageToEveryCreatureAndPlaneswalker() {
        val d = customScenario(StarOfExtinction, "Forest", "Plains", "Grizzly Bears", "Jace Beleren")
        val p = d.player1
        val opponent = d.player2
        val targetLand = d.putPermanentOnBattlefield(opponent, "Forest")
        d.putPermanentOnBattlefield(p, "Plains")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putPermanentOnBattlefield(opponent, "Jace Beleren")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)

        assertNull(d.castSpell(p, spell, listOf(targetLand)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opponent, "Forest").isEmpty())
        assertTrue(d.permanentsNamed(p, "Plains").isNotEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Jace Beleren").isEmpty())
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(opponent))
    }

    @Test
    fun rejectsCreatureAsTargetAndLeavesBattlefieldUnchanged() {
        val d = customScenario(StarOfExtinction, "Forest", "Grizzly Bears", "Jace Beleren")
        val p = d.player1
        val opponent = d.player2
        d.putPermanentOnBattlefield(opponent, "Forest")
        val creature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putPermanentOnBattlefield(opponent, "Jace Beleren")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)

        assertNotNull(d.castSpell(p, spell, listOf(creature)).error)

        assertTrue(d.permanentsNamed(opponent, "Forest").isNotEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(opponent, "Jace Beleren").isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Star of Extinction"))
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(opponent))
    }
}
