package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Poxwalkers
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PoxwalkersScenarioTest {
    @Test
    fun deathtouchKillsBlockersWithMoreToughnessThanPoxwalkersPower() {
        val d = customScenario(Poxwalkers, "Giant Spider")
        val poxwalkers = d.putCreatureOnBattlefield(d.player1, "Poxwalkers")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.removeSummoningSickness(poxwalkers)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(poxwalkers), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(spider to listOf(poxwalkers))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertFalse(poxwalkers in d.getPermanents(d.player1))
        assertFalse(spider in d.getPermanents(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Giant Spider"))
    }

    @Test
    fun deathtouchIsNotGrantedToOtherCreatures() {
        val d = customScenario(Poxwalkers, "Grizzly Bears")
        val poxwalkers = d.putCreatureOnBattlefield(d.player1, "Poxwalkers")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(poxwalkers, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(bear, Keyword.DEATHTOUCH))
    }

    @Test
    fun spellCastFromExileReturnsPoxwalkersFromItsGraveyardTapped() {
        val d = customScenario(Poxwalkers, "Grizzly Bears", "Heralds of Tzeentch", "Plains")
        val p = d.player1
        val poxwalkers = d.putCardInGraveyard(p, "Poxwalkers")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        val heralds = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 4)

        assertNull(d.castSpell(p, heralds).error)
        d.resolveAll()

        assertTrue(poxwalkers in d.getPermanents(p))
        assertTrue(d.isTapped(poxwalkers))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun spellCastFromHandDoesNotReturnPoxwalkers() {
        val d = customScenario(Poxwalkers, "Grizzly Bears")
        val p = d.player1
        val poxwalkers = d.putCardInGraveyard(p, "Poxwalkers")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()

        assertTrue(poxwalkers in d.getGraveyard(p))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun opponentSpellCastFromExileDoesNotReturnPoxwalkers() {
        val d = customScenario(Poxwalkers, "Grizzly Bears", "Heralds of Tzeentch", "Plains")
        val poxwalkers = d.putCardInGraveyard(d.player1, "Poxwalkers")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.putCardOnTopOfLibrary(d.player2, "Grizzly Bears")
        d.putCardOnTopOfLibrary(d.player2, "Plains")
        val heralds = d.putCardInHand(d.player2, "Heralds of Tzeentch")
        d.giveMana(d.player2, Color.BLUE)
        d.giveColorlessMana(d.player2, 4)

        assertNull(d.castSpell(d.player2, heralds).error)
        d.resolveAll()

        assertTrue(poxwalkers in d.getGraveyard(d.player1))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isNotEmpty())
    }
}
