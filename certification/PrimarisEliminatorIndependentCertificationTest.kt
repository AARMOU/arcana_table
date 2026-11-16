package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PrimarisEliminator
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class PrimarisEliminatorIndependentCertificationTest {
    private val name = "Primaris Eliminator"

    private fun GameTestDriver.chooseOption(option: String, chooser: EntityId) {
        var passes = 0
        while (state.pendingDecision !is ChooseOptionDecision) {
            assertTrue("the enters trigger must ask for a mode", passes++ < 10 && state.stack.isNotEmpty())
            bothPass()
        }

        val decision = state.pendingDecision as ChooseOptionDecision
        assertEquals("the entering creature's controller chooses the mode", chooser, decision.playerId)
        val index = decision.options.indexOfFirst { it.contains(option, ignoreCase = true) }
        assertTrue("$option is an available mode: ${decision.options}", index >= 0)
        assertNull(submitDecision(chooser, OptionChosenResponse(decision.id, index)).error)
    }

    private fun GameTestDriver.awaitTargetChoice(chooser: EntityId): ChooseTargetsDecision {
        var passes = 0
        while (state.pendingDecision !is ChooseTargetsDecision) {
            assertTrue("the chosen mode must ask for its target", passes++ < 10 && state.stack.isNotEmpty())
            bothPass()
        }
        return state.pendingDecision as ChooseTargetsDecision
    }

    private fun GameTestDriver.castPrimarisEliminator(player: EntityId) {
        val card = putCardInHand(player, name)
        giveColorlessMana(player, 4)
        giveMana(player, Color.BLACK)
        assertNull(castSpell(player, card).error)
    }

    @Test
    fun castingRequiresFourGenericAndBlackAndExecutionerRoundDestroysItsCreatureTarget() {
        val d = customScenario(PrimarisEliminator, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val eliminator = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull("four generic mana cannot pay the required black mana", d.castSpell(p, eliminator).error)
        assertTrue(d.getHand(p).contains(eliminator))
        assertEquals(4, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, eliminator).error)
        assertTrue("the ability triggers on entry, not while the creature is still a spell",
            d.permanentsNamed(p, name).isEmpty())

        d.chooseOption("Executioner Round", p)
        assertTrue(d.permanentsNamed(p, name).contains(eliminator))
        assertEquals(3, d.projected.getPower(eliminator))
        assertEquals(2, d.projected.getToughness(eliminator))

        val decision = d.awaitTargetChoice(p)
        val offered = decision.legalTargets.values.flatten().toSet()
        assertEquals((d.getCreatures(p) + d.getCreatures(opponent)).toSet(), offered)
        assertFalse(offered.contains(ring))
        assertFalse(offered.contains(p))
        assertFalse(offered.contains(opponent))
        assertNotNull("a noncreature permanent is not a legal target",
            d.submitTargetSelection(p, listOf(ring)).error)
        assertNotNull("a player is not a legal target",
            d.submitTargetSelection(p, listOf(opponent)).error)

        assertNull(d.submitTargetSelection(p, listOf(opposingBear)).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(opponent).contains(opposingBear))
        assertTrue(d.getPermanents(p).contains(ownBear))
        assertTrue(d.getPermanents(opponent).contains(ring))
    }

    @Test
    fun hyperfragRoundOnlyShrinksTheTargetPlayersCreaturesUntilTheNextTurn() {
        val d = customScenario(PrimarisEliminator, "Grizzly Bears", "Serra Angel", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val angel = d.putCreatureOnBattlefield(opponent, "Serra Angel")
        val ring = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        d.castPrimarisEliminator(p)
        d.chooseOption("Hyperfrag Round", p)

        val decision = d.awaitTargetChoice(p)
        assertEquals(setOf(p, opponent), decision.legalTargets.values.flatten().toSet())
        assertNotNull("Hyperfrag Round targets a player, not a creature",
            d.submitTargetSelection(p, listOf(opposingBear)).error)
        assertNull(d.submitTargetSelection(p, listOf(opponent)).error)
        d.resolveAll()

        assertTrue("a creature reduced to 0/0 dies", d.getGraveyard(opponent).contains(opposingBear))
        assertEquals(2, d.projected.getPower(angel))
        assertEquals(2, d.projected.getToughness(angel))
        assertEquals(2, d.projected.getPower(ownBear))
        assertEquals(2, d.projected.getToughness(ownBear))
        assertTrue(d.getPermanents(opponent).contains(ring))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        assertTrue(d.getPermanents(opponent).contains(angel))
        assertEquals("the temporary reduction expires", 4, d.projected.getPower(angel))
        assertEquals(4, d.projected.getToughness(angel))
    }

    @Test
    fun hyperfragRoundCanTargetItsControllerAndLeavesTheOtherPlayersCreaturesAlone() {
        val d = customScenario(PrimarisEliminator, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.castPrimarisEliminator(p)
        d.chooseOption("Hyperfrag Round", p)

        val decision = d.awaitTargetChoice(p)
        assertEquals(setOf(p, opponent), decision.legalTargets.values.flatten().toSet())
        assertNull(d.submitTargetSelection(p, listOf(p)).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(p).contains(ownBear))
        assertTrue(d.getPermanents(opponent).contains(opposingBear))
        assertEquals(2, d.projected.getPower(opposingBear))
        assertEquals(2, d.projected.getToughness(opposingBear))
    }
}
