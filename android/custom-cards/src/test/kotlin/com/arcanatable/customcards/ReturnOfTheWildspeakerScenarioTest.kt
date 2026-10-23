package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ReturnOfTheWildspeaker
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class ReturnOfTheWildspeakerScenarioTest {
    private fun setup() = customScenario(ReturnOfTheWildspeaker).apply {
        registerCard(card("Scenario Beast") {
            manaCost = "{5}"
            typeLine = "Creature — Beast"
            power = 5
            toughness = 2
        })
        registerCard(card("Scenario Human") {
            manaCost = "{1}"
            typeLine = "Creature — Human Soldier"
            power = 9
            toughness = 9
        })
    }

    @Test
    fun drawModeUsesHighestNonHumanPowerInsteadOfHighestOverall() {
        val d = setup()
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Scenario Beast")
        d.putCreatureOnBattlefield(p, "Scenario Human")
        val spell = d.putCardInHand(p, "Return of the Wildspeaker")
        val before = d.getHand(p).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = spell,
            chosenModes = listOf(0), modeTargetsOrdered = listOf(emptyList()),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        assertEquals(before + 4, d.getHand(p).size)
    }

    @Test
    fun pumpModeAffectsOnlyCurrentNonHumanCreaturesForOneTurn() {
        val d = setup()
        val p = d.player1
        val beast = d.putCreatureOnBattlefield(p, "Scenario Beast")
        val human = d.putCreatureOnBattlefield(p, "Scenario Human")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Scenario Beast")
        val spell = d.putCardInHand(p, "Return of the Wildspeaker")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = spell,
            chosenModes = listOf(1), modeTargetsOrdered = listOf(emptyList()),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        assertEquals(8, d.projected.getPower(beast))
        assertEquals(5, d.projected.getToughness(beast))
        assertEquals(9, d.projected.getPower(human))
        assertEquals(5, d.projected.getPower(enemy))
        val lateBeast = d.putCreatureOnBattlefield(p, "Scenario Beast")
        assertEquals(5, d.projected.getPower(lateBeast))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertEquals(5, d.projected.getPower(beast))
    }
}
