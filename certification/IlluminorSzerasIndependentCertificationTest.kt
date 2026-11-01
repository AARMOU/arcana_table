package com.arcanatable.customcards

import com.arcanatable.customcards.cards.IlluminorSzeras
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class IlluminorSzerasIndependentCertificationTest {
    @Test
    fun sacrificingAnotherCreatureAddsBlackEqualToItsManaValue() {
        val d = customScenario(IlluminorSzeras, "Serra Angel")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.removeSummoningSickness(szeras)
        d.putCreatureOnBattlefield(p, "Serra Angel")

        assertNull(d.activate(p, szeras).error)
        d.resolveAll(Choices(pick = listOf("Serra Angel")))

        assertEquals(5, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(szeras))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Serra Angel"))
    }

    @Test
    fun outputUsesManaValueRatherThanPower() {
        val d = customScenario(IlluminorSzeras, "Giant Spider")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.removeSummoningSickness(szeras)
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")
        assertEquals(2, d.projected.getPower(spider))

        assertNull(d.activate(p, szeras).error)
        d.resolveAll(Choices(pick = listOf("Giant Spider")))

        assertEquals(4, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Giant Spider"))
    }

    @Test
    fun cannotSacrificeSzerasItselfWhenNoOtherCreatureIsAvailable() {
        val d = customScenario(IlluminorSzeras)
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.removeSummoningSickness(szeras)

        assertNotNull(d.activate(p, szeras).error)

        assertTrue(d.getPermanents(p).contains(szeras))
        assertFalse(d.isTapped(szeras))
        assertEquals(0, d.manaPool(p).black)
    }

    @Test
    fun aNoncreaturePermanentCannotPayTheSacrificeCost() {
        val d = customScenario(IlluminorSzeras, "Sol Ring")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")
        d.removeSummoningSickness(szeras)

        assertNotNull(d.activate(p, szeras).error)

        assertTrue(d.getPermanents(p).contains(szeras))
        assertTrue(d.getPermanents(p).contains(ring))
        assertFalse(d.isTapped(szeras))
        assertEquals(0, d.manaPool(p).black)
    }

    @Test
    fun anOpponentCreatureCannotPayTheSacrificeCost() {
        val d = customScenario(IlluminorSzeras, "Giant Spider")
        val p = d.player1
        val opponent = d.player2
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        val spider = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        d.removeSummoningSickness(szeras)

        assertNotNull(d.activate(p, szeras).error)

        assertTrue(d.getPermanents(p).contains(szeras))
        assertTrue(d.getPermanents(opponent).contains(spider))
        assertFalse(d.isTapped(szeras))
        assertEquals(0, d.manaPool(p).black)
    }

    @Test
    fun summoningSickSzerasCannotTapButCanActivateAfterward() {
        val d = customScenario(IlluminorSzeras, "Grizzly Bears")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")

        assertNotNull(d.activate(p, szeras).error)
        assertFalse(d.isTapped(szeras))
        assertEquals(0, d.manaPool(p).black)

        d.removeSummoningSickness(szeras)
        assertNull(d.activate(p, szeras).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.isTapped(szeras))
        assertEquals(2, d.manaPool(p).black)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun tappedSzerasCannotActivateAgain() {
        val d = customScenario(IlluminorSzeras, "Grizzly Bears")
        val p = d.player1
        val szeras = d.putCreatureOnBattlefield(p, "Illuminor Szeras")
        d.removeSummoningSickness(szeras)
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")

        assertNull(d.activate(p, szeras).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        assertEquals(2, d.manaPool(p).black)
        assertTrue(d.isTapped(szeras))

        assertNotNull(d.activate(p, szeras).error)

        assertEquals(2, d.manaPool(p).black)
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
    }
}
