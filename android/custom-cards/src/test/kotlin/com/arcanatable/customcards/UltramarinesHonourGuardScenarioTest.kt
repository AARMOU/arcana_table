package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UltramarinesHonourGuard
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class UltramarinesHonourGuardScenarioTest {
    @Test
    fun squadCopiesBoostOtherCreaturesAndNotThemselves() {
        val d = customScenario(UltramarinesHonourGuard, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val card = d.putCardInHand(p, "Ultramarines Honour Guard")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 7)

        assertNull(
            d.submit(
                CastSpell(
                    p,
                    card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 2,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )
        d.resolveAll()

        val guards = d.permanentsNamed(p, "Ultramarines Honour Guard")
        assertEquals(3, guards.size)
        assertEquals(2, guards.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        guards.forEach { assertEquals(4, d.projected.getPower(it)) }
        assertEquals(5, d.projected.getPower(bear))
        assertEquals(5, d.projected.getToughness(bear))
    }
}
