package com.arcanatable.customcards

import com.arcanatable.customcards.cards.InquisitorGreyfax
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class InquisitorGreyfaxScenarioTest {
    @Test
    fun wisdomAffectsOnlyOtherCreaturesYouControlAndFollowsSourceLeaving() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertEquals(3, d.projected.getPower(greyfax))
        assertEquals(3, d.projected.getToughness(greyfax))
        assertTrue(d.projected.hasKeyword(greyfax, Keyword.VIGILANCE))
        assertEquals(3, d.projected.getPower(ally))
        assertEquals(2, d.projected.getToughness(ally))
        assertTrue(d.projected.hasKeyword(ally, Keyword.VIGILANCE))
        assertEquals(2, d.projected.getPower(enemy))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.VIGILANCE))
        d.moveToGraveyard(greyfax)
        assertEquals(2, d.projected.getPower(ally))
        assertFalse(d.projected.hasKeyword(ally, Keyword.VIGILANCE))
    }

    @Test
    fun huntTapsAnEnemyAndCreatesAClueThatCanBeSacrificedToDraw() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        d.removeSummoningSickness(greyfax)
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Inquisitor Greyfax")!!.script.activatedAbilities.single().id
        d.giveColorlessMana(p, 1)
        assertNull(d.submit(ActivateAbility(p, greyfax, ability, targets = listOf(ChosenTarget.Permanent(enemy)))).error)
        assertTrue(d.isTapped(greyfax))
        assertFalse(d.isTapped(enemy))
        d.resolveAll()
        assertTrue(d.isTapped(enemy))
        val clue = d.getPermanents(p).single { d.getCardName(it)?.contains("Clue") == true }
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 2)
        val sacrifice = d.abilityActions(p, clue).single().action as ActivateAbility
        assertNull(d.submit(sacrifice).error)
        d.resolveAll()
        assertFalse(clue in d.getPermanents(p))
        assertEquals(before + 1, d.getHand(p).size)
    }

    @Test
    fun huntCannotTargetYourOwnCreature() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        d.removeSummoningSickness(greyfax)
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Inquisitor Greyfax")!!.script.activatedAbilities.single().id
        d.giveColorlessMana(p, 1)
        assertNotNull(d.submit(ActivateAbility(p, greyfax, ability, targets = listOf(ChosenTarget.Permanent(ally)))).error)
        assertFalse(d.isTapped(greyfax))
    }

    @Test
    fun losingTheOnlyTargetBeforeResolutionAlsoPreventsInvestigating() {
        val d = customScenario(InquisitorGreyfax, "Grizzly Bears")
        val p = d.player1
        val greyfax = d.putCreatureOnBattlefield(p, "Inquisitor Greyfax")
        d.removeSummoningSickness(greyfax)
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Inquisitor Greyfax")!!.script.activatedAbilities.single().id
        d.giveColorlessMana(p, 1)
        assertNull(d.submit(ActivateAbility(p, greyfax, ability, targets = listOf(ChosenTarget.Permanent(enemy)))).error)
        d.moveToGraveyard(enemy)
        d.resolveAll()
        assertTrue(d.getPermanents(p).none { d.getCardName(it)?.contains("Clue") == true })
    }
}
