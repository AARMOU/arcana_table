package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WarpingWail
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class WarpingWailIndependentCertificationTest {
    // Modal spells announce their mode and targets before paying costs.
    private fun castMode(
        d: GameTestDriver, player: EntityId, card: EntityId,
        mode: Int, targets: List<ChosenTarget> = emptyList(),
    ) = d.submit(CastSpell(
        playerId = player, cardId = card, targets = targets,
        paymentStrategy = PaymentStrategy.FromPool,
        chosenModes = listOf(mode), modeTargetsOrdered = listOf(targets),
    ))

    @Test
    fun costsOneGenericAndOneColorlessManaAndExilesAZeroPowerCreature() {
        val d = customScenario(WarpingWail, "Ornithopter")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)

        assertNull(castMode(d, p, wail, 0, listOf(ChosenTarget.Permanent(target))).error)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Ornithopter"))
        assertFalse(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun coloredManaCannotPayTheRequiredColorlessSymbol() {
        val d = customScenario(WarpingWail, "Ornithopter")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveMana(p, Color.BLUE, 2)

        assertNotNull(castMode(d, p, wail, 0, listOf(ChosenTarget.Permanent(target))).error)
        assertTrue(d.getHand(p).contains(wail))
        assertTrue(d.getPermanents(d.player2).contains(target))
        assertEquals(2, d.manaPool(p).blue)
    }

    @Test
    fun exilesACreatureWithToughnessOneEvenWhenItsPowerIsGreaterThanOne() {
        val d = customScenario(WarpingWail, "Savannah Lions")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Savannah Lions")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveColorlessMana(p, 2)

        assertNull(castMode(d, p, wail, 0, listOf(ChosenTarget.Permanent(target))).error)
        d.resolveAll()

        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Savannah Lions"))
        assertFalse(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun cannotTargetACreatureWhosePowerAndToughnessAreBothGreaterThanOne() {
        val d = customScenario(WarpingWail, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveColorlessMana(p, 2)

        assertNotNull(castMode(d, p, wail, 0, listOf(ChosenTarget.Permanent(target))).error)
        assertTrue(d.getHand(p).contains(wail))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun countersASorcerySpellOnAnOpponentsTurn() {
        val d = customScenario(WarpingWail, "Divination")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val sorcery = d.putCardInHand(opponent, "Divination")
        d.giveMana(opponent, Color.BLUE)
        d.giveColorlessMana(opponent, 2)
        assertNull(d.castSpell(opponent, sorcery).error)
        val spellOnStack = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(castMode(d, p, wail, 1, listOf(ChosenTarget.Spell(spellOnStack))).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(opponent).contains(sorcery))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun cannotChooseTheCounterModeForAnInstantSpell() {
        val d = customScenario(WarpingWail, "Shock")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val instant = d.putCardInHand(opponent, "Shock")
        d.giveMana(opponent, Color.RED)
        assertNull(d.castSpell(opponent, instant, listOf(p)).error)
        val spellOnStack = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)
        assertNotNull(castMode(d, p, wail, 1, listOf(ChosenTarget.Spell(spellOnStack))).error)

        assertTrue(d.getHand(p).contains(wail))
        assertEquals(1, d.stackSize)
        assertTrue(d.getStackSpellNames().contains("Shock"))
    }

    @Test
    fun createsAOneOneColorlessEldraziScionThatCanBeSacrificedForColorlessMana() {
        val d = customScenario(WarpingWail)
        val p = d.player1
        val wail = d.putCardInHand(p, "Warping Wail")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)

        assertNull(castMode(d, p, wail, 2).error)
        d.resolveAll()

        val scion = d.permanentsNamed(p, "Eldrazi Scion").single()
        assertTrue(d.state.getEntity(scion)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(scion, "CREATURE"))
        assertEquals(1, d.projected.getPower(scion))
        assertEquals(1, d.projected.getToughness(scion))
        listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)
            .forEach { assertFalse(d.projected.hasColor(scion, it)) }

        val colorlessBefore = d.manaPool(p).colorless
        val sacrificeMana = d.abilityActions(p, scion).single().action
        assertNull(d.submit(sacrificeMana).error)
        assertEquals(colorlessBefore + 1, d.manaPool(p).colorless)
        assertTrue(d.permanentsNamed(p, "Eldrazi Scion").isEmpty())
    }
}
