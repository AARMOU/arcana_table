package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AllIsDust
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AllIsDustIndependentCertificationTest {
    @Test
    fun eachPlayerSacrificesEveryColoredPermanentAndKeepsColorlessPermanents() {
        val d = customScenario(
            AllIsDust,
            "Serra Angel",
            "Merfolk Looter",
            "Goblin Raider",
            "Phyrexian Arena",
            "Grizzly Bears",
            "Sol Ring",
        )
        val angel = d.putPermanentOnBattlefield(d.player1, "Serra Angel")
        val looter = d.putPermanentOnBattlefield(d.player1, "Merfolk Looter")
        val raider = d.putPermanentOnBattlefield(d.player2, "Goblin Raider")
        val arena = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val bears = d.putPermanentOnBattlefield(d.player2, "Grizzly Bears")
        val player1Ring = d.putPermanentOnBattlefield(d.player1, "Sol Ring")
        val player2Ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(d.player1, "All Is Dust")
        d.giveColorlessMana(d.player1, 7)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertEquals(setOf(player1Ring), d.getPermanents(d.player1).toSet())
        assertEquals(setOf(player2Ring), d.getPermanents(d.player2).toSet())
        assertEquals(
            listOf("All Is Dust", "Merfolk Looter", "Serra Angel"),
            d.zoneNames(d.player1, Zone.GRAVEYARD).sorted(),
        )
        assertEquals(
            listOf("Goblin Raider", "Grizzly Bears", "Phyrexian Arena"),
            d.zoneNames(d.player2, Zone.GRAVEYARD).sorted(),
        )
        assertFalse(d.getPermanents(d.player1).any { it in setOf(angel, looter) })
        assertFalse(d.getPermanents(d.player2).any { it in setOf(raider, arena, bears) })
    }

    @Test
    fun sevenGenericManaIsRequiredToCastAllIsDust() {
        val d = customScenario(AllIsDust)
        val spell = d.putCardInHand(d.player1, "All Is Dust")
        d.giveColorlessMana(d.player1, 6)

        assertNotNull(d.castSpell(d.player1, spell).error)
        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player1).isEmpty())

        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertFalse(d.getHand(d.player1).contains(spell))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("All Is Dust"))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(AllIsDust, "Grizzly Bears", "Sol Ring")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(d.player1, "All Is Dust")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(d.player1, 7)

        assertNotNull(d.castSpell(d.player1, spell).error)

        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).containsAll(listOf(bear, ring)))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).isEmpty())
    }
}
