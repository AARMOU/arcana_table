package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CompanyCommander
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class CompanyCommanderIndependentCertificationTest {
    private val name = "Company Commander"

    private fun scenarioWithPlayers(playerCount: Int, vararg libraryCards: String): GameTestDriver =
        customScenario(CompanyCommander, *libraryCards).apply {
            initMultiplayer(decks = List(playerCount) { Deck.of("Plains" to 40) })
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }

    private fun GameTestDriver.castCommander(player: EntityId): EntityId {
        val commander = putCardInHand(player, name)
        giveMana(player, Color.WHITE)
        giveMana(player, Color.BLACK)
        giveColorlessMana(player, 2)
        assertNull(castSpell(player, commander).error)
        resolveAll()
        return commander
    }

    private fun GameTestDriver.soldierTokens(player: EntityId): List<EntityId> =
        getPermanents(player).filter { token ->
            state.getEntity(token)?.has<TokenComponent>() == true &&
                projected.hasType(token, "CREATURE") &&
                projected.hasSubtype(token, "SOLDIER")
        }

    private fun GameTestDriver.attack(
        attackingPlayer: EntityId,
        attacker: EntityId,
        defendingPlayer: EntityId,
    ) {
        removeSummoningSickness(attacker)
        passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = attackingPlayer)
        assertNull(declareAttackers(attackingPlayer, listOf(attacker), defendingPlayer).error)
        resolveAll()
    }

    @Test
    fun castingForTwoGenericWhiteAndBlackCreatesOneWhiteOneOneSoldierPerOpponent() {
        val d = scenarioWithPlayers(2, "Grizzly Bears")
        val p = d.player1
        val commander = d.castCommander(p)

        assertTrue(d.permanentsNamed(p, name).contains(commander))
        assertEquals(2, d.projected.getPower(commander))
        assertEquals(4, d.projected.getToughness(commander))
        assertTrue(d.projected.hasSubtype(commander, "HUMAN"))
        assertTrue(d.projected.hasSubtype(commander, "SOLDIER"))

        val tokens = d.soldierTokens(p)
        assertEquals(1, tokens.size)
        assertTrue(d.projected.hasColor(tokens.single(), Color.WHITE))
        assertEquals(1, d.projected.getPower(tokens.single()))
        assertEquals(1, d.projected.getToughness(tokens.single()))
        assertTrue(d.soldierTokens(d.player2).isEmpty())
    }

    @Test
    fun entersTriggerCreatesExactlyOneSoldierForEachOfTwoOpponents() {
        val d = customScenario(CompanyCommander)
        val players = d.initMultiplayer(decks = List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val p = players[0]
        d.castCommander(p)

        assertEquals(2, d.soldierTokens(p).size)
        assertTrue(d.soldierTokens(players[1]).isEmpty())
        assertTrue(d.soldierTokens(players[2]).isEmpty())
        for (token in d.soldierTokens(p)) {
            assertTrue(d.projected.hasColor(token, Color.WHITE))
            assertEquals(1, d.projected.getPower(token))
            assertEquals(1, d.projected.getToughness(token))
        }
    }

    @Test
    fun castingWithoutWhiteManaIsNotAllowed() {
        val d = scenarioWithPlayers(2)
        val p = d.player1
        val commander = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, commander).error)
        assertTrue(d.getHand(p).contains(commander))
        assertTrue(d.soldierTokens(p).isEmpty())
    }

    @Test
    fun castingWithoutBlackManaIsNotAllowed() {
        val d = scenarioWithPlayers(2)
        val p = d.player1
        val commander = d.putCardInHand(p, name)
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, commander).error)
        assertTrue(d.getHand(p).contains(commander))
        assertTrue(d.soldierTokens(p).isEmpty())
    }

    @Test
    fun oneGenericManaIsNotEnoughToCast() {
        val d = scenarioWithPlayers(2)
        val p = d.player1
        val commander = d.putCardInHand(p, name)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, commander).error)
        assertTrue(d.getHand(p).contains(commander))
        assertTrue(d.soldierTokens(p).isEmpty())
    }

    @Test
    fun whenCompanyCommanderAttacksAllYourCreaturesGainDeathtouchUntilEndOfTurn() {
        val d = scenarioWithPlayers(2, "Grizzly Bears")
        val p = d.player1
        val commander = d.putCreatureOnBattlefield(p, name)
        val otherCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        d.attack(p, commander, d.player2)

        assertTrue(d.projected.hasKeyword(commander, Keyword.DEATHTOUCH))
        assertTrue(d.projected.hasKeyword(otherCreature, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(opposingCreature, Keyword.DEATHTOUCH))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)

        assertFalse(d.projected.hasKeyword(commander, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(otherCreature, Keyword.DEATHTOUCH))
    }

    @Test
    fun attackingWithAnotherCreatureDoesNotTriggerCompanyCommander() {
        val d = scenarioWithPlayers(2, "Grizzly Bears")
        val p = d.player1
        val commander = d.putCreatureOnBattlefield(p, name)
        val otherCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        d.attack(p, otherCreature, d.player2)

        assertFalse(d.projected.hasKeyword(commander, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(otherCreature, Keyword.DEATHTOUCH))
    }

    @Test
    fun anOpponentsAttackingCompanyCommanderOnlyGivesTheirCreaturesDeathtouch() {
        val d = scenarioWithPlayers(2, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val opposingCommander = d.putCreatureOnBattlefield(opponent, name)
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val yourCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        d.attack(opponent, opposingCommander, p)

        assertTrue(d.projected.hasKeyword(opposingCommander, Keyword.DEATHTOUCH))
        assertTrue(d.projected.hasKeyword(opposingCreature, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(yourCreature, Keyword.DEATHTOUCH))
    }

    @Test
    fun creaturesEnteringAfterTheAttackTriggerResolvesDoNotGainDeathtouch() {
        val d = scenarioWithPlayers(2, "Grizzly Bears")
        val p = d.player1
        val commander = d.putCreatureOnBattlefield(p, name)

        d.attack(p, commander, d.player2)
        val lateCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(commander, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(lateCreature, Keyword.DEATHTOUCH))
    }
}
