package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlamogsDreadsire
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class UlamogsDreadsireScenarioTest {
    @Test
    fun tapAbilityCreatesATenTenColorlessEldrazi() {
        val d = customScenario(UlamogsDreadsire)
        val dreadsire = d.putCreatureOnBattlefield(d.player1, "Ulamog's Dreadsire")
        d.removeSummoningSickness(dreadsire)

        assertNull(d.activate(d.player1, dreadsire).error)
        d.resolveAll()

        val token = d.permanentsNamed(d.player1, "Eldrazi").single()
        assertEquals(10, d.projected.getPower(token))
        assertEquals(10, d.projected.getToughness(token))
        assertTrue(d.projected.hasKeyword(dreadsire, Keyword.VIGILANCE))
    }

    @Test
    fun tapAbilityRequiresSummoningSicknessToEndAndWorksAtOpponentPriority() {
        val d = customScenario(UlamogsDreadsire)
        val opponent = d.player2
        val dreadsire = d.putCreatureOnBattlefield(opponent, "Ulamog's Dreadsire")

        assertNull(d.passPriority(d.player1).error)
        assertNotNull(d.activate(opponent, dreadsire).error)
        d.removeSummoningSickness(dreadsire)
        assertNull(d.activate(opponent, dreadsire).error)
        d.resolveAll()

        val token = d.permanentsNamed(opponent, "Eldrazi").single()
        assertEquals(10, d.projected.getPower(token))
        assertEquals(10, d.projected.getToughness(token))
    }

    @Test
    fun wardSacrificesAnEligiblePermanentBeforeTheTargetedSpellResolves() {
        val d = customScenario(UlamogsDreadsire, "Murder", "Grizzly Bears")
        val p = d.player1
        val dreadsire = d.putCreatureOnBattlefield(p, "Ulamog's Dreadsire")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)

        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(d.player2, murder, listOf(dreadsire)).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertNull(d.findPermanent(p, "Ulamog's Dreadsire"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Murder"))
    }

    @Test
    fun wardCannotBePaidBySacrificingAZeroManaValueLand() {
        val d = customScenario(UlamogsDreadsire, "Murder")
        val p = d.player1
        val dreadsire = d.putCreatureOnBattlefield(p, "Ulamog's Dreadsire")
        d.putPermanentOnBattlefield(d.player2, "Plains")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)

        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(d.player2, murder, listOf(dreadsire)).error)
        d.resolveAll()

        assertNotNull(d.findPermanent(p, "Ulamog's Dreadsire"))
        assertEquals(1, d.permanentsNamed(d.player2, "Plains").size)
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Murder"))
    }
}
