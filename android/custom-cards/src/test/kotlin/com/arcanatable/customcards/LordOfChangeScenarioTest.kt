package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LordOfChange
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LordOfChangeScenarioTest {
    @Test
    fun enteringDrawsThreeAndPreservesFlying() {
        val d = customScenario(LordOfChange)
        val p = d.player1
        val lord = d.putCardInHand(p, "Lord of Change")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 6)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, lord).error)
        d.resolveAll()

        assertEquals(before + 2, d.getHand(p).size)
        assertTrue(d.projected.hasKeyword(d.findPermanent(p, "Lord of Change")!!, Keyword.FLYING))
    }

    @Test
    fun opponentsRemovalIsCounteredWhenWardThreeCannotBePaid() {
        val d = customScenario(LordOfChange, "Murder")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Lord of Change")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(d.player2, murder, listOf(lord)).error)
        assertEquals(2, d.state.stack.size)
        d.resolveAll()

        assertNotNull(d.findPermanent(p, "Lord of Change"))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Murder"))
    }

    @Test
    fun opponentCanPayThreeForWardAndResolveRemoval() {
        val d = customScenario(LordOfChange, "Murder")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Lord of Change")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 4)
        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(d.player2, murder, listOf(lord)).error)
        d.bothPass()
        assertTrue(d.pendingDecision is com.wingedsheep.engine.core.SelectManaSourcesDecision)
        assertNull(d.submitManaAutoPayOrDecline(d.player2, autoPay = true).error)
        d.resolveAll()

        assertNull(d.findPermanent(p, "Lord of Change"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Lord of Change"))
    }

    @Test
    fun controllersOwnTargetingDoesNotTriggerWard() {
        val d = customScenario(LordOfChange, "Murder")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Lord of Change")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, murder, listOf(lord)).error)
        assertEquals(1, d.state.stack.size)
        d.resolveAll()
        assertNull(d.findPermanent(p, "Lord of Change"))
    }
}
