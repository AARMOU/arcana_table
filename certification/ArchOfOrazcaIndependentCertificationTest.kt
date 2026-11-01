package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArchOfOrazca
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ArchOfOrazcaIndependentCertificationTest {
    private fun GameTestDriver.playArch(player: EntityId): EntityId {
        val arch = putCardInHand(player, "Arch of Orazca")
        assertNull(playLand(player, arch).error)
        resolveAll()
        return arch
    }

    @Test
    fun tapAbilityAddsOneColorlessManaAndCannotBeActivatedWhileTapped() {
        val d = customScenario(ArchOfOrazca)
        val p = d.player1
        val arch = d.playArch(p)

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, arch, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(arch))

        assertNotNull(d.activate(p, arch, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun ascendGrantsTheBlessingAtTenPermanentsAndEnablesTheDrawAbility() {
        val d = customScenario(ArchOfOrazca, "Grizzly Bears")
        val p = d.player1
        repeat(9) { d.putPermanentOnBattlefield(p, "Sol Ring") }
        val arch = d.playArch(p)
        val handBefore = d.getHandSize(p)
        d.giveColorlessMana(p, 5)

        assertNull(d.activate(p, arch, index = 1).error)
        assertTrue(d.isTapped(arch))
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHandSize(p))
        d.giveColorlessMana(p, 5)
        assertNotNull(d.activate(p, arch, index = 1).error)
        assertEquals(handBefore + 1, d.getHandSize(p))
    }

    @Test
    fun drawAbilityCannotBeActivatedWithoutTheCitysBlessing() {
        val d = customScenario(ArchOfOrazca, "Grizzly Bears")
        val p = d.player1
        val arch = d.playArch(p)
        d.giveColorlessMana(p, 5)
        val handBefore = d.getHandSize(p)

        assertNotNull(d.activate(p, arch, index = 1).error)

        assertFalse(d.isTapped(arch))
        assertEquals(5, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
    }

    @Test
    fun drawAbilityRequiresAllFiveGenericMana() {
        val d = customScenario(ArchOfOrazca, "Grizzly Bears")
        val p = d.player1
        repeat(9) { d.putPermanentOnBattlefield(p, "Sol Ring") }
        val arch = d.playArch(p)
        d.giveColorlessMana(p, 4)
        val handBefore = d.getHandSize(p)

        assertNotNull(d.activate(p, arch, index = 1).error)

        assertFalse(d.isTapped(arch))
        assertEquals(4, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
    }

    @Test
    fun theCitysBlessingRemainsAfterFallingBelowTenPermanents() {
        val d = customScenario(ArchOfOrazca, "Grizzly Bears", "Murder")
        val p = d.player1
        repeat(8) { d.putPermanentOnBattlefield(p, "Sol Ring") }
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val arch = d.playArch(p)

        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(bear))).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())

        val handBefore = d.getHandSize(p)
        d.giveColorlessMana(p, 5)
        assertNull(d.activate(p, arch, index = 1).error)
        d.resolveAll()

        assertEquals(handBefore + 1, d.getHandSize(p))
    }
}
