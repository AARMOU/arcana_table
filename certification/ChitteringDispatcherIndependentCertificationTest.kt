package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChitteringDispatcher
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ChitteringDispatcherIndependentCertificationTest {
    private val name = "Chittering Dispatcher"

    private fun assertSpawn(d: GameTestDriver, spawn: EntityId) {
        assertTrue(d.state.getEntity(spawn)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(spawn, "CREATURE"))
        assertTrue(d.projected.hasSubtype(spawn, "ELDRAZI"))
        assertTrue(d.projected.hasSubtype(spawn, "SPAWN"))
        assertEquals(0, d.projected.getPower(spawn))
        assertEquals(1, d.projected.getToughness(spawn))
        assertTrue(d.projected.getColors(spawn).isEmpty())
    }

    @Test
    fun castingRequiresTwoGenericAndOneGreenAndDevoidMakesItColorless() {
        val d = customScenario(ChitteringDispatcher)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, card).error)
        assertEquals(0, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue(d.projected.getColors(card).isEmpty())
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "ELDRAZI"))
        assertTrue(d.projected.hasSubtype(card, "DRONE"))
        assertEquals(2, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
    }

    @Test
    fun threeColorlessManaCannotPayTheGreenSymbol() {
        val d = customScenario(ChitteringDispatcher)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(3, d.manaPool(p).colorless)
    }

    @Test
    fun myriadCreatesCopiesAttackingEachOtherOpponentAndTheirDepartureCreatesSpawns() {
        val d = customScenario(ChitteringDispatcher)
        val players = d.initMultiplayer(decks = List(4) { Deck.of("Plains" to 40) })
        val p = players[0]
        val defender = players[1]
        val otherOpponents = players.drop(2)
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        d.removeSummoningSickness(dispatcher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)

        assertNull(d.declareAttackers(p, listOf(dispatcher), defender).error)
        d.resolveAll()

        val copies = d.permanentsNamed(p, name).filter {
            d.state.getEntity(it)!!.has<TokenComponent>()
        }
        assertEquals(2, copies.size)
        copies.forEach {
            assertTrue(d.state.getEntity(it)!!.has<TappedComponent>())
            assertTrue(d.projected.getColors(it).isEmpty())
            assertEquals(2, d.projected.getPower(it))
            assertEquals(3, d.projected.getToughness(it))
        }

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = p)

        assertEquals(18, d.getLifeTotal(defender))
        otherOpponents.forEach { assertEquals(18, d.getLifeTotal(it)) }
        assertEquals(listOf(dispatcher), d.permanentsNamed(p, name))
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Spawn").size)
    }

    @Test
    fun myriadDoesNotTriggerWhenDispatcherDoesNotAttack() {
        val d = customScenario(ChitteringDispatcher)
        val p = d.player1
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        d.removeSummoningSickness(dispatcher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, emptyList(), d.player2).error)
        d.resolveAll()

        assertEquals(listOf(dispatcher), d.permanentsNamed(p, name))
        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").isEmpty())
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun leavingTheBattlefieldForTheGraveyardCreatesAColorlessZeroOneEldraziSpawn() {
        val d = customScenario(ChitteringDispatcher, "Murder")
        val p = d.player1
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(dispatcher))).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        val spawn = d.permanentsNamed(p, "Eldrazi Spawn").single()
        assertSpawn(d, spawn)
    }

    @Test
    fun leavingTheBattlefieldForExileAlsoCreatesAnEldraziSpawn() {
        val d = customScenario(ChitteringDispatcher, "Swords to Plowshares")
        val p = d.player1
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        val swords = d.putCardInHand(p, "Swords to Plowshares")
        d.giveMana(p, Color.WHITE)

        assertNull(d.castSpell(p, swords, listOf(dispatcher)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertSpawn(d, d.permanentsNamed(p, "Eldrazi Spawn").single())
    }

    @Test
    fun sacrificingAnEldraziSpawnAddsOneColorlessMana() {
        val d = customScenario(ChitteringDispatcher, "Murder")
        val p = d.player1
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(dispatcher))).error)
        d.resolveAll()
        val spawn = d.permanentsNamed(p, "Eldrazi Spawn").single()
        assertSpawn(d, spawn)
        assertEquals(0, d.manaPool(p).colorless)

        assertNull(d.submit(d.abilityActions(p, spawn).single().action).error)

        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").isEmpty())
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun onlyTheSpawnControllerCanActivateItsSacrificeAbility() {
        val d = customScenario(ChitteringDispatcher, "Murder")
        val p = d.player1
        val opponent = d.player2
        val dispatcher = d.putCreatureOnBattlefield(p, name)
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(dispatcher))).error)
        d.resolveAll()
        val spawn = d.permanentsNamed(p, "Eldrazi Spawn").single()
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertEquals(opponent, d.activePlayer)
        val colorlessBefore = d.manaPool(opponent).colorless

        assertNotNull(d.activate(opponent, spawn).error)

        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").contains(spawn))
        assertEquals(colorlessBefore, d.manaPool(opponent).colorless)
    }
}
