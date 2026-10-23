package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SufferThePast
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SufferThePastScenarioTest {
    @Test
    fun xTargetsAreExiledAndLifeChangesByTheNumberActuallyExiled() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val forest = d.putCardInGraveyard(opponent, "Forest")
        d.putCardInGraveyard(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Suffer the Past")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        val playerTarget = ChosenTarget.Player(opponent)
        val cardTargets = listOf(
            ChosenTarget.Card(bear, opponent, Zone.GRAVEYARD),
            ChosenTarget.Card(forest, opponent, Zone.GRAVEYARD),
        )

        fun cast(targets: List<ChosenTarget>) = d.submit(
            CastSpell(
                playerId = p,
                cardId = spell,
                targets = targets,
                xValue = 2,
                paymentStrategy = PaymentStrategy.FromPool,
            ),
        )

        assertNotNull("X cards must be selected as targets", cast(listOf(playerTarget, cardTargets.first())).error)
        assertNull(cast(listOf(playerTarget) + cardTargets).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Grizzly Bears", "Forest")))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).isEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).containsAll(listOf("Grizzly Bears", "Suffer the Past")))
    }
    @Test
    fun canTargetYourOwnGraveyard() {
        val d = customScenario(SufferThePast, "Grizzly Bears")
        val caster = d.player1
        val bear = d.putCardInGraveyard(caster, "Grizzly Bears")
        val spell = d.putCardInHand(caster, "Suffer the Past")
        d.giveMana(caster, Color.BLACK)
        d.giveColorlessMana(caster, 1)
        val result = d.submit(CastSpell(
            playerId = caster,
            cardId = spell,
            targets = listOf(ChosenTarget.Player(caster), ChosenTarget.Card(bear, caster, Zone.GRAVEYARD)),
            xValue = 1,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        assertNull("Oracle permits targeting yourself", result.error)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(caster))
        assertTrue(d.zoneNames(caster, Zone.EXILE).contains("Grizzly Bears"))
    }
}
