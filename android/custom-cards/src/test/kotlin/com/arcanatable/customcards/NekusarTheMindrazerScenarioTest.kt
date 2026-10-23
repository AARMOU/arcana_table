package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NekusarTheMindrazer
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Test

class NekusarTheMindrazerScenarioTest {
    @Test
    fun opponentDrawStepGivesExtraCardAndDealsDamageForEachDraw() {
        val d = CardPool.driver().apply {
            registerCard(NekusarTheMindrazer)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        d.putCreatureOnBattlefield(d.player1, "Nekusar, the Mindrazer")
        val opponentHandBeforeDraw = d.getHandSize(d.player2)

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.resolveAll()

        assertEquals(opponentHandBeforeDraw + 2, d.getHandSize(d.player2))
        assertEquals(18, d.getLifeTotal(d.player2))
    }
}
