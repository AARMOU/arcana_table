package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeployToTheFront
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class DeployToTheFrontIndependentCertificationTest {
    private fun GameTestDriver.soldierTokens(player: EntityId) =
        getPermanents(player).filter { token ->
            state.getEntity(token)?.has<TokenComponent>() == true &&
                projected.hasType(token, "CREATURE") &&
                projected.hasSubtype(token, "SOLDIER")
        }

    private fun GameTestDriver.giveDeployToTheFrontMana(player: EntityId) {
        giveColorlessMana(player, 5)
        giveMana(player, Color.WHITE, 2)
    }

    @Test
    fun createsOneWhiteOneOneSoldierPerCreatureOnBothBattlefields() {
        val d = customScenario(DeployToTheFront, "Grizzly Bears", "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putPermanentOnBattlefield(player, "Sol Ring")
        val spell = d.putCardInHand(player, "Deploy to the Front")
        d.giveDeployToTheFrontMana(player)

        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()

        val tokens = d.soldierTokens(player)
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertTrue(d.projected.hasColor(token, Color.WHITE))
            assertTrue(d.projected.hasType(token, "CREATURE"))
            assertTrue(d.projected.hasSubtype(token, "SOLDIER"))
            assertEquals(1, d.projected.getPower(token))
            assertEquals(1, d.projected.getToughness(token))
        }
        assertTrue(d.soldierTokens(opponent).isEmpty())
    }

    @Test
    fun countsCreaturesPresentWhenTheSorceryResolves() {
        val d = customScenario(DeployToTheFront, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(player, "Deploy to the Front")
        d.giveDeployToTheFrontMana(player)

        assertNull(d.castSpell(player, spell).error)
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.resolveAll()

        assertEquals(3, d.soldierTokens(player).size)
    }

    @Test
    fun createsNoTokensWhenThereAreNoCreaturesOnTheBattlefield() {
        val d = customScenario(DeployToTheFront)
        val player = d.player1
        val spell = d.putCardInHand(player, "Deploy to the Front")
        d.giveDeployToTheFrontMana(player)

        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()

        assertTrue(d.soldierTokens(player).isEmpty())
    }

    @Test
    fun cannotCastWithoutTwoWhiteManaEvenWithSevenTotalMana() {
        val d = customScenario(DeployToTheFront)
        val player = d.player1
        val spell = d.putCardInHand(player, "Deploy to the Front")
        d.giveColorlessMana(player, 6)
        d.giveMana(player, Color.WHITE)

        assertNotNull(d.castSpell(player, spell).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Deploy to the Front"))
        assertTrue(d.soldierTokens(player).isEmpty())
    }

    @Test
    fun cannotCastDuringTheOpponentsTurn() {
        val d = customScenario(DeployToTheFront)
        val player = d.player1
        val spell = d.putCardInHand(player, "Deploy to the Front")
        d.giveDeployToTheFrontMana(player)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        assertNotNull(d.castSpell(player, spell).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Deploy to the Front"))
        assertTrue(d.soldierTokens(player).isEmpty())
    }
}
