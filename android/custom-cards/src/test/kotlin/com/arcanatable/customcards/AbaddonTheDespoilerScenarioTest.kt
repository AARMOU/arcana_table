package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbaddonTheDespoiler
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbaddonTheDespoilerScenarioTest {
    @Test
    fun abaddonTramplesButDoesNotGrantTrampleToOpponentsCreatures() {
        val d = customScenario(AbaddonTheDespoiler, "Grizzly Bears")
        val abaddon = d.putCreatureOnBattlefield(d.player1, "Abaddon the Despoiler")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(abaddon, Keyword.TRAMPLE))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.TRAMPLE))
    }

    @Test
    fun cascadesWhenTheTotalLifeLostByAllOpponentsMeetsTheSpellManaValue() {
        val d = CardPool.driver("Shock", "Worn Powerstone", "Grizzly Bears", "Ornithopter")
        d.registerCard(AbaddonTheDespoiler)
        val (p, opponent1, opponent2) = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.putCreatureOnBattlefield(p, "Abaddon the Despoiler")

        val firstShock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, firstShock, listOf(opponent1)).error)
        d.resolveAll()

        d.passPriorityTo(p)
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        val secondShock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, secondShock, listOf(opponent2)).error)
        d.resolveAll(Choices(yes = true))
        assertEquals(1, d.permanentsNamed(p, "Ornithopter").size)

        d.passPriorityTo(p)
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val powerstone = d.putCardInHand(p, "Worn Powerstone")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, powerstone).error)
        d.resolveAll(Choices(yes = true))

        assertEquals(18, d.getLifeTotal(opponent1))
        assertEquals(18, d.getLifeTotal(opponent2))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
    }

    @Test
    fun doesNotCascadeWhenNoOpponentHasLostLifeThisTurn() {
        val d = customScenario(AbaddonTheDespoiler, "Worn Powerstone", "Ornithopter")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Abaddon the Despoiler")
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        val powerstone = d.putCardInHand(p, "Worn Powerstone")
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, powerstone).error)
        d.resolveAll(Choices(yes = true))

        assertTrue(d.permanentsNamed(p, "Ornithopter").isEmpty())
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun doesNotCascadeForSpellsCastFromExile() {
        val d = customScenario(
            AbaddonTheDespoiler,
            "Shock",
            "Heralds of Tzeentch",
            "Grizzly Bears",
            "Ornithopter",
        )
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Abaddon the Despoiler")
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        d.resolveAll()

        d.passPriorityTo(p)
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val heralds = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, heralds).error)
        d.resolveAll(Choices(yes = true))

        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertTrue(d.permanentsNamed(p, "Ornithopter").isEmpty())
    }

    @Test
    fun doesNotCascadeOnYourSpellDuringAnOpponentsTurn() {
        val d = CardPool.driver("Shock", "Ornithopter")
        d.registerCard(AbaddonTheDespoiler)
        val (p, opponent1, opponent2) = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.putCreatureOnBattlefield(p, "Abaddon the Despoiler")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent1)

        val opponentShock = d.putCardInHand(opponent1, "Shock")
        d.giveMana(opponent1, Color.RED)
        assertNull(d.castSpell(opponent1, opponentShock, listOf(opponent2)).error)
        d.resolveAll()

        d.passPriorityTo(p)
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(opponent1)).error)
        d.resolveAll(Choices(yes = true))

        assertTrue(d.permanentsNamed(p, "Ornithopter").isEmpty())
    }
}

private fun GameTestDriver.passPriorityTo(player: EntityId) {
    repeat(4) {
        if (priorityPlayer == player) return
        val current = priorityPlayer ?: error("No player has priority")
        assertNull(passPriority(current).error)
    }
    assertEquals(player, priorityPlayer)
}
