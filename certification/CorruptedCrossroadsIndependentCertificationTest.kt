package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CorruptedCrossroads
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CorruptedCrossroadsIndependentCertificationTest {
    @Test
    fun tapAbilityAddsOneColorlessAndCannotBeActivatedAgainWhileTapped() {
        val d = customScenario(CorruptedCrossroads)
        val p = d.player1
        val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
        val initialLife = d.getLifeTotal(p)

        assertNull(d.activate(p, crossroads, index = 0).error)
        d.resolveAll()

        val pool = d.manaPool(p)
        assertEquals(1, pool.colorless)
        assertEquals(0, pool.white + pool.blue + pool.black + pool.red + pool.green)
        assertEquals(initialLife, d.getLifeTotal(p))
        assertTrue(d.isTapped(crossroads))
        assertNotNull(d.activate(p, crossroads, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun lifePaymentAbilityCanAddManaOfEveryColorAndCannotBeReusedWhileTapped() {
        val colors = listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)

        colors.forEach { color ->
            val d = customScenario(CorruptedCrossroads)
            val p = d.player1
            val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
            val initialLife = if (color == Color.WHITE) 1 else 20
            d.setLifeTotal(p, initialLife)

            assertNull(d.activate(p, crossroads, index = 1).error)
            assertEquals(initialLife - 1, d.getLifeTotal(p))
            assertTrue(d.isTapped(crossroads))
            d.resolveAll(Choices(color = color))

            val pool = d.manaPool(p)
            assertEquals(if (color == Color.WHITE) 1 else 0, pool.white)
            assertEquals(if (color == Color.BLUE) 1 else 0, pool.blue)
            assertEquals(if (color == Color.BLACK) 1 else 0, pool.black)
            assertEquals(if (color == Color.RED) 1 else 0, pool.red)
            assertEquals(if (color == Color.GREEN) 1 else 0, pool.green)
            assertEquals(0, pool.colorless)
            assertNotNull(d.activate(p, crossroads, index = 1).error)
            assertEquals(initialLife - 1, d.getLifeTotal(p))
        }
    }

    @Test
    fun lifePaymentAbilityCannotBeActivatedWithoutLifeToPay() {
        val d = customScenario(CorruptedCrossroads)
        val p = d.player1
        val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
        d.setLifeTotal(p, 0)

        assertNotNull(d.activate(p, crossroads, index = 1).error)

        assertEquals(0, d.getLifeTotal(p))
        assertFalse(d.isTapped(crossroads))
        val pool = d.manaPool(p)
        assertEquals(0, pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless)
    }

    @Test
    fun coloredManaCanBeSpentToCastADevoidSpell() {
        val d = customScenario(CorruptedCrossroads, "Eldrazi Mimic")
        val p = d.player1
        val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
        val mimic = d.putCardInHand(p, "Eldrazi Mimic")

        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.RED))
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, mimic).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Eldrazi Mimic").contains(mimic))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Eldrazi Mimic"))
        assertEquals(19, d.getLifeTotal(p))
    }

    @Test
    fun coloredManaCannotBeSpentToCastASpellWithoutDevoid() {
        val d = customScenario(CorruptedCrossroads, "Sol Ring")
        val p = d.player1
        val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
        val solRing = d.putCardInHand(p, "Sol Ring")

        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))

        assertNotNull(d.castSpell(p, solRing).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
        assertEquals(1, d.manaPool(p).green)
        assertEquals(19, d.getLifeTotal(p))
        assertTrue(d.isTapped(crossroads))
    }
}
