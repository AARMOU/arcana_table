package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AngelicAberration
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AngelicAberrationIndependentCertificationTest {
    private val name = "Angelic Aberration"

    @Test
    fun castingForFiveGenericAndWhiteEntersAsColorlessFourFourEldraziAngelWithFlyingAndVigilance() {
        val d = customScenario(AngelicAberration)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertEquals(4, d.projected.getPower(card))
        assertEquals(4, d.projected.getToughness(card))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "ELDRAZI"))
        assertTrue(d.projected.hasSubtype(card, "ANGEL"))
        assertTrue(d.projected.getColors(card).isEmpty())
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(card, Keyword.VIGILANCE))
    }

    @Test
    fun sixColorlessManaCannotReplaceTheRequiredWhiteMana() {
        val d = customScenario(AngelicAberration)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 6)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun fourGenericAndWhiteManaAreNotEnoughToCast() {
        val d = customScenario(AngelicAberration)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.WHITE)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
    }

    @Test
    fun flyingPreventsNonflyingBlockersAndVigilanceKeepsItUntappedWhenAttacking() {
        val d = customScenario(AngelicAberration, "Grizzly Bears")
        val angel = d.putCreatureOnBattlefield(d.player1, name)
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(angel)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(angel), d.player2).error)
        assertFalse(d.isTapped(angel))
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(angel))).error)
        assertTrue(d.permanentsNamed(d.player1, name).contains(angel))
    }

    @Test
    fun enterTriggerSacrificesQualifyingCreaturesAndCreatesOneMatchingTokenPerCreature() {
        val d = customScenario(AngelicAberration, "Ornithopter", "Savannah Lions", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val angel = d.putCreatureOnBattlefield(p, name)
        val lowPower = d.putCreatureOnBattlefield(p, "Ornithopter")
        val lowToughness = d.putCreatureOnBattlefield(p, "Savannah Lions")
        val neitherLow = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingEligible = d.putCreatureOnBattlefield(opponent, "Ornithopter")

        assertEquals("the enter trigger waits on the stack", 1, d.stackSize)
        assertTrue(d.permanentsNamed(p, "Ornithopter").contains(lowPower))
        assertTrue(d.permanentsNamed(p, "Savannah Lions").contains(lowToughness))
        d.resolveAll(Choices(pick = listOf("Ornithopter", "Savannah Lions")))

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).containsAll(listOf("Ornithopter", "Savannah Lions")))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").contains(neitherLow))
        assertTrue(d.permanentsNamed(opponent, "Ornithopter").contains(opposingEligible))
        val tokens = d.permanentsNamed(p, "Eldrazi Angel")
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertEquals(4, d.projected.getPower(token))
            assertEquals(4, d.projected.getToughness(token))
            assertTrue(d.projected.hasType(token, "CREATURE"))
            assertTrue(d.projected.hasSubtype(token, "ELDRAZI"))
            assertTrue(d.projected.hasSubtype(token, "ANGEL"))
            assertTrue(d.projected.getColors(token).isEmpty())
            assertTrue(d.projected.hasKeyword(token, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
        }
        assertTrue(d.permanentsNamed(p, name).contains(angel))
    }

    @Test
    fun choosingZeroCreaturesLeavesEligibleCreaturesAndCreatesNoTokens() {
        val d = customScenario(AngelicAberration, "Ornithopter")
        val p = d.player1
        val angel = d.putCreatureOnBattlefield(p, name)
        val eligible = d.putCreatureOnBattlefield(p, "Ornithopter")

        d.resolveAll(Choices(pick = emptyList()))

        assertTrue(d.permanentsNamed(p, name).contains(angel))
        assertTrue(d.permanentsNamed(p, "Ornithopter").contains(eligible))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Ornithopter"))
        assertTrue(d.permanentsNamed(p, "Eldrazi Angel").isEmpty())
    }
}
