package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.costs.CounterRemovalCost
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.DistributedCounterRemoval
import org.junit.Assert.*
import org.junit.Test

/**
 * Iron Spider, Stark Upgrade (upstream Argentum card):
 * "{2}, Remove two +1/+1 counters from among artifacts you control: Draw a card."
 *
 * Reproduction for the owner's report that the draw ability doesn't work. These run on the
 * real rules engine to settle whether the engine handles the spread-out counter cost, so any
 * remaining bug is in how the app builds the activation.
 */
class IronSpiderScenarioTest {
    private val spiderName = "Iron Spider, Stark Upgrade"

    private fun GameTestDriver.counters(id: EntityId): Int =
        state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun GameTestDriver.setCounters(id: EntityId, n: Int) =
        replaceState(state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withAdded(CounterType.PLUS_ONE_PLUS_ONE, n))
        })

    private fun GameTestDriver.drawAction(p: EntityId, spider: EntityId) =
        abilityActions(p, spider).single {
            (it.action as ActivateAbility).abilityId ==
                cardRegistry.getCard(spiderName)!!.script.activatedAbilities[1].id
        }

    private fun setup(): Triple<GameTestDriver, EntityId, EntityId> {
        val d = scenario(spiderName, "Ornithopter")
        val p = d.player1
        val spider = d.putCreatureOnBattlefield(p, spiderName)
        val thopter = d.putCreatureOnBattlefield(p, "Ornithopter")
        d.giveMana(p, Color.WHITE, 2)
        return Triple(d, spider, thopter)
    }

    /** The counter-type key the engine itself reports, so the test sends what a client would. */
    private fun GameTestDriver.counterKey(p: EntityId, spider: EntityId): String =
        drawAction(p, spider).additionalCostInfo!!.counterRemovalCreatures
            .first { it.availableCountersByType.isNotEmpty() }
            .availableCountersByType.keys.single()

    private fun activateWith(d: GameTestDriver, spider: EntityId, removals: List<DistributedCounterRemoval>) =
        d.submit(
            (d.drawAction(d.player1, spider).action as ActivateAbility).copy(
                costPayment = AdditionalCostPayment(distributedCounterRemovals = removals)
            )
        )

    @Test fun abilityIsOfferedWithOtherArtifactListedAsCounterSource() {
        val (d, spider, thopter) = setup()
        d.setCounters(thopter, 2)
        val la = d.drawAction(d.player1, spider)
        assertTrue("affordable with counters only on another artifact", la.affordable)
        val info = la.additionalCostInfo
        assertNotNull("engine exposes the counter-removal choice", info)
        assertEquals("RemoveCounters", info!!.costType)
        // The engine leaves distributedCounterRemovalTotal at 0 for this cost, so a client can't
        // read the required count (2) from the legal action. It has to come from the ability's
        // AtomRemoveCounters cost in the card script.
        assertEquals(0, info.distributedCounterRemovalTotal)
        val sources = info.counterRemovalCreatures.filter { it.availableCounters > 0 }.map { it.entityId }
        assertEquals(listOf(thopter), sources)
    }

    @Test fun removingBothCountersFromAnotherArtifactDrawsACard() {
        val (d, spider, thopter) = setup()
        d.setCounters(thopter, 2)
        val key = d.counterKey(d.player1, spider)
        assertEquals("the engine names the counter type \"+1/+1\" (what a client must send)", "+1/+1", key)
        val hand = d.getHandSize(d.player1)
        val r = activateWith(d, spider, listOf(DistributedCounterRemoval(thopter, key, 2)))
        assertNull("activation accepted: ${r.error}", r.error)
        d.resolveAll()
        assertEquals(0, d.counters(thopter))
        assertEquals(hand + 1, d.getHandSize(d.player1))
    }

    @Test fun countersCanBeSplitAcrossTwoArtifacts() {
        val (d, spider, thopter) = setup()
        d.setCounters(spider, 1)
        d.setCounters(thopter, 1)
        val key = d.counterKey(d.player1, spider)
        val hand = d.getHandSize(d.player1)
        val r = activateWith(d, spider, listOf(
            DistributedCounterRemoval(spider, key, 1),
            DistributedCounterRemoval(thopter, key, 1),
        ))
        assertNull("split activation accepted: ${r.error}", r.error)
        d.resolveAll()
        assertEquals(0, d.counters(spider))
        assertEquals(0, d.counters(thopter))
        assertEquals(hand + 1, d.getHandSize(d.player1))
    }

    @Test fun activationWithoutAnyDistributionIsWhatTheAppSendsToday() {
        // What the app currently submits: no counter choice at all. The engine rejects it, which
        // is the owner's bug. Counters sit only on the other artifact.
        val (d, spider, thopter) = setup()
        d.setCounters(thopter, 2)
        val hand = d.getHandSize(d.player1)
        val r = d.submit(d.drawAction(d.player1, spider).action)
        d.resolveAll()
        val drew = d.getHandSize(d.player1) == hand + 1
        assertEquals("Counter removal total (0) does not match required count (2)", r.error)
        assertFalse(drew)
        assertEquals(2, d.counters(thopter))
    }

    @Test fun notAffordableWithFewerThanTwoCounters() {
        val (d, spider, thopter) = setup()
        d.setCounters(thopter, 1)
        val la = d.abilityActions(d.player1, spider).singleOrNull {
            (it.action as ActivateAbility).abilityId ==
                d.cardRegistry.getCard(spiderName)!!.script.activatedAbilities[1].id
        }
        assertTrue("one counter isn't enough", la == null || !la.affordable)
    }

    @Test fun appHostFillsInTheCountOnTheRealLegalAction() {
        // What Arcana's offline host sends: the engine's enriched legal action, then CounterRemovalCost.fill.
        val (d, spider, thopter) = setup()
        d.setCounters(thopter, 2)
        val enriched = com.wingedsheep.engine.view.LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
            .enrich(d.legalActions(d.player1), d.state, d.player1)
        val drawId = d.cardRegistry.getCard(spiderName)!!.script.activatedAbilities[1].id
        fun draw(list: List<com.wingedsheep.engine.view.LegalActionInfo>) =
            list.single { (it.action as? ActivateAbility)?.let { a -> a.sourceId == spider && a.abilityId == drawId } == true }
        assertEquals("engine alone leaves the total at 0", 0, draw(enriched).additionalCostInfo!!.distributedCounterRemovalTotal)
        val filled = draw(CounterRemovalCost.fill(enriched, d.state, d.cardRegistry))
        assertEquals(2, filled.additionalCostInfo!!.distributedCounterRemovalTotal)
        assertTrue(filled.additionalCostInfo!!.counterRemovalCreatures.any { it.entityId == thopter && it.availableCountersByType["+1/+1"] == 2 })
        // Every other action is passed through unchanged.
        val others = CounterRemovalCost.fill(enriched, d.state, d.cardRegistry).zip(enriched).filter { (_, before) -> before !== draw(enriched) }
        assertTrue(others.isNotEmpty() && others.all { (after, before) -> after === before })
    }
}
