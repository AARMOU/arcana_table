package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TheSwarmlord
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TheSwarmlordScenarioTest {
    private fun plusCounters(d: GameTestDriver, id: EntityId): Int =
        d.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun paySwarmlord(d: GameTestDriver, player: EntityId) {
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.BLUE)
        d.giveMana(player, Color.RED)
        d.giveColorlessMana(player, 3)
    }

    @Test fun castFromHandWithNoCommanderHistoryGetsNoCounters() {
        val d = customScenario(TheSwarmlord, "Island")
        val p = d.player1
        val card = d.putCardInHand(p, "The Swarmlord")
        paySwarmlord(d, p)

        assertNull(d.castSpell(p, card).error)
        d.resolveStack()
        val entered = d.state.getBattlefield().first {
            d.state.getEntity(it)?.get<CardComponent>()?.name == "The Swarmlord"
        }
        assertEquals(0, plusCounters(d, entered))
    }

    @Test fun firstCommandZoneCastIncludesTheCurrentCastAndEntersWithTwoCounters() {
        val d = customScenario(TheSwarmlord, "Island")
        val p = d.player1
        val card = d.putCardInCommandZone(p, "The Swarmlord")
        d.replaceState(d.state.updateEntity(card) { it.with(CommanderComponent(ownerId = p)) })
        paySwarmlord(d, p)

        assertNull(d.castSpell(p, card).error)
        d.resolveStack()
        assertEquals(2, plusCounters(d, card))
    }

    @Test fun partnerHistoryCountsForItsOwnerButNotAnOpponentsCommander() {
        val d = customScenario(TheSwarmlord, "Island")
        val p = d.player1
        val opponent = d.player2
        val card = d.putCardInCommandZone(p, "The Swarmlord")
        val partner = d.putCardInCommandZone(p, "Grizzly Bears")
        val enemyCommander = d.putCardInCommandZone(opponent, "Craw Wurm")
        d.replaceState(d.state
            .updateEntity(card) { it.with(CommanderComponent(ownerId = p)) }
            .updateEntity(partner) { it.with(CommanderComponent(ownerId = p, castsFromCommandZone = 1)) }
            .updateEntity(enemyCommander) { it.with(CommanderComponent(ownerId = opponent, castsFromCommandZone = 9)) })
        paySwarmlord(d, p)

        assertNull(d.castSpell(p, card).error)
        d.resolveStack()
        assertEquals(4, plusCounters(d, card))
    }

    @Test fun onlyAnotherCounteredCreatureYouControlDraws() {
        val d = customScenario(TheSwarmlord, "Island", "Island", "Island")
        val p = d.player1
        val opponent = d.player2
        val swarmlord = d.putCreatureOnBattlefield(p, "The Swarmlord")
        val countered = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val counterless = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val enemy = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.addComponent(swarmlord, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        d.addComponent(countered, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        d.addComponent(enemy, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val murder = d.putCardInHand(p, "Murder")
        val initialHand = d.getHandSize(p)

        d.giveMana(p, Color.BLACK, 3)
        assertNull(d.castSpell(p, murder, listOf(countered)).error)
        d.resolveStack()
        assertEquals(initialHand, d.getHandSize(p)) // Murder left; exactly one card was drawn.

        val secondMurder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 3)
        assertNull(d.castSpell(p, secondMurder, listOf(counterless)).error)
        d.resolveStack()
        assertEquals(initialHand, d.getHandSize(p)) // no draw for a creature without a counter

        val thirdMurder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 3)
        assertNull(d.castSpell(p, thirdMurder, listOf(enemy)).error)
        d.resolveStack()
        assertEquals(initialHand, d.getHandSize(p)) // no trigger for an opponent's creature
        assertEquals(true, countered in d.state.getZone(p, Zone.GRAVEYARD))
        assertEquals(true, counterless in d.state.getZone(p, Zone.GRAVEYARD))
        assertEquals(true, enemy in d.state.getZone(opponent, Zone.GRAVEYARD))
        assertEquals(1, plusCounters(d, swarmlord))
    }
}
