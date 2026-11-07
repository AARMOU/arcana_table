package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DeathsPresence
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class DeathsPresenceIndependentCertificationTest {
    private val name = "Death's Presence"

    private fun GameTestDriver.plusOneCounters(creature: EntityId): Int =
        state.getEntity(creature)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun castingRequiresFiveGenericManaAndGreenMana() {
        val withoutGreen = customScenario(DeathsPresence)
        val card = withoutGreen.putCardInHand(withoutGreen.player1, name)
        withoutGreen.giveColorlessMana(withoutGreen.player1, 5)

        assertNotNull(withoutGreen.castSpell(withoutGreen.player1, card).error)
        assertTrue(withoutGreen.zoneNames(withoutGreen.player1, Zone.HAND).contains(name))
        withoutGreen.giveMana(withoutGreen.player1, Color.GREEN)
        assertNull(withoutGreen.castSpell(withoutGreen.player1, card).error)
        withoutGreen.resolveAll()
        assertTrue(withoutGreen.permanentsNamed(withoutGreen.player1, name).contains(card))
        assertTrue(withoutGreen.projected.hasType(card, "ENCHANTMENT"))

        val shortOnGeneric = customScenario(DeathsPresence)
        val underpaid = shortOnGeneric.putCardInHand(shortOnGeneric.player1, name)
        shortOnGeneric.giveMana(shortOnGeneric.player1, Color.GREEN)
        shortOnGeneric.giveColorlessMana(shortOnGeneric.player1, 4)

        assertNotNull(shortOnGeneric.castSpell(shortOnGeneric.player1, underpaid).error)
        assertTrue(shortOnGeneric.zoneNames(shortOnGeneric.player1, Zone.HAND).contains(name))
        shortOnGeneric.giveColorlessMana(shortOnGeneric.player1, 1)
        assertNull(shortOnGeneric.castSpell(shortOnGeneric.player1, underpaid).error)
        shortOnGeneric.resolveAll()
        assertTrue(shortOnGeneric.permanentsNamed(shortOnGeneric.player1, name).contains(underpaid))
    }

    @Test
    fun yourCreatureDyingAddsExactlyItsPowerInCountersToYourTarget() {
        val d = customScenario(DeathsPresence, "Murder", "Grizzly Bears", "Giant Spider")
        val player = d.player1
        d.putPermanentOnBattlefield(player, name)
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(player, "Giant Spider")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(victim))).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Giant Spider"))
        assertEquals(2, d.plusOneCounters(target))
        assertEquals(4, d.projected.getPower(target))
        assertEquals(4, d.projected.getToughness(target))
    }

    @Test
    fun anOpponentsCreatureDyingDoesNotTriggerDeathsPresence() {
        val d = customScenario(DeathsPresence, "Murder", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putPermanentOnBattlefield(player, name)
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(victim))).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertEquals(0, d.plusOneCounters(target))
        assertEquals(2, d.projected.getPower(target))
    }

    @Test
    fun anExiledCreatureDoesNotTriggerDeathsPresence() {
        val d = customScenario(DeathsPresence, "Path to Exile", "Grizzly Bears")
        val player = d.player1
        d.putPermanentOnBattlefield(player, name)
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val path = d.putCardInHand(player, "Path to Exile")
        d.giveMana(player, Color.WHITE)

        assertNull(d.castSpell(player, path, listOf(victim)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Grizzly Bears"))
        assertEquals(0, d.plusOneCounters(target))
        assertEquals(2, d.projected.getPower(target))
    }

    @Test
    fun theTargetMustBeACreatureYouControl() {
        val d = customScenario(DeathsPresence, "Murder", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putPermanentOnBattlefield(player, name)
        val victim = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpellWithTargets(player, murder, listOf(ChosenTarget.Permanent(victim))).error)
        d.resolveAll(Choices(targets = listOf(opposingCreature)))

        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertEquals(0, d.plusOneCounters(opposingCreature))
        assertEquals(2, d.projected.getPower(opposingCreature))
    }
}
