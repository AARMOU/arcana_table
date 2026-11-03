package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShardOfTheNightbringer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Shard of the Nightbringer (40K #55): Flying; Drain Life — when it enters, if you cast it, target opponent
 * loses half their life, rounded up, and you gain the life lost this way. No official rulings are published;
 * "if you cast it" is an intervening-if (CR 603.4), and a player who can't lose life loses none (CR 119.8).
 */
class ShardOfTheNightbringerScenarioTest {

    private fun game() = customScenario(ShardOfTheNightbringer, "Reanimate", "Flare of Fortitude")

    private fun GameTestDriver.castShard(p: EntityId): EntityId {
        val shard = putCardInHand(p, "Shard of the Nightbringer")
        giveMana(p, Color.BLACK, 3)
        giveColorlessMana(p, 5)
        assertNull(castSpell(p, shard).error)
        return shard
    }

    private fun drain(opponentLife: Int, expectedLoss: Int) {
        val d = game()
        val p = d.player1
        val o = d.player2
        d.setLifeTotal(o, opponentLife)
        d.castShard(p)
        d.resolveAll(Choices(targets = listOf(o)))
        assertNotNull(d.findPermanent(p, "Shard of the Nightbringer"))
        assertEquals(opponentLife - expectedLoss, d.getLifeTotal(o))
        assertEquals(20 + expectedLoss, d.getLifeTotal(p))
    }

    @Test fun oddLifeRoundsUp() = drain(opponentLife = 15, expectedLoss = 8)

    @Test fun evenLifeHalves() = drain(opponentLife = 20, expectedLoss = 10)

    @Test fun lifeOneLosesOne() = drain(opponentLife = 1, expectedLoss = 1)

    @Test
    fun flyingEightEight() {
        val d = game()
        val p = d.player1
        d.castShard(p)
        d.resolveAll()
        val shard = d.findPermanent(p, "Shard of the Nightbringer")!!
        assertTrue(d.projected.hasKeyword(shard, Keyword.FLYING))
        assertEquals(8, d.projected.getPower(shard))
        assertEquals(8, d.projected.getToughness(shard))
    }

    @Test
    fun reanimatedItDoesNotTrigger() {
        val d = game()
        val p = d.player1
        val o = d.player2
        val shard = d.putCardInGraveyard(p, "Shard of the Nightbringer")
        val reanimate = d.putCardInHand(p, "Reanimate")
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpellWithTargets(p, reanimate, listOf(ChosenTarget.Card(shard, p, Zone.GRAVEYARD))).error)
        d.resolveAll()
        assertTrue(shard in d.state.getBattlefield())
        assertEquals("not cast: no Drain Life", 20, d.getLifeTotal(o))
        assertEquals("only Reanimate's loss of 8", 12, d.getLifeTotal(p))
    }

    @Test
    fun anOpponentWhoCantLoseLifeLosesNoneAndYouGainNone() {
        val d = game()
        val p = d.player1
        val o = d.player2
        d.castShard(p)
        // With the Shard on the stack, the opponent makes their life total unchangeable this turn.
        assertNull(d.passPriority(p).error)
        assertEquals(o, d.state.priorityPlayerId)
        val flare = d.putCardInHand(o, "Flare of Fortitude")
        d.giveMana(o, Color.WHITE, 4)
        assertNull(d.castSpell(o, flare).error)
        d.resolveAll(Choices(targets = listOf(o)))

        assertNotNull(d.findPermanent(p, "Shard of the Nightbringer"))
        assertEquals(20, d.getLifeTotal(o))
        assertEquals("you gain only the life actually lost", 20, d.getLifeTotal(p))
    }
}
