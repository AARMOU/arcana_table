package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeathsPresence
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class DeathsPresenceIndependentCertificationTest {
    private fun GameTestDriver.plusOneCounters(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun fiveGenericAndGreenManaCastDeathsPresence() {
        val d = customScenario(DeathsPresence)
        val p = d.player1
        val card = d.putCardInHand(p, "Death's Presence")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.GREEN)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Death's Presence").contains(card))
        assertEquals(0, d.manaPool(p).green)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun sixColorlessManaCannotPayTheGreenSymbol() {
        val d = customScenario(DeathsPresence)
        val p = d.player1
        val card = d.putCardInHand(p, "Death's Presence")
        d.giveColorlessMana(p, 6)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Death's Presence"))
        assertTrue(d.permanentsNamed(p, "Death's Presence").isEmpty())
        assertEquals(6, d.manaPool(p).colorless)
    }

    @Test
    fun yourDyingCreatureAddsItsPowerInCountersToYourTargetCreature() {
        val d = customScenario(DeathsPresence, "Colossal Dreadmaw", "Grizzly Bears", "Murder")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dreadmaw = d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(dreadmaw)).error)
        d.resolveAll(Choices(targets = listOf(bear)))

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Colossal Dreadmaw"))
        assertEquals(6, d.plusOneCounters(bear))
        assertEquals(8, d.projected.getPower(bear))
        assertEquals(8, d.projected.getToughness(bear))
    }

    @Test
    fun anOpponentsCreatureDyingDoesNotTriggerDeathsPresence() {
        val d = customScenario(DeathsPresence, "Colossal Dreadmaw", "Grizzly Bears", "Murder")
        val p = d.player1
        val opponent = d.player2
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dreadmaw = d.putCreatureOnBattlefield(opponent, "Colossal Dreadmaw")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(dreadmaw)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Colossal Dreadmaw"))
        assertEquals(0, d.plusOneCounters(bear))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(2, d.projected.getToughness(bear))
    }

    @Test
    fun exilingYourCreatureDoesNotTriggerDeathsPresence() {
        val d = customScenario(DeathsPresence, "Colossal Dreadmaw", "Grizzly Bears", "Swords to Plowshares")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dreadmaw = d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val swords = d.putCardInHand(p, "Swords to Plowshares")
        d.giveMana(p, Color.WHITE)

        assertNull(d.castSpell(p, swords, listOf(dreadmaw)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Colossal Dreadmaw"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Colossal Dreadmaw"))
        assertEquals(0, d.plusOneCounters(bear))
    }

    @Test
    fun theTriggeredAbilityCannotTargetAnOpponentsCreature() {
        val d = customScenario(DeathsPresence, "Colossal Dreadmaw", "Grizzly Bears", "Giant Spider", "Murder")
        val p = d.player1
        val opponent = d.player2
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dreadmaw = d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spider = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(dreadmaw)).error)
        d.resolveAll(Choices(targets = listOf(spider)))

        assertEquals(6, d.plusOneCounters(bear))
        assertEquals(0, d.plusOneCounters(spider))
    }

    @Test
    fun theTriggeredAbilityCannotTargetYourNoncreaturePermanent() {
        val d = customScenario(DeathsPresence, "Colossal Dreadmaw", "Grizzly Bears", "Sol Ring", "Murder")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Death's Presence")
        val dreadmaw = d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(dreadmaw)).error)
        d.resolveAll(Choices(targets = listOf(ring)))

        assertEquals(6, d.plusOneCounters(bear))
        assertEquals(0, d.plusOneCounters(ring))
    }
}
