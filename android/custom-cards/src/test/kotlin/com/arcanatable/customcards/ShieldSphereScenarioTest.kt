package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShieldSphere
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ShieldSphereScenarioTest {
    @Test
    fun blockingAddsOneMinusZeroMinusOneCounter() {
        val d = customScenario(ShieldSphere, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val sphere = d.putCreatureOnBattlefield(d.player2, "Shield Sphere")
        d.removeSummoningSickness(attacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player1)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS, activePlayer = d.player1)
        assertNull(d.declareBlockers(d.player2, mapOf(sphere to listOf(attacker))).error)
        d.resolveAll()

        assertEquals(
            1,
            d.state.getEntity(sphere)!!.get<CountersComponent>()!!
                .getCount(CounterType.MINUS_ZERO_MINUS_ONE),
        )
        assertEquals(5, d.projected.getToughness(sphere))
    }

    @Test
    fun notBlockingDoesNotAddCounter() {
        val d = customScenario(ShieldSphere, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val sphere = d.putCreatureOnBattlefield(d.player2, "Shield Sphere")
        d.removeSummoningSickness(attacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player1)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS, activePlayer = d.player1)
        assertNull(d.declareNoBlockers(d.player2).error)
        d.resolveAll()

        assertEquals(
            0,
            d.state.getEntity(sphere)!!.get<CountersComponent>()
                ?.getCount(CounterType.MINUS_ZERO_MINUS_ONE) ?: 0,
        )
    }

    @Test
    fun defenderCannotAttack() {
        val d = customScenario(ShieldSphere)
        val sphere = d.putCreatureOnBattlefield(d.player1, "Shield Sphere")
        d.removeSummoningSickness(sphere)

        assertTrue(d.projected.hasKeyword(sphere, Keyword.DEFENDER))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player1)

        assertNotNull(d.declareAttackers(d.player1, listOf(sphere), d.player2).error)
    }
}
