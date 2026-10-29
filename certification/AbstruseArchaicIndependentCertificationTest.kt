package com.arcanatable.customcards

// Written from WotC Commander Masters Release Notes, not the worker implementation/tests.
// https://magic.wizards.com/en/news/feature/commander-masters-release-notes
import com.arcanatable.customcards.cards.AbstruseArchaic
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AbstruseArchaicIndependentCertificationTest {
    private fun copy(d: GameTestDriver, archaic: EntityId, target: EntityId) = d.submit(
        ActivateAbility(d.player1, archaic,
            d.cardRegistry.getCard("Abstruse Archaic")!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Spell(target))),
    )

    @Test
    fun costsFourAndAttacksWithVigilance() {
        val d = customScenario(AbstruseArchaic)
        val card = d.putCardInHand(d.player1, "Abstruse Archaic")
        d.giveColorlessMana(d.player1, 3)
        assertNotNull(d.castSpell(d.player1, card).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        val archaic = d.findPermanent(d.player1, "Abstruse Archaic")!!
        assertTrue(d.projected.hasKeyword(archaic, Keyword.VIGILANCE))
        d.removeSummoningSickness(archaic)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(archaic), d.player2).error)
        assertFalse(d.isTapped(archaic))
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun copiesColorlessActivatedAbilityWithoutPayingItsSacrificeCostAgain() {
        val d = customScenario(AbstruseArchaic, "Hedron Archive")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val archive = d.putPermanentOnBattlefield(d.player1, "Hedron Archive")
        val hand = d.getHandSize(d.player1)
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.activate(d.player1, archive, 1).error)
        val original = d.getTopOfStack()!!
        assertFalse(d.getPermanents(d.player1).contains(archive))
        assertNull(copy(d, a, original).error)
        d.resolveAll()
        assertEquals(hand + 4, d.getHandSize(d.player1))
        assertTrue(d.getGraveyard(d.player1).contains(archive))
    }

    @Test
    fun copiesAnActualColorlessEnterTheBattlefieldTrigger() {
        val d = customScenario(AbstruseArchaic, "Skyscanner")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val scanner = d.putCardInHand(d.player1, "Skyscanner")
        d.giveColorlessMana(d.player1, 4)
        assertNull(d.castSpell(d.player1, scanner).error)
        assertNull(d.bothPass().error)
        val trigger = d.getTopOfStack()!!
        val hand = d.getHandSize(d.player1)
        assertNull(copy(d, a, trigger).error)
        d.resolveAll()
        assertEquals(hand + 2, d.getHandSize(d.player1))
    }

    @Test
    fun copyMayChooseANewTargetWithoutChangingTheOriginalTarget() {
        val d = customScenario(AbstruseArchaic, "Rod of Ruin")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val rod = d.putPermanentOnBattlefield(d.player1, "Rod of Ruin")
        d.giveColorlessMana(d.player1, 4)
        val ability = d.cardRegistry.getCard("Rod of Ruin")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(d.player1, rod, ability.id,
            targets = listOf(ChosenTarget.Player(d.player2)))).error)
        assertNull(copy(d, a, d.getTopOfStack()!!).error)
        d.resolveAll(Choices(targets = listOf(d.player1)))
        assertEquals(19, d.getLifeTotal(d.player1))
        assertEquals(19, d.getLifeTotal(d.player2))
    }

    @Test
    fun rejectsAnAbilityFromAColoredSource() {
        val d = customScenario(AbstruseArchaic, "Prodigal Pyromancer")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val pyromancer = d.putCreatureOnBattlefield(d.player1, "Prodigal Pyromancer")
        d.removeSummoningSickness(pyromancer)
        val ability = d.cardRegistry.getCard("Prodigal Pyromancer")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(d.player1, pyromancer, ability.id,
            targets = listOf(ChosenTarget.Player(d.player2)))).error)
        val target = d.getTopOfStack()!!
        d.giveColorlessMana(d.player1, 1)
        val before = d.state
        assertNotNull(copy(d, a, target).error)
        assertEquals(before, d.state)
    }

    @Test
    fun rejectsAnOpponentsColorlessAbility() {
        val d = customScenario(AbstruseArchaic, "Hedron Archive")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val archive = d.putPermanentOnBattlefield(d.player2, "Hedron Archive")
        val hand = d.getHandSize(d.player2)
        d.giveColorlessMana(d.player2, 2)
        assertNull(d.passPriority(d.player1).error)
        assertNull(d.activate(d.player2, archive, 1).error)
        val target = d.getTopOfStack()!!
        assertNull(d.passPriority(d.player2).error)
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(copy(d, a, target).error)
        d.resolveAll()
        assertEquals(hand + 2, d.getHandSize(d.player2))
    }

    @Test
    fun rejectsAColorlessSpellAndAManaAbilitySource() {
        val d = customScenario(AbstruseArchaic, "Ornithopter", "Darksteel Ingot")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        d.removeSummoningSickness(a)
        val spell = d.putCardInHand(d.player1, "Ornithopter")
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        assertNotNull(copy(d, a, d.getTopOfStack()!!).error)
        d.resolveAll()
        val ingot = d.putPermanentOnBattlefield(d.player1, "Darksteel Ingot")
        assertNull(d.activate(d.player1, ingot).error)
        assertTrue(d.state.stack.isEmpty())
        assertNotNull(copy(d, a, ingot).error)
    }

    @Test
    fun activationRequiresManaAnUntappedSourceAndNoSummoningSickness() {
        val d = customScenario(AbstruseArchaic, "Skyscanner")
        val a = d.putCreatureOnBattlefield(d.player1, "Abstruse Archaic")
        val scanner = d.putCardInHand(d.player1, "Skyscanner")
        d.giveColorlessMana(d.player1, 3)
        assertNull(d.castSpell(d.player1, scanner).error)
        assertNull(d.bothPass().error)
        val target = d.getTopOfStack()!!
        assertNotNull(copy(d, a, target).error)
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(copy(d, a, target).error)
        d.removeSummoningSickness(a)
        assertNull(copy(d, a, target).error)
        assertTrue(d.isTapped(a))
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(copy(d, a, target).error)
    }
}
