package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SceptreOfEternalGlory
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class SceptreOfEternalGloryScenarioTest {
    private val colors = listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)

    @Test
    fun firstAbilityAddsEachColorWithoutLandsAndPaysForSpell() {
        colors.forEach { color ->
            val d = customScenario(SceptreOfEternalGlory, "Sol Ring")
            val p = d.player1
            val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
            val spell = d.putCardInHand(p, "Sol Ring")

            assertNull(d.activate(p, sceptre, index = 0).error)
            assertTrue(d.isTapped(sceptre))
            assertTrue(d.state.stack.isEmpty())
            d.resolveAll(Choices(color = color))
            assertManaPool(d, p, color, 1)

            assertNotNull(d.activate(p, sceptre, index = 0).error)
            assertNull(d.castSpell(p, spell).error)
            assertManaPool(d, p, color, 0)
        }
    }

    @Test
    fun secondAbilityChoosesOneColorAddsThreeAndPaysForSpell() {
        colors.forEach { color ->
            val d = customScenario(SceptreOfEternalGlory, "Prophetic Prism")
            val p = d.player1
            val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
            val lands = List(3) { d.putPermanentOnBattlefield(p, "Plains") }
            lands.forEach(d::tapPermanent)
            val prism = d.putCardInHand(p, "Prophetic Prism")

            assertNull(d.activate(p, sceptre, index = 1).error)
            assertTrue(d.isTapped(sceptre))
            assertTrue(d.state.stack.isEmpty())
            d.resolveAll(Choices(color = color))
            assertManaPool(d, p, color, 3)

            assertNull(d.castSpell(p, prism).error)
            assertManaPool(d, p, color, 1)
            assertNotNull(d.activate(p, sceptre, index = 1).error)

            d.untapPermanent(sceptre)
            assertNull(d.activate(p, sceptre, index = 1).error)
            d.resolveAll(Choices(color = color))
            assertManaPool(d, p, color, 4)
        }
    }

    @Test
    fun secondAbilityRejectsTooFewOrDifferentlyNamedLands() {
        assertSecondAbilityUnavailable(emptyList())
        assertSecondAbilityUnavailable(listOf("Plains"))
        assertSecondAbilityUnavailable(listOf("Plains", "Plains"))
        assertSecondAbilityUnavailable(listOf("Plains", "Plains", "Island"))
        assertSecondAbilityUnavailable(listOf("Island", "Swamp", "Mountain"))
        assertSecondAbilityUnavailable(
            ownLands = listOf("Island", "Island"),
            opponentLands = listOf("Plains", "Plains", "Plains"),
        )
    }

    @Test
    fun threeSameNamedLandsEnableAbilityEvenWhenTapped() {
        val d = customScenario(SceptreOfEternalGlory)
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
        val lands = List(3) { d.putPermanentOnBattlefield(p, "Plains") }
        lands.forEach(d::tapPermanent)
        assertTrue(lands.all(d::isTapped))

        assertNull(d.activate(p, sceptre, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertManaPool(d, p, Color.GREEN, 3)
    }

    private fun assertSecondAbilityUnavailable(
        ownLands: List<String>,
        opponentLands: List<String> = emptyList(),
    ) {
        val d = customScenario(SceptreOfEternalGlory)
        val p = d.player1
        val sceptre = d.putPermanentOnBattlefield(p, "Sceptre of Eternal Glory")
        ownLands.forEach { d.putPermanentOnBattlefield(p, it) }
        opponentLands.forEach { d.putPermanentOnBattlefield(d.player2, it) }

        assertNotNull(d.activate(p, sceptre, index = 1).error)
        assertFalse(d.isTapped(sceptre))
        assertManaPool(d, p, Color.WHITE, 0)
    }

    private fun assertManaPool(
        d: com.arcanatable.customcards.support.GameTestDriver,
        player: com.wingedsheep.sdk.model.EntityId,
        color: Color,
        amount: Int,
    ) {
        val pool = d.manaPool(player)
        assertEquals(
            listOf(
                if (color == Color.WHITE) amount else 0,
                if (color == Color.BLUE) amount else 0,
                if (color == Color.BLACK) amount else 0,
                if (color == Color.RED) amount else 0,
                if (color == Color.GREEN) amount else 0,
                0,
            ),
            listOf(pool.white, pool.blue, pool.black, pool.red, pool.green, pool.colorless),
        )
    }
}
