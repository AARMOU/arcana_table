package com.arcanatable.customcards

// Independent trusted-main tests for Whisperer of the Wilds (queue #464).
// Authored from the trusted feeder's Scryfall Oracle text, not Copilot's tests or implementation:
// "{T}: Add {G}." and "Ferocious — {T}: Add {G}{G}. Activate only if you control
// a creature with power 4 or greater."
import com.arcanatable.customcards.cards.WhispererOfTheWilds
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class WhispererOfTheWildsIndependentCertificationTest {
    private fun GameTestDriver.readyWhisperer(player: EntityId): EntityId =
        putCreatureOnBattlefield(player, "Whisperer of the Wilds").also {
            removeSummoningSickness(it)
        }

    @Test
    fun ordinaryTapAbilityAddsExactlyOneGreenWithoutFerocious() {
        val d = customScenario(WhispererOfTheWilds)
        val p = d.player1
        val whisperer = d.readyWhisperer(p)
        assertEquals(0, d.projected.getPower(whisperer))
        assertEquals(2, d.projected.getToughness(whisperer))
        assertEquals(0, d.manaPool(p).green)

        assertNull(d.activate(p, whisperer, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).green)
        assertTrue(d.isTapped(whisperer))
    }

    @Test
    fun ferociousAddsExactlyTwoGreenWithFourPowerCreature() {
        val d = customScenario(WhispererOfTheWilds, "Serra Angel")
        val p = d.player1
        val angel = d.putCreatureOnBattlefield(p, "Serra Angel")
        assertEquals(4, d.projected.getPower(angel))
        val whisperer = d.readyWhisperer(p)

        assertNull(d.activate(p, whisperer, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).green)
        assertTrue(d.isTapped(whisperer))
    }

    @Test
    fun fourToughnessWithOnlyTwoPowerDoesNotEnableFerocious() {
        val d = customScenario(WhispererOfTheWilds, "Giant Spider")
        val p = d.player1
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")
        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
        val whisperer = d.readyWhisperer(p)

        assertNotNull(d.activate(p, whisperer, index = 1).error)
        assertEquals(0, d.manaPool(p).green)
        assertFalse(d.isTapped(whisperer))
        assertNull(d.activate(p, whisperer, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).green)
    }

    @Test
    fun opponentsHighPowerCreatureDoesNotCountForFerocious() {
        val d = customScenario(WhispererOfTheWilds, "Colossal Dreadmaw")
        val p = d.player1
        val opponent = d.player2
        val dreadmaw = d.putCreatureOnBattlefield(opponent, "Colossal Dreadmaw")
        assertTrue(d.projected.getPower(dreadmaw) >= 4)
        val whisperer = d.readyWhisperer(p)

        assertNotNull(d.activate(p, whisperer, index = 1).error)
        assertFalse(d.isTapped(whisperer))
        assertEquals(0, d.manaPool(p).green)
    }

    @Test
    fun tappedWhispererCannotMakeAdditionalFerociousMana() {
        val d = customScenario(WhispererOfTheWilds, "Serra Angel")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Serra Angel")
        val whisperer = d.readyWhisperer(p)

        assertNull(d.activate(p, whisperer, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).green)
        assertTrue(d.isTapped(whisperer))
        assertNotNull(d.activate(p, whisperer, index = 1).error)
        assertEquals(1, d.manaPool(p).green)
    }

    @Test
    fun newlyCastWhispererCannotTapUntilSummoningSicknessIsRemoved() {
        val d = customScenario(WhispererOfTheWilds)
        val p = d.player1
        val whisperer = d.putCardInHand(p, "Whisperer of the Wilds")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, whisperer).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(whisperer))
        assertFalse(d.isTapped(whisperer))

        assertNotNull(d.activate(p, whisperer, index = 0).error)
        assertFalse(d.isTapped(whisperer))
        d.removeSummoningSickness(whisperer)
        assertNull(d.activate(p, whisperer, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).green)
    }
}
