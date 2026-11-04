package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ConvergenceOfDominion
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ConvergenceOfDominionScenarioTest {
    private val toy = card("Convergence Unearth Test") {
        manaCost = "{2}"; typeLine = "Artifact Creature — Construct"; power = 1; toughness = 1
        unearth("{2}")
    }
    private fun game() = customScenario(ConvergenceOfDominion, "Grizzly Bears", "Royal Warden", "Unsummon").apply {
        registerCards(listOf(toy))
        putPermanentOnBattlefield(player1, ConvergenceOfDominion.name)
    }
    private fun GameTestDriver.commander(owner: EntityId): EntityId {
        val id = putCreatureOnBattlefield(owner, "Grizzly Bears")
        replaceState(state.updateEntity(id) { it.with(CommanderComponent(owner)) })
        return id
    }
    @Test fun reducesGraveyardAbilityOnlyWhileYouControlYourCommander() {
        val d = game(); val p = d.player1
        val id = d.putCardInGraveyard(p, toy.name)
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, id).error)
        d.commander(p)
        assertNull(d.activate(p, id).error); d.resolveAll()
        assertNotNull(d.findPermanent(p, toy.name))
    }
    @Test fun floorKeepsAOneManaPayment() {
        val d = game(); val p = d.player1
        d.commander(p)
        val id = d.putCardInGraveyard(p, toy.name)
        assertNotNull(d.activate(p, id).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, id).error); d.resolveAll()
    }
    @Test fun opponentsCommanderDoesNotSwitchOnTheReduction() {
        val d = game(); val p = d.player1
        d.commander(d.player2)
        val id = d.putCardInGraveyard(p, toy.name)
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, id).error)
        assertTrue(toy.name in d.zoneNames(p, Zone.GRAVEYARD))
    }
    @Test fun coloredPipsRemainDueOnRoyalWarden() {
        val d = game(); val p = d.player1
        d.commander(p)
        val warden = d.putCardInGraveyard(p, "Royal Warden")
        d.giveColorlessMana(p, 2)
        assertNotNull(d.activate(p, warden).error)
        d.giveMana(p, Color.BLACK)
        assertNull(d.activate(p, warden).error); d.resolveAll()
        assertNotNull(d.findPermanent(p, "Royal Warden"))
    }
    @Test fun battlefieldAbilityIsNotDiscountedAndMillsThree() {
        val d = game(); val p = d.player1
        d.commander(p)
        val source = d.findPermanent(p, ConvergenceOfDominion.name)!!
        val before = d.zoneNames(p, Zone.LIBRARY).size
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, source).error)
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, source).error); d.resolveAll()
        assertEquals(before - 3, d.zoneNames(p, Zone.LIBRARY).size)
        assertTrue(d.isTapped(source))
    }
    @Test fun returningYourCommanderToHandTurnsTheReductionOff() {
        val d = game(); val p = d.player1
        val commander = d.commander(p)
        val bounce = d.putCardInHand(p, "Unsummon")
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, bounce, listOf(commander)).error); d.resolveAll()
        val id = d.putCardInGraveyard(p, toy.name)
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, id).error)
    }
    @Test fun controllingSomeoneElsesCommanderDoesNotEnableTheReduction() {
        val d = game(); val p = d.player1
        val stolen = d.commander(d.player2)
        d.replaceState(d.state.updateEntity(stolen) {
            it.with(com.wingedsheep.engine.state.components.identity.ControllerComponent(p))
        })
        val id = d.putCardInGraveyard(p, toy.name)
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, id).error)
    }

}
