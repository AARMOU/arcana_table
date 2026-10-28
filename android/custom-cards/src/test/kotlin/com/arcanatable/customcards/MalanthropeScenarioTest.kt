package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Malanthrope
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class MalanthropeScenarioTest {
    @Test
    fun enteringExilesOnlyTheTargetPlayersGraveyardAndCountsItsCreatureCards() {
        val d = customScenario(Malanthrope, "Grizzly Bears", "Forest")
        val player = d.player1
        val target = d.player2
        d.putCardInGraveyard(target, "Grizzly Bears")
        d.putCardInGraveyard(target, "Grizzly Bears")
        d.putCardInGraveyard(target, "Forest")
        d.putCardInGraveyard(player, "Grizzly Bears")
        val malanthrope = d.putCardInHand(player, "Malanthrope")
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, malanthrope).error)
        d.resolveAll(Choices(targets = listOf(target)))

        val permanent = d.findPermanent(player, "Malanthrope")!!
        assertTrue(d.projected.hasKeyword(permanent, Keyword.FLYING))
        assertEquals(
            2,
            d.state.getEntity(permanent)!!.get<CountersComponent>()!!
                .getCount(CounterType.PLUS_ONE_PLUS_ONE),
        )
        assertEquals(
            listOf("Forest", "Grizzly Bears", "Grizzly Bears"),
            d.zoneNames(target, Zone.EXILE).sorted(),
        )
        assertTrue(d.zoneNames(target, Zone.GRAVEYARD).isEmpty())
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(player, Zone.GRAVEYARD))
    }

    @Test
    fun exilingAGraveyardWithNoCreatureCardsAddsNoCounters() {
        val d = customScenario(Malanthrope, "Grizzly Bears", "Forest")
        val player = d.player1
        val target = d.player2
        d.putCardInGraveyard(target, "Forest")
        d.putCardInGraveyard(player, "Grizzly Bears")
        val malanthrope = d.putCardInHand(player, "Malanthrope")
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, malanthrope).error)
        d.resolveAll(Choices(targets = listOf(target)))

        val permanent = d.findPermanent(player, "Malanthrope")!!
        assertEquals(
            0,
            d.state.getEntity(permanent)!!.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0,
        )
        assertEquals(listOf("Forest"), d.zoneNames(target, Zone.EXILE))
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(player, Zone.GRAVEYARD))
    }

    @Test
    fun mayTargetYourOwnGraveyard() {
        val d = customScenario(Malanthrope, "Grizzly Bears", "Forest")
        val player = d.player1
        d.putCardInGraveyard(player, "Grizzly Bears")
        d.putCardInGraveyard(player, "Forest")
        d.putCardInGraveyard(d.player2, "Grizzly Bears")
        val malanthrope = d.putCardInHand(player, "Malanthrope")
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, malanthrope).error)
        d.resolveAll(Choices(targets = listOf(player)))

        val permanent = d.findPermanent(player, "Malanthrope")!!
        assertEquals(
            1,
            d.state.getEntity(permanent)!!.get<CountersComponent>()!!
                .getCount(CounterType.PLUS_ONE_PLUS_ONE),
        )
        assertEquals(listOf("Forest", "Grizzly Bears"), d.zoneNames(player, Zone.EXILE).sorted())
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(d.player2, Zone.GRAVEYARD))
    }
}
