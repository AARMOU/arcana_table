package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DarksteelMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class DarksteelMonolithScenarioTest {
    @Test
    fun indestructibleMonolithSurvivesShatter() {
        val d = customScenario(DarksteelMonolith, "Shatter")
        val monolith = d.putPermanentOnBattlefield(d.player1, "Darksteel Monolith")
        assertTrue(d.projected.hasKeyword(monolith, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val shatter = d.putCardInHand(d.player2, "Shatter")
        d.giveMana(d.player2, Color.RED)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, shatter, listOf(monolith)).error)
        d.resolveAll()

        assertEquals(monolith, d.findPermanent(d.player1, "Darksteel Monolith"))
    }

    @Test
    fun controllerMayCastOnlyOneColorlessSpellFromHandForFreeEachTurn() {
        val d = customScenario(DarksteelMonolith, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")

        val firstRing = d.putCardInHand(p, "Sol Ring")
        assertNull(d.submit(CastSpell(playerId = p, cardId = firstRing, useWithoutPayingManaCost = true)).error)
        d.resolveAll(Choices(yes = true))
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertEquals(0, d.manaPool(p).colorless)

        val secondRing = d.putCardInHand(p, "Sol Ring")
        assertNotNull(
            "The free-cast permission is limited to once each turn",
            d.submit(CastSpell(playerId = p, cardId = secondRing, useWithoutPayingManaCost = true)).error,
        )

        val coloredSpell = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull(
            "The free-cast permission applies only to colorless spells",
            d.submit(CastSpell(playerId = p, cardId = coloredSpell, useWithoutPayingManaCost = true)).error,
        )
    }

    @Test
    fun controllerCanDeclineTheFreeCastAndPayTheSpellCostNormally() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        d.giveColorlessMana(p, 1)
        val ring = d.putCardInHand(p, "Sol Ring")

        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun opponentCannotUseTheMonolithsFreeCastPermission() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        d.putPermanentOnBattlefield(d.player1, "Darksteel Monolith")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val ring = d.putCardInHand(d.player2, "Sol Ring")

        assertNotNull(
            d.submit(
                CastSpell(playerId = d.player2, cardId = ring, useWithoutPayingManaCost = true),
            ).error,
        )
    }
}
