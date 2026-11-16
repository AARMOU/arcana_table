package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SceptreOfEternalGlory
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SceptreOfEternalGloryIndependentCertificationTest {
    @Test
    fun castingCostsFourGenericManaAndCanBeActivatedImmediately() {
        val d = customScenario(SceptreOfEternalGlory)
        val p = d.player1
        val sceptre = d.putCardInHand(p, "Sceptre of Eternal Glory")
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, sceptre).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sceptre of Eternal Glory"))
        assertTrue(d.permanentsNamed(p, "Sceptre of Eternal Glory").isEmpty())

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, sceptre).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(sceptre))
        assertEquals(0, d.manaPool(p).colorless)

        assertNull(d.activate(p, sceptre, index = 0).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertEquals(1, d.manaPool(p).blue)
        assertTrue(d.isTapped(sceptre))
    }

    @Test
    fun ordinaryTapAbilityAddsExactlyOneManaOfEachChosenColor() {
        val d = customScenario(SceptreOfEternalGlory)
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
        val colors = listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)

        colors.forEach { color ->
            if (d.isTapped(sceptre)) d.untapPermanent(sceptre)
            assertNull(d.activate(p, sceptre, index = 0).error)
            d.resolveAll(Choices(color = color))
            assertTrue(d.isTapped(sceptre))
        }

        assertEquals(1, d.manaPool(p).white)
        assertEquals(1, d.manaPool(p).blue)
        assertEquals(1, d.manaPool(p).black)
        assertEquals(1, d.manaPool(p).red)
        assertEquals(1, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun ordinaryTapAbilityCannotBeActivatedAgainWhileTapped() {
        val d = customScenario(SceptreOfEternalGlory)
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")

        assertNull(d.activate(p, sceptre, index = 0).error)
        d.resolveAll(Choices(color = Color.RED))
        assertEquals(1, d.manaPool(p).red)
        assertTrue(d.isTapped(sceptre))

        assertNotNull(d.activate(p, sceptre, index = 0).error)
        assertEquals(1, d.manaPool(p).red)
    }

    @Test
    fun threeLandsWithTheSameNameEnableThreeManaOfOneChosenColor() {
        val d = customScenario(SceptreOfEternalGlory, "Plains")
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
        repeat(3) { d.putLandOnBattlefield(p, "Plains") }

        assertNull(d.activate(p, sceptre, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))

        assertEquals(3, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(sceptre))
    }

    @Test
    fun threeLandsWithOnlyTwoSharingANameDoNotEnableThreeManaAbility() {
        val d = customScenario(SceptreOfEternalGlory, "Plains", "Island")
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(p, "Island")

        assertNotNull(d.activate(p, sceptre, index = 1).error)

        assertFalse(d.isTapped(sceptre))
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
    }
}
