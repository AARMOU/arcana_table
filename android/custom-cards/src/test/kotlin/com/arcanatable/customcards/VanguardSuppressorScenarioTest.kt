package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VanguardSuppressor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class VanguardSuppressorScenarioTest {
    @Test
    fun unblockedCombatDamageDrawsExactlyOneCard() {
        val d = customScenario(VanguardSuppressor)
        val p = d.player1
        val suppressor = d.putCreatureOnBattlefield(p, "Vanguard Suppressor")
        d.removeSummoningSickness(suppressor)
        assertTrue(d.projected.hasKeyword(suppressor, Keyword.FLYING))
        val handBefore = d.getHand(p).size

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(suppressor), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(handBefore + 1, d.getHand(p).size)
    }

    @Test
    fun squadPaymentCreatesARealCopyThatAlsoHasCombatDrawTrigger() {
        val d = customScenario(VanguardSuppressor)
        val p = d.player1
        val card = d.putCardInHand(p, "Vanguard Suppressor")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 5)
        assertNull(d.submit(CastSpell(p, card,
            declaredCostSlot = ChoiceSlot.SQUAD, declaredCostTimes = 1,
            paymentStrategy = PaymentStrategy.FromPool)).error)
        d.resolveAll()

        val suppressors = d.permanentsNamed(p, "Vanguard Suppressor")
        assertEquals(2, suppressors.size)
        assertEquals(1, suppressors.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        suppressors.forEach {
            assertTrue(d.projected.hasKeyword(it, Keyword.FLYING))
            d.removeSummoningSickness(it)
        }
        val handBefore = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, suppressors, d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(14, d.getLifeTotal(d.player2))
        assertEquals(handBefore + 2, d.getHand(p).size)
    }
}
