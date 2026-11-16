package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RoyalWarden
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class RoyalWardenIndependentCertificationTest {
    private val name = "Royal Warden"

    private fun assertTwoTappedNecronWarriors(d: GameTestDriver, player: EntityId) {
        val warriors = d.permanentsNamed(player, "Necron Warrior")
        assertEquals(2, warriors.size)
        warriors.forEach { warrior ->
            assertTrue(d.state.getEntity(warrior)!!.has<TokenComponent>())
            assertTrue(d.isTapped(warrior))
            assertTrue(d.projected.hasType(warrior, "ARTIFACT"))
            assertTrue(d.projected.isCreature(warrior))
            assertTrue(d.projected.hasSubtype(warrior, "NECRON"))
            assertTrue(d.projected.hasSubtype(warrior, "WARRIOR"))
            assertTrue(d.projected.hasColor(warrior, Color.BLACK))
            assertEquals(2, d.projected.getPower(warrior))
            assertEquals(2, d.projected.getToughness(warrior))
        }
    }

    @Test
    fun castingPaysThreeGenericAndTwoBlackManaAndCreatesTwoTappedWarriors() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(d.castSpell(player, warden).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(warden))
        assertTrue(d.projected.hasType(warden, "ARTIFACT"))
        assertTrue(d.projected.isCreature(warden))
        assertTrue(d.projected.hasSubtype(warden, "NECRON"))
        assertTrue(d.projected.hasColor(warden, Color.BLACK))
        assertEquals(3, d.projected.getPower(warden))
        assertEquals(2, d.projected.getToughness(warden))
        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).black)
        assertTwoTappedNecronWarriors(d, player)
    }

    @Test
    fun castingCannotBePaidWithOnlyFourMana() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpell(player, warden).error)
        assertTrue(d.getHand(player).contains(warden))
        assertTrue(d.permanentsNamed(player, name).isEmpty())
    }

    @Test
    fun castingCannotBePaidWithOnlyOneBlackMana() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpell(player, warden).error)
        assertTrue(d.getHand(player).contains(warden))
        assertTrue(d.permanentsNamed(player, name).isEmpty())
    }

    @Test
    fun unearthReturnsWithHasteCreatesTwoTappedWarriorsAndExilesAtTheNextEndStep() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val opponent = d.player2
        val warden = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)

        assertNull(d.activate(player, warden).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(warden))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).black)
        assertTrue(d.projected.hasKeyword(warden, Keyword.HASTE))
        assertTwoTappedNecronWarriors(d, player)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(warden), opponent).error)

        d.passPriorityUntil(Step.END, activePlayer = player)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains(name))
        assertFalse(d.permanentsNamed(player, name).contains(warden))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedWithOnlyThreeMana() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.activate(player, warden).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedWithoutBlackMana() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 4)

        assertNotNull(d.activate(player, warden).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedFromTheHand() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val warden = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.activate(player, warden).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))
    }

    @Test
    fun onlyItsOwnerCanUnearthRoyalWardenFromTheGraveyard() {
        val d = customScenario(RoyalWarden)
        val owner = d.player1
        val opponent = d.player2
        val warden = d.putCardInGraveyard(owner, name)
        d.giveColorlessMana(opponent, 3)
        d.giveMana(opponent, Color.BLACK)
        assertNull(d.passPriority(owner).error)
        assertEquals(opponent, d.priorityPlayer)

        assertNotNull(d.activate(opponent, warden).error)
        assertTrue(d.getGraveyardCardNames(owner).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(RoyalWarden)
        val player = d.player1
        val opponent = d.player2
        val warden = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        assertEquals(player, d.priorityPlayer)

        assertNotNull(d.activate(player, warden).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedWhileTheStackIsNotEmpty() {
        val d = customScenario(RoyalWarden, "Shock")
        val player = d.player1
        val warden = d.putCardInGraveyard(player, name)
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(d.player2)).error)
        assertEquals(1, d.stackSize)

        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)
        assertNotNull(d.activate(player, warden).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthExilesRoyalWardenIfItWouldLeaveTheBattlefield() {
        val d = customScenario(RoyalWarden, "Murder")
        val player = d.player1
        val warden = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)
        assertNull(d.activate(player, warden).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, name).contains(warden))

        val murder = d.putCardInHand(player, "Murder")
        d.giveColorlessMana(player, 1)
        d.giveMana(player, Color.BLACK, 2)
        assertNull(d.castSpell(player, murder, listOf(warden)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains(name))
        assertFalse(d.permanentsNamed(player, name).contains(warden))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
    }
}
