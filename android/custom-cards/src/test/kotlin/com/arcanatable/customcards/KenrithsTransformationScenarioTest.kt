package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KenrithsTransformation
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class KenrithsTransformationScenarioTest {
    private fun cast(d: GameTestDriver, owner: EntityId, target: EntityId): Boolean {
        val aura = d.putCardInHand(owner, "Kenrith's Transformation")
        d.giveMana(owner, Color.GREEN)
        d.giveColorlessMana(owner, 1)
        val result = d.castSpell(owner, aura, listOf(target))
        if (result.error != null) return false
        d.resolveAll()
        return true
    }

    @Test
    fun enchantedCreatureBecomesGreenElkThreeThreeWithoutAbilitiesAndControllerDraws() {
        val d = customScenario(KenrithsTransformation, "Serra Angel", "Plains")
        val p = d.player1
        val angel = d.putCreatureOnBattlefield(p, "Serra Angel")
        assertTrue(d.projected.hasKeyword(angel, Keyword.FLYING))
        assertTrue(d.projected.hasColor(angel, Color.WHITE))
        val handBefore = d.getHandSize(p)

        assertTrue(cast(d, p, angel))

        assertEquals("Casting the Aura leaves hand; its enters trigger draws exactly one card", handBefore + 1, d.getHandSize(p))
        assertNotNull(d.findPermanent(p, "Kenrith's Transformation"))
        assertEquals(3, d.projected.getPower(angel))
        assertEquals(3, d.projected.getToughness(angel))
        assertFalse(d.projected.hasKeyword(angel, Keyword.FLYING))
        assertTrue(d.projected.hasLostAllAbilities(angel))
        assertEquals(setOf("GREEN"), d.projected.getColors(angel).map { it.uppercase() }.toSet())
        assertEquals(setOf("Elk"), d.projected.getSubtypes(angel))
        assertTrue(d.projected.isCreature(angel))
    }

    @Test
    fun enchantingALandIsRejectedAndNothingIsDrawn() {
        val d = customScenario(KenrithsTransformation, "Plains")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Plains")
        val handBefore = d.getHandSize(p)

        assertFalse(cast(d, p, land))
        assertNull(d.findPermanent(p, "Kenrith's Transformation"))
        assertEquals("A rejected Aura stays in hand and nothing is drawn", handBefore + 1, d.getHandSize(p))
    }

    @Test
    fun opponentCreatureIsTransformedToo() {
        val d = customScenario(KenrithsTransformation, "Llanowar Elves")
        val p = d.player1
        val elves = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        assertEquals(1, d.projected.getPower(elves))

        assertTrue(cast(d, p, elves))
        assertEquals(3, d.projected.getPower(elves))
        assertTrue(d.projected.hasLostAllAbilities(elves))
        assertEquals(setOf("Elk"), d.projected.getSubtypes(elves))
    }
}
