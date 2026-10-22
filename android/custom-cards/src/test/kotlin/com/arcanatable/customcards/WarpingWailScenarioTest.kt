package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WarpingWail
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class WarpingWailScenarioTest {
    @Test
    fun exileModeAcceptsEitherLowPowerOrLowToughnessButRejectsTwoTwo() {
        val d = customScenario(WarpingWail, "Grizzly Bears")
        d.registerCard(card("Fragile Giant") {
            manaCost = "{1}"
            typeLine = "Creature — Giant"
            power = 2
            toughness = 1
        })
        val p = d.player1
        val valid = d.putCreatureOnBattlefield(d.player2, "Fragile Giant")
        val invalid = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveColorlessMana(p, 2)
        assertNotNull(d.submit(CastSpell(
            playerId = p, cardId = wail, chosenModes = listOf(0),
            modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(invalid))),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = wail, chosenModes = listOf(0),
            modeTargetsOrdered = listOf(listOf(ChosenTarget.Permanent(valid))),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player2, "Fragile Giant").isEmpty())
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Fragile Giant"))
    }

    @Test
    fun counterModeCountersOnlyASorcerySpellOnTheStack() {
        val d = customScenario(WarpingWail)
        d.registerCard(card("Scenario Sorcery") {
            manaCost = "{1}"
            typeLine = "Sorcery"
            spell { effect = Effects.DrawCards(1) }
        })
        val p = d.player1
        val original = d.putCardInHand(p, "Scenario Sorcery")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, original).error)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = wail, chosenModes = listOf(1),
            modeTargetsOrdered = listOf(listOf(ChosenTarget.Spell(original))),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Scenario Sorcery"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Warping Wail"))
    }

    @Test
    fun thirdModeMakesManaCapableScionWithSacrificeAsCost() {
        val d = customScenario(WarpingWail)
        val p = d.player1
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveColorlessMana(p, 2)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = wail, chosenModes = listOf(2),
            modeTargetsOrdered = listOf(emptyList()),
            paymentStrategy = PaymentStrategy.FromPool,
        )).error)
        d.resolveAll()
        val scion = d.permanentsNamed(p, "Eldrazi Scion").single()
        val action = d.abilityActions(p, scion).single().action as ActivateAbility
        assertNull(d.submit(action).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Eldrazi Scion").isEmpty())
        assertEquals(1, d.manaPool(p).colorless)
    }
}
