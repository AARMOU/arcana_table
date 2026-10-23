package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SicarianInfiltrator
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class SicarianInfiltratorScenarioTest {
    @Test fun oneCastWithoutSquadDrawsExactlyOne() {
        val d = customScenario(SicarianInfiltrator)
        val p = d.player1
        val card = d.putCardInHand(p, "Sicarian Infiltrator")
        val count = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(count, d.getHand(p).size)
        assertEquals(1, d.permanentsNamed(p, "Sicarian Infiltrator").size)
        assertTrue(d.projected.hasKeyword(card, Keyword.FLASH))
    }

    @Test fun paidSquadTwiceCreatesTwoRealCopiesAndThreeDrawTriggers() {
        val d = customScenario(SicarianInfiltrator)
        val p = d.player1
        val card = d.putCardInHand(p, "Sicarian Infiltrator")
        val count = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 6)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = card,
            declaredCostSlot = ChoiceSlot.SQUAD, declaredCostTimes = 2,
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        val copies = d.permanentsNamed(p, "Sicarian Infiltrator")
        assertEquals(3, copies.size)
        assertEquals(2, copies.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(count + 2, d.getHand(p).size)
    }
}
