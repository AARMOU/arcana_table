package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArmoryAutomaton
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ArmoryAutomatonScenarioTest {
    private fun cast(d: GameTestDriver, choices: Choices): Pair<GameTestDriver, com.wingedsheep.sdk.model.EntityId> {
        val p = d.player1
        val card = d.putCardInHand(p, "Armory Automaton")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll(choices)
        return d to card
    }

    private fun attached(d: GameTestDriver, equipment: com.wingedsheep.sdk.model.EntityId) =
        d.state.getEntity(equipment)!!.get<AttachedToComponent>()?.targetId

    @Test
    fun entersAndAttachesEveryChosenEquipmentIncludingOpponentsWithoutChangingControl() {
        val d = customScenario(ArmoryAutomaton, "Swiftfoot Boots", "Bonesplitter")
        val mine = d.putPermanentOnBattlefield(d.player1, "Swiftfoot Boots")
        val theirs = d.putPermanentOnBattlefield(d.player2, "Bonesplitter")
        val (_, automaton) = cast(d, Choices(yes = true, targets = listOf(mine, theirs)))
        val onField = d.getCreatures(d.player1).first { d.getCardName(it) == "Armory Automaton" }

        assertEquals(onField, attached(d, mine))
        assertEquals(onField, attached(d, theirs))
        assertEquals(d.player2, d.state.getEntity(theirs)!!.get<ControllerComponent>()!!.playerId)
        assertNotNull(automaton)
    }

    @Test
    fun attachesOnlyTheChosenSubset() {
        val d = customScenario(ArmoryAutomaton, "Swiftfoot Boots", "Bonesplitter")
        val mine = d.putPermanentOnBattlefield(d.player1, "Swiftfoot Boots")
        val other = d.putPermanentOnBattlefield(d.player1, "Bonesplitter")
        cast(d, Choices(yes = true, targets = listOf(mine)))

        assertNotNull(attached(d, mine))
        assertNull(attached(d, other))
    }

    @Test
    fun decliningTheMayAttachesNothing() {
        val d = customScenario(ArmoryAutomaton, "Swiftfoot Boots")
        val mine = d.putPermanentOnBattlefield(d.player1, "Swiftfoot Boots")
        cast(d, Choices(yes = false, targets = listOf(mine)))

        assertNull(attached(d, mine))
    }

    @Test
    fun entersWithNoEquipmentAndNothingHappens() {
        val d = customScenario(ArmoryAutomaton)
        cast(d, Choices())

        assertEquals(1, d.getCreatures(d.player1).count { d.getCardName(it) == "Armory Automaton" })
    }

    @Test
    fun attackingAttachesEquipmentAgain() {
        val d = customScenario(ArmoryAutomaton, "Swiftfoot Boots")
        val automaton = d.putCreatureOnBattlefield(d.player1, "Armory Automaton")
        val boots = d.putPermanentOnBattlefield(d.player1, "Swiftfoot Boots")
        d.removeSummoningSickness(automaton)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(automaton), d.player2).error)
        d.resolveAll(Choices(yes = true, targets = listOf(boots)))

        assertEquals(automaton, attached(d, boots))
    }
}
