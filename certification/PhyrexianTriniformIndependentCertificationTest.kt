package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PhyrexianTriniform
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PhyrexianTriniformIndependentCertificationTest {
    private val name = "Phyrexian Triniform"

    @Test
    fun castingRequiresNineManaAndEntersAsAColorlessNineNineArtifactCreature() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 8)
        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertEquals(9, d.projected.getPower(card))
        assertEquals(9, d.projected.getToughness(card))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "PHYREXIAN"))
        assertTrue(d.projected.hasSubtype(card, "GOLEM"))
        assertTrue(d.projected.getColors(card).isEmpty())
    }

    @Test
    fun dyingCreatesExactlyThreeColorlessThreeThreePhyrexianGolemArtifactCreatures() {
        val d = customScenario(PhyrexianTriniform, "Murder")
        val p = d.player1
        val triniform = d.putCreatureOnBattlefield(p, name)
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(triniform))).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        val golems = d.permanentsNamed(p, "Phyrexian Golem")
        assertEquals(3, golems.size)
        golems.forEach { golem ->
            assertEquals(3, d.projected.getPower(golem))
            assertEquals(3, d.projected.getToughness(golem))
            assertTrue(d.projected.hasType(golem, "ARTIFACT"))
            assertTrue(d.projected.hasType(golem, "CREATURE"))
            assertTrue(d.projected.hasSubtype(golem, "PHYREXIAN"))
            assertTrue(d.projected.hasSubtype(golem, "GOLEM"))
            assertTrue(d.projected.getColors(golem).isEmpty())
        }
    }

    @Test
    fun exilingPhyrexianTriniformDoesNotTriggerItsDiesAbility() {
        val d = customScenario(PhyrexianTriniform, "Path to Exile")
        val p = d.player1
        val triniform = d.putCreatureOnBattlefield(p, name)
        val path = d.putCardInHand(p, "Path to Exile")
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, path, listOf(triniform)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertTrue(d.permanentsNamed(p, "Phyrexian Golem").isEmpty())
    }

    @Test
    fun encoreRequiresTwelveManaAndDoesNotExileTheCardWhenUnpaid() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val card = d.putCardInGraveyard(p, name)
        d.giveColorlessMana(p, 11)

        assertNotNull(d.activate(p, card).error)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains(name))
        assertEquals(11, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun encoreCanOnlyBeActivatedFromTheGraveyard() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 12)

        assertNotNull(d.activate(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertEquals(12, d.manaPool(p).colorless)
    }

    @Test
    fun encoreCanOnlyBeActivatedAtSorceryTiming() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val card = d.putCardInGraveyard(p, name)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(p, 12)

        assertNotNull(d.activate(p, card).error)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertEquals(12, d.manaPool(p).colorless)
    }

    @Test
    fun encoreExilesAsCostCreatesHastyAttackingCopyAndSacrificesItAtNextEndStep() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val opponent = d.player2
        val card = d.putCardInGraveyard(p, name)
        d.giveColorlessMana(p, 12)

        assertNull(d.activate(p, card).error)
        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        val copies = d.permanentsNamed(p, name)
        assertEquals(1, copies.size)
        val copy = copies.single()
        assertEquals(9, d.projected.getPower(copy))
        assertEquals(9, d.projected.getToughness(copy))
        assertTrue(d.projected.hasKeyword(copy, Keyword.HASTE))
        assertTrue(d.projected.hasType(copy, "ARTIFACT"))
        assertTrue(d.projected.hasType(copy, "CREATURE"))

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(11, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(p, name).contains(copy))

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(3, d.permanentsNamed(p, "Phyrexian Golem").size)
    }
}
