package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FlayedOne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class FlayedOneIndependentCertificationTest {
    private val name = "Flayed One"

    @Test
    fun costsTwoGenericAndBlackAndEntersAsAFourOneNecronWithLifelink() {
        val d = customScenario(FlayedOne)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, card).error)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        val flayedOne = d.permanentsNamed(p, name).single()
        assertTrue(d.projected.hasType(flayedOne, "ARTIFACT"))
        assertTrue(d.projected.isCreature(flayedOne))
        assertTrue(d.projected.hasSubtype(flayedOne, "NECRON"))
        assertEquals(4, d.projected.getPower(flayedOne))
        assertEquals(1, d.projected.getToughness(flayedOne))
        assertTrue(d.projected.hasKeyword(flayedOne, Keyword.LIFELINK))
    }

    @Test
    fun cannotCastWithoutBothGenericAndBlackMana() {
        val shortGeneric = customScenario(FlayedOne)
        val p = shortGeneric.player1
        val card = shortGeneric.putCardInHand(p, name)
        shortGeneric.giveMana(p, Color.BLACK)
        shortGeneric.giveColorlessMana(p, 1)

        assertNotNull(shortGeneric.castSpell(p, card).error)
        assertTrue(shortGeneric.getHand(p).contains(card))
        assertTrue(shortGeneric.permanentsNamed(p, name).isEmpty())

        val noBlack = customScenario(FlayedOne)
        val pWithoutBlack = noBlack.player1
        val cardWithoutBlack = noBlack.putCardInHand(pWithoutBlack, name)
        noBlack.giveColorlessMana(pWithoutBlack, 2)

        assertNotNull(noBlack.castSpell(pWithoutBlack, cardWithoutBlack).error)
        assertTrue(noBlack.getHand(pWithoutBlack).contains(cardWithoutBlack))
        assertTrue(noBlack.permanentsNamed(pWithoutBlack, name).isEmpty())
    }

    @Test
    fun enteringMillsExactlyThreeOfItsControllersCardsOnlyWhenItResolves() {
        val d = customScenario(FlayedOne, "Forest", "Island", "Swamp", "Plains")
        val p = d.player1
        val opponent = d.player2
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY)
        val opponentLibraryBefore = d.zoneNames(opponent, Zone.LIBRARY)
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, card).error)
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).isEmpty())
        assertEquals(opponentLibraryBefore, d.zoneNames(opponent, Zone.LIBRARY))
        d.resolveAll()

        val libraryAfter = d.zoneNames(p, Zone.LIBRARY)
        val graveyardAfter = d.zoneNames(p, Zone.GRAVEYARD)
        assertEquals(libraryBefore.size - 3, libraryAfter.size)
        assertEquals(3, graveyardAfter.size)
        assertTrue(libraryBefore.containsAll(graveyardAfter))
        assertEquals(opponentLibraryBefore, d.zoneNames(opponent, Zone.LIBRARY))
    }

    @Test
    fun combatDamageWithLifelinkGainsFourLife() {
        val d = customScenario(FlayedOne)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        val flayedOne = d.permanentsNamed(p, name).single()
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(d.player2))
        d.removeSummoningSickness(flayedOne)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(flayedOne), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(24, d.getLifeTotal(p))
        assertEquals(16, d.getLifeTotal(d.player2))
    }
}
