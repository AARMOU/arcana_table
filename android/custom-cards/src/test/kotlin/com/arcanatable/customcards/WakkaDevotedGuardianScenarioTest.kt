package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WakkaDevotedGuardian
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakkaDevotedGuardianScenarioTest {
    @Test
    fun combatDamageDestroysTargetArtifactAndBlitzballCaptainAddsCounters() {
        val d = CardPool.driver("Sol Ring", "Grizzly Bears").apply {
            registerCard(WakkaDevotedGuardian)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val wakka = d.putCreatureOnBattlefield(d.player1, "Wakka, Devoted Guardian")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.removeSummoningSickness(wakka)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(wakka), d.player2).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.bothPass()
        d.resolveAll(Choices(targets = listOf(ring)))
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.passPriorityUntil(Step.END)
        d.resolveAll()

        assertNull("the targeted artifact was destroyed", d.findPermanent(d.player2, "Sol Ring"))
        assertEquals(1, d.state.getEntity(wakka)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(1, d.state.getEntity(bear)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
    }
}
