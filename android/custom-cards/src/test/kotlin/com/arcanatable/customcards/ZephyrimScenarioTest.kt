package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zephyrim
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class ZephyrimScenarioTest {
    @Test
    fun squadCreatesCopiesThatKeepFlyingAndVigilance() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 7)

        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 2,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )
        d.resolveAll()

        val zephyrims = d.permanentsNamed(p, "Zephyrim")
        assertEquals(3, zephyrims.size)
        assertEquals(2, zephyrims.count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        zephyrims.forEach {
            assertTrue(d.projected.hasKeyword(it, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(it, Keyword.VIGILANCE))
        }
    }

    @Test
    fun flyingPreventsGroundCreaturesFromBlockingAndVigilanceLeavesTheAttackerUntapped() {
        val d = customScenario(Zephyrim, "Grizzly Bears")
        val p = d.player1
        val zephyrim = d.putCreatureOnBattlefield(p, "Zephyrim")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(zephyrim)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(zephyrim), d.player2).error)
        assertFalse(d.isTapped(zephyrim))
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(zephyrim to listOf(bear))).error)
    }

    @Test
    fun squadCanBeDeclined() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val hand = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, hand).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(p, "Zephyrim").size)
    }

    @Test
    fun miracleCastsTheFirstDrawForItsAlternativeCost() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Zephyrim")

        d.passPriorityUntil(Step.DRAW, activePlayer = p)
        assertTrue(d.pendingDecision is YesNoDecision)
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)
        d.decide(Choices(yes = true))
        d.resolveAll(Choices(yes = true))

        assertEquals(1, d.permanentsNamed(p, "Zephyrim").size)
        assertTrue(d.getHand(p).none { d.getCardName(it) == "Zephyrim" })
    }

    @Test
    fun miracleMayBeDeclinedAndDoesNotTriggerForASecondCardDrawn() {
        val firstDraw = customScenario(Zephyrim)
        val player = firstDraw.player1
        firstDraw.putCardOnTopOfLibrary(player, "Zephyrim")

        firstDraw.passPriorityUntil(Step.DRAW, activePlayer = player)
        assertTrue(firstDraw.pendingDecision is YesNoDecision)
        firstDraw.giveMana(player, Color.WHITE)
        firstDraw.giveColorlessMana(player, 1)
        firstDraw.decide(Choices(yes = false))
        firstDraw.resolveAll()
        assertTrue(firstDraw.getHand(player).any { firstDraw.getCardName(it) == "Zephyrim" })
        assertTrue(firstDraw.permanentsNamed(player, "Zephyrim").isEmpty())

        val laterDraw = customScenario(Zephyrim, "Divination")
        val laterPlayer = laterDraw.player1
        laterDraw.putCardOnTopOfLibrary(laterPlayer, "Zephyrim")
        laterDraw.putCardOnTopOfLibrary(laterPlayer, "Plains")
        val divination = laterDraw.putCardInHand(laterPlayer, "Divination")
        laterDraw.giveMana(laterPlayer, Color.BLUE)
        laterDraw.giveColorlessMana(laterPlayer, 2)

        assertNull(laterDraw.castSpell(laterPlayer, divination).error)
        laterDraw.resolveAll()

        assertTrue(laterDraw.pendingDecision == null)
        assertTrue(laterDraw.getHand(laterPlayer).any { laterDraw.getCardName(it) == "Zephyrim" })
        assertTrue(laterDraw.permanentsNamed(laterPlayer, "Zephyrim").isEmpty())
    }
}
