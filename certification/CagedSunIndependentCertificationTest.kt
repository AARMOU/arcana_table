package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CagedSun
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CagedSunIndependentCertificationTest {
    private val name = "Caged Sun"

    @Test
    fun castingRequiresSixGenericMana() {
        val d = customScenario(CagedSun)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 5)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))
        assertEquals(5, d.manaPool(player).colorless)

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))

        assertTrue(d.permanentsNamed(player, name).contains(card))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertEquals(0, d.manaPool(player).colorless)
    }

    @Test
    fun chosenColorBoostsOnlyYourCreaturesOfThatColor() {
        val d = customScenario(CagedSun, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        val angel = d.putCreatureOnBattlefield(player, "Serra Angel")
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opponentAngel = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.WHITE))

        assertEquals(5, d.projected.getPower(angel))
        assertEquals(5, d.projected.getToughness(angel))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(2, d.projected.getToughness(bear))
        assertEquals(4, d.projected.getPower(opponentAngel))
        assertEquals(4, d.projected.getToughness(opponentAngel))
    }

    @Test
    fun chosenColorManaFromYourForestAddsExactlyOneGreen() {
        val d = customScenario(CagedSun, "Forest")
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val forest = d.putLandOnBattlefield(player, "Forest")

        assertNull(d.activate(player, forest).error)
        d.resolveAll()

        assertEquals(2, d.manaPool(player).green)
        assertEquals(0, d.manaPool(player).colorless)
    }

    @Test
    fun landManaOfAColorOtherThanTheChosenColorIsNotIncreased() {
        val d = customScenario(CagedSun, "Mountain")
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val mountain = d.putLandOnBattlefield(player, "Mountain")

        assertNull(d.activate(player, mountain).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(player).red)
        assertEquals(0, d.manaPool(player).green)
    }

    @Test
    fun matchingManaFromANonlandAbilityDoesNotTriggerCagedSun() {
        val d = customScenario(CagedSun, "Llanowar Elves")
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val elves = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.removeSummoningSickness(elves)

        assertNull(d.activate(player, elves).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(player).green)
    }

    @Test
    fun opponentAddingChosenColorManaDoesNotTriggerYourCagedSun() {
        val d = customScenario(CagedSun, "Forest")
        val player = d.player1
        val opponent = d.player2
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val forest = d.putLandOnBattlefield(opponent, "Forest")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, opponent)

        assertNull(d.activate(opponent, forest).error)
        d.resolveAll()

        assertEquals(0, d.manaPool(player).green)
        assertEquals(1, d.manaPool(opponent).green)
    }

    @Test
    fun eachSeparateMatchingLandManaAbilityAddsOneAdditionalMana() {
        val d = customScenario(CagedSun, "Forest")
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val forest = d.putLandOnBattlefield(player, "Forest")

        repeat(2) {
            d.untapPermanent(forest)
            assertNull(d.activate(player, forest).error)
            d.resolveAll()
        }

        assertEquals(4, d.manaPool(player).green)
    }
}
