package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArmoryAutomaton
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ArmoryAutomatonScenarioTest {
    private fun GameTestDriver.attachedTo(id: com.wingedsheep.sdk.model.EntityId) =
        state.getEntity(id)?.get<AttachedToComponent>()?.targetId

    private fun setup() = customScenario(ArmoryAutomaton, "Swiftfoot Boots", "Short Sword")

    @Test
    fun enteringAttachesAnyNumberOfEquipmentIncludingOpponentsWithoutChangingControl() {
        val d = setup()
        val p = d.player1
        val mine = d.putPermanentOnBattlefield(p, "Swiftfoot Boots")
        val theirs = d.putPermanentOnBattlefield(d.player2, "Short Sword")
        val card = d.putCardInHand(p, "Armory Automaton")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(yes = true, targets = listOf(mine, theirs)))

        assertEquals(card, d.attachedTo(mine))
        assertEquals(card, d.attachedTo(theirs))
        assertEquals(p, d.getController(mine))
        assertEquals(d.player2, d.getController(theirs))
    }

    @Test
    fun enteringWithOnlyOneEquipmentChosenAttachesOnlyThatOne() {
        val d = setup()
        val p = d.player1
        val mine = d.putPermanentOnBattlefield(p, "Swiftfoot Boots")
        val other = d.putPermanentOnBattlefield(p, "Short Sword")
        val card = d.putCardInHand(p, "Armory Automaton")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(yes = true, targets = listOf(mine)))

        assertEquals(card, d.attachedTo(mine))
        assertNull(d.attachedTo(other))
    }

    @Test
    fun decliningTheMayAttachesNothing() {
        val d = setup()
        val p = d.player1
        val mine = d.putPermanentOnBattlefield(p, "Swiftfoot Boots")
        val card = d.putCardInHand(p, "Armory Automaton")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(yes = false, targets = listOf(mine)))

        assertNull(d.attachedTo(mine))
    }

    @Test
    fun enteringWithNoEquipmentDoesNothing() {
        val d = setup()
        val p = d.player1
        val card = d.putCardInHand(p, "Armory Automaton")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        d.assertPermanentExists(p, "Armory Automaton")
    }

    @Test
    fun attackingAttachesEquipmentAndDecliningDoesNot() {
        val d = setup()
        val p = d.player1
        val automaton = d.putCreatureOnBattlefield(p, "Armory Automaton")
        val other = d.putCreatureOnBattlefield(p, "Armory Automaton")
        val boots = d.putPermanentOnBattlefield(p, "Swiftfoot Boots")
        val sword = d.putPermanentOnBattlefield(p, "Short Sword")
        d.removeSummoningSickness(automaton)
        d.removeSummoningSickness(other)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(automaton), d.player2).error)
        d.resolveAll(Choices(yes = false, targets = listOf(boots, sword)))
        assertNull(d.attachedTo(boots))
        assertNull(d.attachedTo(sword))
    }

    @Test
    fun attackingMovesEquipmentFromAnotherCreature() {
        val d = setup()
        val p = d.player1
        val automaton = d.putCreatureOnBattlefield(p, "Armory Automaton")
        val other = d.putCreatureOnBattlefield(p, "Armory Automaton")
        val boots = d.putPermanentOnBattlefield(p, "Swiftfoot Boots")
        val sword = d.putPermanentOnBattlefield(p, "Short Sword")
        d.removeSummoningSickness(automaton)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(automaton), d.player2).error)
        d.resolveAll(Choices(yes = true, targets = listOf(boots, sword)))
        assertEquals(automaton, d.attachedTo(boots))
        assertEquals(automaton, d.attachedTo(sword))
        assertNotEquals(other, d.attachedTo(boots))
    }
}
