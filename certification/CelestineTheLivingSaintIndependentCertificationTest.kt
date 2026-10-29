package com.arcanatable.customcards

// Written from WotC 40K Release Notes, including both Healing Tears rulings.
// https://magic.wizards.com/en/news/feature/warhammer-40000-commander-release-notes-2022-09-19
// No worker implementation or scenario source was read when authoring these checks.
import com.arcanatable.customcards.cards.CelestineTheLivingSaint
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class CelestineTheLivingSaintIndependentCertificationTest {
    private fun gainFour(d: GameTestDriver) {
        val missionary = d.putCardInHand(d.player1, "Lone Missionary")
        d.giveMana(d.player1, Color.WHITE)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, missionary).error)
        d.resolveAll()
        assertEquals(24, d.getLifeTotal(d.player1))
    }

    @Test
    fun costsFourAndWhiteAndActuallyHasFlyingAndLifelinkInCombat() {
        val d = customScenario(CelestineTheLivingSaint)
        val card = d.putCardInHand(d.player1, "Celestine, the Living Saint")
        d.giveColorlessMana(d.player1, 5)
        assertNotNull(d.castSpell(d.player1, card).error)
        d.giveMana(d.player1, Color.WHITE)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        val saint = d.findPermanent(d.player1, "Celestine, the Living Saint")!!
        assertTrue(d.projected.hasKeyword(saint, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(saint, Keyword.LIFELINK))
        d.removeSummoningSickness(saint)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(saint), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(23, d.getLifeTotal(d.player1))
        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun zeroLifeGainedStillReturnsAManaValueZeroCreature() {
        val d = customScenario(CelestineTheLivingSaint, "Ornithopter", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        val zero = d.putCardInGraveyard(d.player1, "Ornithopter")
        val tooBig = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        d.passPriorityUntil(Step.END)
        d.resolveAll(Choices(targets = listOf(zero)))
        assertTrue(d.getPermanents(d.player1).contains(zero))
        assertTrue(d.getGraveyard(d.player1).contains(tooBig))
    }

    @Test
    fun usesGrossLifeGainedRatherThanNetLifeChangeAtTheExactManaValueBoundary() {
        val d = customScenario(CelestineTheLivingSaint, "Lone Missionary", "Shock", "Giant Spider", "Platinum Angel")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        val four = d.putCardInGraveyard(d.player1, "Giant Spider")
        val seven = d.putCardInGraveyard(d.player1, "Platinum Angel")
        gainFour(d)
        val shock = d.putCardInHand(d.player1, "Shock")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, shock, listOf(d.player1)).error)
        d.resolveAll()
        assertEquals(22, d.getLifeTotal(d.player1))
        d.passPriorityUntil(Step.END)
        d.resolveAll(Choices(targets = listOf(four)))
        assertTrue(d.getPermanents(d.player1).contains(four))
        assertTrue(d.getGraveyard(d.player1).contains(seven))
    }

    @Test
    fun cannotReturnANoncreatureOrAnOpponentsCreature() {
        val d = customScenario(CelestineTheLivingSaint, "Lone Missionary", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        val land = d.putCardInGraveyard(d.player1, "Plains")
        val opponent = d.putCardInGraveyard(d.player2, "Grizzly Bears")
        gainFour(d)
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player1).contains(land))
        assertTrue(d.getGraveyard(d.player2).contains(opponent))
        assertTrue(d.getPermanents(d.player1).none { it == land || it == opponent })
    }

    @Test
    fun onlyTriggersOnControllersEndStep() {
        val d = customScenario(CelestineTheLivingSaint, "Ornithopter")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val zero = d.putCardInGraveyard(d.player1, "Ornithopter")
        d.passPriorityUntil(Step.END, activePlayer = d.player2)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player1).contains(zero))
        assertFalse(d.getPermanents(d.player1).contains(zero))
        d.passPriorityUntil(Step.END, activePlayer = d.player1)
        d.resolveAll(Choices(targets = listOf(zero)))
        assertTrue(d.getPermanents(d.player1).contains(zero))
    }

    @Test
    fun lifeGainFromAPreviousTurnDoesNotPermitALargerTarget() {
        val d = customScenario(CelestineTheLivingSaint, "Lone Missionary", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        gainFour(d)
        // No creatures in our graveyard on the life-gain turn.
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val bear = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        d.passPriorityUntil(Step.END, activePlayer = d.player1)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player1).contains(bear))
        assertFalse(d.getPermanents(d.player1).contains(bear))
    }

    @Test
    fun targetIsBoundBeforeLaterLifeGainAndTheHigherValueCardIsNotOffered() {
        val d = customScenario(CelestineTheLivingSaint, "Soulmender", "Savannah Lions", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Celestine, the Living Saint")
        val soulmender = d.putCreatureOnBattlefield(d.player1, "Soulmender")
        d.removeSummoningSickness(soulmender)
        assertNull(d.activate(d.player1, soulmender).error)
        d.resolveAll()
        val one = d.putCardInGraveyard(d.player1, "Savannah Lions")
        val two = d.putCardInGraveyard(d.player1, "Grizzly Bears")
        d.passPriorityUntil(Step.END)
        val choice = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("Healing Tears must choose its target before resolution", choice)
        val offered = choice!!.legalTargets.values.flatten()
        assertTrue(offered.contains(one))
        assertFalse(offered.contains(two))
        assertNull(d.submitTargetSelection(d.player1, listOf(one)).error)
        d.untapPermanent(soulmender)
        assertNull(d.activate(d.player1, soulmender).error)
        d.resolveAll(Choices(targets = listOf(two)))
        assertEquals(22, d.getLifeTotal(d.player1))
        assertTrue(d.getPermanents(d.player1).contains(one))
        assertTrue(d.getGraveyard(d.player1).contains(two))
    }
}
