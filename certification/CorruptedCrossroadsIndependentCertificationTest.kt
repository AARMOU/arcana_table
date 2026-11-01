package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CorruptedCrossroads
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
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
            assertEquals(listOf(color), pool.restrictedMana.map { it.color })
            assertEquals(0, pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless)
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
        assertTrue(pool.restrictedMana.isEmpty())
    }

    @Test
    fun coloredManaCanBeSpentToCastADevoidSpell() {
        val d = customScenario(CorruptedCrossroads, "Ornithopter")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val crossroads = d.putLandOnBattlefield(p, "Corrupted Crossroads")
        val wail = d.putCardInHand(p, "Warping Wail")

        assertNull(d.activate(p, crossroads, index = 1).error)
        d.resolveAll(Choices(color = Color.RED))
        d.giveColorlessMana(p, 1)

        assertNull(d.submit(CastSpell(
            playerId = p,
            cardId = wail,
            targets = listOf(ChosenTarget.Permanent(target)),
            paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = listOf(0),
            modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(target))),
        )).error)
        d.resolveAll()

        assertFalse(d.zoneNames(d.player2, Zone.BATTLEFIELD).contains("Ornithopter"))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Ornithopter"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Warping Wail"))
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
        assertEquals(listOf(Color.GREEN), d.manaPool(p).restrictedMana.map { it.color })
        assertEquals(19, d.getLifeTotal(p))
        assertTrue(d.isTapped(crossroads))
    }
}
