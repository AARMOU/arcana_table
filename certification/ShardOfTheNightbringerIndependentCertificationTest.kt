package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShardOfTheNightbringer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class ShardOfTheNightbringerIndependentCertificationTest {
    private val name = "Shard of the Nightbringer"

    @Test
    fun castingRequiresFiveGenericAndThreeBlackAndEntersAsAnEightEightFlyingCtan() {
        val d = customScenario(ShardOfTheNightbringer)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLACK, 3)
        d.giveColorlessMana(player, 4)
        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()
        assertTrue(d.getPermanents(player).contains(card))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "C'TAN"))
        assertTrue(d.projected.hasColor(card, Color.BLACK))
        assertEquals(8, d.projected.getPower(card))
        assertEquals(8, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(30, d.getLifeTotal(player))
        assertEquals(10, d.getLifeTotal(d.player2))
    }

    @Test
    fun extraGenericManaCannotReplaceTheThirdBlackMana() {
        val d = customScenario(ShardOfTheNightbringer)
        val card = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 6)
        assertNotNull(d.castSpell(d.player1, card).error)
        assertTrue(d.getHand(d.player1).contains(card))
        assertTrue(d.permanentsNamed(d.player1, name).isEmpty())
        assertEquals(20, d.getLifeTotal(d.player1))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun oddLifeTotalIsHalvedRoundingTheLifeLostUp() {
        val d = customScenario(ShardOfTheNightbringer)
        d.setLifeTotal(d.player2, 19)
        val card = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 3)
        d.giveColorlessMana(d.player1, 5)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        assertTrue(d.getPermanents(d.player1).contains(card))
        assertEquals(9, d.getLifeTotal(d.player2))
        assertEquals(30, d.getLifeTotal(d.player1))
    }

    @Test
    fun entersTriggerTargetsOnlyAnOpponentAndUsesLifeAtResolution() {
        val d = customScenario(ShardOfTheNightbringer, "Shock")
        val players = d.initMultiplayer(decks = List(3) { Deck.of("Plains" to 40) })
        val player = players[0]
        val chosen = players[1]
        val other = players[2]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.setLifeTotal(chosen, 19)
        val card = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLACK, 3)
        d.giveColorlessMana(player, 5)
        assertNull(d.castSpell(player, card).error)
        assertEquals(20, d.getLifeTotal(player))
        assertEquals(19, d.getLifeTotal(chosen))
        assertEquals(20, d.getLifeTotal(other))

        repeat(3) { assertNull(d.passPriority(d.priorityPlayer!!).error) }
        assertTrue(d.getPermanents(player).contains(card))
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("The enters trigger must choose its target before resolving", decision)
        val legal = decision!!.legalTargets.values.flatten()
        assertTrue(legal.contains(chosen))
        assertTrue(legal.contains(other))
        assertFalse(legal.contains(player))
        assertNotNull(d.submitTargetSelection(player, listOf(player)).error)
        assertNull(d.submitTargetSelection(player, listOf(chosen)).error)
        assertEquals(20, d.getLifeTotal(player))
        assertEquals(19, d.getLifeTotal(chosen))
        assertTrue(d.state.stack.isNotEmpty())

        repeat(2) {
            if (d.priorityPlayer != player) assertNull(d.passPriority(d.priorityPlayer!!).error)
        }
        assertEquals(player, d.priorityPlayer)
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(chosen)).error)
        repeat(3) { assertNull(d.passPriority(d.priorityPlayer!!).error) }
        assertEquals(17, d.getLifeTotal(chosen))
        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.state.stack.isNotEmpty())
        d.resolveAll()
        assertEquals(8, d.getLifeTotal(chosen))
        assertEquals(29, d.getLifeTotal(player))
        assertEquals(20, d.getLifeTotal(other))
    }

    @Test
    fun reanimatingItWithoutCastingDoesNotDrainTheOpponent() {
        val d = customScenario(ShardOfTheNightbringer, "Reanimate")
        val player = d.player1
        val shard = d.putCardInGraveyard(player, name)
        val reanimate = d.putCardInHand(player, "Reanimate")
        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpellWithTargets(
            player, reanimate, listOf(ChosenTarget.Card(shard, player, Zone.GRAVEYARD)),
        ).error)
        d.resolveAll()
        assertTrue(d.getPermanents(player).contains(shard))
        assertEquals(12, d.getLifeTotal(player))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun flyingPreventsACreatureWithoutFlyingOrReachFromBlocking() {
        val d = customScenario(ShardOfTheNightbringer, "Grizzly Bears")
        val shard = d.putCreatureOnBattlefield(d.player1, name)
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(shard)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(shard), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(shard))).error)
    }
}
