package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArcoFlagellant
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class ArcoFlagellantScenarioTest {
    @Test
    fun squadCreatesCopiesAndEachArcoCanPayLifeForIndestructible() {
        val d = customScenario(ArcoFlagellant, "Murder")
        val p = d.player1
        val opponent = d.player2
        val card = d.putCardInHand(p, "Arco-Flagellant")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)

        assertNull(
            d.submit(
                CastSpell(
                    p,
                    card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 1,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )
        d.resolveAll()

        val arcos = d.permanentsNamed(p, "Arco-Flagellant")
        assertEquals(2, arcos.size)
        val arco = arcos.first()
        val lifeBeforeAbility = d.getLifeTotal(p)
        assertNull(d.activate(p, arco).error)
        d.resolveAll()

        assertEquals(lifeBeforeAbility - 3, d.getLifeTotal(p))
        assertTrue(d.projected.hasKeyword(arco, Keyword.INDESTRUCTIBLE))

        d.passPriority(p)
        val murder = d.putCardInHand(opponent, "Murder")
        d.giveMana(opponent, Color.BLACK, 2)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, murder, listOf(arco)).error)
        d.resolveAll()
        assertEquals(arco, d.findPermanent(p, "Arco-Flagellant"))
    }

    @Test
    fun arcoFlagellantCannotBlock() {
        val d = customScenario(ArcoFlagellant, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val arco = d.putCreatureOnBattlefield(d.player2, "Arco-Flagellant")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player1)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_BLOCKERS, activePlayer = d.player1)
        assertNotNull(d.declareBlockers(d.player2, mapOf(attacker to listOf(arco))).error)
    }
}
