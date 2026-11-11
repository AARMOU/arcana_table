package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NecronMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class NecronMonolithIndependentCertificationTest {
    @Test fun sevenColorlessManaCastsTheSevenSevenArtifactVehicle() {
        val d = customScenario(NecronMonolith)
        val p = d.player1
        val monolith = d.putCardInHand(p, "Necron Monolith")
        d.giveColorlessMana(p, 7)
        assertNull(d.castSpell(p, monolith).error)
        d.resolveAll()

        val permanent = d.permanentsNamed(p, "Necron Monolith").single()
        val card = d.state.getEntity(permanent)!!.get<CardComponent>()!!
        assertTrue(card.typeLine.isArtifact)
        assertFalse(card.typeLine.isCreature)
        assertEquals(7, card.baseStats!!.power)
        assertEquals(7, card.baseStats!!.toughness)
    }

    @Test fun sixManaCannotCastNecronMonolith() {
        val d = customScenario(NecronMonolith)
        val p = d.player1
        val monolith = d.putCardInHand(p, "Necron Monolith")
        d.giveColorlessMana(p, 6)
        assertNotNull(d.castSpell(p, monolith).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Necron Monolith"))
        assertTrue(d.permanentsNamed(p, "Necron Monolith").isEmpty())
    }

    @Test fun crewRequiresFourTotalPower() {
        val d = customScenario(NecronMonolith, "Grizzly Bears")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Necron Monolith")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertNotNull(d.activate(p, monolith).error)

        assertFalse(d.isTapped(bear))
        assertTrue(d.getGraveyardCardNames(p).isEmpty())
    }

    @Test fun creaturesControlledByAnOpponentCannotCrewTheVehicle() {
        val d = customScenario(NecronMonolith, "Grizzly Bears")
        val monolith = d.putPermanentOnBattlefield(d.player1, "Necron Monolith")
        val bear1 = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val bear2 = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertNotNull(d.activate(d.player1, monolith).error)

        assertFalse(d.isTapped(bear1))
        assertFalse(d.isTapped(bear2))
    }

    @Test fun crewMakesTheVehicleAnAttackerOnlyUntilEndOfTurn() {
        val d = customScenario(NecronMonolith, "Grizzly Bears")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Necron Monolith")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertNull(d.activate(p, monolith).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p)
        assertNotNull(d.declareAttackers(p, listOf(monolith), d.player2).error)
    }

    @Test fun attackingMillsThreeAndCreatesOneTwoTwoBlackArtifactWarriorPerCreature() {
        val d = customScenario(NecronMonolith, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Necron Monolith")
        val bear1 = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bear2 = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        assertNull(d.activate(p, monolith).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        assertTrue(d.isTapped(bear1))
        assertTrue(d.isTapped(bear2))
        assertTrue(d.getGraveyardCardNames(p).isEmpty())

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(monolith), d.player2).error)
        d.passPriorityUntil(Step.END)

        val graveyard = d.getGraveyardCardNames(p)
        assertEquals(3, graveyard.size)
        assertEquals(2, graveyard.count { it == "Grizzly Bears" })
        assertEquals(1, graveyard.count { it == "Sol Ring" })
        val warriors = d.permanentsNamed(p, "Necron Warrior")
        assertEquals(2, warriors.size)
        warriors.forEach { warrior ->
            val card = d.state.getEntity(warrior)!!.get<CardComponent>()!!
            assertTrue(card.typeLine.isArtifact)
            assertTrue(card.typeLine.isCreature)
            assertEquals(2, card.baseStats!!.power)
            assertEquals(2, card.baseStats!!.toughness)
            assertEquals(setOf(Color.BLACK), card.colors)
        }
        d.assertLifeTotal(d.player2, 13)
    }

    @Test fun millingOnlyNoncreatureCardsCreatesNoWarriors() {
        val d = customScenario(NecronMonolith, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Necron Monolith")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Plains")
        assertNull(d.activate(p, monolith).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(monolith), d.player2).error)
        d.passPriorityUntil(Step.END)

        assertEquals(3, d.getGraveyardCardNames(p).size)
        assertTrue(d.permanentsNamed(p, "Necron Warrior").isEmpty())
    }

    @Test fun flyingVehicleCannotBeBlockedByAGroundCreature() {
        val d = customScenario(NecronMonolith, "Grizzly Bears")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Necron Monolith")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertNull(d.activate(p, monolith).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(monolith), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(monolith))).error)
        assertFalse(d.isTapped(blocker))
    }

    @Test fun indestructibleVehicleSurvivesADestroyEffect() {
        val d = customScenario(NecronMonolith, "Murder", "Grizzly Bears")
        val monolith = d.putPermanentOnBattlefield(d.player1, "Necron Monolith")
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.activate(d.player1, monolith).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        d.passPriority(d.player1)
        assertNull(d.castSpell(d.player2, d.findCardInHand(d.player2, "Murder")!!, listOf(monolith)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player1, "Necron Monolith").contains(monolith))
        assertFalse(d.getGraveyardCardNames(d.player1).contains("Necron Monolith"))
    }
}
