package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SliverGravemother
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.permanentsNamed
import com.arcanatable.customcards.support.projected
import com.arcanatable.customcards.support.resolveAll
import com.arcanatable.customcards.support.zoneNames
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class SliverGravemotherScenarioTest {
    @Test
    fun grantedEncorePaysManaValueAndCreatesOneHastyAttackerPerOpponentThatDiesAtEndStep() {
        val d = CardPool.driver("Sentinel Sliver")
        d.registerCard(SliverGravemother)
        val players = d.initMultiplayer(
            decks = List(4) { Deck.of("Plains" to 40) },
            startingPlayer = 0,
        )
        val player = players[0]
        val opponents = players.drop(1).toSet()
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, player)
        d.putPermanentOnBattlefield(player, "Sliver Gravemother")
        val sentinel = d.putCardInGraveyard(player, "Sentinel Sliver")

        fun encoreActions() = d.legalActions(player).mapNotNull { it.action as? ActivateAbility }
            .filter { it.sourceId == sentinel }

        assertTrue("Two mana value is not payable yet", encoreActions().isEmpty())
        d.giveColorlessMana(player, 2)
        val action = encoreActions().single()
        d.submitSuccess(action)

        assertEquals(listOf("Sentinel Sliver"), d.zoneNames(player, Zone.EXILE))
        d.resolveAll()

        val tokens = d.permanentsNamed(player, "Sentinel Sliver")
            .filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
        assertEquals("One copy for each of the three opponents", 3, tokens.size)
        val defenders = tokens.map { id ->
            val attacker = d.state.getEntity(id)?.get<AttackingComponent>()
            assertNotNull("Each token should enter attacking", attacker)
            assertTrue(d.projected.hasKeyword(id, com.wingedsheep.sdk.core.Keyword.HASTE))
            attacker!!.defenderId
        }.toSet()
        assertEquals("Each token attacks a different opponent", opponents, defenders)

        d.passPriorityUntil(Step.END, player)
        d.resolveAll()
        assertTrue("The encore tokens should be sacrificed at end step", d.permanentsNamed(player, "Sentinel Sliver")
            .none { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(listOf("Sentinel Sliver"), d.zoneNames(player, Zone.EXILE))
    }
}
