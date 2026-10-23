package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ImmoralBargain
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class ImmoralBargainScenarioTest {
    @Test
    fun sacrificesCreatureToDestroyTargetPermanent() {
        val d = CardPool.driver("Sol Ring", "Grizzly Bears").apply {
            registerCard(ImmoralBargain)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val bargain = d.putCardInHand(p, "Immoral Bargain")
        d.giveMana(p, com.wingedsheep.sdk.core.Color.BLACK)
        d.giveMana(p, com.wingedsheep.sdk.core.Color.GREEN)
        d.giveColorlessMana(p, 1)
        val cast = d.submit(
            CastSpell(
                playerId = p,
                cardId = bargain,
                targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(ring)),
                additionalCostPayment = AdditionalCostPayment(variableCostPermanents = listOf(bear)),
                paymentStrategy = PaymentStrategy.AutoPay,
            ),
        )
        assertNull(cast.error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertNull(d.findPermanent(d.player2, "Sol Ring"))
    }
}
