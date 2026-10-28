package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LokhustHeavyDestroyer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LokhustHeavyDestroyerScenarioTest {
    @Test
    fun aFlyingAttackerCanBeBlockedOnlyByFlyingCreatures() {
        val d = customScenario(LokhustHeavyDestroyer, "Grizzly Bears", "Serra Angel")
        val player = d.player1
        val attacker = d.putCreatureOnBattlefield(player, "Lokhust Heavy Destroyer")
        val nonFlyingBlocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val flyingBlocker = d.putCreatureOnBattlefield(d.player2, "Serra Angel")

        assertTrue(d.projected.hasKeyword(attacker, Keyword.FLYING))
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(d.player2, mapOf(nonFlyingBlocker to listOf(attacker))).error)
        assertNull(d.declareBlockers(d.player2, mapOf(flyingBlocker to listOf(attacker))).error)
    }

    @Test
    fun entryHasEachPlayerChooseAndSacrificeTheirOwnCreature() {
        val d = customScenario(LokhustHeavyDestroyer, "Grizzly Bears", "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player1, "Llanowar Elves")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.BLACK, 3)

        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(pick = listOf("Llanowar Elves", "Grizzly Bears")))

        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Llanowar Elves"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertNull(d.findPermanent(d.player1, "Llanowar Elves"))
        assertNull(d.findPermanent(d.player2, "Grizzly Bears"))
        assertEquals(1, d.permanentsNamed(d.player1, "Lokhust Heavy Destroyer").size)
    }

    @Test
    fun entryAllowsSacrificingTheSourceAndDoesNothingForAPlayerWithNoCreatures() {
        val d = customScenario(LokhustHeavyDestroyer)
        val card = d.putCardInHand(d.player1, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(d.player1, 1)
        d.giveMana(d.player1, Color.BLACK, 3)

        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(pick = listOf("Lokhust Heavy Destroyer")))

        assertTrue(d.permanentsNamed(d.player1, "Lokhust Heavy Destroyer").isEmpty())
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Lokhust Heavy Destroyer"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).isEmpty())
    }

    @Test
    fun unearthCostsFiveGenericAndThreeBlackGrantsHasteAndExilesAtNextEndStep() {
        val d = customScenario(LokhustHeavyDestroyer, "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInGraveyard(player, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.BLACK, 3)

        assertNull(d.activate(player, card).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        val creature = d.findPermanent(player, "Lokhust Heavy Destroyer")!!
        assertTrue(d.projected.hasKeyword(creature, Keyword.HASTE))

        d.passPriorityUntil(Step.END, activePlayer = player)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Lokhust Heavy Destroyer").isEmpty())
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Lokhust Heavy Destroyer"))
    }

    @Test
    fun unearthExilesTheCreatureIfItWouldLeaveTheBattlefield() {
        val d = customScenario(LokhustHeavyDestroyer, "Grizzly Bears", "Unsummon")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInGraveyard(player, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.BLACK, 3)

        assertNull(d.activate(player, card).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        val creature = d.findPermanent(player, "Lokhust Heavy Destroyer")!!
        val unsummon = d.putCardInHand(player, "Unsummon")
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, unsummon, listOf(creature)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Lokhust Heavy Destroyer").isEmpty())
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Lokhust Heavy Destroyer"))
        assertFalse(d.zoneNames(player, Zone.HAND).contains("Lokhust Heavy Destroyer"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Lokhust Heavy Destroyer"))
    }

    @Test
    fun unearthCannotBeActivatedWithInsufficientManaOrOutsideSorceryTiming() {
        val d = customScenario(LokhustHeavyDestroyer)
        val player = d.player1
        val card = d.putCardInGraveyard(player, "Lokhust Heavy Destroyer")
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 3)

        assertNotNull(d.activate(player, card).error)
        d.giveColorlessMana(player, 1)
        d.passPriorityUntil(Step.END, activePlayer = player)

        assertTrue(d.abilityActions(player, card).isEmpty())
        assertNotNull(d.activate(player, card).error)
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Lokhust Heavy Destroyer"))
    }
}
