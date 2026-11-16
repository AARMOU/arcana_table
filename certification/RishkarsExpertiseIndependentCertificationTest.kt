package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RishkarsExpertise
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class RishkarsExpertiseIndependentCertificationTest {
    @Test
    fun castingRequiresFourGenericAndTwoGreenMana() {
        val d = customScenario(RishkarsExpertise)
        val p = d.player1
        val card = d.putCardInHand(p, "Rishkar's Expertise")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Rishkar's Expertise"))
    }

    @Test
    fun fiveManaOrSixColorlessManaCannotPayTheFourGreenGreenCost() {
        val short = customScenario(RishkarsExpertise)
        val shortCard = short.putCardInHand(short.player1, "Rishkar's Expertise")
        short.giveColorlessMana(short.player1, 5)
        short.giveMana(short.player1, Color.GREEN)

        assertNotNull(short.castSpell(short.player1, shortCard).error)
        assertTrue(short.getHand(short.player1).contains(shortCard))

        val noGreen = customScenario(RishkarsExpertise)
        val noGreenCard = noGreen.putCardInHand(noGreen.player1, "Rishkar's Expertise")
        noGreen.giveColorlessMana(noGreen.player1, 6)

        assertNotNull(noGreen.castSpell(noGreen.player1, noGreenCard).error)
        assertTrue(noGreen.getHand(noGreen.player1).contains(noGreenCard))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(RishkarsExpertise)
        val p = d.player1
        val card = d.putCardInHand(p, "Rishkar's Expertise")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Rishkar's Expertise"))
    }

    @Test
    fun drawsExactlyTheGreatestPowerAmongYourCreatures() {
        val d = customScenario(
            RishkarsExpertise,
            "Shock",
            "Divination",
            "Giant Growth",
            "Sol Ring",
            "Murder",
            "Lightning Bolt",
            "Plains",
            "Grizzly Bears",
            "Serra Angel",
            "Colossal Dreadmaw",
            "Carnage Tyrant",
        )
        val p = d.player1
        val card = d.putCardInHand(p, "Rishkar's Expertise")
        val handBeforeCast = d.getHand(p).size
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Serra Angel")
        d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        d.putCreatureOnBattlefield(d.player2, "Carnage Tyrant")
        val drawOrder = listOf("Shock", "Divination", "Giant Growth", "Sol Ring", "Murder", "Lightning Bolt", "Plains")
            .asReversed()
            .map { d.putCardOnTopOfLibrary(p, it) }
            .asReversed()

        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.getHand(p).containsAll(drawOrder.take(6)))
        assertEquals(handBeforeCast + 5, d.getHand(p).size)
        assertTrue(d.state.getZone(p, Zone.LIBRARY).contains(drawOrder[6]))
    }

    @Test
    fun drawsNoCardsWhenYouControlNoCreatures() {
        val d = customScenario(RishkarsExpertise, "Shock")
        val p = d.player1
        val card = d.putCardInHand(p, "Rishkar's Expertise")
        val shock = d.putCardOnTopOfLibrary(p, "Shock")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertFalse(d.getHand(p).contains(card))
        assertTrue(d.state.getZone(p, Zone.LIBRARY).contains(shock))
        assertFalse(d.state.getZone(p, Zone.GRAVEYARD).contains(shock))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Rishkar's Expertise"))
    }

    @Test
    fun castsAManaValueFiveSpellFromHandWithoutPayingItsManaCost() {
        val d = customScenario(RishkarsExpertise, "Serra Angel", "Ornithopter")
        val p = d.player1
        val expertise = d.putCardInHand(p, "Rishkar's Expertise")
        val angel = d.putCardInHand(p, "Serra Angel")
        d.putCreatureOnBattlefield(p, "Ornithopter")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, expertise).error)
        d.resolveAll(Choices(pick = listOf("Serra Angel")))

        assertTrue(d.permanentsNamed(p, "Serra Angel").contains(angel))
        assertFalse(d.getHand(p).contains(angel))
    }

    @Test
    fun cannotCastAManaValueAboveFiveOrACardThatIsNotInYourHand() {
        val d = customScenario(RishkarsExpertise, "Colossal Dreadmaw", "Grizzly Bears", "Ornithopter")
        val p = d.player1
        val expertise = d.putCardInHand(p, "Rishkar's Expertise")
        val dreadmaw = d.putCardInHand(p, "Colossal Dreadmaw")
        val bears = d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Ornithopter")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, expertise).error)
        d.resolveAll(Choices(pick = listOf("Colossal Dreadmaw", "Grizzly Bears")))

        assertTrue(d.getHand(p).contains(dreadmaw))
        assertTrue(d.state.getZone(p, Zone.LIBRARY).contains(bears))
        assertTrue(d.permanentsNamed(p, "Colossal Dreadmaw").isEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
    }

    @Test
    fun mayDeclineToCastAndKeepTheSpellInHand() {
        val d = customScenario(RishkarsExpertise, "Serra Angel", "Ornithopter")
        val p = d.player1
        val expertise = d.putCardInHand(p, "Rishkar's Expertise")
        val angel = d.putCardInHand(p, "Serra Angel")
        d.putCreatureOnBattlefield(p, "Ornithopter")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, expertise).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.getHand(p).contains(angel))
        assertTrue(d.permanentsNamed(p, "Serra Angel").isEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Rishkar's Expertise"))
    }
}
