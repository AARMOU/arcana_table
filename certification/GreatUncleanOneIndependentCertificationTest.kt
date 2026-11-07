package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GreatUncleanOne
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class GreatUncleanOneIndependentCertificationTest {
    private val name = "Great Unclean One"

    private fun threePlayerScenario(vararg libraryCards: String): Pair<GameTestDriver, List<EntityId>> {
        val d = customScenario(GreatUncleanOne, *libraryCards)
        val players = d.initMultiplayer(decks = List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d to players
    }

    private fun GameTestDriver.tokens(player: EntityId): List<EntityId> =
        getPermanents(player).filter { state.getEntity(it)?.has<TokenComponent>() == true }

    private fun shock(d: GameTestDriver, caster: EntityId, target: EntityId) {
        val shock = d.putCardInHand(caster, "Shock")
        d.giveMana(caster, Color.RED)
        assertNull(d.castSpellWithTargets(caster, shock, listOf(ChosenTarget.Player(target))).error)
        d.resolveAll()
    }

    private fun assertPlaguebearer(d: GameTestDriver, token: EntityId) {
        assertTrue(d.state.getEntity(token)!!.has<TokenComponent>())
        assertEquals("Plaguebearer of Nurgle", d.getCardName(token))
        assertTrue(d.projected.hasType(token, "CREATURE"))
        assertTrue(d.projected.hasSubtype(token, "DEMON"))
        assertTrue(d.projected.hasColor(token, Color.BLACK))
        assertEquals(1, d.projected.getColors(token).size)
        assertEquals(1, d.projected.getPower(token))
        assertEquals(3, d.projected.getToughness(token))
    }

    @Test
    fun castingRequiresFourGenericAndBlackAndEntersAsABlackFourFiveDemon() {
        val missingBlack = customScenario(GreatUncleanOne)
        val withoutBlack = missingBlack.putCardInHand(missingBlack.player1, name)
        missingBlack.giveColorlessMana(missingBlack.player1, 5)
        assertNotNull(missingBlack.castSpell(missingBlack.player1, withoutBlack).error)
        assertTrue(missingBlack.getHand(missingBlack.player1).contains(withoutBlack))

        val d = customScenario(GreatUncleanOne)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 3)
        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(card))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "DEMON"))
        assertTrue(d.projected.hasColor(card, Color.BLACK))
        assertEquals(4, d.projected.getPower(card))
        assertEquals(5, d.projected.getToughness(card))
    }

    @Test
    fun controllerEndStepMakesEachOpponentLoseTwoAndCreatesTokensOnlyForThoseBelowYourLife() {
        val (d, players) = threePlayerScenario("Shock")
        val controller = players[0]
        val opponentBelow = players[1]
        val opponentEqualAfterLoss = players[2]
        shock(d, controller, controller)
        shock(d, controller, opponentBelow)

        assertEquals(18, d.getLifeTotal(controller))
        assertEquals(18, d.getLifeTotal(opponentBelow))
        assertEquals(20, d.getLifeTotal(opponentEqualAfterLoss))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponentBelow)
        d.putCreatureOnBattlefield(controller, name)
        d.passPriorityUntil(Step.END, activePlayer = opponentBelow)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(opponentBelow))
        assertEquals(20, d.getLifeTotal(opponentEqualAfterLoss))
        assertTrue(d.tokens(controller).isEmpty())

        d.passPriorityUntil(Step.END, activePlayer = controller)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(opponentBelow))
        assertEquals(18, d.getLifeTotal(opponentEqualAfterLoss))
        val tokens = d.tokens(controller)
        assertEquals(1, tokens.size)
        assertPlaguebearer(d, tokens.single())
        assertTrue(d.tokens(opponentBelow).isEmpty())
        assertTrue(d.tokens(opponentEqualAfterLoss).isEmpty())
    }

    @Test
    fun opponentsAtYourLifeAfterLosingTwoDoNotCreateTokens() {
        val (d, players) = threePlayerScenario("Shock")
        val controller = players[0]
        val opponentOne = players[1]
        val opponentTwo = players[2]
        shock(d, controller, controller)
        d.putCreatureOnBattlefield(controller, name)

        d.passPriorityUntil(Step.END, activePlayer = controller)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(controller))
        assertEquals(18, d.getLifeTotal(opponentOne))
        assertEquals(18, d.getLifeTotal(opponentTwo))
        assertTrue(d.tokens(controller).isEmpty())
        assertTrue(d.tokens(opponentOne).isEmpty())
        assertTrue(d.tokens(opponentTwo).isEmpty())
    }
}
