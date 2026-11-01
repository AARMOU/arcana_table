package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Chronomancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ChronomancerIndependentCertificationTest {
    @Test
    fun flyingPreventsAGroundCreatureFromBlocking() {
        val d = customScenario(Chronomancer, "Grizzly Bears")
        val player = d.player1
        val chronomancer = d.putCreatureOnBattlefield(player, "Chronomancer")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(chronomancer)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(chronomancer, Keyword.FLYING))
        assertNull(d.declareAttackers(player, listOf(chronomancer), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(d.player2, mapOf(bear to listOf(chronomancer))).error)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
    }

    @Test
    fun atomicTransmutationPaysOneTapsSacrificesAnotherArtifactAndDraws() {
        val d = customScenario(Chronomancer)
        val player = d.player1
        val chronomancer = d.putCreatureOnBattlefield(player, "Chronomancer")
        d.removeSummoningSickness(chronomancer)
        d.putPermanentOnBattlefield(player, "Sol Ring")
        d.giveColorlessMana(player, 1)
        val handBefore = d.getHandSize(player)

        assertNull(d.activate(player, chronomancer).error)
        assertEquals(0, d.manaPool(player).colorless)
        assertTrue(d.isTapped(chronomancer))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals(handBefore, d.getHandSize(player))
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHandSize(player))
    }

    @Test
    fun atomicTransmutationCannotBeActivatedWithoutItsManaCost() {
        val d = customScenario(Chronomancer)
        val player = d.player1
        val chronomancer = d.putCreatureOnBattlefield(player, "Chronomancer")
        d.removeSummoningSickness(chronomancer)
        d.putPermanentOnBattlefield(player, "Sol Ring")

        assertNotNull(d.activate(player, chronomancer).error)

        assertFalse(d.isTapped(chronomancer))
        assertTrue(d.permanentsNamed(player, "Sol Ring").isNotEmpty())
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).isEmpty())
    }

    @Test
    fun atomicTransmutationRequiresAnotherArtifactToSacrifice() {
        val d = customScenario(Chronomancer, "Grizzly Bears")
        val player = d.player1
        val chronomancer = d.putCreatureOnBattlefield(player, "Chronomancer")
        d.removeSummoningSickness(chronomancer)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.giveColorlessMana(player, 1)

        assertNotNull(d.activate(player, chronomancer).error)

        assertFalse(d.isTapped(chronomancer))
        assertTrue(d.permanentsNamed(player, "Chronomancer").contains(chronomancer))
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").contains(bear))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).isEmpty())
    }

    @Test
    fun atomicTransmutationCannotBeActivatedAgainWhileChronomancerIsTapped() {
        val d = customScenario(Chronomancer, "Ornithopter")
        val player = d.player1
        val chronomancer = d.putCreatureOnBattlefield(player, "Chronomancer")
        d.removeSummoningSickness(chronomancer)
        d.putPermanentOnBattlefield(player, "Sol Ring")
        d.giveColorlessMana(player, 2)

        assertNull(d.activate(player, chronomancer).error)
        d.resolveAll()
        val handAfterFirstActivation = d.getHandSize(player)
        val ornithopter = d.putPermanentOnBattlefield(player, "Ornithopter")

        assertTrue(d.isTapped(chronomancer))
        assertNotNull(d.activate(player, chronomancer).error)
        assertEquals(handAfterFirstActivation, d.getHandSize(player))
        assertTrue(d.permanentsNamed(player, "Ornithopter").contains(ornithopter))
        assertEquals(1, d.manaPool(player).colorless)
    }

    @Test
    fun unearthCostsTwoGenericAndBlackReturnsWithHasteAndExilesAtNextEndStep() {
        val d = customScenario(Chronomancer)
        val player = d.player1
        val chronomancer = d.putCardInGraveyard(player, "Chronomancer")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK)

        assertNull(d.activate(player, chronomancer).error)
        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).black)
        d.resolveAll()
        val returned = d.findPermanent(player, "Chronomancer")!!
        assertTrue(d.projected.hasKeyword(returned, Keyword.HASTE))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(returned), d.player2).error)
        d.passPriorityUntil(Step.END, activePlayer = player)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Chronomancer"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Chronomancer"))
        assertTrue(d.permanentsNamed(player, "Chronomancer").isEmpty())
    }

    @Test
    fun unearthCannotBeActivatedWithoutItsBlackMana() {
        val d = customScenario(Chronomancer)
        val player = d.player1
        val chronomancer = d.putCardInGraveyard(player, "Chronomancer")
        d.giveColorlessMana(player, 3)

        assertNotNull(d.activate(player, chronomancer).error)

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Chronomancer"))
        assertTrue(d.permanentsNamed(player, "Chronomancer").isEmpty())
        assertEquals(3, d.manaPool(player).colorless)
    }

    @Test
    fun unearthCanOnlyBeActivatedAsASorcery() {
        val d = customScenario(Chronomancer)
        val player = d.player1
        val opponent = d.player2
        val chronomancer = d.putCardInGraveyard(player, "Chronomancer")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.activate(player, chronomancer).error)

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Chronomancer"))
        assertTrue(d.permanentsNamed(player, "Chronomancer").isEmpty())
        assertEquals(2, d.manaPool(player).colorless)
        assertEquals(1, d.manaPool(player).black)
    }

    @Test
    fun unearthExilesChronomancerIfItWouldLeaveTheBattlefield() {
        val d = customScenario(Chronomancer, "Shock")
        val player = d.player1
        val chronomancer = d.putCardInGraveyard(player, "Chronomancer")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK)
        assertNull(d.activate(player, chronomancer).error)
        d.resolveAll()
        val returned = d.findPermanent(player, "Chronomancer")!!
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, shock, listOf(returned)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Chronomancer"))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Chronomancer"))
        assertTrue(d.permanentsNamed(player, "Chronomancer").isEmpty())
    }
}
