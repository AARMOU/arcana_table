package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SavaiThundermane
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class SavaiThundermaneScenarioTest {
    @Test
    fun cyclingCanPayToDamageCreatureAndGainLife() {
        val d = customScenario(SavaiThundermane, "Dismantling Wave", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Savai Thundermane")
        val bears = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val cyclingCard = d.putCardInHand(p, "Dismantling Wave")
        d.giveMana(p, Color.WHITE, 2)
        d.giveColorlessMana(p, 8)

        assertNull(d.submit(CycleCard(p, cyclingCard, PaymentStrategy.FromPool)).error)
        d.resolveAll(Choices(targets = listOf(bears)))

        assertEquals(22, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
    }
}
