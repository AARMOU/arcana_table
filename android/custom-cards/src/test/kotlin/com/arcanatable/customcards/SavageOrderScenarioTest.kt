package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SavageOrder
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class SavageOrderScenarioTest {
    @Test
    fun sacrificesDinosaurAndFindsDinosaur() {
        val d = CardPool.driver("Colossal Dreadmaw").apply {
            registerCard(SavageOrder)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val dinosaur = d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        d.putCardOnTopOfLibrary(p, "Colossal Dreadmaw")
        val order = d.putCardInHand(p, "Savage Order")
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)

        val result = d.submit(
            CastSpell(
                playerId = p,
                cardId = order,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(dinosaur)),
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )
        assertNull(result.error)
        d.resolveAll(Choices(pick = listOf("Colossal Dreadmaw")))

        assertTrue(d.zoneNames(p, com.wingedsheep.sdk.core.Zone.GRAVEYARD).contains("Colossal Dreadmaw"))
        assertEquals(1, d.permanentsNamed(p, "Colossal Dreadmaw").size)
    }
}
