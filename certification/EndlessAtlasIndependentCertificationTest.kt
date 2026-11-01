package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EndlessAtlas
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class EndlessAtlasIndependentCertificationTest {
    @Test
    fun twoManaAndTappingDrawOneCardWithThreeLandsOfTheSameName() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(3) { d.putLandOnBattlefield(p, "Plains") }
        d.giveColorlessMana(p, 2)
        val handBefore = d.getHandSize(p)

        assertNull(d.activate(p, atlas).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(atlas))
        assertEquals(handBefore, d.getHandSize(p))
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHandSize(p))
    }

    @Test
    fun oneManaIsNotEnoughToActivate() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(3) { d.putLandOnBattlefield(p, "Plains") }
        d.giveColorlessMana(p, 1)
        val handBefore = d.getHandSize(p)

        assertNotNull(d.activate(p, atlas).error)

        assertFalse(d.isTapped(atlas))
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
    }

    @Test
    fun threeLandsWithOnlyTwoSharingANameDoNotEnableTheAbility() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(p, "Forest")
        d.giveColorlessMana(p, 2)
        val handBefore = d.getHandSize(p)

        assertNotNull(d.activate(p, atlas).error)

        assertFalse(d.isTapped(atlas))
        assertEquals(2, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
    }

    @Test
    fun landsControlledByAnOpponentDoNotEnableTheAbility() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(p, "Plains")
        d.putLandOnBattlefield(d.player2, "Plains")
        d.giveColorlessMana(p, 2)
        val handBefore = d.getHandSize(p)

        assertNotNull(d.activate(p, atlas).error)

        assertFalse(d.isTapped(atlas))
        assertEquals(2, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
    }

    @Test
    fun theAbilityCanBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(3) { d.putLandOnBattlefield(p, "Plains") }
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveColorlessMana(p, 2)
        val handBefore = d.getHandSize(p)

        assertNull(d.activate(p, atlas).error)
        assertTrue(d.isTapped(atlas))
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHandSize(p))
    }

    @Test
    fun aTappedAtlasCannotBeActivatedAgain() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(3) { d.putLandOnBattlefield(p, "Plains") }
        d.giveColorlessMana(p, 4)
        val handBefore = d.getHandSize(p)

        assertNull(d.activate(p, atlas).error)
        d.resolveAll()
        assertEquals(handBefore + 1, d.getHandSize(p))
        assertTrue(d.isTapped(atlas))
        assertEquals(2, d.manaPool(p).colorless)

        assertNotNull(d.activate(p, atlas).error)

        assertEquals(handBefore + 1, d.getHandSize(p))
        assertEquals(2, d.manaPool(p).colorless)
        assertTrue(d.isTapped(atlas))
    }
}
