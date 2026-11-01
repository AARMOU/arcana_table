package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArmoryAutomaton
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ArmoryAutomatonIndependentCertificationTest {
    private fun assertAttachedTo(d: GameTestDriver, equipment: EntityId, creature: EntityId) {
        assertEquals(creature, d.state.getEntity(equipment)?.get<AttachedToComponent>()?.targetId)
    }

    @Test
    fun castingRequiresThreeColorlessMana() {
        val d = customScenario(ArmoryAutomaton)
        val player = d.player1
        val card = d.putCardInHand(player, "Armory Automaton")
        d.giveColorlessMana(player, 2)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Armory Automaton"))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val automaton = d.findPermanent(player, "Armory Automaton")
        assertNotNull(automaton)
        assertEquals(2, d.projected.getPower(automaton!!))
        assertEquals(2, d.projected.getToughness(automaton))
    }

    @Test
    fun enteringAttachesAnyNumberOfEquipmentWithoutChangingTheirControllers() {
        val d = customScenario(ArmoryAutomaton, "Bonesplitter", "Cranial Plating")
        val player = d.player1
        val opponent = d.player2
        val bonesplitter = d.putPermanentOnBattlefield(player, "Bonesplitter")
        val plating = d.putPermanentOnBattlefield(opponent, "Cranial Plating")
        val card = d.putCardInHand(player, "Armory Automaton")
        d.giveColorlessMana(player, 3)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(targets = listOf(bonesplitter, plating)))

        assertAttachedTo(d, bonesplitter, card)
        assertAttachedTo(d, plating, card)
        assertEquals(player, d.getController(bonesplitter))
        assertEquals(opponent, d.getController(plating))
        assertTrue(d.getPermanents(player).contains(bonesplitter))
        assertTrue(d.getPermanents(opponent).contains(plating))
    }

    @Test
    fun decliningTheEnteringTriggerLeavesEquipmentUnattached() {
        val d = customScenario(ArmoryAutomaton, "Bonesplitter")
        val player = d.player1
        val equipment = d.putPermanentOnBattlefield(player, "Bonesplitter")
        val card = d.putCardInHand(player, "Armory Automaton")
        d.giveColorlessMana(player, 3)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(yes = false))

        assertNull(d.state.getEntity(equipment)?.get<AttachedToComponent>())
        assertEquals(2, d.projected.getPower(card))
        assertTrue(d.permanentsNamed(player, "Armory Automaton").contains(card))
    }

    @Test
    fun attackingTriggersAnOptionalEquipmentAttachment() {
        val d = customScenario(ArmoryAutomaton, "Bonesplitter")
        val player = d.player1
        val equipment = d.putPermanentOnBattlefield(player, "Bonesplitter")
        val automaton = d.putCreatureOnBattlefield(player, "Armory Automaton")
        d.removeSummoningSickness(automaton)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(automaton), d.player2).error)
        d.resolveAll(Choices(targets = listOf(equipment)))

        assertAttachedTo(d, equipment, automaton)
    }

    @Test
    fun aNonEquipmentCannotBeChosenAsAnAttackTriggerTarget() {
        val d = customScenario(ArmoryAutomaton, "Bonesplitter", "Grizzly Bears")
        val player = d.player1
        val equipment = d.putPermanentOnBattlefield(player, "Bonesplitter")
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val automaton = d.putCreatureOnBattlefield(player, "Armory Automaton")
        d.removeSummoningSickness(automaton)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(automaton), d.player2).error)
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("the attack trigger must choose its targets before resolution", decision)
        val legalTargets = decision!!.legalTargets.values.flatten()
        assertTrue(legalTargets.contains(equipment))
        assertFalse(legalTargets.contains(creature))
        assertNotNull(d.submitTargetSelection(player, listOf(creature)).error)
        assertNull(d.submitTargetSelection(player, listOf(equipment)).error)
        d.resolveAll()

        assertAttachedTo(d, equipment, automaton)
        assertNull(d.state.getEntity(creature)?.get<AttachedToComponent>())
    }
}
