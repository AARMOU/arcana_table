package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziTemple
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class EldraziTempleIndependentCertificationTest {
    private fun GameTestDriver.playTemple(player: EntityId): EntityId {
        val temple = putCardInHand(player, "Eldrazi Temple")
        assertNull(playLand(player, temple).error)
        resolveAll()
        return temple
    }

    @Test
    fun firstAbilityAddsOneColorlessManaAndTappingPreventsEitherAbility() {
        val d = customScenario(EldraziTemple)
        val p = d.player1
        val temple = d.playTemple(p)

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, temple, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(temple))

        assertNotNull(d.activate(p, temple, index = 0).error)
        assertNotNull(d.activate(p, temple, index = 1).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun secondAbilityAddsTwoColorlessMana() {
        val d = customScenario(EldraziTemple)
        val p = d.player1
        val temple = d.playTemple(p)

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, temple, index = 1).error)
        assertEquals(2, d.manaPool(p).colorless)
        assertTrue(d.isTapped(temple))
        assertNotNull(d.activate(p, temple, index = 0).error)
    }

    @Test
    fun firstAbilityManaCanCastANonEldraziColorlessSpell() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val temple = d.playTemple(p)
        val ring = d.putCardInHand(p, "Sol Ring")

        assertNull(d.activate(p, temple, index = 0).error)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).none { it == "Sol Ring" })
    }

    @Test
    fun secondAbilityManaCanCastAColorlessEldraziSpell() {
        val d = customScenario(EldraziTemple, "Eldrazi Mimic")
        val p = d.player1
        val temple = d.playTemple(p)
        val mimic = d.putCardInHand(p, "Eldrazi Mimic")

        assertNull(d.activate(p, temple, index = 1).error)
        assertNull(d.castSpell(p, mimic).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Eldrazi Mimic").isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).none { it == "Eldrazi Mimic" })
    }

    @Test
    fun secondAbilityManaCannotCastANonEldraziSpell() {
        val d = customScenario(EldraziTemple, "Sol Ring")
        val p = d.player1
        val temple = d.playTemple(p)
        val ring = d.putCardInHand(p, "Sol Ring")

        assertNull(d.activate(p, temple, index = 1).error)
        assertNotNull(d.castSpell(p, ring).error)

        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(2, d.manaPool(p).colorless)
    }

    @Test
    fun secondAbilityManaCannotCastAColoredEldraziSpell() {
        val d = customScenario(EldraziTemple, "Eldrazi Displacer")
        val p = d.player1
        val temple = d.playTemple(p)
        val displacer = d.putCardInHand(p, "Eldrazi Displacer")
        d.giveMana(p, Color.WHITE, 1)

        assertNull(d.activate(p, temple, index = 1).error)
        assertNotNull(d.castSpell(p, displacer).error)

        assertTrue(d.permanentsNamed(p, "Eldrazi Displacer").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Eldrazi Displacer"))
        assertEquals(2, d.manaPool(p).colorless)
        assertEquals(1, d.manaPool(p).white)
    }

    @Test
    fun secondAbilityManaCanPayForAnAbilityOfAColorlessEldrazi() {
        val d = customScenario(EldraziTemple, "Endbringer")
        val p = d.player1
        val temple = d.playTemple(p)
        val endbringer = d.putPermanentOnBattlefield(p, "Endbringer")
        val handBefore = d.getHandSize(p)

        assertNull(d.activate(p, temple, index = 1).error)
        assertNull(d.activate(p, endbringer, index = 2).error)
        d.resolveAll()

        assertTrue(d.isTapped(endbringer))
        assertEquals(handBefore + 1, d.getHandSize(p))
    }

    @Test
    fun secondAbilityManaCannotPayForAnAbilityOfAColoredEldrazi() {
        val d = customScenario(EldraziTemple, "Eldrazi Displacer", "Grizzly Bears")
        val p = d.player1
        val temple = d.playTemple(p)
        val displacer = d.putCreatureOnBattlefield(p, "Eldrazi Displacer")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.giveMana(p, Color.WHITE, 1)

        assertNull(d.activate(p, temple, index = 1).error)
        assertNotNull(d.activate(p, displacer, index = 0).error)

        assertFalse(d.isTapped(displacer))
        assertEquals(2, d.manaPool(p).colorless)
        assertEquals(1, d.manaPool(p).white)
    }
}
