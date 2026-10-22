package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Venomcrawler
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class VenomcrawlerScenarioTest {
    @Test
    fun creaturesOfEitherControllerIncludingTokensAddOneCounterPerDeath() {
        val d = customScenario(Venomcrawler, "Grizzly Bears", "Murder")
        val p = d.player1
        val crawler = d.putCreatureOnBattlefield(p, "Venomcrawler")
        val creatures = listOf(
            d.putCreatureOnBattlefield(p, "Grizzly Bears"),
            d.putToken(d.player2, "Grizzly Bears")
        )
        for (creature in creatures) {
            val murder = d.putCardInHand(p, "Murder")
            d.giveMana(p, Color.BLACK, 2)
            d.giveColorlessMana(p, 1)
            assertNull(d.castSpell(p, murder, listOf(creature)).error)
            d.resolveAll()
        }

        assertEquals(2, d.state.getEntity(crawler)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(4, d.projected.getPower(crawler))
        assertEquals(4, d.projected.getToughness(crawler))
    }

    @Test
    fun lifelinkGainsLifeFromActualCombatDamage() {
        val d = customScenario(Venomcrawler)
        val p = d.player1
        val crawler = d.putCreatureOnBattlefield(p, "Venomcrawler")
        d.removeSummoningSickness(crawler)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(crawler), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(22, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(d.player2))
    }

    @Test
    fun noncreatureArtifactGoingToGraveyardDoesNotTriggerCrawler() {
        val d = customScenario(Venomcrawler, "Sol Ring", "Disenchant")
        val p = d.player1
        val crawler = d.putCreatureOnBattlefield(p, "Venomcrawler")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val removal = d.putCardInHand(p, "Disenchant")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, removal, listOf(ring)).error)
        d.resolveAll()

        assertNull(d.findPermanent(d.player2, "Sol Ring"))
        assertEquals(0, d.state.getEntity(crawler)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(2, d.projected.getPower(crawler))
    }
}
