package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Reverberate
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ReverberateIndependentCertificationTest {
    @Test
    fun copiesAnInstantAndCanChooseANewTarget() {
        val d = customScenario(Reverberate, "Shock")
        val p = d.player1
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        val original = d.getTopOfStack()!!
        val reverberate = d.putCardInHand(p, "Reverberate")
        d.giveMana(p, Color.RED, 2)

        assertNull(d.castSpellWithTargets(p, reverberate, listOf(ChosenTarget.Spell(original))).error)
        assertEquals(0, d.manaPool(p).red)
        d.resolveAll(Choices(targets = listOf(p)))

        assertEquals(18, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(d.player2))
        assertTrue(d.getGraveyardCardNames(p).containsAll(listOf("Shock", "Reverberate")))
    }

    @Test
    fun copiesAnOpponentsSorceryAtInstantSpeed() {
        val d = customScenario(Reverberate, "Divination")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val divination = d.putCardInHand(opponent, "Divination")
        d.giveMana(opponent, Color.BLUE)
        d.giveColorlessMana(opponent, 2)
        assertNull(d.castSpell(opponent, divination).error)
        val original = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)

        val reverberate = d.putCardInHand(p, "Reverberate")
        d.giveMana(p, Color.RED, 2)
        assertNull(d.castSpellWithTargets(p, reverberate, listOf(ChosenTarget.Spell(original))).error)
        val pHandAfterCast = d.getHandSize(p)
        val opponentHandAfterCast = d.getHandSize(opponent)
        d.resolveAll()

        assertEquals(pHandAfterCast + 2, d.getHandSize(p))
        assertEquals(opponentHandAfterCast + 2, d.getHandSize(opponent))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Divination"))
        assertTrue(d.getGraveyardCardNames(p).contains("Reverberate"))
    }

    @Test
    fun requiresTwoRedManaToCast() {
        val d = customScenario(Reverberate, "Shock")
        val p = d.player1
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        val original = d.getTopOfStack()!!
        val reverberate = d.putCardInHand(p, "Reverberate")
        d.giveMana(p, Color.RED)

        assertNotNull(d.castSpellWithTargets(p, reverberate, listOf(ChosenTarget.Spell(original))).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Reverberate"))
        assertEquals(1, d.manaPool(p).red)
        assertEquals(listOf("Shock"), d.getStackSpellNames())

        d.giveMana(p, Color.RED)
        assertNull(d.castSpellWithTargets(p, reverberate, listOf(ChosenTarget.Spell(original))).error)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(d.player2))
        assertTrue(d.getGraveyardCardNames(p).contains("Reverberate"))
    }

    @Test
    fun cannotTargetACreatureSpell() {
        val d = customScenario(Reverberate, "Grizzly Bears")
        val p = d.player1
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bears).error)
        val creatureSpell = d.getTopOfStack()!!
        val reverberate = d.putCardInHand(p, "Reverberate")
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpellWithTargets(p, reverberate, listOf(ChosenTarget.Spell(creatureSpell))).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Reverberate"))
        assertEquals(listOf("Grizzly Bears"), d.getStackSpellNames())
        assertEquals(2, d.manaPool(p).red)
    }
}
