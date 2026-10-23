package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GreyKnightParagon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class GreyKnightParagonScenarioTest {
    @Test
    fun flashesIntoOpponentsCombatAndDestroysAnAttackingNonDemon() {
        val d = customScenario(GreyKnightParagon, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val attacker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val reserve = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(attacker), d.player1).error)
        d.passPriority(d.player2)
        val knight = d.putCardInHand(d.player1, "Grey Knight Paragon")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 4)
        assertNull(d.castSpell(d.player1, knight).error)
        d.resolveAll(Choices(targets = listOf(attacker)))

        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(d.player2, Zone.EXILE).contains("Grizzly Bears"))
        assertEquals(listOf(reserve), d.permanentsNamed(d.player2, "Grizzly Bears"))
        assertTrue(d.projected.hasKeyword(d.findPermanent(d.player1, "Grey Knight Paragon")!!, Keyword.FLASH))
    }

    @Test
    fun anAttackingDemonIsExiledInsteadOfDestroyed() {
        val d = customScenario(GreyKnightParagon, "Lord of the Pit")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val demon = d.putCreatureOnBattlefield(d.player2, "Lord of the Pit")
        d.removeSummoningSickness(demon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(demon), d.player1).error)
        d.passPriority(d.player2)
        val knight = d.putCardInHand(d.player1, "Grey Knight Paragon")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 4)
        assertNull(d.castSpell(d.player1, knight).error)
        d.resolveAll(Choices(targets = listOf(demon)))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Lord of the Pit"))
        assertFalse(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Lord of the Pit"))
        assertTrue(d.permanentsNamed(d.player2, "Lord of the Pit").isEmpty())
    }

    @Test
    fun enteringOutsideCombatDoesNotDestroyANonattackingCreature() {
        val d = customScenario(GreyKnightParagon, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val knight = d.putCardInHand(d.player1, "Grey Knight Paragon")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 4)
        assertNull(d.castSpell(d.player1, knight).error)
        d.resolveAll()
        assertEquals(bear, d.findPermanent(d.player2, "Grizzly Bears"))
        assertNotNull(d.findPermanent(d.player1, "Grey Knight Paragon"))
    }
}
