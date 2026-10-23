package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zephyrim
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class ZephyrimScenarioTest {
    @Test
    fun squadCopiesRetainFlyingAndVigilance() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 5)

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
        d.resolveAll(Choices(yes = true))

        val zephyrim = d.permanentsNamed(p, "Zephyrim")
        assertEquals(2, zephyrim.size)
        assertEquals(1, zephyrim.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        zephyrim.forEach {
            assertTrue(d.projected.hasKeyword(it, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(it, Keyword.VIGILANCE))
        }
    }

    @Test
    fun miracleCanCastZephyrimForItsMiracleCostOnTheFirstDrawOfTheTurn() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        d.registerCard(card("Zephyrim Miracle Draw Test") {
            manaCost = "{U}"
            typeLine = "Instant"
            spell { effect = Effects.DrawCards(1) }
        })
        val zephyrimCard = d.putCardOnTopOfLibrary(p, "Zephyrim")
        val drawSpell = d.putCardInHand(p, "Zephyrim Miracle Draw Test")
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, drawSpell).error)
        d.resolveAll()

        val miracleCast = d.legalActions(p).mapNotNull { it.action as? CastSpell }
            .single { it.cardId == zephyrimCard && it.alternativeCostType == AlternativeCostType.MIRACLE }
        assertNull(d.submit(miracleCast).error)
        d.resolveAll()

        assertNotNull(d.findPermanent(p, "Zephyrim"))
        assertTrue(d.getHand(p).none { d.getCardName(it) == "Zephyrim" })
    }

    @Test
    fun miracleIsUnavailableWhenZephyrimIsNotTheFirstCardDrawnThatTurn() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        d.registerCard(card("Zephyrim Miracle Draw Test") {
            manaCost = "{U}"
            typeLine = "Instant"
            spell { effect = Effects.DrawCards(1) }
        })
        val zephyrimCard = d.putCardOnTopOfLibrary(p, "Zephyrim")
        d.putCardOnTopOfLibrary(p, "Plains")
        val firstDraw = d.putCardInHand(p, "Zephyrim Miracle Draw Test")
        val secondDraw = d.putCardInHand(p, "Zephyrim Miracle Draw Test")
        d.giveMana(p, Color.BLUE, 2)

        assertNull(d.castSpell(p, firstDraw).error)
        d.resolveAll()
        assertNull(d.castSpell(p, secondDraw).error)
        d.resolveAll()

        assertNull(d.findPermanent(p, "Zephyrim"))
        assertTrue(d.getHand(p).any { d.getCardName(it) == "Zephyrim" })
        assertTrue(
            d.legalActions(p).mapNotNull { it.action as? CastSpell }
                .none { it.cardId == zephyrimCard && it.alternativeCostType == AlternativeCostType.MIRACLE },
        )
    }
}
