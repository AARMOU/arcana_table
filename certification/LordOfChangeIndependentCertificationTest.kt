package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LordOfChange
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LordOfChangeIndependentCertificationTest {
    private val name = "Lord of Change"

    @Test
    fun castingRequiresSixGenericAndOneBlueAndEntersAsASixSixWithFlying() {
        val short = customScenario(LordOfChange)
        val shortCard = short.putCardInHand(short.player1, name)
        short.giveColorlessMana(short.player1, 5)
        short.giveMana(short.player1, Color.BLUE)

        assertNotNull(short.castSpell(short.player1, shortCard).error)
        assertTrue(short.getHand(short.player1).contains(shortCard))
        assertEquals(0, short.stackSize)
        assertEquals(5, short.manaPool(short.player1).colorless)
        assertEquals(1, short.manaPool(short.player1).blue)

        val missingBlue = customScenario(LordOfChange)
        val missingBlueCard = missingBlue.putCardInHand(missingBlue.player1, name)
        missingBlue.giveColorlessMana(missingBlue.player1, 6)

        assertNotNull(missingBlue.castSpell(missingBlue.player1, missingBlueCard).error)
        assertTrue(missingBlue.getHand(missingBlue.player1).contains(missingBlueCard))
        assertEquals(0, missingBlue.stackSize)
        assertEquals(6, missingBlue.manaPool(missingBlue.player1).colorless)

        val paid = customScenario(LordOfChange)
        val player = paid.player1
        val card = paid.putCardInHand(player, name)
        paid.giveColorlessMana(player, 6)
        paid.giveMana(player, Color.BLUE)

        assertNull(paid.castSpell(player, card).error)
        paid.resolveAll()

        assertTrue(paid.permanentsNamed(player, name).contains(card))
        assertTrue(paid.projected.isCreature(card))
        assertTrue(paid.projected.hasSubtype(card, "DEMON"))
        assertEquals(6, paid.projected.getPower(card))
        assertEquals(6, paid.projected.getToughness(card))
        assertTrue(paid.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(0, paid.manaPool(player).colorless)
        assertEquals(0, paid.manaPool(player).blue)
    }

    @Test
    fun flyingPreventsGroundBlockersButAllowsAFlyingBlocker() {
        val d = customScenario(LordOfChange, "Grizzly Bears", "Serra Angel")
        val player = d.player1
        val opponent = d.player2
        val lord = d.putCreatureOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val angel = d.putCreatureOnBattlefield(opponent, "Serra Angel")
        d.removeSummoningSickness(lord)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(lord), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, mapOf(bear to listOf(lord))).error)
        assertNull(d.declareBlockers(opponent, mapOf(angel to listOf(lord))).error)
    }

    @Test
    fun anOpponentCanPayWardThreeToResolveATargetedSpell() {
        val d = customScenario(LordOfChange, "Giant Growth")
        val player = d.player1
        val opponent = d.player2
        val lord = d.putCreatureOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val growth = d.putCardInHand(opponent, "Giant Growth")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 3)

        assertNull(d.castSpell(opponent, growth, listOf(lord)).error)
        resolveWithWardPayment(d, opponent)

        assertEquals(9, d.projected.getPower(lord))
        assertEquals(9, d.projected.getToughness(lord))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Giant Growth"))
        assertEquals(0, d.manaPool(opponent).colorless)
        assertEquals(0, d.manaPool(opponent).green)
    }

    @Test
    fun twoManaCannotPayWardAndTheTargetedSpellIsCountered() {
        val d = customScenario(LordOfChange, "Giant Growth")
        val player = d.player1
        val opponent = d.player2
        val lord = d.putCreatureOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val growth = d.putCardInHand(opponent, "Giant Growth")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 2)

        assertNull(d.castSpell(opponent, growth, listOf(lord)).error)
        d.resolveAll()

        assertEquals(6, d.projected.getPower(lord))
        assertEquals(6, d.projected.getToughness(lord))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Giant Growth"))
        assertEquals(2, d.manaPool(opponent).colorless)
    }

    @Test
    fun wardDoesNotTaxItsControllerForTargetingTheirLord() {
        val d = customScenario(LordOfChange, "Giant Growth")
        val player = d.player1
        val lord = d.putCreatureOnBattlefield(player, name)
        val growth = d.putCardInHand(player, "Giant Growth")
        d.giveMana(player, Color.GREEN)

        assertNull(d.castSpell(player, growth, listOf(lord)).error)
        d.resolveAll()

        assertEquals(9, d.projected.getPower(lord))
        assertEquals(9, d.projected.getToughness(lord))
        assertEquals(0, d.manaPool(player).green)
    }

    @Test
    fun architectOfDeceptionDrawsExactlyThreeOnlyAfterLordEnters() {
        val d = customScenario(LordOfChange, "Forest", "Island", "Swamp")
        val player = d.player1
        val drawnCards = listOf(
            d.putCardOnTopOfLibrary(player, "Forest"),
            d.putCardOnTopOfLibrary(player, "Island"),
            d.putCardOnTopOfLibrary(player, "Swamp"),
        )
        val libraryBeforeCast = d.zoneNames(player, Zone.LIBRARY)
        val lord = d.putCardInHand(player, name)
        val handBeforeCast = d.getHandSize(player)
        d.giveColorlessMana(player, 6)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, lord).error)
        assertEquals(1, d.stackSize)
        assertEquals(handBeforeCast - 1, d.getHandSize(player))
        assertTrue(drawnCards.none { it in d.getHand(player) })
        assertEquals(libraryBeforeCast, d.zoneNames(player, Zone.LIBRARY))

        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(lord))
        assertTrue(d.getHand(player).containsAll(drawnCards))
        assertEquals(handBeforeCast + 2, d.getHandSize(player))
        assertEquals(libraryBeforeCast.size - 3, d.zoneNames(player, Zone.LIBRARY).size)
    }

    private fun resolveWithWardPayment(d: GameTestDriver, payer: com.wingedsheep.sdk.model.EntityId) {
        repeat(30) {
            when (d.state.pendingDecision) {
                is SelectManaSourcesDecision -> assertNull(d.submitManaAutoPayOrDecline(payer, autoPay = true).error)
                null -> if (d.stackSize == 0) return else d.bothPass()
                else -> d.decide(Choices(yes = true))
            }
        }
        fail("Ward-payment stack did not settle")
    }
}
