package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SkorpekhLord
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SkorpekhLordScenarioTest {
    @Test
    fun grantsOnlyOtherAlliedArtifactCreaturesPowerAndMenace() {
        val d = customScenario(SkorpekhLord, "Ornithopter", "Grizzly Bears")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Skorpekh Lord")
        val ally = d.putCreatureOnBattlefield(p, "Ornithopter")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        assertEquals(3, d.projected.getPower(lord))
        assertTrue(d.projected.hasKeyword(lord, Keyword.MENACE))
        assertEquals(1, d.projected.getPower(ally))
        assertEquals(2, d.projected.getToughness(ally))
        assertTrue(d.projected.hasKeyword(ally, Keyword.MENACE))
        assertEquals(0, d.projected.getPower(enemy))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.MENACE))
        assertEquals(2, d.projected.getPower(bear))
        assertFalse(d.projected.hasKeyword(bear, Keyword.MENACE))
    }

    @Test
    fun unearthReturnsWithHasteAndExilesAtNextEndStep() {
        val d = customScenario(SkorpekhLord)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Skorpekh Lord")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, card).error)
        d.resolveAll()
        val lord = d.findPermanent(p, "Skorpekh Lord")!!
        assertTrue(d.projected.hasKeyword(lord, Keyword.HASTE))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Skorpekh Lord"))

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Skorpekh Lord").isEmpty())
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Skorpekh Lord"))
    }

    @Test
    fun unearthExilesInsteadOfReturningToHand() {
        val d = customScenario(SkorpekhLord, "Unsummon")
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Skorpekh Lord")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, card).error)
        d.resolveAll()
        val lord = d.findPermanent(p, "Skorpekh Lord")!!
        val unsummon = d.putCardInHand(p, "Unsummon")
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, unsummon, listOf(lord)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Skorpekh Lord").isEmpty())
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Skorpekh Lord"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Skorpekh Lord"))
    }

    @Test
    fun unearthCannotBeActivatedOutsideSorceryTiming() {
        val d = customScenario(SkorpekhLord)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Skorpekh Lord")
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertTrue(d.abilityActions(p, card).isEmpty())
        assertNotNull(d.activate(p, card).error)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Skorpekh Lord"))
    }
}
