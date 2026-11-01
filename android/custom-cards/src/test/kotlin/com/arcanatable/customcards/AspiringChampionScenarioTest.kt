package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AspiringChampion
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class AspiringChampionScenarioTest {
    @Test
    fun menaceRequiresAtLeastTwoBlockersAndBlockedDamageDoesNotTriggerAscension() {
        val d = customScenario(AspiringChampion, "Pestilence Demon", "Wall of Omens")
        val player = d.player1
        val champion = d.putCreatureOnBattlefield(player, "Aspiring Champion")
        val firstBlocker = d.putCreatureOnBattlefield(d.player2, "Wall of Omens")
        val secondBlocker = d.putCreatureOnBattlefield(d.player2, "Wall of Omens")
        d.putCardOnTopOfLibrary(player, "Pestilence Demon")
        d.removeSummoningSickness(champion)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS, activePlayer = player)

        assertNotNull(d.declareBlockers(d.player2, mapOf(firstBlocker to listOf(champion))).error)
        assertNull(
            d.declareBlockers(
                d.player2,
                mapOf(firstBlocker to listOf(champion), secondBlocker to listOf(champion)),
            ).error,
        )
        d.resolveAll()

        assertTrue(champion in d.getPermanents(player))
        assertFalse(d.getPermanents(player).any { d.getCardName(it) == "Pestilence Demon" })
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun combatDamageSacrificesChampionAndDealsDemonPowerToEachOpponent() {
        val d = CardPool.driver("Pestilence Demon").apply {
            initMultiplayer(List(3) { Deck.of("Plains" to 40) })
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = d.player1
        val champion = d.putCreatureOnBattlefield(player, "Aspiring Champion")
        d.putCardOnTopOfLibrary(player, "Pestilence Demon")
        d.putCardOnTopOfLibrary(player, "Plains")
        d.removeSummoningSickness(champion)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = player)
        d.resolveAll()

        assertFalse(champion in d.getPermanents(player))
        assertTrue(d.getPermanents(player).any { d.getCardName(it) == "Pestilence Demon" })
        assertEquals(10, d.getLifeTotal(d.player2))
        assertEquals(13, d.getLifeTotal(d.player3))
    }

    @Test
    fun nonDemonCreatureEntersWithoutDealingAdditionalDamage() {
        val d = customScenario(AspiringChampion, "Grizzly Bears")
        val player = d.player1
        val champion = d.putCreatureOnBattlefield(player, "Aspiring Champion")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Plains")
        d.removeSummoningSickness(champion)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = player)
        d.resolveAll()

        assertFalse(champion in d.getPermanents(player))
        assertTrue(d.getPermanents(player).any { d.getCardName(it) == "Grizzly Bears" })
        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun sacrificeStillHappensWhenLibraryHasNoCreature() {
        val d = customScenario(AspiringChampion)
        val player = d.player1
        val champion = d.putCreatureOnBattlefield(player, "Aspiring Champion")
        d.removeSummoningSickness(champion)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(champion), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = player)
        d.resolveAll()

        assertFalse(champion in d.getPermanents(player))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Aspiring Champion"))
        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(40, d.zoneNames(player, Zone.LIBRARY).size)
    }
}
