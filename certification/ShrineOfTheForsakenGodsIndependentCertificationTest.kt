package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShrineOfTheForsakenGods
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ShrineOfTheForsakenGodsIndependentCertificationTest {
    private fun GameTestDriver.playShrine(player: EntityId): EntityId {
        val shrine = putCardInHand(player, "Shrine of the Forsaken Gods")
        assertNull(playLand(player, shrine).error)
        resolveAll()
        return shrine
    }

    @Test
    fun tapAbilityAddsOneColorlessManaAndCannotBeActivatedWhileTapped() {
        val d = customScenario(ShrineOfTheForsakenGods)
        val p = d.player1
        val shrine = d.playShrine(p)

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, shrine, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(shrine))

        assertNotNull(d.activate(p, shrine, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun sevenLandsEnableTwoColorlessManaForAColorlessSpell() {
        val d = customScenario(ShrineOfTheForsakenGods, "Sol Ring")
        val p = d.player1
        repeat(6) { d.tapPermanent(d.putLandOnBattlefield(p, "Forest")) }
        val shrine = d.playShrine(p)

        assertNull(d.activate(p, shrine, index = 1).error)
        d.resolveAll()
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertTrue(d.isTapped(shrine))
        assertNotNull(d.activate(p, shrine, index = 0).error)

        val solRing = d.putCardInHand(p, "Sol Ring")
        // Pay from the restricted pool, never from the six fixture lands.
        assertNull(d.submit(CastSpell(p, solRing, paymentStrategy = PaymentStrategy.FromPool)).error)
        assertEquals(1, d.manaPool(p).restrictedMana.size)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())
    }

    @Test
    fun sixLandsDoNotEnableTheTwoColorlessManaAbility() {
        val d = customScenario(ShrineOfTheForsakenGods)
        val p = d.player1
        repeat(5) { d.putLandOnBattlefield(p, "Forest") }
        val shrine = d.playShrine(p)

        assertNotNull(d.activate(p, shrine, index = 1).error)

        assertFalse(d.isTapped(shrine))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun manaFromTheSevenLandAbilityCannotCastAColoredSpell() {
        val d = customScenario(ShrineOfTheForsakenGods, "Grizzly Bears")
        val p = d.player1
        repeat(6) { d.tapPermanent(d.putLandOnBattlefield(p, "Forest")) }
        val shrine = d.playShrine(p)
        d.giveMana(p, Color.GREEN)

        assertNull(d.activate(p, shrine, index = 1).error)
        d.resolveAll()
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)

        val bears = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull(d.castSpell(p, bears).error)

        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).restrictedMana.size)
        assertEquals(1, d.manaPool(p).green)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
    }
}
