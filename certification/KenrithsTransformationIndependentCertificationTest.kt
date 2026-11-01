package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KenrithsTransformation
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class KenrithsTransformationIndependentCertificationTest {
    @Test
    fun enteringAuraDrawsOnlyWhenItResolvesAndTransformsTheEnchantedCreature() {
        val d = customScenario(KenrithsTransformation, "Serra Angel", "Plains")
        val p = d.player1
        val angel = d.putCreatureOnBattlefield(p, "Serra Angel")
        assertTrue(d.projected.hasColor(angel, Color.WHITE))
        assertTrue(d.projected.hasKeyword(angel, Keyword.FLYING))

        val aura = d.putCardInHand(p, "Kenrith's Transformation")
        val plains = d.putCardOnTopOfLibrary(p, "Plains")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, aura, listOf(angel)).error)
        assertEquals(handBeforeCast - 1, d.getHandSize(p))
        assertFalse(d.getHand(p).contains(plains))

        d.resolveAll()

        assertEquals("Casting spends one card and the Aura trigger draws one", handBeforeCast, d.getHandSize(p))
        assertTrue(d.getHand(p).contains(plains))
        assertEquals(3, d.projected.getBasePower(angel))
        assertEquals(3, d.projected.getBaseToughness(angel))
        assertTrue(d.projected.hasColor(angel, Color.GREEN))
        assertFalse(d.projected.hasColor(angel, Color.WHITE))
        assertEquals(setOf("CREATURE"), d.projected.getTypes(angel).intersect(CardType.entries.map { it.name }.toSet()))
        assertEquals(setOf("ELK"), d.projected.getSubtypes(angel).map { it.uppercase() }.toSet())
        assertTrue(d.projected.hasLostAllAbilities(angel))
        assertFalse(d.projected.hasKeyword(angel, Keyword.FLYING))
    }

    @Test
    fun anOpponentArtifactCreatureLosesItsOtherTypesAndCreatureTypes() {
        val d = customScenario(KenrithsTransformation, "Steel Overseer")
        val overseer = d.putCreatureOnBattlefield(d.player2, "Steel Overseer")
        assertTrue(d.projected.hasType(overseer, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(overseer, "CONSTRUCT"))
        assertFalse(d.projected.hasColor(overseer, Color.GREEN))

        val aura = d.putCardInHand(d.player1, "Kenrith's Transformation")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, aura, listOf(overseer)).error)
        d.resolveAll()

        assertTrue(d.projected.hasType(overseer, "CREATURE"))
        assertFalse(d.projected.hasType(overseer, "ARTIFACT"))
        assertEquals(setOf("CREATURE"), d.projected.getTypes(overseer).intersect(CardType.entries.map { it.name }.toSet()))
        assertEquals(setOf("ELK"), d.projected.getSubtypes(overseer).map { it.uppercase() }.toSet())
        assertTrue(d.projected.hasColor(overseer, Color.GREEN))
        assertEquals(3, d.projected.getBasePower(overseer))
        assertEquals(3, d.projected.getBaseToughness(overseer))
        assertTrue(d.projected.hasLostAllAbilities(overseer))
    }

    @Test
    fun enchantCreatureCannotEnchantANoncreatureOrEnterToDraw() {
        val d = customScenario(KenrithsTransformation, "Plains", "Forest")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Plains")
        val aura = d.putCardInHand(p, "Kenrith's Transformation")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, aura, listOf(land)).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Kenrith's Transformation"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Forest"))
        assertTrue(d.permanentsNamed(p, "Kenrith's Transformation").isEmpty())
        assertTrue(d.permanentsNamed(p, "Plains").isNotEmpty())
    }

    @Test
    fun twoGenericManaCannotPayTheGreenSymbol() {
        val d = customScenario(KenrithsTransformation, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val aura = d.putCardInHand(p, "Kenrith's Transformation")
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, aura, listOf(bear)).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Kenrith's Transformation"))
        assertFalse(d.projected.hasLostAllAbilities(bear))
    }

    @Test
    fun oneGreenManaCannotPayTheGenericSymbol() {
        val d = customScenario(KenrithsTransformation, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val aura = d.putCardInHand(p, "Kenrith's Transformation")
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, aura, listOf(bear)).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Kenrith's Transformation"))
        assertFalse(d.projected.hasLostAllAbilities(bear))
    }
}
